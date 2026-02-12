package vm.agent.kafka_metrics_service.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Service
public class MetricsConsumer {

    @Value("${dashboard.ingest-url}")
    private String ingestUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "${kafka.topic.metrics}", groupId = "metrics-group")
    public void consume(String message) throws Exception {

        // Convert JSON String → Map
        Map<String, Object> jsonPayload =
                objectMapper.readValue(message, Map.class);

        // Send proper JSON body
        restTemplate.postForObject(ingestUrl, jsonPayload, Void.class);
    }
}
