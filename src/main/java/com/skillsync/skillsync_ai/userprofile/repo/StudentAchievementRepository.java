package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.StudentAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentAchievementRepository extends JpaRepository<StudentAchievement, UUID> {
    List<StudentAchievement> findAllByStudentProfileIdOrderByDateDesc(UUID studentProfileId);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM StudentAchievement a JOIN FETCH a.studentProfile p WHERE p.userId IN :userIds AND p.deletedAt IS NULL")
    List<StudentAchievement> findAchievementsForUsers(@org.springframework.data.repository.query.Param("userIds") List<UUID> userIds);
}
