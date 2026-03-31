# Kafka Metrics Service - System Summary

## 1. Project Overview

**Type:** Spring Boot Application (Microservice)  
**Primary Purpose:** Real-time metrics collection and streaming pipeline using Apache Kafka  
**Key Responsibility:** Acts as a central hub to pull metrics from AI agents, stream them through Kafka, and forward them to operational dashboards for monitoring and observability.

This service bridges autonomous agents with monitoring dashboards, ensuring real-time visibility into agent telemetry (Metrics, Events, Logs, Traces - MELT data).

---

## 2. Entry Point(s)

**Main Class:** `KafkaMetricsServiceApplication.java`

```
Location: src/main/java/vm/agent/kafka_metrics_service/KafkaMetricsServiceApplication.java
```

**How it starts:**
1. Spring Boot application initializes via `SpringApplication.run()`
2. `@EnableScheduling` annotation enables scheduled tasks
3. Container listens on port **8086** (configured in `application.yaml`)
4. All Spring beans and Kafka listeners are automatically instantiated by the Spring container

**Initialization Flow:**
```
main() 
  → SpringApplication.run()
    → Spring Context Initialization
      → Bean Creation (@Configuration, @Service, @Component)
      → Kafka listeners registered
      → Scheduled tasks enabled
      → Application ready on port 8086
```

---

## 3. High-Level Architecture

**Architecture Pattern:** Layered (Event-Driven Producer-Consumer Pattern)

**Major Layers/Modules:**

1. **Scheduler Layer** (`scheduler/`)
   - `MetricsScheduler` - Periodically pulls metrics from external AI agent service
   
2. **Producer Layer** (`producer/`)
   - `MetricsProducer` - Publishes metrics to Kafka topic
   
3. **Kafka Topic** (in-memory distributed queue)
   - Topic: `ai-metrics`
   
4. **Consumer Layer** (`consumer/`)
   - `MetricsConsumer` - Listens to Kafka messages and forwards to dashboard
   
5. **API Layer** (`controller/`)
   - `MetricsController` - REST endpoints to expose metrics and health status
   
6. **Metrics Tracking Layer** (`metrics/`)
   - `KafkaMetricsTracker` - Tracks produced/consumed/failed counts
   
7. **Configuration Layer** (`config/`)
   - `AppConfig` - Beans for RestTemplate and ObjectMapper

---

## 4. End-to-End Flow (VERY IMPORTANT)

**Complete Request/Data Flow:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KAFKA METRICS SERVICE FLOW                           │
└─────────────────────────────────────────────────────────────────────────────┘

