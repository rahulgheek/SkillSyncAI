package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.common.events.CompatibilityScoreComputedEvent;
import com.skillsync.skillsync_ai.matching.dto.CompatibilityScoreResponse;
import com.skillsync.skillsync_ai.matching.entity.CompatibilityScore;
import com.skillsync.skillsync_ai.matching.repo.CompatibilityScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityScoreService {

    private final CompatibilityScoreRepository scoreRepository;

    // --- AI MODULE LISTENER ---
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onScoreComputed(CompatibilityScoreComputedEvent event) {
        log.info("Saving computed compatibility score for Student: {} and Project: {}", event.studentUserId(), event.projectId());

        // Update existing score if the student/project changed, otherwise create new
        CompatibilityScore score = scoreRepository.findByProjectIdAndStudentUserId(event.projectId(), event.studentUserId())
                .orElse(CompatibilityScore.builder()
                        .projectId(event.projectId())
                        .studentUserId(event.studentUserId())
                        .build());

        score.setScore(event.score());
        score.setRationale(event.rationale());
        score.setComputedAt(Instant.now());

        scoreRepository.save(score);
    }

    // --- DASHBOARD READS ---
    @Transactional(readOnly = true)
    public Page<CompatibilityScoreResponse> getRecommendedProjectsForStudent(UUID studentId, Pageable pageable) {
        return scoreRepository.findAllByStudentUserIdOrderByScoreDesc(studentId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<CompatibilityScoreResponse> getTopCandidatesForProject(UUID projectId, Pageable pageable) {
        return scoreRepository.findAllByProjectIdOrderByScoreDesc(projectId, pageable)
                .map(this::mapToResponse);
    }

    private CompatibilityScoreResponse mapToResponse(CompatibilityScore s) {
        return new CompatibilityScoreResponse(s.getId(), s.getProjectId(), s.getStudentUserId(),
                s.getScore(), s.getRationale(), s.getComputedAt());
    }
}