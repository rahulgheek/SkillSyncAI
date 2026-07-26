package com.skillsync.skillsync_ai.ai.services;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfResumeExtractorService {

    private final Cloudinary cloudinary;

    public String extractTextFromUrl(String pdfUrl) {
        log.info("Downloading and extracting text from PDF: {}", pdfUrl);
        try {
            String downloadUrl = pdfUrl;
            
            // Generate a signed URL if it's a Cloudinary URL to bypass ACL restrictions
            if (pdfUrl.contains("res.cloudinary.com")) {
                try {
                    String resourceType = pdfUrl.contains("/raw/upload") ? "raw" : "image";
                    String afterUpload = pdfUrl.substring(pdfUrl.indexOf("upload/") + 7);
                    // Handle optional version string (v1234567/)
                    String publicId = afterUpload;
                    if (afterUpload.matches("^v\\d+/.*")) {
                        publicId = afterUpload.substring(afterUpload.indexOf("/") + 1);
                    }
                    
                    downloadUrl = cloudinary.url()
                            .resourceType(resourceType)
                            .type("upload")
                            .signed(true)
                            .generate(publicId);
                    log.info("Generated signed Cloudinary URL for public ID: {}", publicId);
                } catch (Exception e) {
                    log.warn("Failed to generate signed Cloudinary URL, falling back to original URL: {}", e.getMessage());
                }
            }

            URL url = new URL(downloadUrl);
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (InputStream inputStream = connection.getInputStream()) {
                byte[] pdfBytes = inputStream.readAllBytes();

                try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String extractedText = stripper.getText(document);

                    if (extractedText == null || extractedText.trim().isEmpty()) {
                        throw new RuntimeException("Extracted text is empty. The PDF might be scanned/image-based.");
                    }

                    return extractedText;
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract text from PDF URL [{}]: {}", pdfUrl, e.getMessage());
            throw new RuntimeException("PDF Extraction failed", e);
        }
    }
}