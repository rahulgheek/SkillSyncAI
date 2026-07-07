package com.skillsync.skillsync_ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.skillsync.skillsync_ai")
public class SkillsyncAiApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(SkillsyncAiApplication.class, args);
	}

}
