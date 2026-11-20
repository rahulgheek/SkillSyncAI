package com.skillsync.skillsync_ai.ai.entity;

import com.skillsync.skillsync_ai.ai.entity.enums.RoadmapStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "roadmaps",
        schema = "ai",
        indexes = @Index(name = "idx_roadmaps_user_status", columnList = "user_id, status")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE ai.roadmaps SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Roadmap {

    // 1. THE FIX: Roadmap gets its own unique ID
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 2. THE FIX: userId becomes a normal column to track ownership
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "student_profile_id", nullable = false)
    private UUID studentProfileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_gap_analysis_id")
    private SkillGapAnalysis skillGapAnalysis;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String curriculum;

    @Column(name = "current_topic")
    private String currentTopic;

    @Column(name = "completed_topics", columnDefinition = "TEXT")
    private String completedTopics;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoadmapStatus status = RoadmapStatus.ACTIVE;

    @OneToMany(mappedBy = "roadmap", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private java.util.List<Checkpoint> checkpoints;

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