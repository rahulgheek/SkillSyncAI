package com.skillsync.skillsync_ai.notification.services;

public class NotificationTemplates {

    public static final String TITLE_RESUME_ANALYZED = "AI Resume Analysis Complete";
    public static String messageResumeAnalyzed(int skillCount) {
        return String.format("We successfully extracted %d skills from your resume.", skillCount);
    }

    public static final String TITLE_WELCOME_TEAM = "Welcome to the Team!";
    public static String messageWelcomeTeam() {
        return "You have officially joined the project.";
    }

    public static final String TITLE_PROJECT_PUBLISHED = "Project Published";
    public static String messageProjectPublished(String projectTitle) {
        return String.format("Your project '%s' is now live and recruiting.", projectTitle);
    }
}