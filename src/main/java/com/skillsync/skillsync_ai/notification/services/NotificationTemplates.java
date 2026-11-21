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

    public static final String TITLE_MATCHING_READY = "AI Recommendations Ready";
    public static String messageMatchingReady(int candidateCount) {
        return String.format("We ranked %d match%s for you. Open your dashboard to review them.",
                candidateCount, candidateCount == 1 ? "" : "es");
    }

    public static final String TITLE_APPLICATION_DECIDED = "Application Update";
    public static String messageApplicationDecided(String projectTitle, boolean accepted) {
        return accepted
                ? String.format("Your application to '%s' was accepted.", projectTitle)
                : String.format("Your application to '%s' was not accepted.", projectTitle);
    }

    public static final String TITLE_INVITATION_RESPONDED = "Invitation Update";
    public static String messageInvitationResponded(String studentName, String projectTitle, boolean accepted) {
        return accepted
                ? String.format("%s accepted your invitation to join '%s'.", studentName, projectTitle)
                : String.format("%s declined your invitation to join '%s'.", studentName, projectTitle);
    }

    public static final String TITLE_MEMBER_REMOVED = "Team Membership Update";
    public static String messageMemberRemoved(String projectTitle, boolean removedByOwner) {
        return removedByOwner
                ? String.format("You were removed from '%s'.", projectTitle)
                : String.format("You left '%s'.", projectTitle);
    }

    public static final String TITLE_ROLE_CHANGED = "Project Role Updated";
    public static String messageRoleChanged(String projectTitle, String oldRoleTitle, String newRoleTitle) {
        if (oldRoleTitle == null || oldRoleTitle.isBlank()) {
            return String.format("Your role in '%s' is now %s.", projectTitle, newRoleTitle);
        }
        return String.format("Your role in '%s' changed from %s to %s.",
                projectTitle, oldRoleTitle, newRoleTitle);
    }

    public static final String TITLE_PROJECT_FULL = "Team Full";
    public static String messageProjectFull(String projectTitle) {
        return String.format("'%s' has reached its maximum team size. Recruitment is now closed.", projectTitle);
    }

    public static final String TITLE_PROJECT_REOPENED = "Recruitment Reopened";
    public static String messageProjectReopened(String projectTitle) {
        return String.format("'%s' is below capacity and is recruiting again.", projectTitle);
    }
}