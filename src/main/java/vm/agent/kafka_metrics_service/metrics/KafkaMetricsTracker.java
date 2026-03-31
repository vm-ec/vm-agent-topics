package vm.agent.kafka_metrics_service.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaMetricsTracker {

    @Value("${wrapper.kafka-metrics-url:http://localhost:8083/ai/kafka-metrics}")
    private String wrapperKafkaMetricsUrl;

    private final RestTemplate restTemplate;

    private final AtomicLong produced = new AtomicLong(0);
    private final AtomicLong consumed = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private final AtomicLong lag = new AtomicLong(0);

    private volatile long lastFailureTime = 0;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    private static final int MAX_CONSECUTIVE_FAILURES = 10;
    private static final long FAILURE_COOLDOWN_MS = 60000;

    // ✅ ADD THIS METHOD (your controller needs it)
    public Map<String, Object> getMetrics() {
        return Map.of(
                "produced", produced.get(),
                "consumed", consumed.get(),
                "failed", failed.get(),
                "lag", lag.get()
        );
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

        if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;

            if (timeSinceLastFailure < FAILURE_COOLDOWN_MS) {
                log.debug("Skipping due to cooldown");
                return;
            } else {
                consecutiveFailures.set(0);
                log.info("Resuming after cooldown");
            }
        }

        try {
            Map<String, Object> metrics = getMetrics();

            restTemplate.postForObject(wrapperKafkaMetricsUrl, metrics, Void.class);

            if (consecutiveFailures.get() > 0) {
                log.info("Connection restored");
                consecutiveFailures.set(0);
            }

        } catch (Exception e) {
            consecutiveFailures.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();

            log.warn("Failed sending metrics: {}", e.getMessage());
        }
    }
}