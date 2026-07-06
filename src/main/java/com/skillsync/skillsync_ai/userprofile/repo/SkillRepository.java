package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {
    Optional<Skill> findByNameIgnoreCase(String name);
    List<Skill> findByCategoryAndIsActiveTrue(String category);
    List<Skill> findByNameContainingIgnoreCaseAndIsActiveTrue(String nameFragment);
}