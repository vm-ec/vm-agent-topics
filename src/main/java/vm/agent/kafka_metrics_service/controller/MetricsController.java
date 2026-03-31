package vm.agent.kafka_metrics_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vm.agent.kafka_metrics_service.metrics.KafkaMetricsTracker;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final KafkaMetricsTracker metricsTracker;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metricsTracker.getMetrics());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> getHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "kafka-metrics-service"
        ));
    }
}