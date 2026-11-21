package com.skillsync.skillsync_ai.userprofile.entity;

import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillLevel;
import com.skillsync.skillsync_ai.userprofile.entity.enums.SkillSource;
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
        name = "student_skills",
        schema = "profile",
        uniqueConstraints = @UniqueConstraint(name = "uk_student_skills_profile_skill", columnNames = {"student_profile_id", "skill_id"}),
        indexes = {
                @Index(name = "idx_student_skills_profile", columnList = "student_profile_id"),
                @Index(name = "idx_student_skills_skill", columnList = "skill_id"),
                // Shortlist join: skill first, then profile. The reverse order
                // makes PostgreSQL scan every profile that owns a skill.
                @Index(name = "idx_student_skills_skill_profile", columnList = "skill_id, student_profile_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE profile.student_skills SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class StudentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SkillLevel level = SkillLevel.BEGINNER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SkillSource source = SkillSource.MANUAL;

    @Column(name = "endorsed_count", nullable = false)
    @Builder.Default
    private Integer endorsedCount = 0;

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