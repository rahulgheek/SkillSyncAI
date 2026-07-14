package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.StudentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentSkillRepository extends JpaRepository<StudentSkill, UUID> {
    List<StudentSkill> findAllByStudentProfileId(UUID studentProfileId);
    Optional<StudentSkill> findByStudentProfileIdAndSkillId(UUID studentProfileId, UUID skillId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT p.userId as userId, s.name as skillName, ss.level as skillLevel " +
                    "FROM StudentSkill ss " +
                    "JOIN ss.studentProfile p " +
                    "JOIN ss.skill s " +
                    "WHERE p.deletedAt IS NULL" // Filters out inactive/deleted profiles
    )
    java.util.List<StudentSkillProjection> findAllActiveStudentSkills();
}