package vm.agent.kafka_metrics_service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import vm.agent.kafka_metrics_service.producer.MetricsProducer;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsScheduler {

    private final RestTemplate restTemplate;
    private final MetricsProducer producer;

    @Value("${ai-agent.metrics.source-url}")
    private String metricsUrl;

    @Scheduled(fixedRate = 10000)
    public void pullMetrics() {
        try {
            log.info("Pulling metrics from: {}", metricsUrl);
            String metrics = restTemplate.getForObject(metricsUrl, String.class);
            
            if (metrics == null || metrics.isEmpty()) {
                log.warn("No metrics received from AI Agent service");
                return;
            }
            
            log.info("Successfully pulled metrics: {}", metrics);
            producer.sendMetrics(metrics);
        } catch (RestClientException e) {
            log.error("Failed to pull metrics from AI Agent service at {}: {}", metricsUrl, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while pulling metrics", e);
        }
    }
}