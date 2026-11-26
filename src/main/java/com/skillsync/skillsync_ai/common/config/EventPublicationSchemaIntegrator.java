package com.skillsync.skillsync_ai.common.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

/**
 * Maps Spring Modulith's event publication payload to PostgreSQL {@code text}.
 *
 * <p>Spring Modulith 2.1.0 declares {@code serializedEvent} with
 * {@code @Column(nullable = false)} and no length, so Hibernate exports the
 * column as {@code varchar(255)}. A domain event with a non-trivial JSON
 * payload (for example a generated roadmap) exceeds that limit and the
 * transaction fails with {@code value too long for type character varying(255)}.</p>
 *
 * <p>An integrator is used instead of only running {@code ALTER TABLE} after
 * startup because Hibernate compares every mapped column with the live schema
 * on each {@code ddl-auto=update} run. If the mapping still says
 * {@code varchar(255)}, Hibernate would narrow a previously widened column
 * back. This runs before schema export, so the mapping and the database agree
 * from the first startup.</p>
 *
 * <p>Discovered through
 * {@code META-INF/services/org.hibernate.integrator.spi.Integrator}.</p>
 */
@Slf4j
public class EventPublicationSchemaIntegrator implements Integrator {

    private static final String EVENT_PUBLICATION_TABLE = "event_publication";
    private static final String EVENT_PUBLICATION_ARCHIVE_TABLE = "event_publication_archive";
    private static final String SERIALIZED_EVENT_COLUMN = "serialized_event";

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext,
                          SessionFactoryImplementor sessionFactory) {
        widenSerializedEventColumns(metadata);
    }

    static void widenSerializedEventColumns(Metadata metadata) {
        for (Table table : metadata.collectTableMappings()) {
            if (!isEventPublicationTable(table)) {
                continue;
            }

            for (Column column : table.getColumns()) {
                if (isSerializedEventColumn(column)) {
                    column.setSqlType("text");
                    log.debug("Mapped {}.{} as text", table.getName(), column.getName());
                }
            }
        }
    }

    private static boolean isEventPublicationTable(Table table) {
        String name = table.getName();
        return name != null
                && (name.equalsIgnoreCase(EVENT_PUBLICATION_TABLE)
                || name.equalsIgnoreCase(EVENT_PUBLICATION_ARCHIVE_TABLE));
    }

    private static boolean isSerializedEventColumn(Column column) {
        String name = column.getName();
        return name != null
                && (name.equalsIgnoreCase(SERIALIZED_EVENT_COLUMN)
                || name.equalsIgnoreCase("serializedEvent"));
    }
}
