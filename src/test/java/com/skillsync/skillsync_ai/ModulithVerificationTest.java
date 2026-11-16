package com.skillsync.skillsync_ai;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerificationTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules modules = ApplicationModules.of(SkillsyncAiApplication.class);

        // This prints a beautiful breakdown of your modules in the build logs
        System.out.println(modules);

        // This throws an exception if any module tries to illegally import another module's internal classes
        modules.verify();
    }
}