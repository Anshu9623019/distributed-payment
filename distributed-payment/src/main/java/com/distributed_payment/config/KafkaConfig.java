package com.distributed_payment.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private Resource truststoreResource;

    @Value("${spring.kafka.properties.ssl.truststore.password:}")
    private String truststorePassword;

    @Value("${spring.kafka.properties.ssl.keystore.location:}")
    private Resource keystoreResource;

    @Value("${spring.kafka.properties.ssl.keystore.password:}")
    private String keystorePassword;

    @Value("${spring.kafka.properties.ssl.key.password:}")
    private String keyPassword;

    /**
     * Safely extracts keys out of the compiled executable JAR context into the
     * ephemeral file system runtime space for the native Kafka Driver.
     */
    private void appendSslProperties(Map<String, Object> props) {
        if ("SSL".equalsIgnoreCase(securityProtocol)) {
            props.put("security.protocol", "SSL");
            try {
                if (truststoreResource != null && truststoreResource.exists()) {
                    String tsPath = copyResourceToTempFile(truststoreResource, "truststore", ".jks");
                    props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, tsPath);
                }
                if (keystoreResource != null && keystoreResource.exists()) {
                    String ksPath = copyResourceToTempFile(keystoreResource, "keystore", ".jks");
                    props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ksPath);
                }
            } catch (IOException e) {
                throw new IllegalStateException("CRITICAL CONFIGURATION ERROR: Failed to unpack Kafka JKS certificates from JAR structure", e);
            }
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
            props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword);
            props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);
        }
    }

    private String copyResourceToTempFile(Resource resource, String prefix, String suffix) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            File tempFile = File.createTempFile(prefix + "_", suffix);
            tempFile.deleteOnExit(); // Clean up system disk storage space when VM terminates
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile.getAbsolutePath();
        }
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        appendSslProperties(configProps);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        appendSslProperties(configProps);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate(ProducerFactory<String, String> stringProducerFactory) {
        return new KafkaTemplate<>(stringProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        appendSslProperties(props);
        return new DefaultKafkaConsumerFactory<>(props);
    }
}