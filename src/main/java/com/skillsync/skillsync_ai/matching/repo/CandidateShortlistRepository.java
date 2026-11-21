package com.skillsync.skillsync_ai.matching.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Cross-region candidate retrieval.
 *
 * <p>Both queries are a single round trip to PostgreSQL and rank by canonical
 * {@code skill_id}, not by Java substring matching. This removes the
 * "Java matches JavaScript" class of false positives and avoids loading every
 * student, skill and achievement in a college into the JVM.</p>
 */
@Repository
@RequiredArgsConstructor
public class CandidateShortlistRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Rank students in a college against a project's overall and per-role
     * required skills. Only candidates with at least one exact skill match are
     * returned, ordered by overlap, proficiency and endorsements.
     */
    public List<ProjectCandidateRow> findCandidatesForProject(
            UUID projectId, UUID collegeId, UUID ownerId, int limit) {

        String sql = """
                WITH required AS (
                    SELECT DISTINCT (elem->>'skillId')::uuid AS skill_id
                    FROM project.projects p
                    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(p.required_skills, '[]'::jsonb)) AS elem
                    WHERE p.id = :projectId
                      AND elem->>'skillId' IS NOT NULL

                    UNION

                    SELECT DISTINCT (elem->>'skillId')::uuid AS skill_id
                    FROM project.project_roles pr
                    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(pr.required_skills, '[]'::jsonb)) AS elem
                    WHERE pr.project_id = :projectId
                      AND pr.deleted_at IS NULL
                      AND elem->>'skillId' IS NOT NULL
                ),
                required_count AS (
                    SELECT COUNT(*) AS total FROM required
                ),
                matched AS (
                    SELECT
                        u.id AS user_id,
                        sp.id AS student_profile_id,
                        COUNT(DISTINCT ss.skill_id) AS matched_skills,
                        COALESCE(AVG(CASE ss.level
                            WHEN 'EXPERT' THEN 4.0
                            WHEN 'ADVANCED' THEN 3.0
                            WHEN 'INTERMEDIATE' THEN 2.0
                            ELSE 1.0 END), 0.0) AS avg_level,
                        COALESCE(SUM(ss.endorsed_count), 0) AS endorsements,
                        COALESCE(STRING_AGG(DISTINCT s.name, ', ' ORDER BY s.name), '') AS matched_skill_names
                    FROM auth.users u
                    JOIN profile.student_profiles sp
                      ON sp.user_id = u.id AND sp.deleted_at IS NULL
                    JOIN profile.student_skills ss
                      ON ss.student_profile_id = sp.id AND ss.deleted_at IS NULL
                    JOIN profile.skills s
                      ON s.id = ss.skill_id AND s.deleted_at IS NULL
                    JOIN required r ON r.skill_id = ss.skill_id
                    WHERE u.college_id = :collegeId
                      AND u.role = 'STUDENT'
                      AND u.id <> :ownerId
                      AND u.deleted_at IS NULL
                      AND u.is_active = TRUE
                    GROUP BY u.id, sp.id
                )
                SELECT
                    m.user_id            AS "userId",
                    m.student_profile_id AS "studentProfileId",
                    m.matched_skills     AS "matchedSkills",
                    rc.total             AS "requiredSkillCount",
                    m.avg_level          AS "avgLevel",
                    m.endorsements       AS "endorsements",
                    m.matched_skill_names AS "matchedSkillNames"
                FROM matched m
                CROSS JOIN required_count rc
                ORDER BY m.matched_skills DESC, m.avg_level DESC, m.endorsements DESC, m.user_id
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("collegeId", collegeId)
                .addValue("ownerId", ownerId)
                .addValue("limit", limit);

        return jdbc.query(sql, params, (rs, rowNum) -> new ProjectCandidateRow(
                rs.getObject("userId", UUID.class),
                rs.getObject("studentProfileId", UUID.class),
                rs.getInt("matchedSkills"),
                rs.getInt("requiredSkillCount"),
                rs.getDouble("avgLevel"),
                rs.getLong("endorsements"),
                rs.getString("matchedSkillNames")
        ));
    }

    /**
     * Rank recruiting projects in a college for one student using the same
     * canonical skill ids.
     */
    public List<StudentProjectRow> findProjectsForStudent(
            UUID studentId, UUID collegeId, int limit) {

        String sql = """
                WITH student_skills AS (
                    SELECT ss.skill_id, ss.level, ss.endorsed_count
                    FROM profile.student_skills ss
                    JOIN profile.student_profiles sp ON sp.id = ss.student_profile_id
                    WHERE sp.user_id = :studentId
                      AND sp.deleted_at IS NULL
                      AND ss.deleted_at IS NULL
                ),
                eligible_projects AS (
                    SELECT p.id,
                           p.title,
                           p.description,
                           COALESCE(p.required_skills, '[]'::jsonb) AS required_skills
                    FROM project.projects p
                    WHERE p.college_id = :collegeId
                      AND p.status = 'RECRUITING'
                      AND p.owner_id <> :studentId
                      AND p.deleted_at IS NULL
                ),
                project_required AS (
                    SELECT ep.id AS project_id, (elem->>'skillId')::uuid AS skill_id
                    FROM eligible_projects ep
                    CROSS JOIN LATERAL jsonb_array_elements(ep.required_skills) AS elem
                    WHERE elem->>'skillId' IS NOT NULL

                    UNION

                    SELECT ep.id AS project_id, (elem->>'skillId')::uuid AS skill_id
                    FROM eligible_projects ep
                    JOIN project.project_roles pr
                      ON pr.project_id = ep.id AND pr.deleted_at IS NULL
                    CROSS JOIN LATERAL jsonb_array_elements(COALESCE(pr.required_skills, '[]'::jsonb)) AS elem
                    WHERE elem->>'skillId' IS NOT NULL
                ),
                required_counts AS (
                    SELECT project_id, COUNT(DISTINCT skill_id) AS total
                    FROM project_required
                    GROUP BY project_id
                ),
                matched AS (
                    SELECT
                        pr.project_id,
                        COUNT(DISTINCT pr.skill_id) AS matched_skills,
                        COALESCE(AVG(CASE ss.level
                            WHEN 'EXPERT' THEN 4.0
                            WHEN 'ADVANCED' THEN 3.0
                            WHEN 'INTERMEDIATE' THEN 2.0
                            ELSE 1.0 END), 0.0) AS avg_level,
                        COALESCE(SUM(ss.endorsed_count), 0) AS endorsements,
                        COALESCE(STRING_AGG(DISTINCT s.name, ', ' ORDER BY s.name), '') AS matched_skill_names
                    FROM project_required pr
                    JOIN student_skills ss ON ss.skill_id = pr.skill_id
                    JOIN profile.skills s ON s.id = ss.skill_id AND s.deleted_at IS NULL
                    GROUP BY pr.project_id
                )
                SELECT
                    ep.id               AS "projectId",
                    ep.title            AS "title",
                    ep.description      AS "description",
                    m.matched_skills    AS "matchedSkills",
                    rc.total            AS "requiredSkillCount",
                    m.avg_level         AS "avgLevel",
                    m.endorsements      AS "endorsements",
                    m.matched_skill_names AS "matchedSkillNames",
                    (
                        SELECT STRING_AGG(DISTINCT names.name, ', ')
                        FROM (
                            SELECT elem->>'skillName' AS name
                            FROM jsonb_array_elements(ep.required_skills) AS elem
                            UNION
                            SELECT elem->>'skillName' AS name
                            FROM project.project_roles pr2
                            CROSS JOIN LATERAL jsonb_array_elements(COALESCE(pr2.required_skills, '[]'::jsonb)) AS elem
                            WHERE pr2.project_id = ep.id AND pr2.deleted_at IS NULL
                        ) names
                        WHERE names.name IS NOT NULL
                    ) AS "requiredSkillNames"
                FROM eligible_projects ep
                JOIN matched m ON m.project_id = ep.id
                JOIN required_counts rc ON rc.project_id = ep.id
                ORDER BY m.matched_skills DESC, m.avg_level DESC, m.endorsements DESC, ep.id
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("collegeId", collegeId)
                .addValue("limit", limit);

        return jdbc.query(sql, params, (rs, rowNum) -> new StudentProjectRow(
                rs.getObject("projectId", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("matchedSkills"),
                rs.getInt("requiredSkillCount"),
                rs.getDouble("avgLevel"),
                rs.getLong("endorsements"),
                rs.getString("matchedSkillNames"),
                rs.getString("requiredSkillNames")
        ));
    }

    public record ProjectCandidateRow(
            UUID userId,
            UUID studentProfileId,
            int matchedSkills,
            int requiredSkillCount,
            double avgLevel,
            long endorsements,
            String matchedSkillNames
    ) {
    }

    public record StudentProjectRow(
            UUID projectId,
            String title,
            String description,
            int matchedSkills,
            int requiredSkillCount,
            double avgLevel,
            long endorsements,
            String matchedSkillNames,
            String requiredSkillNames
    ) {
    }
}
