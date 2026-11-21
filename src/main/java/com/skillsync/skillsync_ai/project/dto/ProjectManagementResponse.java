package com.skillsync.skillsync_ai.project.dto;

import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owner-facing project management snapshot. Applications and invitations are
 * reported separately because they are different request funnels and the
 * owner acts on them from different screens.
 */
public record ProjectManagementResponse(
        UUID projectId,
        String title,
        ProjectStatus status,
        Integer maxTeamSize,
        int currentMembers,
        int openSlots,
        boolean full,
        boolean acceptingRequests,
        boolean deadlinePassed,
        Instant applicationDeadline,
        long daysRemaining,
        ApplicationCounts applications,
        InvitationCounts invitations,
        List<RoleManagement> roles
) {
    public record ApplicationCounts(
            long pending,
            long accepted,
            long rejected,
            long withdrawn,
            long total
    ) {}

    public record InvitationCounts(
            long pending,
            long accepted,
            long declined,
            long expired,
            long revoked,
            long total
    ) {}

    public record RoleManagement(
            UUID id,
            String title,
            String description,
            List<RequiredSkill> requiredSkills,
            int headcount,
            int filled,
            int openSlots,
            long pendingApplications,
            long pendingInvitations,
            boolean full
    ) {}
}
