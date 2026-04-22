# AGENTS.md - AI Agent Guide

## Project Context

**Kafka Metrics Service** - A Spring Boot microservice that bridges autonomous AI agents with operational dashboards by collecting and streaming metrics through Apache Kafka.

**Role in Ecosystem:** Acts as a real-time metrics pipeline that:
1. **Pulls** MELT data (Metrics, Events, Logs, Traces) from AI agent services
2. **Streams** it through Kafka for reliable async transport
3. **Forwards** to dashboard/monitoring systems for observability

## Architecture Overview

### Data Flow Pattern: Pull → Produce → Consume → Forward

```
AI Agent Service (localhost:8083)
  ↓ (HTTP GET, every 10 seconds)
MetricsScheduler pulls JSON metrics
  ↓
MetricsProducer sends to Kafka
  ↓
Kafka Topic: ai-metrics (localhost:9092)
  ↓
MetricsConsumer listens (@KafkaListener)
  ↓
Dashboard Service (localhost:8082, HTTP POST)
```

### Layer Structure

- **scheduler/** - `MetricsScheduler`: Scheduled pulls every 10 seconds via `@Scheduled(fixedRate=10000)`
- **producer/** - `MetricsProducer`: Publishes to Kafka topic using `KafkaTemplate<String, String>`
- **consumer/** - `MetricsConsumer`: `@KafkaListener` processes messages, forwards via HTTP POST
- **metrics/** - `KafkaMetricsTracker`: Tracks counters (produced/consumed/failed) and heartbeats metrics to wrapper
- **controller/** - `MetricsController`: Exposes REST API `/api/metrics` for service stats
- **config/** - `AppConfig`: Provides `RestTemplate` and `ObjectMapper` beans

## Critical Patterns & Conventions

### 1. Dependency Injection via Lombok
All services use `@Service` with `@RequiredArgsConstructor` (Lombok) for constructor-based injection:
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final SomeDependency dependency;  // injected automatically
}
```

### 2. Externalized Configuration
All service URLs and Kafka settings are in `application.yaml`:
```yaml
ai-agent.metrics.source-url: http://localhost:8083/ai/metrics
kafka.topic.metrics: ai-metrics
dashboard.ingest-url: http://localhost:8082/internal/metrics
wrapper.kafka-metrics-url: http://localhost:8083/ai/kafka-metrics
```
**Do not hardcode URLs** - inject via `@Value("${config.key}")`

### 3. Atomic Counters for Thread Safety
Metrics use `AtomicLong` for concurrent counter updates without synchronization:
```java
private final AtomicLong produced = new AtomicLong(0);
produced.incrementAndGet();  // thread-safe increment
```

### 4. Consumer Error Handling Pattern
When consumer fails, errors are tracked but exception is rethrown for Kafka to handle:
```java
try {
    // process message
    metricsTracker.incrementConsumed();
} catch (Exception e) {
    metricsTracker.incrementFailed();
    throw new RuntimeException(e);  // let Kafka retry/dead letter it
}
```

### 5. Scheduled Tasks with Fixed Rate
Scheduler pulls metrics on fixed 10-second intervals (no initial delay):
```java
@Scheduled(fixedRate = 10000)  // 10 seconds, in milliseconds
public void pullMetrics() {
    String metrics = restTemplate.getForObject(metricsUrl, String.class);
    producer.sendMetrics(metrics);
}
```

### 6. Resilience Pattern: Consecutive Failure Tracking
`KafkaMetricsTracker` implements exponential cooldown after 10 consecutive failures:
- Tracks `lastFailureTime` and `consecutiveFailures`
- After 10 failures, stops attempts for 60 seconds (`FAILURE_COOLDOWN_MS`)
- Used for sending wrapper metrics every 5 seconds

### 7. Message Format: String-based JSON
All Kafka messages are `String` type (JSON payload), converted to `Map<String, Object>` only when needed:
```java
Map<String, Object> jsonPayload = objectMapper.readValue(message, Map.class);
```

## Build & Test Workflow

### Build
```bash
mvn clean package
```

### Run Locally
```bash
mvn spring-boot:run
```

### Key Endpoints
- **Service Metrics:** `GET http://localhost:8086/api/metrics`
- **Health Check:** `GET http://localhost:8086/api/metrics/health`
- **Actuator:** `http://localhost:8086/actuator/*`

### Prerequisites
- Java 17+ (specified in pom.xml)
- Maven 3.6+
- Kafka broker on localhost:9092
- AI Agent service on localhost:8083
- Dashboard service on localhost:8082

## Cross-Component Communication

### 1. Scheduler → Producer
`MetricsScheduler` calls `MetricsProducer.sendMetrics(json)` after pulling from AI service.

### 2. Producer → Kafka
`KafkaTemplate<String, String>` sends to `ai-metrics` topic. Synchronous by default (waits for broker confirmation).

### 3. Kafka → Consumer
`@KafkaListener` on group `metrics-group` consumes from `ai-metrics` topic with `auto-offset-reset: earliest`.

### 4. Consumer → Dashboard
Parsed metrics sent via `RestTemplate.postForObject()` to `${dashboard.ingest-url}`.

### 5. Metrics → Wrapper Callback
Every 5 seconds, `KafkaMetricsTracker.sendMetricsToWrapper()` POSTs current counters to wrapper service for external observability.

## Common Tasks for AI Agents

### Adding New Metrics Endpoint
1. Add `@GetMapping` method in `MetricsController`
2. Retrieve data from `KafkaMetricsTracker` via injected dependency
3. Return as `ResponseEntity<Map<...>>`

### Changing Kafka Topic
Update `kafka.topic.metrics` in `application.yaml` - used by both producer and consumer via `@Value` injection.

### Modifying Pull Frequency
Change `@Scheduled(fixedRate=10000)` in `MetricsScheduler` - value in milliseconds.

### Adding Retry Logic
Consumer currently rethrows exceptions. To add retries, use Spring Kafka's `@RetryableTopic` annotation with `backoff` configuration on `@KafkaListener` methods.

### Debugging Message Flow
- **Check produced count:** `GET /api/metrics` shows `produced` counter
- **Check consumed count:** Same endpoint shows `consumed` counter
- **Check failures:** Same endpoint shows `failed` counter
- **Verify Kafka:** Ensure broker is running on localhost:9092 and topic `ai-metrics` exists

## External Dependencies & Integration Points

| Service | URL | Purpose | Protocol |
|---------|-----|---------|----------|
| AI Agent | `http://localhost:8083/ai/metrics` | Source of metrics | HTTP GET |
| Kafka | `localhost:9092` | Message transport | TCP (Kafka Protocol) |
| Dashboard | `http://localhost:8082/internal/metrics` | Sink for metrics | HTTP POST |
| Wrapper | `http://localhost:8083/ai/kafka-metrics` | Receives service health | HTTP POST |

## Key Technologies

- **Spring Boot 4.0.2** - Application framework
- **Spring Kafka** - Kafka integration
- **Jackson (Fasterxml)** - JSON serialization/deserialization
- **Lombok** - Boilerplate reduction (@RequiredArgsConstructor, @Slf4j)
- **Java 17** - Language version

## Testing Strategy

Project includes test dependencies:
- `spring-boot-starter-actuator-test`
- `spring-boot-starter-kafka-test`
- `spring-boot-starter-webmvc-test`

Tests should verify:
- Scheduler pulls metrics at correct intervals
- Producer messages reach Kafka
- Consumer forwards to dashboard on success
- Failed messages increment failure counter
- Controller endpoints return correct structure

## Known Limitations & Future Improvements

1. **No Dead Letter Queue (DLQ):** Failed consumer messages are logged but not retried. Messages may be lost if dashboard is down.
2. **No Authentication:** Kafka and HTTP endpoints have no security. Internal network only.
3. **Single Consumer Group:** Service must be replicated for HA. No automatic failover.
4. **Lag Counter Unused:** `lag` field in `getMetrics()` exists but is never updated - consider removing or implementing lag tracking.
5. **No Retry Backoff:** Producer sends synchronously without retries.

## File Organization Convention

```
src/main/java/vm/agent/kafka_metrics_service/
├── scheduler/       # Scheduled task orchestration
├── producer/        # Kafka message publishing
├── consumer/        # Kafka message consumption & forwarding
├── controller/      # REST API endpoints
├── metrics/         # Metrics tracking & reporting
└── config/          # Bean configuration
```

Each component has a single responsibility. Add new layers by creating a new package following this pattern.

