package com.skillsync.skillsync_ai.matching.entity;

import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of one background matching run.
 *
 * <p>The {@code lock_key} column is the database-level idempotency guard. It is
 * set to {@code PROJECT:<id>} or {@code STUDENT:<id>} while a job is active and
 * set to {@code null} when the job finishes. PostgreSQL allows many {@code null}
 * values in a unique index, so at most one active job can exist per subject
 * while history is preserved.</p>
 */
@Entity
@Table(
        name = "recommendation_jobs",
        schema = "matching",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recommendation_jobs_lock_key",
                columnNames = "lock_key"
        ),
        indexes = {
                @Index(name = "idx_recommendation_jobs_subject", columnList = "subject_id, requested_at"),
                @Index(name = "idx_recommendation_jobs_project", columnList = "project_id, requested_at"),
                @Index(name = "idx_recommendation_jobs_status", columnList = "status, requested_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RecommendationJobType type;

    /** Project id for PROJECT_CANDIDATES, student user id for STUDENT_PROJECTS. */
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    /** Convenience reference for the project UI; null for student jobs. */
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecommendationJobStatus status;

    /** Hash of the project requirements (or student marker) used for reuse. */
    @Column(name = "input_hash", length = 64)
    private String inputHash;

    /** Unique while active, null after completion. */
    @Column(name = "lock_key", length = 80)
    private String lockKey;

    @Column(name = "candidate_count", nullable = false)
    @Builder.Default
    private int candidateCount = 0;

    @Column(name = "ai_candidate_count", nullable = false)
    @Builder.Default
    private int aiCandidateCount = 0;

    @Column(name = "ai_applied", nullable = false)
    @Builder.Default
    private boolean aiApplied = false;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private Long version;
}
