package com.skillsync.skillsync_ai.matching.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.AiMatchingException;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import com.skillsync.skillsync_ai.common.events.RecommendationsReadyEvent;
import com.skillsync.skillsync_ai.matching.entity.RecommendationJob;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobType;
import com.skillsync.skillsync_ai.matching.repo.CandidateShortlistRepository;
import com.skillsync.skillsync_ai.matching.repo.CandidateShortlistRepository.ProjectCandidateRow;
import com.skillsync.skillsync_ai.matching.repo.RecommendationJobRepository;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentAchievementRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillProjection;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationGenerationWorkerTest {

    @Mock
    private RecommendationJobRepository jobRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectRoleRepository projectRoleRepository;

    @Mock
    private CandidateShortlistRepository shortlistRepository;

    @Mock
    private StudentSkillRepository studentSkillRepository;

    @Mock
    private StudentAchievementRepository studentAchievementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiMatchingGateway aiMatchingGateway;

    @Mock
    private RecommendationScoringService scoringService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private RecommendationGenerationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new RecommendationGenerationWorker(
                jobRepository, projectRepository, projectRoleRepository, shortlistRepository,
                studentSkillRepository, studentAchievementRepository, userRepository,
                aiMatchingGateway, scoringService, eventPublisher, transactionTemplate,
                new ObjectMapper());
        ReflectionTestUtils.setField(worker, "maxShortlistCandidates", 50);
        ReflectionTestUtils.setField(worker, "maxAiCandidates", 20);
        ReflectionTestUtils.setField(worker, "maxAiProjects", 10);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void process_fallsBackToDeterministicScoresWhenAiFails() {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        RecommendationJob job = RecommendationJob.builder()
                .id(jobId)
                .type(RecommendationJobType.PROJECT_CANDIDATES)
                .subjectId(projectId)
                .projectId(projectId)
                .requestedBy(ownerId)
                .status(RecommendationJobStatus.PENDING)
                .requestedAt(Instant.now())
                .build();

        Project project = Project.builder()
                .id(projectId)
                .ownerId(ownerId)
                .collegeId(UUID.randomUUID())
                .title("Project")
                .description("Description")
                .requiredSkills(List.of(new RequiredSkill(skillId, "Java", "INTERMEDIATE")))
                .build();

        ProjectCandidateRow row = new ProjectCandidateRow(
                candidateId, profileId, 1, 1, 3.0, 2, "Java");

        StudentSkillProjection skill = mock(StudentSkillProjection.class);
        when(skill.getUserId()).thenReturn(candidateId);
        when(skill.getSkillName()).thenReturn("Java");
        when(skill.getSkillLevel()).thenReturn("ADVANCED");

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(shortlistRepository.findCandidatesForProject(
                eq(projectId), any(UUID.class), eq(ownerId), anyInt()))
                .thenReturn(List.of(row));
        when(studentSkillRepository.findSkillsForUsers(anyList())).thenReturn(List.of(skill));
        when(studentAchievementRepository.findAchievementsForUsers(anyList())).thenReturn(List.of());
        when(aiMatchingGateway.scoreCandidates(eq(projectId), anyString(), anyList()))
                .thenThrow(new AiMatchingException("Groq is down"));
        when(scoringService.persistProjectScores(
                eq(projectId), anyString(), anyList(), anyMap()))
                .thenReturn(1);

        worker.process(jobId);

        assertThat(job.getStatus()).isEqualTo(RecommendationJobStatus.READY);
        assertThat(job.isAiApplied()).isFalse();
        assertThat(job.getCandidateCount()).isEqualTo(1);
        assertThat(job.getAiCandidateCount()).isEqualTo(1);
        assertThat(job.getError()).contains("AI re-ranking unavailable");
        assertThat(job.getLockKey()).isNull();
        assertThat(job.getFinishedAt()).isNotNull();

        ArgumentCaptor<Map<UUID, RecommendationScoringService.DeterministicScore>> captor =
                ArgumentCaptor.forClass(Map.class);
        verify(scoringService).persistProjectScores(
                eq(projectId), anyString(), anyList(), captor.capture());

        RecommendationScoringService.DeterministicScore deterministic =
                captor.getValue().get(candidateId);
        assertThat(deterministic).isNotNull();
        // Full overlap + ADVANCED (3.0) + 2 endorsements -> 60 + 20 + 2 = 82.
        assertThat(deterministic.score()).isEqualByComparingTo("82.00");
        assertThat(deterministic.matchedSkills()).containsExactly("Java");

        verify(eventPublisher).publishEvent(any(RecommendationsReadyEvent.class));
    }
}
