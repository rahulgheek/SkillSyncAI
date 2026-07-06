package com.skillsync.skillsync_ai.userprofile.repo;

import com.skillsync.skillsync_ai.userprofile.entity.PreviousProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PreviousProjectRepository extends JpaRepository<PreviousProject, UUID> {
    List<PreviousProject> findAllByStudentProfileIdOrderByStartedOnDesc(UUID studentProfileId);
}