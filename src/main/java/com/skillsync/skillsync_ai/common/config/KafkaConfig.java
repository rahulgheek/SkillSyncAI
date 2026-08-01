package com.skillsync.skillsync_ai.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String RESUME_UPLOADED_TOPIC = "resume-uploaded-topic";
    public static final String RESUME_ANALYZED_TOPIC = "resume-analyzed-topic";
    public static final String CERTIFICATE_UPLOADED_TOPIC = "certificate-uploaded-topic";

    @Bean
    public NewTopic resumeUploadedTopic() {
        return TopicBuilder.name(RESUME_UPLOADED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic resumeAnalyzedTopic() {
        return TopicBuilder.name(RESUME_ANALYZED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic certificateUploadedTopic() {
        return TopicBuilder.name(CERTIFICATE_UPLOADED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
