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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.config.KafkaConfig;
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
    private final PdfResumeExtractorService pdfResumeExtractor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaConfig.RESUME_UPLOADED_TOPIC, groupId = "ai-module-group")
    void onResumeUploaded(ResumeUploadedEvent event) {
        log.info("AI Module intercepted ResumeUploadedEvent via Kafka for User ID: {}", event.userId());

        SkillAnalysisResult result = SkillAnalysisResult.builder()
                .userId(event.userId())
                .studentProfileId(event.userId()) // Workaround for DB schema constraint anomaly
                .sourceResumeUrl(event.resumeUrl())
                .status(AnalysisStatus.PENDING)
                .build();

        result = analysisRepository.save(result);

        try {
            // FIXED: Removed double semicolon
            String extractedText = pdfResumeExtractor.extractTextFromUrl(event.resumeUrl());

            var geminiResponse = geminiApiService.analyzeResume(extractedText).block();

            // FIXED: Using getExtractedText()
            if (geminiResponse == null || geminiResponse.getExtractedText() == null) {
                throw new RuntimeException("No response from Gemini");
            }

            // FIXED: Using getExtractedText()
            Map<String, Object> parsedData = geminiApiService.parseJsonToMap(geminiResponse.getExtractedText());

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

            ResumeAnalysisCompletedEvent completedEvent = new ResumeAnalysisCompletedEvent(event.userId(), finalSkills, result.getSummary());
            kafkaTemplate.send(KafkaConfig.RESUME_ANALYZED_TOPIC, event.userId().toString(), objectMapper.writeValueAsString(completedEvent));
            log.info("Gemini successfully analyzed resume for User ID: {}", event.userId());

        } catch (Exception e) {
            log.error("AI Analysis failed: {}", e.getMessage());
            result.setStatus(AnalysisStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            analysisRepository.save(result);
        }
    }
}