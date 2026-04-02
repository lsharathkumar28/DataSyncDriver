# DataSyncDriver

A Spring Boot microservice that synchronizes user identity data from a central **DataSynchronizer** (identity vault) to external target systems via pluggable connectors.

## What It Does

- **Bulk sync** — Pulls all users from the DataSynchronizer REST API and loads them into external systems
- **Real-time sync** — Streams individual user changes via Apache Kafka as they happen
- **Schema mapping** — Dynamically maps internal fields to target-system fields with transforms (`UPPER`, `LOWER`, `TRIM`) and defaults
- **Sync rule filtering** — Controls which attributes flow through per connector, with direction and priority support
- **Pluggable connectors** — CSV and JSON file connectors included; add new ones by implementing a single interface

## Architecture

```
DataSynchronizer (Identity Vault)          DataSyncDriver
┌────────────────────────┐          ┌─────────────────────────┐
│  REST: /api/v1/users   ├─ HTTP ──►  InitialSyncService     │
│  Kafka: user-events    ├─ Event ─►  UserEventConsumer       │
└────────────────────────┘          │         │                │
                                    │   SchemaMapper           │
                                    │   SyncRuleFilter         │
                                    │         │                │
                                    │   CsvConnector ──► .csv  │
                                    │   JsonConnector ─► .json │
                                    └─────────────────────────┘
```

## Tech Stack

| Technology | Purpose |
|---|---|
| Spring Boot 4.0.4 | Application framework |
| Apache Kafka | Real-time event streaming + cross-instance cache invalidation |
| PostgreSQL | Centralized configuration database (shared across instances) |
| Spring Data JPA + Hibernate | ORM and auto DDL |
| Caffeine | In-memory caching for configuration data |
| Jackson 3 | JSON serialization |
| SpringDoc OpenAPI | Swagger UI |
| Docker Compose | Multi-driver deployment |

## Prerequisites

- Java 17+
- Docker Desktop

## Quick Start

```bash
# 1. Start infrastructure (PostgreSQL + Kafka)
cd /path/to/DataSynchronizer
docker compose up -d

# 2. Start the DataSynchronizer
./mvnw spring-boot:run

# 3. Start the DataSyncDriver
cd /path/to/DataSyncDriver
./mvnw clean spring-boot:run

# 4. Open Swagger UI
open http://localhost:8081/swagger-ui.html

# 5. Create a user in DataSynchronizer
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","firstName":"John","lastName":"Doe","emailId":"john@example.com","phoneNumber":"555-0101","attributes":{"department":"Engineering","title":"Developer"}}'

# 6. Trigger bulk sync
curl -X POST http://localhost:8081/api/v1/sync/initial

# 7. Check output
cat sync-output.csv
```

Any subsequent users created/updated/deleted in DataSynchronizer will automatically sync via Kafka in real time.

## Running with Docker Compose (CSV + JSON drivers)

```bash
# Build the JAR
./mvnw clean package -DskipTests

# Start both drivers
docker compose up --build
```

This starts two driver instances from the same image:

| Container | Port | Connector | Output |
|---|---|---|---|
| `datasync-driver-csv` | 8081 | CSV | `output/sync-output.csv` |
| `datasync-driver-json` | 8082 | JSON | `output/sync-output.json` |

Each has its own Kafka consumer group, so both receive all events independently.

## API Endpoints

### Sync
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/sync/initial` | Trigger full bulk synchronization |

### Configuration
| Method | Endpoint | Description |
|---|---|---|
| `GET/POST/PUT/DELETE` | `/api/v1/config/connections` | Manage connection configs |
| `GET/POST/PUT/DELETE` | `/api/v1/config/mappings` | Manage schema mappings |
| `GET/POST/PUT/DELETE` | `/api/v1/config/sync-rules` | Manage sync rules |

Full interactive API docs at **http://localhost:8081/swagger-ui.html**

## Configuration

Key properties in `application.properties`:

```properties
driver.connector.type=csv                          # Active connector: csv or json
driver.datasynchronizer.base-url=http://localhost:8080  # DataSynchronizer URL
driver.csv.output-path=sync-output.csv             # CSV output file
driver.json.output-path=sync-output.json           # JSON output file
```

See [DESIGN.md](DESIGN.md) for the full technical design document.