1. PULL PHASE (Every 10 seconds)
   ────────────────────────────────
   MetricsScheduler.pullMetrics()
        ↓
   RestTemplate.getForObject()
        ↓
   External Service (http://localhost:8083/ai/metrics)
        ↓
   Receive metrics as JSON string

2. PRODUCE PHASE
   ──────────────
   MetricsProducer.sendMetrics(metricsJson)
        ↓
   KafkaTemplate.send(topic="ai-metrics", message=metricsJson)
        ↓
   Message stored in Kafka broker (localhost:9092)
        ↓
   KafkaMetricsTracker.incrementProduced()

3. CONSUME PHASE
   ──────────────
   MetricsConsumer listens on topic="ai-metrics"
        ↓
   @KafkaListener receives message
        ↓
   ObjectMapper.readValue() converts JSON string → Map<String, Object>
        ↓
   RestTemplate.postForObject(ingestUrl, jsonPayload)
        ↓
   Forward to Dashboard (http://localhost:8082/internal/metrics)
        ↓
   KafkaMetricsTracker.incrementConsumed()

4. MONITORING PHASE
   ──────────────────
   MetricsController.getMetrics() 
        ↓
   Return {produced, consumed, failed, lag} counts via REST API
        ↓
   GET /api/metrics returns current service metrics

ERROR HANDLING:
   If consumer fails → catch exception → incrementFailed() → rethrow
```

**File/Class Names Involved:**
- `MetricsScheduler.java` - Initiates pull
- `MetricsProducer.java` - Sends to Kafka
- `MetricsConsumer.java` - Consumes from Kafka
- `MetricsController.java` - Exposes metrics
- `KafkaMetricsTracker.java` - Tracks statistics

---

## 5. Key Components & Their Responsibilities

### **MetricsScheduler** (`scheduler/MetricsScheduler.java`)
- **Responsibility:** Pull metrics from external AI agent service at fixed intervals
- **Trigger:** Scheduled task runs every 10,000 ms (10 seconds)
- **Method:** `pullMetrics()` 
- **Logic:** 
  - Calls REST endpoint: `${ai-agent.metrics.source-url}`
  - Receives JSON string
  - Delegates to `MetricsProducer.sendMetrics()`

### **MetricsProducer** (`producer/MetricsProducer.java`)
- **Responsibility:** Publish metrics to Kafka topic
- **Key Method:** `sendMetrics(String json)`
- **Logic:**
  - Uses `KafkaTemplate` to send message
  - Publishes to topic: `${kafka.topic.metrics}` (value: `ai-metrics`)
  - Calls `KafkaMetricsTracker.incrementProduced()` for statistics

### **MetricsConsumer** (`consumer/MetricsConsumer.java`)
- **Responsibility:** Consume messages from Kafka and forward to dashboard
- **Trigger:** `@KafkaListener` on topic `ai-metrics`
- **Group ID:** `metrics-group`
- **Logic:**
  - Receives Kafka message as String
  - Parses JSON using `ObjectMapper` into Map
  - POSTs parsed data to dashboard URL: `${dashboard.ingest-url}`
  - Increments consumed counter on success
  - Increments failed counter on exception

### **MetricsController** (`controller/MetricsController.java`)
- **Responsibility:** Expose REST API endpoints for monitoring
- **Base Path:** `/api/metrics`
- **Endpoints:**
  - `GET /api/metrics` - Returns {produced, consumed, failed, lag}
  - `GET /api/metrics/health` - Returns {status: "UP", service: "kafka-metrics-service"}

### **KafkaMetricsTracker** (`metrics/KafkaMetricsTracker.java`)
- **Responsibility:** Track and expose Kafka operation statistics
- **Counters:**
  - `produced` - Total messages sent to Kafka
  - `consumed` - Total messages received from Kafka
  - `failed` - Total processing failures
  - `lag` - Consumer lag tracking
- **Key Methods:**
  - `incrementProduced()` - Increment produced counter
  - `incrementConsumed()` - Increment consumed counter
  - `incrementFailed()` - Increment failed counter
  - `getMetrics()` - Return current metrics as Map

### **AppConfig** (`config/AppConfig.java`)
- **Responsibility:** Configure application beans
- **Beans Created:**
  - `RestTemplate` - For HTTP calls to external services
  - `ObjectMapper` - For JSON parsing/serialization

---

## 6. Data Flow

**Data Structure:**
- **Format:** JSON string initially, converted to `Map<String, Object>` for processing
- **Kafka Topic:** `ai-metrics`
- **Deserializer:** `StringDeserializer` (both key and value)
- **Serializer:** `StringSerializer` (both key and value)

**Data Journey:**
```
External AI Agent Service
         ↓ (JSON string)
   RestTemplate
         ↓
   KafkaTemplate (serialize to String)
         ↓
   Kafka Topic: ai-metrics (in Kafka broker)
         ↓ (String message from Kafka)
   MetricsConsumer
         ↓ (ObjectMapper.readValue)
   Map<String, Object>
         ↓ (HTTP POST)
   Dashboard Service (receives Map/JSON)
```

**No persistent storage:** Data flows through Kafka in-memory, not stored in a database.

---

## 7. External Integrations

1. **AI Agent Metrics Service** (Source)
   - URL: `http://localhost:8083/ai/metrics`
   - Protocol: HTTP GET
   - Response: JSON string with metrics

2. **Kafka Broker**
   - Address: `localhost:9092`
   - Topic: `ai-metrics`
   - Consumer Group: `metrics-group`
   - Offset Strategy: `earliest` (start from beginning if group is new)

3. **Dashboard/Ingest Service** (Sink)
   - URL: `http://localhost:8082/internal/metrics`
   - Protocol: HTTP POST
   - Request Body: `Map<String, Object>` (JSON payload)

4. **Actuator** (Spring Boot monitoring)
   - Provides built-in `/actuator/*` endpoints for application health

---

## 8. Configuration & Setup

**Configuration File:** `application.yaml`

```yaml
Server:
  port: 8086

Spring Kafka:
  bootstrap-servers: localhost:9092
  consumer.group-id: metrics-group
  consumer.auto-offset-reset: earliest
  producer/consumer serializers: StringSerializer/StringDeserializer

Kafka Topic:
  metrics: ai-metrics

AI Agent:
  metrics.source-url: http://localhost:8083/ai/metrics

Dashboard:
  ingest-url: http://localhost:8082/internal/metrics
```

**Important Configuration Points:**
- Kafka broker must be running on `localhost:9092`
- AI Agent service must expose metrics on port 8083
- Dashboard service must listen on port 8082
- Service runs on port **8086**

**Dependencies (from pom.xml):**
- Spring Boot 4.0.2
- Spring Kafka (Kafka client)
- Spring Web MVC
- Spring Actuator
- Jackson (JSON processing)
- Lombok (boilerplate reduction)
- Java 17

---

## 9. Build & Run Instructions

**Prerequisites:**
- Java 17+
- Maven 3.6+
- Kafka broker running (port 9092)
- AI Agent service running (port 8083)
- Dashboard service running (port 8082)

**Build:**
```bash
cd C:\srivani\code\git\vm-agent-topics\vm-agent-topics
mvn clean package
```

**Run:**
```bash
# Option 1: Using Maven
mvn spring-boot:run

# Option 2: Using Java JAR
java -jar target/kafka-metrics-service-0.0.1-SNAPSHOT.jar
```

**Service Available At:**
- Metrics API: `http://localhost:8086/api/metrics`
- Health Check: `http://localhost:8086/api/metrics/health`
- Actuator: `http://localhost:8086/actuator/*`

---

## 10. Important Observations

### Design Patterns Used:
1. **Producer-Consumer Pattern** - Classic async message processing
2. **Scheduled Task Pattern** - Time-based metric pulling
3. **Dependency Injection** - Spring's `@RequiredArgsConstructor` with Lombok
4. **Separation of Concerns** - Clear layer separation (scheduler → producer → consumer → dashboard)

### Potential Improvements/Risks:

1. **Error Handling in Consumer:**
   - Current: Exceptions are caught and failed counter incremented, then exception is rethrown
   - Risk: If dashboard service is down, messages will fail and be lost
   - Improvement: Implement Dead Letter Queue (DLQ) pattern for failed messages

2. **No Retries:**
   - If external services (AI agent or dashboard) are temporarily down, requests fail immediately
   - Improvement: Add exponential backoff and retry logic

3. **Hardcoded Ports:**
   - All service URLs are hardcoded in configuration
   - Improvement: Consider service discovery (Eureka, Consul) for dynamic routing

4. **Single Consumer Group:**
   - Only one consumer group listening to the topic
   - If this service fails, messages accumulate in Kafka
   - Improvement: Deploy in HA configuration with multiple instances

5. **No Authentication:**
   - No SSL/TLS for Kafka communication
   - No API authentication for REST endpoints
   - Risk: Suitable only for internal network/development

6. **Lag Tracking:**
   - `lag` counter exists but is never incremented
   - Currently unused metric

---

## 11. Visual Flow Diagram

```
┌──────────────────────────┐
│  AI Agent Service        │
│ (localhost:8083)         │
│ /ai/metrics              │
└────────────┬─────────────┘
             │ (JSON metrics)
             │
             ▼
┌──────────────────────────┐
│  MetricsScheduler        │
│ (pulls every 10 sec)     │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│  MetricsProducer         │
│ (sends to Kafka)         │
└────────────┬─────────────┘
             │
             ▼
    ┌────────────────────┐
    │  Kafka Broker      │
    │ (localhost:9092)   │
    │  Topic: ai-metrics │
    └────────┬───────────┘
             │
             ▼
┌──────────────────────────┐
│  MetricsConsumer         │
│ (@KafkaListener)         │
└────────────┬─────────────┘
             │ (HTTP POST)
             ▼
┌──────────────────────────┐
│  Dashboard Service       │
│ (localhost:8082)         │
│ /internal/metrics        │
└──────────────────────────┘

                MONITORING
                    │
                    ▼
        ┌──────────────────────────┐
        │  MetricsController       │
        │  GET /api/metrics        │
        │  GET /api/metrics/health │
        └──────────────────────────┘
```

---

## Summary

The **Kafka Metrics Service** is a lightweight, event-driven middleware that:

1. **Pulls** metrics from AI agents at regular intervals
2. **Streams** them through Apache Kafka for reliable message delivery
3. **Forwards** processed metrics to operational dashboards
4. **Exposes** operational metrics via REST API for monitoring the service itself

It serves as a critical component in an autonomous agent ecosystem, enabling real-time observability through the MELT (Metrics, Events, Logs, Traces) framework.

