package vm.agent.kafka_metrics_service.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vm.agent.kafka_metrics_service.producer.MetricsProducer;

@EnableScheduling
@Service
@RequiredArgsConstructor
public class MetricsScheduler {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MetricsProducer producer;

    @Value("${ai-agent.metrics.source-url}")
    private String metricsUrl;

    @Scheduled(fixedRate = 10000)
    public void pullMetrics() {
        String metrics = restTemplate.getForObject(metricsUrl, String.class);
        producer.sendMetrics(metrics);
    }
}
