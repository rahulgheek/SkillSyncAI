package com.skillsync.skillsync_ai.ai.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.AiMatchingException;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredCandidate;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredProject;
import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.ProjectProfileForAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchSkillGapAnalyzerTest {

    @Mock
    private GroqApiService groqApiService;

    @Mock
    private RecommendationPromptFactory promptFactory;

    private BatchSkillGapAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BatchSkillGapAnalyzer(groqApiService, promptFactory, new ObjectMapper());
    }

    @Test
    void scoreCandidates_filtersUnknownIdsAndNormalisesConfidence() {
        UUID candidateId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        String response = """
                {"matches":[
                  {"userId":"%s","semanticScore":88.5,"confidence":"high",
                   "matchedSkills":["Java"],"rationale":"Strong backend fit."},
                  {"userId":"%s","semanticScore":99,"confidence":"HIGH",
                   "matchedSkills":[],"rationale":"Invented candidate."}
                ]}
                """.formatted(candidateId, unknownId);

        when(promptFactory.buildBatchMatchPrompt(anyString(), anyList())).thenReturn("prompt");
        when(groqApiService.analyzeBatchMatch("prompt")).thenReturn(Mono.just(response));

        List<ScoredCandidate> scores = analyzer.scoreCandidates(
                UUID.randomUUID(), "{}",
                List.of(new CandidateProfileForAi(candidateId, UUID.randomUUID(), List.of(), List.of())));

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).userId()).isEqualTo(candidateId);
        assertThat(scores.get(0).semanticScore()).isEqualByComparingTo("88.50");
        assertThat(scores.get(0).confidence()).isEqualTo(RecommendationConfidence.HIGH);
        assertThat(scores.get(0).rationale()).isEqualTo("Strong backend fit.");
    }

    @Test
    void scoreCandidates_defaultsInvalidFieldsInsteadOfFailing() {
        UUID candidateId = UUID.randomUUID();
        String response = """
                {"matches":[
                  {"userId":"%s","semanticScore":null,"confidence":"SUPER_HIGH",
                   "matchedSkills":null,"rationale":null}
                ]}
                """.formatted(candidateId);

        when(promptFactory.buildBatchMatchPrompt(anyString(), anyList())).thenReturn("prompt");
        when(groqApiService.analyzeBatchMatch("prompt")).thenReturn(Mono.just(response));

        List<ScoredCandidate> scores = analyzer.scoreCandidates(
                UUID.randomUUID(), "{}",
                List.of(new CandidateProfileForAi(candidateId, null, List.of(), List.of())));

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).semanticScore()).isEqualByComparingTo("0.00");
        assertThat(scores.get(0).confidence()).isEqualTo(RecommendationConfidence.LOW);
        assertThat(scores.get(0).matchedSkills()).isEmpty();
        assertThat(scores.get(0).rationale()).isEqualTo("No rationale provided.");
    }

    @Test
    void scoreCandidates_blankResponseRaisesAiMatchingException() {
        when(promptFactory.buildBatchMatchPrompt(anyString(), anyList())).thenReturn("prompt");
        when(groqApiService.analyzeBatchMatch("prompt")).thenReturn(Mono.just(""));

        assertThatThrownBy(() -> analyzer.scoreCandidates(
                UUID.randomUUID(), "{}",
                List.of(new CandidateProfileForAi(UUID.randomUUID(), null, List.of(), List.of()))))
                .isInstanceOf(AiMatchingException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void scoreCandidates_malformedJsonRaisesAiMatchingException() {
        when(promptFactory.buildBatchMatchPrompt(anyString(), anyList())).thenReturn("prompt");
        when(groqApiService.analyzeBatchMatch("prompt")).thenReturn(Mono.just("not-json"));

        assertThatThrownBy(() -> analyzer.scoreCandidates(
                UUID.randomUUID(), "{}",
                List.of(new CandidateProfileForAi(UUID.randomUUID(), null, List.of(), List.of()))))
                .isInstanceOf(AiMatchingException.class)
                .hasMessageContaining("parse");
    }

    @Test
    void scoreProjects_filtersUnknownIds() {
        UUID projectId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();
        String response = """
                {"matches":[
                  {"projectId":"%s","semanticScore":77,"confidence":"MEDIUM",
                   "matchedSkills":["Spring"],"rationale":"Good fit."},
                  {"projectId":"%s","semanticScore":99,"confidence":"HIGH",
                   "matchedSkills":[],"rationale":"Invented project."}
                ]}
                """.formatted(projectId, unknownId);

        when(promptFactory.buildBatchProjectsPrompt(anyString(), anyList())).thenReturn("prompt");
        when(groqApiService.analyzeBatchMatch("prompt")).thenReturn(Mono.just(response));

        List<ScoredProject> scores = analyzer.scoreProjects(
                UUID.randomUUID(), "{}",
                List.of(new ProjectProfileForAi(projectId, "Title", "Description", List.of("Java"))));

        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).projectId()).isEqualTo(projectId);
        assertThat(scores.get(0).confidence()).isEqualTo(RecommendationConfidence.MEDIUM);
    }
}
