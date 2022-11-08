package io.kineticedge.a.stream;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.kineticedge.a.ApplicationProperties;
import io.kineticedge.configuration.util.PropertyUtil;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;


@Component
public class Stream {

    private static final Logger log = LoggerFactory.getLogger(Stream.class);

    private final ApplicationContext applicationContext;
    private final ApplicationProperties applicationProperties;

    private KafkaStreams kafkaStreams = null;

    @Autowired
    public Stream(
            final ApplicationContext applicationContext,
            final ApplicationProperties applicationProperties) {
        this.applicationContext = applicationContext;
        this.applicationProperties = applicationProperties;
    }

    @PostConstruct
    public void start() {

        Map<String, Object> properties = applicationProperties.getKafka();

        properties.put(CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG, "SAMPLE");

        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        create(applicationContext, streamsBuilder(), applicationProperties.getKafka(), applicationProperties.isCleanupOnStart());
    }

    private StreamsBuilder streamsBuilder() {

        final StreamsBuilder builder = new StreamsBuilder();

        builder.stream("foo")
                .peek((k, v) -> log.error("k={}, v={}", k, v))
                .to("bar");

        return builder;
    }

    private static final Duration SHUTDOWN = Duration.ofSeconds(20);


    public KafkaStreams create(final ApplicationContext applicationContext, final StreamsBuilder builder, final Map<String, Object> streamProperties, boolean cleanupOnStart) {

        final Properties properties = PropertyUtil.toProperties(streamProperties);
        final Topology topology = builder.build(properties);

        log.info("Topology:\n" + topology.describe());

        kafkaStreams = new KafkaStreams(topology, properties);

        if (cleanupOnStart) {
            log.warn("cleanup of local state-store directory, should not be used in production.");
            kafkaStreams.cleanUp();
        }

        // if a stream gets an exception that fails to be handled within the DSL, we want the spring-boot
        // application to shutdown so it can be restarted by the application orchestrator (e.g. k8s).
        kafkaStreams.setUncaughtExceptionHandler(
                e -> {
                    log.error("unhandled streams exception, shutting down.", e);
                    log.info("Kafka Streams state={}", kafkaStreams.state());
                    final int exit = SpringApplication.exit(applicationContext, (ExitCodeGenerator) () -> 1);

                    System.out.println("Spring Exit : " + exit);
                    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
                });

        kafkaStreams.setStateListener((newState, oldState) -> {
            log.info("Kafka Streams State Change: new={}, previous={}", newState, oldState);
        });

        kafkaStreams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Runtime shutdown hook.");
            log.info("Kafka Streams state={}", kafkaStreams.state());
            if (kafkaStreams.state().isRunningOrRebalancing()) {
                log.info("shutting down Kafka Streams.");

                //TODO "scaling down" behavior...
                KafkaStreams.CloseOptions closeOptions = new KafkaStreams.CloseOptions().timeout(SHUTDOWN).leaveGroup(true);

                final boolean cleanShutdown = kafkaStreams.close(closeOptions);
                if (!cleanShutdown) {
                    System.out.println("Kafka Streams was not shut down cleaning, aborted after waiting=" + SHUTDOWN);
                } else {
                    System.out.println("Kafka Streams cleanly shut down.");
                }
            }
            System.out.println("Kafka Streams down");
        }));

        return kafkaStreams;
    }
}
