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
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the lifecycle of background matching jobs.
 *
 * <p>The request thread only validates ownership, computes an input hash,
 * creates (or reuses) a job row and returns {@code 202}. All database-heavy and
 * LLM work happens on the dedicated AI executor. This keeps the cross-region
 * database latency and the multi-second Groq latency off the HTTP path.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationJobService {

    private final RecommendationJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final UserRepository userRepository;
    private final RecommendationGenerationWorker worker;
    private final TransactionTemplate transactionTemplate;

    @Value("${matching.ai.cooldown-minutes:10}")
    private long cooldownMinutes;

    @Value("${matching.job.stale-minutes:10}")
    private long staleMinutes;

    // ------------------------------------------------------------------
    // Project owner: rank candidates
    // ------------------------------------------------------------------

    public RecommendationJobResponse requestProjectGeneration(UUID ownerId, UUID projectId) {
        String lockKey = "PROJECT:" + projectId;

        try {
            RecommendationJobResponse response = transactionTemplate.execute(status -> {
                Project project = projectRepository.findByIdForUpdate(projectId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
                if (!project.getOwnerId().equals(ownerId)) {
                    throw new SecurityException("Only the project owner can generate recommendations.");
                }

                List<ProjectRole> roles = projectRoleRepository.findAllByProjectId(projectId);
                if (!hasRequiredSkills(project, roles)) {
                    throw new IllegalStateException(
                            "Add at least one required skill before generating recommendations.");
                }

                String inputHash = hashProjectInputs(project, roles);
                Optional<RecommendationJob> active = jobRepository.findByLockKey(lockKey);
                if (active.isPresent()) {
                    RecommendationJob job = active.get();
                    if (!isStale(job)) {
                        return RecommendationJobResponse.from(job);
                    }
                    markStaleFailed(job);
                }

                Optional<RecommendationJob> reusable = jobRepository
                        .findFirstByProjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                                projectId, inputHash, RecommendationJobStatus.READY,
                                Instant.now().minus(cooldownMinutes, ChronoUnit.MINUTES));
                if (reusable.isPresent()) {
                    return RecommendationJobResponse.from(reusable.get());
                }

                RecommendationJob job = RecommendationJob.builder()
                        .type(RecommendationJobType.PROJECT_CANDIDATES)
                        .subjectId(projectId)
                        .projectId(projectId)
                        .requestedBy(ownerId)
                        .status(RecommendationJobStatus.PENDING)
                        .inputHash(inputHash)
                        .lockKey(lockKey)
                        .requestedAt(Instant.now())
                        .build();
                return RecommendationJobResponse.from(jobRepository.save(job));
            });

            dispatch(response);
            return response;
        } catch (DataIntegrityViolationException duplicate) {
            // Two clicks raced past the lock. The unique lock_key won; return
            // the job that is already active instead of failing the request.
            return jobRepository.findByLockKey(lockKey)
                    .map(RecommendationJobResponse::from)
                    .orElseThrow(() -> duplicate);
        }
    }

    // ------------------------------------------------------------------
    // Student: rank projects
    // ------------------------------------------------------------------

    public RecommendationJobResponse requestStudentGeneration(UUID studentId) {
        User student = userRepository.findWithCollegeById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (student.getRole() != User.Role.STUDENT) {
            throw new SecurityException("Only students can discover projects.");
        }

        String lockKey = "STUDENT:" + studentId;
        // Skills are loaded inside the worker; the marker keeps the request
        // path to a single read. The cooldown window is what prevents repeats.
        String inputHash = "student:" + studentId;

        try {
            RecommendationJobResponse response = transactionTemplate.execute(status -> {
                Optional<RecommendationJob> active = jobRepository.findByLockKey(lockKey);
                if (active.isPresent()) {
                    RecommendationJob job = active.get();
                    if (!isStale(job)) {
                        return RecommendationJobResponse.from(job);
                    }
                    markStaleFailed(job);
                }

                Optional<RecommendationJob> reusable = jobRepository
                        .findFirstByTypeAndSubjectIdAndInputHashAndStatusAndFinishedAtAfterOrderByFinishedAtDesc(
                                RecommendationJobType.STUDENT_PROJECTS, studentId, inputHash,
                                RecommendationJobStatus.READY,
                                Instant.now().minus(cooldownMinutes, ChronoUnit.MINUTES));
                if (reusable.isPresent()) {
                    return RecommendationJobResponse.from(reusable.get());
                }

                RecommendationJob job = RecommendationJob.builder()
                        .type(RecommendationJobType.STUDENT_PROJECTS)
                        .subjectId(studentId)
                        .requestedBy(studentId)
                        .status(RecommendationJobStatus.PENDING)
                        .inputHash(inputHash)
                        .lockKey(lockKey)
                        .requestedAt(Instant.now())
                        .build();
                return RecommendationJobResponse.from(jobRepository.save(job));
            });

            dispatch(response);
            return response;
        } catch (DataIntegrityViolationException duplicate) {
            return jobRepository.findByLockKey(lockKey)
                    .map(RecommendationJobResponse::from)
                    .orElseThrow(() -> duplicate);
        }
    }

    // ------------------------------------------------------------------
    // Status reads
    // ------------------------------------------------------------------

    public RecommendationJobResponse getJob(UUID requesterId, UUID jobId) {
        RecommendationJob job = jobRepository.findByIdAndRequestedBy(jobId, requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Matching job not found"));
        return refreshIfStale(job);
    }

    public RecommendationJobResponse getLatestProjectJob(UUID ownerId, UUID projectId) {
        RecommendationJob job = jobRepository.findFirstByProjectIdOrderByRequestedAtDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("No matching job found for this project"));
        // The job already records who requested it, so ownership is enforced
        // without an extra cross-region project lookup.
        if (!job.getRequestedBy().equals(ownerId)) {
            throw new SecurityException("Only the project owner can view matching jobs.");
        }
        return refreshIfStale(job);
    }

    public RecommendationJobResponse getLatestStudentJob(UUID studentId) {
        RecommendationJob job = jobRepository
                .findFirstByTypeAndSubjectIdOrderByRequestedAtDesc(
                        RecommendationJobType.STUDENT_PROJECTS, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No matching job found for this student"));
        return refreshIfStale(job);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void dispatch(RecommendationJobResponse response) {
        if (response == null || response.status() != RecommendationJobStatus.PENDING) {
            return;
        }
        try {
            worker.process(response.id());
        } catch (TaskRejectedException rejected) {
            log.error("AI matching queue is full; failing job {}", response.id());
            failJob(response.id(), "The matching queue is full. Please retry in a moment.");
        } catch (Exception e) {
            log.error("Failed to dispatch matching job {}", response.id(), e);
            failJob(response.id(), "Could not start the matching job.");
        }
    }

    private RecommendationJobResponse refreshIfStale(RecommendationJob job) {
        if (isStale(job)) {
            markStaleFailed(job);
        }
        return RecommendationJobResponse.from(job);
    }

    private boolean isStale(RecommendationJob job) {
        if (job.getStatus() != RecommendationJobStatus.PENDING
                && job.getStatus() != RecommendationJobStatus.RUNNING) {
            return false;
        }
        Instant requestedAt = job.getRequestedAt();
        return requestedAt != null
                && requestedAt.isBefore(Instant.now().minus(staleMinutes, ChronoUnit.MINUTES));
    }

    /**
     * Marks a crashed or abandoned job as failed and releases its unique lock.
     * {@code saveAndFlush} is required: a new job may be inserted in the same
     * transaction and the unique index must see the released key first.
     */
    private void markStaleFailed(RecommendationJob job) {
        job.setStatus(RecommendationJobStatus.FAILED);
        job.setLockKey(null);
        job.setError("Job timed out before completion.");
        job.setFinishedAt(Instant.now());
        jobRepository.saveAndFlush(job);
    }

    private void failJob(UUID jobId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            RecommendationJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() == RecommendationJobStatus.READY) {
                return;
            }
            job.setStatus(RecommendationJobStatus.FAILED);
            job.setLockKey(null);
            job.setError(message);
            job.setFinishedAt(Instant.now());
        });
    }

    private boolean hasRequiredSkills(Project project, List<ProjectRole> roles) {
        if (project.getRequiredSkills() != null && !project.getRequiredSkills().isEmpty()) {
            return true;
        }
        return roles.stream()
                .anyMatch(role -> role.getRequiredSkills() != null && !role.getRequiredSkills().isEmpty());
    }

    /**
     * Stable hash of everything that affects the prompt. Sorted so database
     * row order cannot change the hash.
     */
    private String hashProjectInputs(Project project, List<ProjectRole> roles) {
        List<String> parts = new ArrayList<>();
        project.getRequiredSkills().stream()
                .map(this::describeSkill)
                .sorted()
                .forEach(parts::add);

        roles.stream()
                .sorted(Comparator.comparing(role -> role.getId().toString()))
                .forEach(role -> {
                    parts.add("role:" + role.getId() + ":" + role.getTitle());
                    role.getRequiredSkills().stream()
                            .map(this::describeSkill)
                            .sorted()
                            .forEach(parts::add);
                });

        return sha256(String.join("|", parts));
    }

    private String describeSkill(RequiredSkill skill) {
        return skill.skillId() + ":" + skill.minimumLevel();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
