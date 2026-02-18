package vm.agent.kafka_metrics_service.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import vm.agent.kafka_metrics_service.metrics.KafkaMetricsTracker;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetricsConsumer {

    @Value("${dashboard.ingest-url}")
    private String ingestUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaMetricsTracker metricsTracker;

    @KafkaListener(topics = "${kafka.topic.metrics}", groupId = "metrics-group")
    public void consume(String message) throws Exception {
        try {
            Map<String, Object> jsonPayload = objectMapper.readValue(message, Map.class);
            restTemplate.postForObject(ingestUrl, jsonPayload, Void.class);
            metricsTracker.incrementConsumed();
        } catch (Exception e) {
            metricsTracker.incrementFailed();
            throw e;
        }
    }
}
