package com.skillsync.skillsync_ai.matching.entity;

import com.skillsync.skillsync_ai.common.enums.RecommendationConfidence;
import com.skillsync.skillsync_ai.matching.entity.enums.RecommendationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "project_recommendations",
        schema = "matching",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"project_id", "user_id"}) // Fix 10: Uniqueness
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProjectRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "semantic_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal semanticScore; // Fix 2: Renamed from aiScore

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationConfidence confidence; // Fix 3: HIGH, MEDIUM, LOW

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion; // Fix 4: e.g., "v1.0-batch-skills"

    @Column(name = "final_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationStatus status; // GENERATED, LOW_MATCH, etc.

    @Column(name = "expires_at")
    private Instant expiresAt; // Fix 9: Expiration

    // --- Auditing Fields (Fix 8) ---
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version; // Optimistic locking

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "matched_skills", columnDefinition = "jsonb")
    private java.util.List<String> matchedSkills = new java.util.ArrayList<>();
}