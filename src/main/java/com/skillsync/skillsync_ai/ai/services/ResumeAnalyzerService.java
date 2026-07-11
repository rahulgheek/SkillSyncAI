package com.skillsync.skillsync_ai.ai.services;

import com.skillsync.skillsync_ai.ai.entity.ExtractedSkill;
import com.skillsync.skillsync_ai.ai.entity.SkillAnalysisResult;
import com.skillsync.skillsync_ai.ai.entity.enums.AnalysisStatus;
import com.skillsync.skillsync_ai.ai.repo.SkillAnalysisResultRepository;
import com.skillsync.skillsync_ai.common.events.ResumeAnalysisCompletedEvent;
import com.skillsync.skillsync_ai.common.events.ResumeUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalyzerService {

    private final SkillAnalysisResultRepository analysisRepository;
    private final GeminiApiService geminiApiService;
    private final ApplicationEventPublisher eventPublisher;
    private final PdfResumeExtractorService pdfResumeExtractor;

    @ApplicationModuleListener
    void onResumeUploaded(ResumeUploadedEvent event) {
        log.info("AI Module intercepted ResumeUploadedEvent for User ID: {}", event.userId());

        SkillAnalysisResult result = SkillAnalysisResult.builder()
                .userId(event.userId()) // Fixed boundary ID
                .sourceResumeUrl(event.resumeUrl())
                .status(AnalysisStatus.PENDING)
                .build();

        result = analysisRepository.save(result);

        try {
            // Placeholder: Replace with actual PDFBox extraction logic later
            String extractedText = pdfResumeExtractor.extractTextFromUrl(event.resumeUrl());;

            var geminiResponse = geminiApiService.analyzeResume(extractedText).block();
            if (geminiResponse == null) throw new RuntimeException("No response from Gemini");

            Map<String, Object> parsedData = geminiApiService.parseJsonToMap(geminiResponse.getResponseText());

            result.setSummary((String) parsedData.get("summary"));
            result.setRawGrokResponse(parsedData);

            List<Map<String, String>> rawSkills = (List<Map<String, String>>) parsedData.get("skills");
            List<ExtractedSkill> finalSkills = new ArrayList<>();

            if (rawSkills != null) {
                for (Map<String, String> skillMap : rawSkills) {
                    finalSkills.add(new ExtractedSkill(
                            skillMap.get("skillName"),
                            skillMap.get("confidence"),
                            skillMap.get("inferredLevel")
                    ));
                }
            }

            result.setExtractedSkills(finalSkills);
            result.setStatus(AnalysisStatus.COMPLETED);
            analysisRepository.save(result);

            // FIRE THE EVENT: Let the userprofile module know to update the database!
            eventPublisher.publishEvent(new ResumeAnalysisCompletedEvent(event.userId(), finalSkills));

            log.info("Gemini successfully analyzed resume for User ID: {}", event.userId());

        } catch (Exception e) {
            log.error("AI Analysis failed: {}", e.getMessage());
            result.setStatus(AnalysisStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            analysisRepository.save(result);
        }
    }
}