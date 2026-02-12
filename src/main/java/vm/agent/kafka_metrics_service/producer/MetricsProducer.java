package vm.agent.kafka_metrics_service.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.metrics}")
    private String topic;

    public void sendMetrics(String json) {
        kafkaTemplate.send(topic, json);
    }
}
