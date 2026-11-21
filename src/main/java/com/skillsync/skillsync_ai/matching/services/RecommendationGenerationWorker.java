package com.skillsync.skillsync_ai.matching.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.AiMatchingException;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredCandidate;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway.ScoredProject;
import com.skillsync.skillsync_ai.ai.services.RecommendationPromptFactory;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import com.skillsync.skillsync_ai.common.events.AiAchievement;
import com.skillsync.skillsync_ai.common.events.AiSkill;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.common.events.ProjectProfileForAi;
import com.skillsync.skillsync_ai.common.events.RecommendationsReadyEvent;
import com.skillsync.skillsync_ai.matching.entity.RecommendationJob;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationJobType;
import com.skillsync.skillsync_ai.matching.repo.CandidateShortlistRepository;
import com.skillsync.skillsync_ai.matching.repo.CandidateShortlistRepository.ProjectCandidateRow;
import com.skillsync.skillsync_ai.matching.repo.CandidateShortlistRepository.StudentProjectRow;
import com.skillsync.skillsync_ai.matching.repo.RecommendationJobRepository;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement;
import com.skillsync.skillsync_ai.userprofile.repo.StudentAchievementRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillProjection;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executes one matching job on the dedicated AI executor.
 *
 * <p>Data access is deliberately staged to minimise cross-region round trips:
 * one shortlist query, one batch skills query, one batch achievements query,
 * one Groq call, one batch lookup of existing recommendations and one
 * {@code saveAll}. No per-candidate selects or saves.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationGenerationWorker {

    private final RecommendationJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final CandidateShortlistRepository shortlistRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentAchievementRepository studentAchievementRepository;
    private final UserRepository userRepository;
    private final AiMatchingGateway aiMatchingGateway;
    private final RecommendationScoringService scoringService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${matching.shortlist.max-candidates:50}")
    private int maxShortlistCandidates;

    @Value("${matching.ai.max-candidates:20}")
    private int maxAiCandidates;

    @Value("${matching.ai.max-projects:10}")
    private int maxAiProjects;

    @Async("aiTaskExecutor")
    public void process(UUID jobId) {
        RecommendationJob job = transactionTemplate.execute(status -> {
            RecommendationJob current = jobRepository.findById(jobId).orElse(null);
            if (current == null || current.getStatus() != RecommendationJobStatus.PENDING) {
                return null;
            }
            current.setStatus(RecommendationJobStatus.RUNNING);
            current.setStartedAt(Instant.now());
            return current;
        });

        if (job == null) {
            log.debug("Skipping matching job {}: not pending or no longer exists", jobId);
            return;
        }

        try {
            if (job.getType() == RecommendationJobType.PROJECT_CANDIDATES) {
                processProject(jobId, job.getSubjectId());
            } else {
                processStudent(jobId, job.getSubjectId());
            }
        } catch (Exception e) {
            log.error("Matching job {} failed", jobId, e);
            failJob(jobId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Project -> candidates
    // ------------------------------------------------------------------

    private void processProject(UUID jobId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project no longer exists"));
        List<ProjectRole> roles = projectRoleRepository.findAllByProjectId(projectId);

        List<ProjectCandidateRow> shortlist = shortlistRepository.findCandidatesForProject(
                projectId, project.getCollegeId(), project.getOwnerId(), maxShortlistCandidates);

        if (shortlist.isEmpty()) {
            finalizeReady(jobId, 0, 0, false,
                    "No students in this college matched the required skills.");
            return;
        }

        List<UUID> userIds = shortlist.stream().map(ProjectCandidateRow::userId).toList();
        List<StudentSkillProjection> allSkills = studentSkillRepository.findSkillsForUsers(userIds);
        List<StudentAchievement> allAchievements = studentAchievementRepository.findAchievementsForUsers(userIds);

        Map<UUID, List<StudentSkillProjection>> skillsByUser = allSkills.stream()
                .collect(Collectors.groupingBy(StudentSkillProjection::getUserId));
        Map<UUID, List<StudentAchievement>> achievementsByUser = allAchievements.stream()
                .collect(Collectors.groupingBy(a -> a.getStudentProfile().getUserId()));

        Map<UUID, RecommendationScoringService.DeterministicScore> deterministic = new LinkedHashMap<>();
        for (ProjectCandidateRow row : shortlist) {
            deterministic.put(row.userId(), new RecommendationScoringService.DeterministicScore(
                    DeterministicMatchScore.compute(
                            row.matchedSkills(), row.requiredSkillCount(),
                            row.avgLevel(), row.endorsements()),
                    splitSkills(row.matchedSkillNames())));
        }

        List<ProjectCandidateRow> aiRows = shortlist.stream()
                .sorted(Comparator.comparing(
                        (ProjectCandidateRow row) -> deterministic.get(row.userId()).score(),
                        Comparator.reverseOrder()))
                .limit(maxAiCandidates)
                .toList();

        List<CandidateProfileForAi> aiProfiles = aiRows.stream()
                .map(row -> new CandidateProfileForAi(
                        row.userId(),
                        row.studentProfileId(),
                        skillsByUser.getOrDefault(row.userId(), List.of()).stream()
                                .map(skill -> new AiSkill(skill.getSkillName(), skill.getSkillLevel()))
                                .toList(),
                        achievementsByUser.getOrDefault(row.userId(), List.of()).stream()
                                .map(achievement -> new AiAchievement(
                                        achievement.getTitle(),
                                        achievement.getType(),
                                        achievement.getOrganization()))
                                .toList()))
                .toList();

        List<ScoredCandidate> aiScores = List.of();
        boolean aiApplied = false;
        String warning = null;
        try {
            String projectDataJson = buildProjectDataJson(project, roles);
            aiScores = aiMatchingGateway.scoreCandidates(projectId, projectDataJson, aiProfiles);
            aiApplied = !aiScores.isEmpty();
        } catch (AiMatchingException e) {
            warning = "AI re-ranking unavailable: " + e.getMessage();
            log.warn("Falling back to deterministic scores for project {}: {}", projectId, e.getMessage());
        } catch (Exception e) {
            warning = "AI re-ranking unavailable: could not build the project prompt.";
            log.warn("Failed to build project prompt for {}", projectId, e);
        }

        int persisted = scoringService.persistProjectScores(
                projectId,
                RecommendationPromptFactory.CURRENT_VERSION,
                aiScores,
                deterministic);

        finalizeReady(jobId, persisted, aiProfiles.size(), aiApplied, warning);
    }

    private String buildProjectDataJson(Project project, List<ProjectRole> roles) throws JsonProcessingException {
        Map<String, Object> projectData = new LinkedHashMap<>();
        projectData.put("title", project.getTitle());
        projectData.put("description", project.getDescription());
        projectData.put("overallRequiredSkills",
                project.getRequiredSkills() == null ? List.of() : project.getRequiredSkills());
        projectData.put("roles", roles.stream()
                .map(role -> {
                    Map<String, Object> roleData = new LinkedHashMap<>();
                    roleData.put("title", role.getTitle());
                    roleData.put("requiredSkills",
                            role.getRequiredSkills() == null ? List.of() : role.getRequiredSkills());
                    return roleData;
                })
                .toList());
        return objectMapper.writeValueAsString(projectData);
    }

    // ------------------------------------------------------------------
    // Student -> projects
    // ------------------------------------------------------------------

    private void processStudent(UUID jobId, UUID studentId) {
        User student = userRepository.findWithCollegeById(studentId)
                .orElseThrow(() -> new IllegalStateException("Student no longer exists"));
        UUID collegeId = student.getCollege().getId();

        List<StudentProjectRow> shortlist = shortlistRepository.findProjectsForStudent(
                studentId, collegeId, maxShortlistCandidates);

        if (shortlist.isEmpty()) {
            finalizeReady(jobId, 0, 0, false,
                    "No recruiting projects matched your skills yet.");
            return;
        }

        List<StudentSkillProjection> skills = studentSkillRepository.findSkillsForUsers(List.of(studentId));
        List<StudentAchievement> achievements = studentAchievementRepository.findAchievementsForUsers(List.of(studentId));

        List<AiSkill> aiSkills = skills.stream()
                .map(skill -> new AiSkill(skill.getSkillName(), skill.getSkillLevel()))
                .toList();
        List<AiAchievement> aiAchievements = achievements.stream()
                .map(achievement -> new AiAchievement(
                        achievement.getTitle(),
                        achievement.getType(),
                        achievement.getOrganization()))
                .toList();
        UUID studentProfileId = skills.isEmpty() ? null : skills.get(0).getStudentProfileId();

        CandidateProfileForAi studentProfile = new CandidateProfileForAi(
                studentId, studentProfileId, aiSkills, aiAchievements);

        Map<UUID, RecommendationScoringService.DeterministicScore> deterministic = new LinkedHashMap<>();
        for (StudentProjectRow row : shortlist) {
            deterministic.put(row.projectId(), new RecommendationScoringService.DeterministicScore(
                    DeterministicMatchScore.compute(
                            row.matchedSkills(), row.requiredSkillCount(),
                            row.avgLevel(), row.endorsements()),
                    splitSkills(row.matchedSkillNames())));
        }

        List<ProjectProfileForAi> aiProjects = shortlist.stream()
                .sorted(Comparator.comparing(
                        (StudentProjectRow row) -> deterministic.get(row.projectId()).score(),
                        Comparator.reverseOrder()))
                .limit(maxAiProjects)
                .map(row -> new ProjectProfileForAi(
                        row.projectId(),
                        row.title(),
                        row.description(),
                        splitSkills(row.requiredSkillNames())))
                .toList();

        List<ScoredProject> aiScores = List.of();
        boolean aiApplied = false;
        String warning = null;
        try {
            String studentJson = objectMapper.writeValueAsString(studentProfile);
            aiScores = aiMatchingGateway.scoreProjects(studentId, studentJson, aiProjects);
            aiApplied = !aiScores.isEmpty();
        } catch (AiMatchingException e) {
            warning = "AI re-ranking unavailable: " + e.getMessage();
            log.warn("Falling back to deterministic scores for student {}: {}", studentId, e.getMessage());
        } catch (Exception e) {
            warning = "AI re-ranking unavailable: could not build the student prompt.";
            log.warn("Failed to build student prompt for {}", studentId, e);
        }

        int persisted = scoringService.persistStudentScores(
                studentId,
                RecommendationPromptFactory.CURRENT_VERSION,
                aiScores,
                deterministic);

        finalizeReady(jobId, persisted, aiProjects.size(), aiApplied, warning);
    }

    // ------------------------------------------------------------------
    // State transitions
    // ------------------------------------------------------------------

    private void finalizeReady(
            UUID jobId, int candidateCount, int aiCandidateCount, boolean aiApplied, String warning) {

        transactionTemplate.executeWithoutResult(status -> {
            RecommendationJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() != RecommendationJobStatus.RUNNING) {
                return;
            }

            job.setStatus(RecommendationJobStatus.READY);
            job.setLockKey(null);
            job.setCandidateCount(candidateCount);
            job.setAiCandidateCount(aiCandidateCount);
            job.setAiApplied(aiApplied);
            job.setError(truncate(warning, 2000));
            job.setFinishedAt(Instant.now());

            if (candidateCount > 0) {
                eventPublisher.publishEvent(new RecommendationsReadyEvent(
                        job.getRequestedBy(),
                        job.getProjectId(),
                        job.getType() == RecommendationJobType.PROJECT_CANDIDATES ? "PROJECT" : "STUDENT",
                        candidateCount,
                        aiApplied));
            }
        });
    }

    private void failJob(UUID jobId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            RecommendationJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() == RecommendationJobStatus.READY) {
                return;
            }
            job.setStatus(RecommendationJobStatus.FAILED);
            job.setLockKey(null);
            job.setError(truncate(message == null ? "Matching job failed." : message, 2000));
            job.setFinishedAt(Instant.now());
        });
    }

    private List<String> splitSkills(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
