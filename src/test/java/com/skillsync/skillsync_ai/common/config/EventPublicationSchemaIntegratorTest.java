package com.skillsync.skillsync_ai.common.config;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.integrator.spi.IntegratorService;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication;

import static org.assertj.core.api.Assertions.assertThat;

class EventPublicationSchemaIntegratorTest {

    @Test
    void isDiscoveredByHibernate() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            IntegratorService integratorService = registry.requireService(IntegratorService.class);
            assertThat(integratorService.getIntegrators())
                    .anyMatch(EventPublicationSchemaIntegrator.class::isInstance);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void mapsSerializedEventAsText() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(DefaultJpaEventPublication.class)
                    .buildMetadata();

            EventPublicationSchemaIntegrator.widenSerializedEventColumns(metadata);

            Table table = metadata.collectTableMappings().stream()
                    .filter(candidate -> "event_publication".equalsIgnoreCase(candidate.getName()))
                    .findFirst()
                    .orElseThrow();
            Column serializedEvent = table.getColumns().stream()
                    .filter(column -> "serialized_event".equalsIgnoreCase(column.getName())
                            || "serializedEvent".equalsIgnoreCase(column.getName()))
                    .findFirst()
                    .orElseThrow();

            assertThat(serializedEvent.getSqlType(metadata)).isEqualTo("text");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
