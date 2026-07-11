package com.skillsync.skillsync_ai.ai.services;

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
public class PdfResumeExtractorService {

    public String extractTextFromUrl(String pdfUrl) {
        log.info("Downloading and extracting text from PDF: {}", pdfUrl);
        try {
            URL url = new URL(pdfUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (InputStream inputStream = connection.getInputStream()) {
                // PDFBox 3.0+ requires reading bytes for the new Loader
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