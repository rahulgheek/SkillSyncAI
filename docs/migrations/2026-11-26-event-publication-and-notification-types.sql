-- Fix AI roadmap event-publication overflow and the stale notification enum
-- check constraint.
--
-- Apply once to PostgreSQL (Neon compatible). Both statements are safe to
-- re-run. The application also applies them automatically at startup, so this
-- script is the explicit production path for deployments that manage schema
-- changes manually.
--
-- Tested against PostgreSQL 15+.

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Event publication payload
-- ---------------------------------------------------------------------------
-- Spring Modulith 2.1.0 maps event_publication.serialized_event as
-- varchar(255) because the field has @Column(nullable = false) without a
-- length. Domain events such as RoadmapGeneratedEvent serialize to much larger
-- JSON, so committing the transaction failed with
-- "value too long for type character varying(255)".
ALTER TABLE IF EXISTS event_publication
    ALTER COLUMN serialized_event TYPE text;

-- Only present when Modulith event archiving is enabled.
ALTER TABLE IF EXISTS event_publication_archive
    ALTER COLUMN serialized_event TYPE text;

-- ---------------------------------------------------------------------------
-- 2. Notification type check constraint
-- ---------------------------------------------------------------------------
-- Hibernate's ddl-auto=update never refreshes enum check constraints. The
-- existing constraint predates NotificationType.MATCHING_READY, so creating
-- that notification failed. Keep this list in sync with
-- com.skillsync.skillsync_ai.notification.entity.enums.NotificationType.
ALTER TABLE IF EXISTS notification.notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE IF EXISTS notification.notifications
    ADD CONSTRAINT notifications_type_check CHECK (type IN (
        'PROJECT_PUBLISHED',
        'APPLICATION_RECEIVED',
        'APPLICATION_DECIDED',
        'INVITATION_RECEIVED',
        'INVITATION_RESPONDED',
        'TEAM_MEMBER_ADDED',
        'TEAM_MEMBER_REMOVED',
        'TEAM_ROLE_CHANGED',
        'PROJECT_FULL',
        'PROJECT_REOPENED',
        'RESUME_ANALYZED',
        'ROADMAP_GENERATED',
        'MATCHING_READY',
        'SYSTEM_ALERT'
    ));

COMMIT;
