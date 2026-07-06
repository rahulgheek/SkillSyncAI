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
}