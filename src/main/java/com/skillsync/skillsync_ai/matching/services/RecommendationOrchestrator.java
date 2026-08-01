package com.skillsync.skillsync_ai.matching.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.ai.AiMatchingGateway;
import com.skillsync.skillsync_ai.authentication.entity.User;
import com.skillsync.skillsync_ai.authentication.repo.UserRepository;
import com.skillsync.skillsync_ai.common.events.AiSkill;
import com.skillsync.skillsync_ai.common.events.CandidateProfileForAi;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillProjection;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentAchievementRepository;
import com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement;
import com.skillsync.skillsync_ai.common.events.AiAchievement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationOrchestrator {

    private final UserRepository userRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentAchievementRepository studentAchievementRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final AiMatchingGateway aiMatchingGateway;
    private final ObjectMapper objectMapper;

    public void generateRecommendationsForProject(UUID ownerId, UUID projectId) {
        log.info("Starting Recommendation Generation for Project: {}", projectId);

        // 1. Fetch Project and Verify Security
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));

        if (!project.getOwnerId().equals(ownerId)) {
            throw new SecurityException("Only the project owner can generate recommendations.");
        }

        List<ProjectRole> roles = projectRoleRepository.findAllByProjectId(projectId);
        
        List<String> projectOverallSkills = project.getRequiredSkills().stream()
                .map(com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill::skillName)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .toList();
                
        List<List<String>> roleSkillSets = new ArrayList<>();
        if (!roles.isEmpty()) {
            for (ProjectRole role : roles) {
                List<String> rSkills = role.getRequiredSkills().stream()
                        .map(com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill::skillName)
                        .filter(Objects::nonNull)
                        .map(String::toLowerCase)
                        .toList();
                if (!rSkills.isEmpty()) {
                    roleSkillSets.add(rSkills);
                }
            }
        }
        
        if (projectOverallSkills.isEmpty() && roleSkillSets.isEmpty()) {
            log.warn("Project {} has no required skills defined. Aborting recommendations.", projectId);
            return;
        }

        // STAGE 1: COLLEGE ISOLATION (SQL LEVEL)
        List<User> collegeStudents = userRepository.findAllByCollegeId(project.getCollegeId());
        
        // Filter out the project owner themselves and keep only students
        List<UUID> candidateIds = collegeStudents.stream()
                .filter(u -> u.getRole() == User.Role.STUDENT)
                .filter(u -> !u.getId().equals(ownerId))
                .map(User::getId)
                .toList();

        if (candidateIds.isEmpty()) {
            log.info("No available candidates found in college {}", project.getCollegeId());
            return;
        }

        log.info("Stage 1 completed: Found {} potential student candidates in the college.", candidateIds.size());

        // Fetch skills for all these candidates (Batch Query)
        List<StudentSkillProjection> allCandidateSkills = studentSkillRepository.findSkillsForUsers(candidateIds);

        // Group skills by user
        Map<UUID, List<StudentSkillProjection>> skillsByUserId = allCandidateSkills.stream()
                .collect(Collectors.groupingBy(StudentSkillProjection::getUserId));

        // Fetch achievements for all these candidates (Batch Query)
        List<StudentAchievement> allCandidateAchievements = studentAchievementRepository.findAchievementsForUsers(candidateIds);
        
        // Group achievements by user
        Map<UUID, List<StudentAchievement>> achievementsByUserId = allCandidateAchievements.stream()
                .collect(Collectors.groupingBy(a -> a.getStudentProfile().getUserId()));

        // STAGE 2: HEURISTIC PRE-FILTERING
        // We will score each candidate based on a simple string matching overlap.
        // For each required skill keyword, if the candidate has it (case-insensitive substring), they get a point.
        
        List<CandidateOverlap> overlaps = new ArrayList<>();
        
        for (UUID userId : candidateIds) {
            List<StudentSkillProjection> userSkills = skillsByUserId.getOrDefault(userId, Collections.emptyList());
            
            int maxOverlapScore = 0;
            
            // Check overlap against overall project skills
            int projectOverlap = 0;
            for (String reqSkill : projectOverallSkills) {
                boolean hasSkill = userSkills.stream()
                        .anyMatch(us -> us.getSkillName().toLowerCase().contains(reqSkill) || 
                                        reqSkill.contains(us.getSkillName().toLowerCase()));
                if (hasSkill) projectOverlap++;
            }
            maxOverlapScore = Math.max(maxOverlapScore, projectOverlap);
            
            // Check overlap against individual roles (OR condition)
            for (List<String> roleSkills : roleSkillSets) {
                int roleOverlap = 0;
                for (String reqSkill : roleSkills) {
                    boolean hasSkill = userSkills.stream()
                            .anyMatch(us -> us.getSkillName().toLowerCase().contains(reqSkill) || 
                                            reqSkill.contains(us.getSkillName().toLowerCase()));
                    if (hasSkill) roleOverlap++;
                }
                maxOverlapScore = Math.max(maxOverlapScore, roleOverlap);
            }
            
            overlaps.add(new CandidateOverlap(userId, maxOverlapScore, userSkills));
        }

        // Sort descending by max overlap score, and take the top 50 (or max 50)
        int MAX_CANDIDATES = 50;
        List<CandidateOverlap> topCandidates = overlaps.stream()
                .filter(o -> o.overlapScore > 0 || (projectOverallSkills.isEmpty() && roleSkillSets.isEmpty())) 
                .sorted((a, b) -> Integer.compare(b.overlapScore, a.overlapScore))
                .limit(MAX_CANDIDATES)
                .toList();

        if (topCandidates.isEmpty()) {
            log.info("Stage 2 completed: No candidates had any overlapping skills for Project {}", projectId);
            return;
        }

        log.info("Stage 2 completed: Shortlisted top {} candidates based on heuristic overlap.", topCandidates.size());

        // Map to AI Input Format
        List<CandidateProfileForAi> aiProfiles = topCandidates.stream()
                .map(o -> {
                    List<AiSkill> aiSkills = o.skills().stream()
                            .map(s -> new AiSkill(s.getSkillName(), s.getSkillLevel()))
                            .toList();
                            
                    List<StudentAchievement> userAchievements = achievementsByUserId.getOrDefault(o.userId(), Collections.emptyList());
                    List<AiAchievement> aiAchievements = userAchievements.stream()
                            .map(a -> new AiAchievement(a.getTitle(), a.getType(), a.getOrganization()))
                            .toList();
                            
                    UUID studentProfileId = o.skills().isEmpty() ? null : o.skills().get(0).getStudentProfileId();
                    return new CandidateProfileForAi(o.userId(), studentProfileId, aiSkills, aiAchievements);
                })
                .toList();

        // STAGE 3: DEEP SEMANTIC ANALYSIS (GEMINI AI)
        // Fire and forget - this is handled asynchronously/in the background by the AI Gateway
        try {
            // Include roles in the JSON string for the AI prompt
            Map<String, Object> projectDataMap = new HashMap<>();
            projectDataMap.put("overallRequiredSkills", project.getRequiredSkills());
            
            List<Map<String, Object>> rolesData = new ArrayList<>();
            for (ProjectRole role : roles) {
                Map<String, Object> roleMap = new HashMap<>();
                roleMap.put("title", role.getTitle());
                roleMap.put("requiredSkills", role.getRequiredSkills());
                rolesData.add(roleMap);
            }
            projectDataMap.put("roles", rolesData);
            
            String requiredSkillsJson = objectMapper.writeValueAsString(projectDataMap);
            
            log.info("Calling BatchSkillGapAnalyzer with Groq...");
            aiMatchingGateway.analyzeCandidatesBatch(projectId, requiredSkillsJson, aiProfiles);
            log.info("Returned from BatchSkillGapAnalyzer");
            
            log.info("Stage 3 completed: Dispatched {} candidates to Groq AI for deep semantic analysis.", aiProfiles.size());
        } catch (Exception e) {
            log.error("Failed to serialize required skills for AI prompt", e);
        }
    }

    private record CandidateOverlap(UUID userId, int overlapScore, List<StudentSkillProjection> skills) {}

    public void generateRecommendationsForStudent(UUID studentId) {
        log.info("Starting Project Discovery Generation for Student: {}", studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        if (student.getRole() != User.Role.STUDENT) {
            throw new SecurityException("Only students can discover projects.");
        }

        // STAGE 1: FETCH STUDENT PROFILE
        List<StudentSkillProjection> studentSkills = studentSkillRepository.findSkillsForUsers(List.of(studentId));
        List<StudentAchievement> studentAchievements = studentAchievementRepository.findAchievementsForUsers(List.of(studentId));
        
        List<AiSkill> aiSkills = studentSkills.stream()
                .map(s -> new AiSkill(s.getSkillName(), s.getSkillLevel()))
                .toList();
        List<AiAchievement> aiAchievements = studentAchievements.stream()
                .map(a -> new AiAchievement(a.getTitle(), a.getType(), a.getOrganization()))
                .toList();
        UUID studentProfileId = studentSkills.isEmpty() ? null : studentSkills.get(0).getStudentProfileId();
        CandidateProfileForAi studentAiProfile = new CandidateProfileForAi(studentId, studentProfileId, aiSkills, aiAchievements);

        // STAGE 2: FETCH ACTIVE COLLEGE PROJECTS
        List<Project> activeProjects = projectRepository.findByCollegeIdAndStatus(student.getCollege().getId(), com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus.RECRUITING);
        
        // Filter out projects the student owns
        List<Project> eligibleProjects = activeProjects.stream()
                .filter(p -> !p.getOwnerId().equals(studentId))
                .toList();

        if (eligibleProjects.isEmpty()) {
            log.info("No available projects found in college {}", student.getCollege().getId());
            return;
        }
        log.info("Stage 1 completed: Found {} potential projects in the college.", eligibleProjects.size());

        // STAGE 3: HEURISTIC PRE-FILTERING
        List<ProjectOverlap> overlaps = new ArrayList<>();
        List<String> studentSkillKeywords = studentSkills.stream().map(s -> s.getSkillName().toLowerCase()).toList();

        for (Project project : eligibleProjects) {
            List<String> reqSkills = project.getRequiredSkills().stream()
                    .map(com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill::skillName)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .toList();
            
            int overlapScore = 0;
            for (String req : reqSkills) {
                if (studentSkillKeywords.stream().anyMatch(sk -> sk.contains(req) || req.contains(sk))) {
                    overlapScore++;
                }
            }
            overlaps.add(new ProjectOverlap(project, overlapScore));
        }

        int MAX_PROJECTS = 10; // We send top 10 projects to Groq
        List<com.skillsync.skillsync_ai.common.events.ProjectProfileForAi> aiProjects = overlaps.stream()
                // Sort descending by overlap score
                .sorted((a, b) -> Integer.compare(b.overlapScore, a.overlapScore))
                .limit(MAX_PROJECTS)
                .map(o -> new com.skillsync.skillsync_ai.common.events.ProjectProfileForAi(
                        o.project().getId(),
                        o.project().getTitle(),
                        o.project().getDescription(),
                        o.project().getRequiredSkills().stream().map(com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill::skillName).toList()
                ))
                .toList();

        log.info("Stage 2 completed: Shortlisted top {} projects based on heuristic overlap.", aiProjects.size());

        // STAGE 4: DEEP SEMANTIC ANALYSIS (GROQ AI)
        try {
            String studentProfileJson = objectMapper.writeValueAsString(studentAiProfile);
            log.info("Calling BatchSkillGapAnalyzer with Groq for Student Discovery...");
            aiMatchingGateway.analyzeProjectsBatch(studentId, studentProfileJson, aiProjects);
            log.info("Returned from BatchSkillGapAnalyzer");
        } catch (Exception e) {
            log.error("Failed to serialize student profile for AI prompt", e);
        }
    }

    private record ProjectOverlap(Project project, int overlapScore) {}
}
