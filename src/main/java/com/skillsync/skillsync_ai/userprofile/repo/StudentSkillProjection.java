package com.skillsync.skillsync_ai.userprofile.repo;

import java.util.UUID;

public interface StudentSkillProjection {
    UUID getUserId();
    String getSkillName();
    String getSkillLevel();
}