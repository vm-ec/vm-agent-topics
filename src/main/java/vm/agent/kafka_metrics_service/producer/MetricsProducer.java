package vm.agent.kafka_metrics_service.producer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import vm.agent.kafka_metrics_service.metrics.KafkaMetricsTracker;

@Service
@RequiredArgsConstructor
public class MetricsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMetricsTracker metricsTracker;

    @Value("${kafka.topic.metrics}")
    private String topic;

    public void sendMetrics(String json) {
        kafkaTemplate.send(topic, json);
        metricsTracker.incrementProduced();
    }
}
