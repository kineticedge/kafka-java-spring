package io.kineticedge.a;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {

    private boolean cleanupOnStart = false;

    private Map<String, Object> kafka;

    public void setCleanupOnStart(boolean cleanupOnStart) {
        this.cleanupOnStart = cleanupOnStart;
    }

    public boolean isCleanupOnStart() {
        return cleanupOnStart;
    }

    public void setKafka(Map<String, Object> kafka) {
        this.kafka = kafka;
    }

    public Map<String, Object> getKafka() {
        return kafka;
    }
}
