package vm.agent.kafka_metrics_service.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class KafkaMetricsTracker {

    @Value("${wrapper.kafka-metrics-url:http://localhost:8083/ai/kafka-metrics}")
    private String wrapperKafkaMetricsUrl;

    @Value("${wrapper.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${wrapper.read-timeout:10000}")
    private int readTimeout;

    private final RestTemplate restTemplate;
    private final AtomicLong produced = new AtomicLong(0);
    private final AtomicLong consumed = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private final AtomicLong lag = new AtomicLong(0);
    private volatile long lastFailureTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int MAX_CONSECUTIVE_FAILURES = 10;
    private static final long FAILURE_COOLDOWN_MS = 60000; // 1 minute

    public KafkaMetricsTracker() {
        ClientHttpRequestFactory factory = new BufferingClientHttpRequestFactory(
                new SimpleClientHttpRequestFactory());
        this.restTemplate = new RestTemplate(factory);
    }

    public void incrementProduced() {
        produced.incrementAndGet();
    }

    public void incrementConsumed() {
        consumed.incrementAndGet();
    }

    public void incrementFailed() {
        failed.incrementAndGet();
    }

    public void updateLag(long lagValue) {
        lag.set(lagValue);
    }

    @Scheduled(fixedRate = 5000)
    public void sendMetricsToWrapper() {
        // Check if we're in cooldown period after too many consecutive failures
        if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLastFailure < FAILURE_COOLDOWN_MS) {
                log.debug("Skipping metrics send due to too many consecutive failures. Retrying in {}ms",
                        FAILURE_COOLDOWN_MS - timeSinceLastFailure);
                return;
            } else {
                // Reset failure count after cooldown period
                consecutiveFailures.set(0);
                log.info("Resuming metrics send after cooldown period");
            }
        }

        try {
            Map<String, Object> metrics = Map.of(
                "produced", produced.get(),
                "consumed", consumed.get(),
                "failed", failed.get(),
                "lag", lag.get()
            );
            log.debug("Sending Kafka metrics to wrapper: {}", metrics);
            restTemplate.postForObject(wrapperKafkaMetricsUrl, metrics, Void.class);
            log.debug("Successfully sent Kafka metrics to {}", wrapperKafkaMetricsUrl);

            // Reset failure counter on successful send
            if (consecutiveFailures.get() > 0) {
                log.info("Connection to wrapper service restored");
                consecutiveFailures.set(0);
            }
        } catch (Exception e) {
            consecutiveFailures.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();

            int currentFailures = consecutiveFailures.get();
            if (currentFailures <= 3) {
                log.warn("Failed to send Kafka metrics to {} (attempt {}): {}",
                        wrapperKafkaMetricsUrl, currentFailures, e.getMessage());
            } else if (currentFailures == MAX_CONSECUTIVE_FAILURES) {
                log.error("Failed to send Kafka metrics to {} after {} attempts. Entering cooldown mode. Error: {}",
                        wrapperKafkaMetricsUrl, currentFailures, e.getMessage());
            } else if (currentFailures % 5 == 0) {
                log.warn("Continuing to fail sending metrics to {} (attempt {})",
                        wrapperKafkaMetricsUrl, currentFailures);
            }
        }
    }
}
