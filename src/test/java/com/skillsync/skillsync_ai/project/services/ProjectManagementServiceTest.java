package com.skillsync.skillsync_ai.project.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsync.skillsync_ai.common.events.ProjectRecruitmentStatusChangedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberAcceptedEvent;
import com.skillsync.skillsync_ai.common.events.TeamMemberRemovedEvent;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import com.skillsync.skillsync_ai.matching.repo.ApplicationRepository;
import com.skillsync.skillsync_ai.matching.repo.InvitationRepository;
import com.skillsync.skillsync_ai.project.dto.ProjectManagementResponse;
import com.skillsync.skillsync_ai.project.dto.UpdateProjectRequest;
import com.skillsync.skillsync_ai.project.dto.UpdateProjectRoleRequest;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.entity.TeamMember;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.project.repo.TeamMemberRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectManagementServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectRoleRepository projectRoleRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private StudentSkillRepository studentSkillRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ProjectManagementService service;

    @Test
    void getProjectManagementReportsSeparateRequestFunnelsAndRoleFill() {
        Project project = project(ProjectStatus.RECRUITING, (short) 4);
        ProjectRole role = role(project, (short) 2);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(2L);
        when(applicationRepository.countByProjectIdGroupByStatus(PROJECT_ID)).thenReturn(List.of(
                new Object[]{ApplicationStatus.PENDING, 3L},
                new Object[]{ApplicationStatus.ACCEPTED, 1L},
                new Object[]{ApplicationStatus.REJECTED, 2L}
        ));
        when(invitationRepository.countByProjectIdGroupByStatus(PROJECT_ID)).thenReturn(List.of(
                new Object[]{InvitationStatus.PENDING, 2L},
                new Object[]{InvitationStatus.DECLINED, 1L}
        ));
        when(invitationRepository.countByProjectIdAndStatusAndExpiresAtBefore(
                eq(PROJECT_ID), eq(InvitationStatus.PENDING), any()))
                .thenReturn(1L);
        when(projectRoleRepository.findAllByProjectId(PROJECT_ID)).thenReturn(List.of(role));
        when(teamMemberRepository.countByProjectIdAndStatusGroupByRole(
                PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(List.<Object[]>of(new Object[]{ROLE_ID, 1L}));
        when(applicationRepository.countByProjectIdAndStatusGroupByRole(
                PROJECT_ID, ApplicationStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[]{ROLE_ID, 3L}));
        when(invitationRepository.countByProjectIdAndStatusGroupByRole(
                PROJECT_ID, InvitationStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[]{ROLE_ID, 2L}));

        ProjectManagementResponse response = service.getProjectManagement(OWNER_ID, PROJECT_ID);

        assertEquals(2, response.currentMembers());
        assertEquals(2, response.openSlots());
        assertFalse(response.full());
        assertTrue(response.acceptingRequests());

        assertEquals(3, response.applications().pending());
        assertEquals(1, response.applications().accepted());
        assertEquals(2, response.applications().rejected());
        assertEquals(6, response.applications().total());

        assertEquals(1, response.invitations().pending());
        assertEquals(1, response.invitations().expired());
        assertEquals(3, response.invitations().total());

        assertEquals(1, response.roles().get(0).filled());
        assertEquals(1, response.roles().get(0).openSlots());
        assertEquals(3, response.roles().get(0).pendingApplications());
        assertEquals(2, response.roles().get(0).pendingInvitations());
        assertFalse(response.roles().get(0).full());
    }

    @Test
    void addMemberDirectlyRejectsFullTeam() {
        Project project = project(ProjectStatus.RECRUITING, (short) 2);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.existsByProjectIdAndUserIdAndStatus(
                PROJECT_ID, STUDENT_ID, MemberStatus.ACTIVE)).thenReturn(false);
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(2L);

        assertThrows(IllegalStateException.class,
                () -> service.addMemberDirectly(OWNER_ID, PROJECT_ID, STUDENT_ID, ROLE_ID));

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    void updateProjectRejectsMaxTeamSizeBelowActiveMembers() {
        Project project = project(ProjectStatus.RECRUITING, (short) 5);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(3L);

        UpdateProjectRequest request = new UpdateProjectRequest(
                "Updated", "desc", (short) 2, Instant.now().plus(7, ChronoUnit.DAYS));

        assertThrows(IllegalStateException.class,
                () -> service.updateProject(OWNER_ID, PROJECT_ID, request));

        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateProjectRoleRejectsHeadcountBelowFilledMembers() {
        Project project = project(ProjectStatus.RECRUITING, (short) 5);
        ProjectRole role = role(project, (short) 3);

        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                PROJECT_ID, ROLE_ID, MemberStatus.ACTIVE)).thenReturn(2L);

        UpdateProjectRoleRequest request = new UpdateProjectRoleRequest(null, null, null, (short) 1);

        assertThrows(IllegalStateException.class,
                () -> service.updateProjectRole(OWNER_ID, PROJECT_ID, ROLE_ID, request));

        verify(projectRoleRepository, never()).save(any());
    }

    @Test
    void deleteProjectRoleRejectsRoleWithAssignedMembers() {
        Project project = project(ProjectStatus.RECRUITING, (short) 5);
        ProjectRole role = role(project, (short) 2);

        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                PROJECT_ID, ROLE_ID, MemberStatus.ACTIVE)).thenReturn(1L);

        assertThrows(IllegalStateException.class,
                () -> service.deleteProjectRole(OWNER_ID, PROJECT_ID, ROLE_ID));

        verify(projectRoleRepository, never()).delete(any());
    }

    @Test
    void addMemberToTeamAutoClosesRecruitmentWhenTeamBecomesFull() {
        Project project = project(ProjectStatus.RECRUITING, (short) 2);

        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.existsByProjectIdAndUserIdAndStatus(
                PROJECT_ID, STUDENT_ID, MemberStatus.ACTIVE)).thenReturn(false);
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(1L, 2L);
        when(teamMemberRepository.save(any(TeamMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.addMemberToTeam(PROJECT_ID, null, STUDENT_ID);

        assertEquals(ProjectStatus.IN_PROGRESS, project.getStatus());
        verify(eventPublisher).publishEvent(any(ProjectRecruitmentStatusChangedEvent.class));
        verify(eventPublisher).publishEvent(any(TeamMemberAcceptedEvent.class));
    }

    @Test
    void removeMemberReopensRecruitmentWhenTeamFallsBelowCapacity() {
        Project project = project(ProjectStatus.IN_PROGRESS, (short) 3);
        TeamMember member = TeamMember.builder()
                .id(UUID.randomUUID())
                .project(project)
                .userId(STUDENT_ID)
                .status(MemberStatus.ACTIVE)
                .build();

        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.findByProjectIdAndUserIdAndStatus(
                PROJECT_ID, STUDENT_ID, MemberStatus.ACTIVE)).thenReturn(Optional.of(member));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(2L);
        when(projectRepository.save(any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.removeMember(OWNER_ID, PROJECT_ID, STUDENT_ID);

        assertEquals(ProjectStatus.RECRUITING, project.getStatus());
        assertEquals(MemberStatus.REMOVED, member.getStatus());
        verify(eventPublisher).publishEvent(any(TeamMemberRemovedEvent.class));
        verify(eventPublisher).publishEvent(any(ProjectRecruitmentStatusChangedEvent.class));
    }

    private Project project(ProjectStatus status, short maxTeamSize) {
        return Project.builder()
                .id(PROJECT_ID)
                .ownerId(OWNER_ID)
                .collegeId(UUID.randomUUID())
                .title("Test Project")
                .description("desc")
                .status(status)
                .maxTeamSize(maxTeamSize)
                .applicationDeadline(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }

    private ProjectRole role(Project project, short headcount) {
        return ProjectRole.builder()
                .id(ROLE_ID)
                .project(project)
                .title("Frontend Developer")
                .headcount(headcount)
                .filledCount((short) 0)
                .build();
    }
}
