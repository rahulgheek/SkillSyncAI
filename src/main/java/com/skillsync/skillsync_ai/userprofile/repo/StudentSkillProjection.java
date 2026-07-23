package com.skillsync.skillsync_ai.userprofile.repo;

import java.util.UUID;

public interface StudentSkillProjection {
    UUID getUserId();
    UUID getStudentProfileId();
    String getSkillName();
    String getSkillLevel();
}