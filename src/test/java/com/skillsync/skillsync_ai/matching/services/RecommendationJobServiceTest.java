package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import com.skillsync.skillsync_ai.common.exception.ResourceNotFoundException;
import com.skillsync.skillsync_ai.matching.dto.RecommendationJobResponse;
import com.skillsync.skillsync_ai.matching.entity.RecommendationJob;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobType;
import com.skillsync.skillsync_ai.matching.repo.RecommendationJobRepository;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationJobServiceTest {

    @Mock
    private RecommendationJobRepository jobRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectRoleRepository projectRoleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RecommendationGenerationWorker worker;

    @Mock
    private TransactionTemplate transactionTemplate;

    private RecommendationJobService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationJobService(
                jobRepository, projectRepository, projectRoleRepository,
                userRepository, worker, transactionTemplate);
        ReflectionTestUtils.setField(service, "cooldownMinutes", 10L);
        ReflectionTestUtils.setField(service, "staleMinutes", 10L);

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
    void requestProjectGeneration_createsPendingJobAndDispatches() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.of(project(projectId, ownerId)));
        when(projectRoleRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(jobRepository.findByLockKey("PROJECT:" + projectId)).thenReturn(Optional.empty());
        when(jobRepository.findFirstByProjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                eq(projectId), anyString(), eq(RecommendationJobStatus.READY), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any(RecommendationJob.class))).thenAnswer(invocation -> {
            RecommendationJob job = invocation.getArgument(0);
            job.setId(jobId);
            return job;
        });

        RecommendationJobResponse response = service.requestProjectGeneration(ownerId, projectId);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.type()).isEqualTo(RecommendationJobType.PROJECT_CANDIDATES);
        assertThat(response.status()).isEqualTo(RecommendationJobStatus.PENDING);

        ArgumentCaptor<RecommendationJob> captor = ArgumentCaptor.forClass(RecommendationJob.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getLockKey()).isEqualTo("PROJECT:" + projectId);
        assertThat(captor.getValue().getInputHash()).isNotBlank();
        verify(worker).process(jobId);
    }

    @Test
    void requestProjectGeneration_rejectsNonOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.of(project(projectId, ownerId)));

        assertThatThrownBy(() -> service.requestProjectGeneration(UUID.randomUUID(), projectId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("owner");

        verifyNoInteractions(worker);
    }

    @Test
    void requestProjectGeneration_requiresAtLeastOneSkill() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder()
                .id(projectId)
                .ownerId(ownerId)
                .collegeId(UUID.randomUUID())
                .requiredSkills(List.of())
                .build();

        when(projectRepository.findByIdForUpdate(projectId)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.requestProjectGeneration(ownerId, projectId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required skill");

        verifyNoInteractions(worker);
    }

    @Test
    void requestProjectGeneration_reusesActiveJob() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        RecommendationJob active = job(jobId, projectId, ownerId, RecommendationJobStatus.RUNNING,
                Instant.now().minus(1, ChronoUnit.MINUTES));

        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.of(project(projectId, ownerId)));
        when(projectRoleRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(jobRepository.findByLockKey("PROJECT:" + projectId)).thenReturn(Optional.of(active));

        RecommendationJobResponse response = service.requestProjectGeneration(ownerId, projectId);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.status()).isEqualTo(RecommendationJobStatus.RUNNING);
        verify(jobRepository, never()).save(any(RecommendationJob.class));
        verify(worker, never()).process(any());
    }

    @Test
    void requestProjectGeneration_recoversStaleJobAndStartsNewOne() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID staleId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        RecommendationJob stale = job(staleId, projectId, ownerId, RecommendationJobStatus.RUNNING,
                Instant.now().minus(30, ChronoUnit.MINUTES));

        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.of(project(projectId, ownerId)));
        when(projectRoleRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(jobRepository.findByLockKey("PROJECT:" + projectId)).thenReturn(Optional.of(stale));
        when(jobRepository.saveAndFlush(any(RecommendationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.findFirstByProjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                eq(projectId), anyString(), eq(RecommendationJobStatus.READY), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any(RecommendationJob.class))).thenAnswer(invocation -> {
            RecommendationJob job = invocation.getArgument(0);
            job.setId(newId);
            return job;
        });

        RecommendationJobResponse response = service.requestProjectGeneration(ownerId, projectId);

        assertThat(response.id()).isEqualTo(newId);
        assertThat(stale.getStatus()).isEqualTo(RecommendationJobStatus.FAILED);
        assertThat(stale.getLockKey()).isNull();
        verify(jobRepository).saveAndFlush(stale);
        verify(worker).process(newId);
    }

    @Test
    void requestProjectGeneration_reusesRecentReadyResult() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        RecommendationJob ready = job(jobId, projectId, ownerId, RecommendationJobStatus.READY,
                Instant.now().minus(2, ChronoUnit.MINUTES));
        ready.setLockKey(null);

        when(projectRepository.findByIdForUpdate(projectId))
                .thenReturn(Optional.of(project(projectId, ownerId)));
        when(projectRoleRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(jobRepository.findByLockKey("PROJECT:" + projectId)).thenReturn(Optional.empty());
        when(jobRepository.findFirstByProjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                eq(projectId), anyString(), eq(RecommendationJobStatus.READY), any(Instant.class)))
                .thenReturn(Optional.of(ready));

        RecommendationJobResponse response = service.requestProjectGeneration(ownerId, projectId);

        assertThat(response.status()).isEqualTo(RecommendationJobStatus.READY);
        verify(jobRepository, never()).save(any(RecommendationJob.class));
        verify(worker, never()).process(any());
    }

    @Test
    void requestStudentGeneration_createsStudentJob() {
        UUID studentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User student = User.builder().id(studentId).role(User.Role.STUDENT).build();

        when(userRepository.findWithCollegeById(studentId)).thenReturn(Optional.of(student));
        when(jobRepository.findByLockKey("STUDENT:" + studentId)).thenReturn(Optional.empty());
        when(jobRepository.findFirstByTypeAndSubjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                eq(RecommendationJobType.STUDENT_PROJECTS), eq(studentId), anyString(),
                eq(RecommendationJobStatus.READY), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any(RecommendationJob.class))).thenAnswer(invocation -> {
            RecommendationJob job = invocation.getArgument(0);
            job.setId(jobId);
            return job;
        });

        RecommendationJobResponse response = service.requestStudentGeneration(studentId);

        assertThat(response.id()).isEqualTo(jobId);
        assertThat(response.type()).isEqualTo(RecommendationJobType.STUDENT_PROJECTS);
        assertThat(response.projectId()).isNull();
        verify(worker).process(jobId);
    }

    @Test
    void getJob_rejectsAnotherRequester() {
        UUID jobId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        when(jobRepository.findByIdAndRequestedBy(jobId, requesterId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(requesterId, jobId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getJob_marksStaleRunningJobAsFailed() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        RecommendationJob stale = job(jobId, projectId, ownerId, RecommendationJobStatus.RUNNING,
                Instant.now().minus(30, ChronoUnit.MINUTES));

        when(jobRepository.findByIdAndRequestedBy(jobId, ownerId)).thenReturn(Optional.of(stale));
        when(jobRepository.saveAndFlush(any(RecommendationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecommendationJobResponse response = service.getJob(ownerId, jobId);

        assertThat(response.status()).isEqualTo(RecommendationJobStatus.FAILED);
        assertThat(response.error()).contains("timed out");
    }

    @Test
    void getLatestProjectJob_usesTheJobRequesterForOwnershipWithoutAProjectLookup() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        RecommendationJob ready = job(jobId, projectId, ownerId, RecommendationJobStatus.READY, Instant.now());
        ready.setLockKey(null);

        when(jobRepository.findFirstByProjectIdOrderByRequestedAtDesc(projectId))
                .thenReturn(Optional.of(ready));

        RecommendationJobResponse response = service.getLatestProjectJob(ownerId, projectId);

        assertThat(response.id()).isEqualTo(jobId);
        verifyNoInteractions(projectRepository);
    }

    @Test
    void getLatestProjectJob_rejectsAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        RecommendationJob ready = job(UUID.randomUUID(), projectId, ownerId,
                RecommendationJobStatus.READY, Instant.now());
        ready.setLockKey(null);

        when(jobRepository.findFirstByProjectIdOrderByRequestedAtDesc(projectId))
                .thenReturn(Optional.of(ready));

        assertThatThrownBy(() -> service.getLatestProjectJob(UUID.randomUUID(), projectId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("owner");
    }

    private Project project(UUID projectId, UUID ownerId) {
        return Project.builder()
                .id(projectId)
                .ownerId(ownerId)
                .collegeId(UUID.randomUUID())
                .title("Project")
                .requiredSkills(List.of(new RequiredSkill(
                        UUID.randomUUID(), "Java", "INTERMEDIATE")))
                .build();
    }

    private RecommendationJob job(
            UUID jobId, UUID projectId, UUID ownerId,
            RecommendationJobStatus status, Instant requestedAt) {
        return RecommendationJob.builder()
                .id(jobId)
                .type(RecommendationJobType.PROJECT_CANDIDATES)
                .subjectId(projectId)
                .projectId(projectId)
                .requestedBy(ownerId)
                .status(status)
                .inputHash("hash")
                .lockKey("PROJECT:" + projectId)
                .requestedAt(requestedAt)
                .build();
    }
}
