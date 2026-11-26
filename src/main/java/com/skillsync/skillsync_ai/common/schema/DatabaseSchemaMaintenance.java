package com.skillsync.skillsync_ai.common.schema;

import com.skillsync.skillsync_ai.notification.entity.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Applies schema fixes that Hibernate's {@code ddl-auto=update} cannot express.
 *
 * <p>Hibernate creates missing tables and columns, but it never widens an
 * existing column and it never refreshes enum check constraints. Both gaps
 * caused production failures:</p>
 *
 * <ul>
 *   <li>{@code event_publication.serialized_event} was created as
 *       {@code varchar(255)}, so publishing a large roadmap event failed.</li>
 *   <li>{@code notifications_type_check} predated
 *       {@link NotificationType#MATCHING_READY}, so inserting that notification
 *       failed.</li>
 * </ul>
 *
 * <p>This component runs after every singleton (including the
 * {@code EntityManagerFactory}, which performs Hibernate's schema update) has
 * been initialised. The event-publication column type is also mapped as
 * {@code text} by {@link com.skillsync.skillsync_ai.common.config.EventPublicationSchemaIntegrator},
 * which prevents Hibernate from narrowing the column on the next startup.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSchemaMaintenance implements SmartInitializingSingleton {

    private static final String EVENT_PUBLICATION_TABLE = "event_publication";
    private static final String NOTIFICATION_TABLE = "notification.notifications";
    private static final String NOTIFICATION_TYPE_CONSTRAINT = "notifications_type_check";
    private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']+)'");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void afterSingletonsInstantiated() {
        widenEventPublicationPayload();
        refreshNotificationTypeConstraint();
    }

    private void widenEventPublicationPayload() {
        try {
            jdbcTemplate.execute("ALTER TABLE " + EVENT_PUBLICATION_TABLE
                    + " ALTER COLUMN serialized_event TYPE text");
            log.debug("Ensured {}.serialized_event is text", EVENT_PUBLICATION_TABLE);
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Could not widen " + EVENT_PUBLICATION_TABLE + ".serialized_event to text. "
                            + "Apply docs/migrations/2026-11-26-event-publication-and-notification-types.sql "
                            + "manually, then restart.",
                    e);
        }
    }

    private void refreshNotificationTypeConstraint() {
        try {
            String existingDefinition = findNotificationTypeConstraint();
            if (existingDefinition != null && definesExactlyNotificationTypes(existingDefinition)) {
                return;
            }

            jdbcTemplate.execute("ALTER TABLE " + NOTIFICATION_TABLE
                    + " DROP CONSTRAINT IF EXISTS " + NOTIFICATION_TYPE_CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE " + NOTIFICATION_TABLE
                    + " ADD CONSTRAINT " + NOTIFICATION_TYPE_CONSTRAINT
                    + " CHECK (type IN (" + notificationTypeLiterals() + "))");
            log.info("Refreshed {} with {} values", NOTIFICATION_TYPE_CONSTRAINT,
                    NotificationType.values().length);
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Could not refresh " + NOTIFICATION_TABLE + "." + NOTIFICATION_TYPE_CONSTRAINT + ". "
                            + "Apply docs/migrations/2026-11-26-event-publication-and-notification-types.sql "
                            + "manually, then restart.",
                    e);
        }
    }

    private String findNotificationTypeConstraint() {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT pg_get_constraintdef(c.oid)
                    FROM pg_constraint c
                    JOIN pg_class t ON t.oid = c.conrelid
                    JOIN pg_namespace n ON n.oid = t.relnamespace
                    WHERE c.conname = ?
                      AND t.relname = 'notifications'
                      AND n.nspname = 'notification'
                    """, String.class, NOTIFICATION_TYPE_CONSTRAINT);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean definesExactlyNotificationTypes(String definition) {
        Set<String> actual = new HashSet<>();
        Matcher matcher = QUOTED_LITERAL.matcher(definition);
        while (matcher.find()) {
            actual.add(matcher.group(1));
        }

        Set<String> expected = Arrays.stream(NotificationType.values())
                .map(NotificationType::name)
                .collect(Collectors.toSet());
        return actual.equals(expected);
    }

    private String notificationTypeLiterals() {
        return Arrays.stream(NotificationType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
    }
}
