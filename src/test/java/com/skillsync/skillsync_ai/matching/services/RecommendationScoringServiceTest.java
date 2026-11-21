package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredCandidate;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredProject;
import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.matching.entity.ProjectRecommendation;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationStatus;
import com.skillsync.skillsync_ai.matching.repo.RecommendationRepository;
import com.skillsync.skillsync_ai.project.ProjectGateway;
import com.skillsync.skillsync_ai.userprofile.UserProfileGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationScoringServiceTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private UserProfileGateway userProfileGateway;

    @Mock
    private ProjectGateway projectGateway;

    private RecommendationScoringService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationScoringService(
                recommendationRepository, userProfileGateway, projectGateway);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistProjectScores_blendsAiAndDeterministicAndFallsBackForMissingAi() {
        UUID projectId = UUID.randomUUID();
        UUID aiUser = UUID.randomUUID();
        UUID fallbackUser = UUID.randomUUID();

        Map<UUID, RecommendationScoringService.DeterministicScore> deterministic = Map.of(
                aiUser, new RecommendationScoringService.DeterministicScore(
                        new BigDecimal("50.00"), List.of("Java")),
                fallbackUser, new RecommendationScoringService.DeterministicScore(
                        new BigDecimal("80.00"), List.of("Spring")));

        List<ScoredCandidate> aiScores = List.of(new ScoredCandidate(
                aiUser, new BigDecimal("90.00"), RecommendationConfidence.HIGH,
                List.of("Java"), "Excellent backend fit."));

        when(recommendationRepository.findAllByProjectIdAndUserIdIn(eq(projectId), anyCollection()))
                .thenReturn(List.of());
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int persisted = service.persistProjectScores(
                projectId, "v3", aiScores, deterministic);

        assertThat(persisted).isEqualTo(2);

        ArgumentCaptor<List<ProjectRecommendation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(recommendationRepository).saveAll(captor.capture());
        List<ProjectRecommendation> saved = captor.getValue();

        ProjectRecommendation ai = saved.stream()
                .filter(r -> r.getUserId().equals(aiUser)).findFirst().orElseThrow();
        assertThat(ai.getSemanticScore()).isEqualByComparingTo("90.00");
        assertThat(ai.getDeterministicScore()).isEqualByComparingTo("50.00");
        // 0.8 * 90 + 0.2 * 50
        assertThat(ai.getFinalScore()).isEqualByComparingTo("82.00");
        assertThat(ai.getConfidence()).isEqualTo(RecommendationConfidence.HIGH);
        assertThat(ai.getRationale()).isEqualTo("Excellent backend fit.");
        assertThat(ai.getStatus()).isEqualTo(RecommendationStatus.GENERATED);

        ProjectRecommendation fallback = saved.stream()
                .filter(r -> r.getUserId().equals(fallbackUser)).findFirst().orElseThrow();
        assertThat(fallback.getSemanticScore()).isEqualByComparingTo("80.00");
        assertThat(fallback.getFinalScore()).isEqualByComparingTo("80.00");
        assertThat(fallback.getMatchedSkills()).containsExactly("Spring");
        assertThat(fallback.getRationale()).contains("verified skill overlap");
        assertThat(fallback.getStatus()).isEqualTo(RecommendationStatus.GENERATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistProjectScores_updatesExistingRowsInTheSameBatch() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectRecommendation existing = ProjectRecommendation.builder()
                .id(UUID.randomUUID())
                .projectId(projectId)
                .userId(userId)
                .build();

        when(recommendationRepository.findAllByProjectIdAndUserIdIn(eq(projectId), anyCollection()))
                .thenReturn(List.of(existing));
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.persistProjectScores(
                projectId, "v3", List.of(),
                Map.of(userId, new RecommendationScoringService.DeterministicScore(
                        new BigDecimal("45.00"), List.of("Java"))));

        ArgumentCaptor<List<ProjectRecommendation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(recommendationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0)).isSameAs(existing);
        assertThat(existing.getFinalScore()).isEqualByComparingTo("45.00");
        assertThat(existing.getStatus()).isEqualTo(RecommendationStatus.GENERATED);
    }

    @Test
    void persistProjectScores_emptyMapDoesNotTouchTheDatabase() {
        int persisted = service.persistProjectScores(
                UUID.randomUUID(), "v3", List.of(), Map.of());

        assertThat(persisted).isZero();
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void persistStudentScores_usesTheSameBatchContract() {
        UUID studentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(recommendationRepository.findAllByUserIdAndProjectIdIn(eq(studentId), anyCollection()))
                .thenReturn(List.of());
        when(recommendationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int persisted = service.persistStudentScores(
                studentId, "v3",
                List.of(new ScoredProject(projectId, new BigDecimal("70.00"),
                        RecommendationConfidence.MEDIUM, List.of("Java"), "Good fit.")),
                Map.of(projectId, new RecommendationScoringService.DeterministicScore(
                        new BigDecimal("60.00"), List.of("Java"))));

        assertThat(persisted).isEqualTo(1);
        ArgumentCaptor<List<ProjectRecommendation>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(recommendationRepository).saveAll(captor.capture());
        ProjectRecommendation saved = captor.getValue().get(0);
        // 0.8 * 70 + 0.2 * 60 = 68
        assertThat(saved.getFinalScore()).isEqualByComparingTo("68.00");
        assertThat(saved.getUserId()).isEqualTo(studentId);
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getConfidence()).isEqualTo(RecommendationConfidence.MEDIUM);
        verify(recommendationRepository, never()).findByProjectIdAndUserId(any(), any());
    }
}
