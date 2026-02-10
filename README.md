# vm-agent-topics
Central repository for Kafka and Axon topic definitions used by autonomous agents and operational dashboards, serving as a single source of truth for event-driven communication and observability.

### Developers
- Gurpreet Singh
- Srivani Vadthya

### Key Responsibilities

- **Agents**
  - Act as **Kafka producers**
  - Publish events and telemetry to Kafka topics
  - Emit MELT data related to model interactions

- **Dashboards / Operational Tools**
  - Act as **Kafka consumers**
  - Subscribe to relevant topics
  - Visualize, aggregate, and analyze agent behavior and system health

---

## Agent Responsibilities

Agents are responsible for **produing messages** to Kafka topics defined in this repository.

### What Agents Publish

Agents publish **MELT information** describing their interaction with models and the platform:

### Metrics
- Latency
- Token usage
- Cost
- Throughput
- Success / failure rates

### Events
- Model invocation started / completed
- Agent lifecycle events
- Tool invocation events
- Error and retry events

### Logs
- Structured execution logs
- Warnings and non-fatal issues
- Contextual debugging information

### Traces
- Correlation and request IDs
- End-to-end execution flows
- Parent / child spans across agent actions

All messages **must conform** to the topic schemas and conventions defined in this repository.

---

## Dashboard Responsibilities

Operational dashboards and monitoring systems act as **Kafka consumers**.

### What Dashboards Consume

Dashboards consume topic data to:

- Monitor agent health and availability
- Visualize agent-to-model interactions
- Track performance, cost, and reliability
- Detect anomalies and failures
- Provide real-time and historical observability

Dashboards **must not** publish data back to these topics unless explicitly defined.

---

## Topic Design Principles

All topics in this repository follow these principles:

- Clear ownership (agent-facing vs platform-facing)
- Consistent naming conventions
- Schema-first design
- Backward-compatible evolution
- Safe handling of high-cardinality MELT data

Topics may be organized by:

- Agent lifecycle
- Model interaction
- Telemetry type (metrics, events, logs, traces)
- Operational vs analytical use cases

---

## Kafka and Axon Usage

This repository supports:

### Kafka Topics
- Primary transport for agent telemetry and events
- Used for real-time streaming and dashboard consumption

### Axon Topics (Optional)
- Used for event sourcing and CQRS workflows
- May represent domain events derived from agent activity

Kafka remains the primary integration layer between agents and dashboards.

---

## Repository Contents

This repository contains:

- Topic definitions and naming standards
- Message ownership and responsibilities
- Schema references (Avro / JSON / Protobuf)
- Producer guidelines for agents
- Consumer guidelines for dashboards

---

## Out of Scope

This repository does **not** contain:

- Agent implementation code
- Kafka producer or consumer implementations
- Dashboard UI or visualization logic
- Business or orchestration logic

---

## Contribution Guidelines

- New topics must be reviewed for:
  - Naming consistency
  - Schema clarity
  - MELT alignment
  - Consumer impact
- Breaking changes must follow versioning guidelines
- Schemas should remain backward compatible whenever possible

---

## Summary

This repository defines the **messaging contract** between:

- **Agents producing MELT telemetry via Kafka**
- **Dashboards consuming that data for observability and operations**

By centralizing topic definitions here, the platform ensures consistency, scalability, and operational visibility across all agent-driven workflows.

