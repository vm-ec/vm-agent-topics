package vm.agent.kafka_metrics_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling

@SpringBootApplication
public class KafkaMetricsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaMetricsServiceApplication.class, args);
	}

}
