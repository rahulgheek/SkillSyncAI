package com.skillsync.skillsync_ai.notes.entity;

import com.skillsync.skillsync_ai.notes.entity.enums.SummaryStatus;
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

@Entity
@Table(name = "notes", schema = "notes", indexes = {
        @Index(name = "idx_note_owner_updated", columnList = "owner_user_id, updated_at"),
        @Index(name = "idx_note_owner_project", columnList = "owner_user_id, project_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE notes.notes SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Cross-module ref -> auth.users.id
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    // Cross-module ref -> project.projects.id (nullable for personal notes)
    @Column(name = "project_id")
    private UUID projectId;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    // AI summary cached in the database
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false)
    @Builder.Default
    private SummaryStatus summaryStatus = SummaryStatus.IDLE;

    // NEW: Track when the AI last ran so the frontend can compare it to updatedAt
    @Column(name = "summary_updated_at")
    private Instant summaryUpdatedAt;

    // Clean Lombok boolean naming
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private boolean pinned = false;

    // Simple tag list for frontend filtering
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "note_tags", schema = "notes", joinColumns = @JoinColumn(name = "note_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

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