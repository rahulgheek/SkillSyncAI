package com.skillsync.skillsync_ai.ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapSessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A roadmap session groups one or more AI-generated roadmaps under a single
 * user journey. Unlike the previous "one active roadmap per user" model, a
 * user can create and revisit as many sessions as they want.
 */
@Entity
@Table(
        name = "roadmap_sessions",
        schema = "ai",
        indexes = {
                @Index(name = "idx_roadmap_sessions_user_last_viewed", columnList = "user_id, last_viewed_at"),
                @Index(name = "idx_roadmap_sessions_user_status", columnList = "user_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE ai.roadmap_sessions SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RoadmapSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "student_profile_id")
    private UUID studentProfileId;

    @Column(nullable = false)
    private String title;

    @Column(name = "target_role")
    private String targetRole;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoadmapSessionStatus status = RoadmapSessionStatus.ACTIVE;

    /**
     * Updated every time the user opens the session. The dashboard uses this
     * to show the most recently viewed session rather than the most recently
     * generated one.
     */
    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Roadmap> roadmaps = new ArrayList<>();

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
