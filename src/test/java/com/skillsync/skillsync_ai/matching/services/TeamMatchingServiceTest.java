package com.skillsync.skillsync_ai.matching.services;

import com.skillsync.skillsync_ai.common.events.ApplicationDecidedEvent;
import com.skillsync.skillsync_ai.common.events.InvitationRespondedEvent;
import com.skillsync.skillsync_ai.matching.entity.Application;
import com.skillsync.skillsync_ai.matching.entity.Invitation;
import com.skillsync.skillsync_ai.matching.entity.enums.ApplicationStatus;
import com.skillsync.skillsync_ai.matching.entity.enums.InvitationStatus;
import com.skillsync.skillsync_ai.matching.repo.ApplicationRepository;
import com.skillsync.skillsync_ai.matching.repo.InvitationRepository;
import com.skillsync.skillsync_ai.project.ProjectGateway;
import com.skillsync.skillsync_ai.project.entity.Project;
import com.skillsync.skillsync_ai.project.entity.ProjectRole;
import com.skillsync.skillsync_ai.project.entity.enums.MemberStatus;
import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.repo.ProjectRepository;
import com.skillsync.skillsync_ai.project.repo.ProjectRoleRepository;
import com.skillsync.skillsync_ai.project.repo.TeamMemberRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentProfileRepository;
import com.skillsync.skillsync_ai.userprofile.repo.StudentSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamMatchingServiceTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID INVITATION_ID = UUID.randomUUID();

    @Mock private ApplicationRepository applicationRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectRoleRepository projectRoleRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private StudentSkillRepository studentSkillRepository;
    @Mock private ProjectGateway projectGateway;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TeamMatchingService service;

    @Test
    void applyToProjectRejectsProjectThatIsNotRecruiting() {
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.IN_PROGRESS, (short) 5)));

        assertThrows(IllegalStateException.class,
                () -> service.applyToProject(STUDENT_ID, PROJECT_ID, ROLE_ID, "hello"));

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyToProjectRejectsExpiredDeadline() {
        Project project = project(ProjectStatus.RECRUITING, (short) 5);
        project.setApplicationDeadline(Instant.now().minus(1, ChronoUnit.DAYS));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));

        assertThrows(IllegalStateException.class,
                () -> service.applyToProject(STUDENT_ID, PROJECT_ID, ROLE_ID, "hello"));

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyToProjectRejectsFullTeam() {
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.RECRUITING, (short) 2)));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(2L);

        assertThrows(IllegalStateException.class,
                () -> service.applyToProject(STUDENT_ID, PROJECT_ID, ROLE_ID, "hello"));

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyToProjectRejectsFullRole() {
        Project project = project(ProjectStatus.RECRUITING, (short) 5);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(1L);
        when(projectRoleRepository.findById(ROLE_ID))
                .thenReturn(Optional.of(role(project, (short) 1)));
        when(teamMemberRepository.countByProjectIdAndProjectRoleIdAndStatus(
                PROJECT_ID, ROLE_ID, MemberStatus.ACTIVE)).thenReturn(1L);

        assertThrows(IllegalStateException.class,
                () -> service.applyToProject(STUDENT_ID, PROJECT_ID, ROLE_ID, "hello"));

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyToProjectCreatesPendingApplicationWhenCapacityIsAvailable() {
        Project project = project(ProjectStatus.RECRUITING, (short) 5);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(1L);
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.findByUserId(STUDENT_ID)).thenReturn(Optional.empty());
        when(studentProfileRepository.findAllByUserIdIn(any())).thenReturn(List.of());
        when(projectRoleRepository.findAllByIdIn(any())).thenReturn(List.of());

        var response = service.applyToProject(STUDENT_ID, PROJECT_ID, null, "hello");

        assertEquals(ApplicationStatus.PENDING, response.status());
        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void decideOnApplicationAddsMemberBeforeMarkingApplicationAccepted() {
        Application application = application(ApplicationStatus.PENDING);
        Project project = project(ProjectStatus.RECRUITING, (short) 5);

        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.findAllByUserIdIn(any())).thenReturn(List.of());
        when(projectRoleRepository.findAllByIdIn(any())).thenReturn(List.of());

        var response = service.decideOnApplication(OWNER_ID, APPLICATION_ID, true);

        assertEquals(ApplicationStatus.ACCEPTED, response.status());

        InOrder order = inOrder(projectGateway, applicationRepository);
        order.verify(projectGateway).addMember(PROJECT_ID, ROLE_ID, STUDENT_ID);
        order.verify(applicationRepository).save(application);

        verify(eventPublisher).publishEvent(any(ApplicationDecidedEvent.class));
    }

    @Test
    void decideOnApplicationDoesNotMarkAcceptedWhenCapacityFails() {
        Application application = application(ApplicationStatus.PENDING);
        Project project = project(ProjectStatus.RECRUITING, (short) 2);

        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        doThrow(new IllegalStateException("The team is already full."))
                .when(projectGateway).addMember(PROJECT_ID, ROLE_ID, STUDENT_ID);

        assertThrows(IllegalStateException.class,
                () -> service.decideOnApplication(OWNER_ID, APPLICATION_ID, true));

        assertEquals(ApplicationStatus.PENDING, application.getStatus());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void inviteStudentRejectsWhenTeamIsFull() {
        Project project = project(ProjectStatus.RECRUITING, (short) 2);
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(teamMemberRepository.countByProjectIdAndStatus(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(2L);

        assertThrows(IllegalStateException.class,
                () -> service.inviteStudent(OWNER_ID, PROJECT_ID, ROLE_ID, STUDENT_ID, "join us"));

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void respondToInvitationRejectsAcceptWhenProjectIsClosed() {
        Invitation invitation = invitation(InvitationStatus.PENDING);
        when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(projectRepository.findByIdForUpdate(PROJECT_ID))
                .thenReturn(Optional.of(project(ProjectStatus.IN_PROGRESS, (short) 5)));

        assertThrows(IllegalStateException.class,
                () -> service.respondToInvitation(STUDENT_ID, INVITATION_ID, true));

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void respondToInvitationAcceptsAndAddsMember() {
        Invitation invitation = invitation(InvitationStatus.PENDING);
        Project project = project(ProjectStatus.RECRUITING, (short) 5);

        when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(invitation));
        when(projectRepository.findByIdForUpdate(PROJECT_ID)).thenReturn(Optional.of(project));
        when(invitationRepository.save(any(Invitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.findAllByUserIdIn(any())).thenReturn(List.of());
        when(projectRoleRepository.findAllByIdIn(any())).thenReturn(List.of());

        var response = service.respondToInvitation(STUDENT_ID, INVITATION_ID, true);

        assertEquals(InvitationStatus.ACCEPTED, response.status());
        verify(projectGateway).addMember(PROJECT_ID, ROLE_ID, STUDENT_ID);
        verify(eventPublisher).publishEvent(any(InvitationRespondedEvent.class));
    }

    private Project project(ProjectStatus status, short maxTeamSize) {
        return Project.builder()
                .id(PROJECT_ID)
                .ownerId(OWNER_ID)
                .collegeId(UUID.randomUUID())
                .title("Test Project")
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

    private Application application(ApplicationStatus status) {
        return Application.builder()
                .id(APPLICATION_ID)
                .projectId(PROJECT_ID)
                .projectRoleId(ROLE_ID)
                .applicantUserId(STUDENT_ID)
                .status(status)
                .build();
    }

    private Invitation invitation(InvitationStatus status) {
        return Invitation.builder()
                .id(INVITATION_ID)
                .projectId(PROJECT_ID)
                .projectRoleId(ROLE_ID)
                .invitedUserId(STUDENT_ID)
                .invitedBy(OWNER_ID)
                .status(status)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }
}
