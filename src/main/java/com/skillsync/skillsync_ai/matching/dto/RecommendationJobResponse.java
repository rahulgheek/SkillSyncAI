package com.skillsync.skillsync_ai.matching.dto;

import com.skillsync.skillsync_ai.matching.entity.RecommendationJob;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobType;

import java.time.Instant;
import java.util.UUID;

/**
 * Status payload returned by the 202 generation endpoints and the job polling
 * endpoints. The frontend should poll (or wait for the STOMP notification)
 * until {@code status} is {@code READY} or {@code FAILED}.
 */
public record RecommendationJobResponse(
        UUID id,
        RecommendationJobType type,
        RecommendationJobStatus status,
        UUID projectId,
        UUID subjectId,
        int candidateCount,
        int aiCandidateCount,
        boolean aiApplied,
        String error,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt
) {
    public static RecommendationJobResponse from(RecommendationJob job) {
        return new RecommendationJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getProjectId(),
                job.getSubjectId(),
                job.getCandidateCount(),
                job.getAiCandidateCount(),
                job.isAiApplied(),
                job.getError(),
                job.getRequestedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
