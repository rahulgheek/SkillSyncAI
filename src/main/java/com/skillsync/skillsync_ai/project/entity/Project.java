package com.skillsync.skillsync_ai.project.entity;

import com.skillsync.skillsync_ai.project.entity.enums.ProjectStatus;
import com.skillsync.skillsync_ai.project.entity.enums.RequiredSkill;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "projects",
        schema = "project",
        indexes = {
                @Index(name = "idx_projects_owner", columnList = "owner_id"),
                @Index(name = "idx_projects_college_status", columnList = "college_id, status"),
                @Index(name = "idx_projects_status_created", columnList = "status, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE project.projects SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId; // Cross-module reference -> auth.users.id

    @Column(name = "college_id", nullable = false)
    private UUID collegeId; // Cross-module reference -> auth.colleges.id

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    // Hibernate 6 Native JSONB Mapping
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<RequiredSkill> requiredSkills = new ArrayList<>();

    @Column(name = "max_team_size")
    private Short maxTeamSize;

    @Column(name = "application_deadline")
    private Instant applicationDeadline;

    /**
     * Optimistic lock. Capacity-sensitive membership changes also take a
     * pessimistic row lock through ProjectRepository.findByIdForUpdate, but
     * the version protects edits that do not go through that path.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    // --- Auditing & Soft Delete Fields ---

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}