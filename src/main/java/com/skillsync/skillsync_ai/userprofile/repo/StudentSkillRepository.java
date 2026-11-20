package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.StudentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentSkillRepository extends JpaRepository<StudentSkill, UUID> {

    @Query("SELECT ss FROM StudentSkill ss JOIN FETCH ss.skill WHERE ss.studentProfile.id = :studentProfileId")
    List<StudentSkill> findAllByStudentProfileId(@Param("studentProfileId") UUID studentProfileId);

    @Query("SELECT ss FROM StudentSkill ss JOIN FETCH ss.skill JOIN FETCH ss.studentProfile sp WHERE sp.id IN :profileIds")
    List<StudentSkill> findAllByStudentProfileIdIn(@Param("profileIds") Collection<UUID> profileIds);

    Optional<StudentSkill> findByStudentProfileIdAndSkillId(UUID studentProfileId, UUID skillId);

    @Query(
            "SELECT p.userId as userId, p.id as studentProfileId, s.name as skillName, ss.level as skillLevel " +
                    "FROM StudentSkill ss " +
                    "JOIN ss.studentProfile p " +
                    "JOIN ss.skill s " +
                    "WHERE p.deletedAt IS NULL" // Filters out inactive/deleted profiles
    )
    List<StudentSkillProjection> findAllActiveStudentSkills();

    @Query(
            "SELECT p.userId as userId, p.id as studentProfileId, s.name as skillName, ss.level as skillLevel " +
                    "FROM StudentSkill ss " +
                    "JOIN ss.studentProfile p " +
                    "JOIN ss.skill s " +
                    "WHERE p.deletedAt IS NULL AND p.userId IN :userIds"
    )
    List<StudentSkillProjection> findSkillsForUsers(@Param("userIds") List<UUID> userIds);
}
