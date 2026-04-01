# DataSyncDriver — Technical Design Document

## 1. Overview

The **DataSyncDriver** is a Spring Boot microservice that synchronizes user identity data from a central internal system (**DataSynchronizer**) to one or more external target systems. It acts as a bridge — pulling user records via REST, streaming real-time changes via Apache Kafka, and pushing the transformed data into external connectors (currently CSV).

The driver is designed with a **pluggable connector architecture**, meaning new target systems can be added by implementing a single interface — without modifying any existing code.

---

## 2. Architecture

```
┌──────────────────────┐         ┌─────────────────────────┐
│   DataSynchronizer   │         │     DataSyncDriver      │
│   (Identity Vault)   │         │      (This Service)     │
│                      │         │                         │
│  REST: /api/v1/users ├────────►│  InitialSyncService     │
│                      │  HTTP   │  (bulk pull all users)  │
│                      │         │                         │
│  Kafka: user-events  ├────────►│  UserEventConsumer      │
│                      │  Event  │  (real-time streaming)  │
└──────────────────────┘         │                         │
                                 │  ┌───────────────────┐  │
                                 │  │  SchemaMapper      │  │
                                 │  │  (field mapping)   │  │
                                 │  └────────┬──────────┘  │
                                 │           │              │
                                 │  ┌────────▼──────────┐  │
                                 │  │  SyncRuleFilter    │  │
                                 │  │  (attribute rules) │  │
                                 │  └────────┬──────────┘  │
                                 │           │              │
                                 │  ┌────────▼──────────┐  │
                                 │  │  CsvConnector      │  │     ┌──────────────┐
                                 │  │  (writes CSV)      ├──────►│ sync-output  │
                                 │  └───────────────────┘  │     │    .csv      │
                                 │                         │     └──────────────┘
                                 │  ┌───────────────────┐  │
                                 │  │  PostgreSQL        │  │
                                 │  │  (shared config)   │  │
                                 │  └───────────────────┘  │
                                 │                         │
                                 │  ┌───────────────────┐  │
                                 │  │  Caffeine Cache    │  │
                                 │  │  (local, per inst) │  │
                                 │  └───────────────────┘  │
                                 └─────────────────────────┘
```

---

## 3. Technology Stack

| Technology | Version | Purpose |
|---|---|---|
| **Java** | 17 | Programming language |
| **Spring Boot** | 4.0.4 | Application framework |
| **Spring Data JPA** | (via Boot) | ORM / repository layer |
| **Hibernate** | (via Boot) | JPA implementation, auto DDL |
| **PostgreSQL** | 18.x | Centralized configuration database (shared across all driver instances) |
| **Apache Kafka** | (via Docker) | Event streaming — real-time user change events + cross-instance cache invalidation |
| **Spring Kafka** | (via Boot) | Kafka consumer/producer integration |
| **Caffeine** | (via Boot) | High-performance in-memory caching for configuration data |
| **Jackson 3** | 3.1.0 (`tools.jackson`) | JSON serialization/deserialization |
| **Lombok** | (via Boot) | Boilerplate reduction — `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j` |
| **SpringDoc OpenAPI** | 2.8.6 | Auto-generated Swagger UI + API documentation |
| **Bean Validation** | (via Boot) | Request DTO validation with `@NotBlank`, `@NotNull` |
| **Maven** | (wrapper included) | Build tool with `mvnw` for zero-setup builds |
| **Docker Compose** | — | PostgreSQL and Kafka infrastructure (shared with DataSynchronizer) |

---

## 4. Project Structure

```
src/main/java/com/outreach/datasyncdriver/
├── DataSyncDriverApplication.java      # Spring Boot entry point
├── config/                             # Spring configuration classes
│   ├── CacheConfig.java                # Caffeine cache manager setup
│   ├── KafkaConsumerConfig.java        # Kafka consumer factories
│   ├── KafkaProducerConfig.java        # Kafka producer for cache invalidation
│   └── OpenApiConfig.java             # Swagger/OpenAPI metadata
├── connector/                          # External system connectors (SPI)
│   ├── ExternalSystemConnector.java    # Interface — the connector SPI
│   ├── CsvConnector.java              # CSV file implementation
│   └── JsonFileConnector.java         # JSON file implementation
├── consumer/                           # Kafka listeners
│   ├── UserEventConsumer.java          # Listens to user-events topic
│   └── CacheInvalidationConsumer.java  # Listens to cache-invalidation topic
├── controller/                         # REST API endpoints
│   ├── ConfigurationController.java    # CRUD for connections, mappings, rules
│   ├── SyncController.java            # Trigger initial sync
│   └── GlobalExceptionHandler.java    # Translates exceptions → JSON errors
├── dto/                                # Data Transfer Objects
│   ├── ConnectionConfigRequest.java    # Input for connection CRUD
│   ├── SchemaMappingRequest.java       # Input for mapping CRUD
│   ├── SyncRuleRequest.java            # Input for sync rule CRUD
│   ├── UserChangeEvent.java            # Kafka event from DataSynchronizer
│   └── UserResponse.java              # REST response from DataSynchronizer
├── entity/                             # JPA entities (database tables)
│   ├── ConnectionConfig.java           # connection_configs table
│   ├── SchemaMapping.java              # schema_mappings table
│   ├── SyncRule.java                   # sync_rules table
│   ├── SystemType.java                 # Enum: INTERNAL, EXTERNAL
│   └── SyncDirection.java             # Enum: INBOUND, OUTBOUND, BIDIRECTIONAL
├── repository/                         # Spring Data JPA repositories
│   ├── ConnectionConfigRepository.java
│   ├── SchemaMappingRepository.java
│   └── SyncRuleRepository.java
└── service/                            # Business logic
    ├── ConfigurationService.java       # CRUD + caching + cache invalidation
    ├── InitialSyncService.java         # Bulk sync via REST
    ├── SchemaMapper.java               # Field-level mapping engine
    ├── SyncRuleFilter.java             # Attribute filtering per connector
    └── CacheInvalidationPublisher.java # Publishes invalidation to Kafka

src/main/resources/
├── application.properties              # All configuration
└── data.sql                            # Seed data (runs on every startup)
```

---

## 5. Detailed Class Descriptions

### 5.1 Configuration Layer (`config/`)

#### `CacheConfig.java`
Sets up the **Caffeine cache manager** with three named caches: `connections`, `schemaMappings`, and `syncRules`. Configured with a maximum of 500 entries and a 30-minute TTL. Configuration data is read-heavy and rarely mutated, so caching provides significant performance gains.

#### `KafkaConsumerConfig.java`
Defines Kafka consumer factories using **String deserialization** for all consumers. Spring Kafka 4.0 deprecated the typed `JsonDeserializer`, so JSON parsing is handled manually in the listener methods using Jackson's `ObjectMapper`. Two listener container factories are configured:
- `kafkaListenerContainerFactory` — for user event messages
- `stringKafkaListenerContainerFactory` — for cache invalidation messages

#### `KafkaProducerConfig.java`
Configures a `KafkaTemplate<String, String>` for publishing cache-invalidation events. When any driver instance mutates configuration data, it sends the cache name to a Kafka topic so other instances can evict their local caches.

#### `OpenApiConfig.java`
Customizes the Swagger/OpenAPI metadata (title, description, version, contact info) displayed at `/swagger-ui.html`.

---

### 5.2 Connector SPI (`connector/`)

#### `ExternalSystemConnector.java` (Interface)
The **Service Provider Interface** — the central abstraction that all target-system connectors must implement. Defines a dual-mode API:

| Method | Mode | Purpose |
|---|---|---|
| `name()` | Both | Human-readable connector name |
| `targetSystemName()` | Mapped | Identifier for schema mapping lookup |
| `pushChange(event)` | Legacy | Push a single raw DTO change |
| `initialLoad(users)` | Legacy | Bulk-load all users as raw DTOs |
| `pushMappedChange(type, data)` | Mapped | Push a schema-mapped change |
| `initialLoadMapped(records, fields)` | Mapped | Bulk-load schema-mapped records |

The driver automatically selects **mapped mode** when schema mappings exist for a connector's target system, and falls back to **legacy mode** otherwise.

#### `CsvConnector.java`
The concrete implementation of `ExternalSystemConnector` that writes user data to a local CSV file (`sync-output.csv`). Activated by `driver.connector.type=csv`. Supports both modes:
- **Mapped mode**: Dynamic column headers from schema mappings, ordered output
- **Legacy mode**: Fixed hardcoded header with direct DTO field access

Handles CSV escaping (commas, quotes, newlines) and operates in append mode for incremental changes.

#### `JsonFileConnector.java`
Writes user data to a local JSON file (`sync-output.json`) as a pretty-printed JSON array. Activated by `driver.connector.type=json`. Supports both modes:
- **Mapped mode**: Each record is a JSON object with keys derived from schema mappings
- **Legacy mode**: Serializes raw DTOs directly

For incremental changes, it reads the existing array from the file, appends the new record, and rewrites. Uses Jackson 3's `ObjectMapper` (injected by Spring) for serialization.

---

### 5.3 Kafka Consumers (`consumer/`)

#### `UserEventConsumer.java`
Listens on the `user-events` Kafka topic (consumer group: `datasync-driver`). For each message:
1. Deserializes the JSON string into a `UserChangeEvent` using Jackson `ObjectMapper`
2. Iterates over all registered `ExternalSystemConnector` beans
3. For each connector, checks if schema mappings exist for its target system
4. If mapped → transforms via `SchemaMapper`, filters via `SyncRuleFilter`, calls `pushMappedChange()`
5. If unmapped → passes the raw DTO via `pushChange()`

#### `CacheInvalidationConsumer.java`
Listens on the `config-cache-invalidation` topic with a **unique consumer group per instance** (UUID-suffixed). This ensures **every instance** receives every invalidation message. On receipt, it:
1. Looks up the named Caffeine cache (e.g., `"connections"`)
2. Calls `cache.clear()` to evict all entries
3. Falls back to clearing all caches if the named cache is not found

---

### 5.4 REST Controllers (`controller/`)

#### `ConfigurationController.java`
Full CRUD REST API under `/api/v1/config/` for managing:

| Resource | Endpoints | Features |
|---|---|---|
| **Connections** | `GET/POST/PUT/DELETE /connections` | Lookup by ID, name, or system type |
| **Schema Mappings** | `GET/POST/PUT/DELETE /mappings` | Batch create, group queries, system-pair queries |
| **Sync Rules** | `GET/POST/PUT/DELETE /sync-rules` | Batch create, connector-specific queries, enabled-only filter |

All endpoints are annotated with `@Operation` and `@ApiResponse` for Swagger documentation.

#### `SyncController.java`
Exposes a single endpoint:
- `POST /api/v1/sync/initial` — Triggers a full synchronization by calling `InitialSyncService.runInitialSync()`

Returns a JSON response with the sync status and user count.

#### `GlobalExceptionHandler.java`
Translates exceptions into structured JSON error responses:
- `IllegalArgumentException` → 400 Bad Request
- `MethodArgumentNotValidException` → 400 with field-level validation errors
- `Exception` (catch-all) → 500 Internal Server Error

---

### 5.5 DTOs (`dto/`)

#### `UserResponse.java`
Maps the JSON response from `GET /api/v1/users` on the DataSynchronizer. Fields: `userId`, `name`, `firstName`, `middleName`, `lastName`, `emailId`, `phoneNumber`, `attributes` (Map).

#### `UserChangeEvent.java`
Mirrors the Kafka event published by the DataSynchronizer when a user is created, updated, or deleted. Contains the same fields as `UserResponse` plus `changeType` (enum: `CREATED`, `UPDATED`, `DELETED`) and `timestamp`.

#### `ConnectionConfigRequest.java`
Input DTO for creating/updating connections. Validated with `@NotBlank` and `@NotNull` annotations.

#### `SchemaMappingRequest.java`
Input DTO for creating/updating field-level schema mappings. Includes source/target field paths, data type, transform expression, and default value.

#### `SyncRuleRequest.java`
Input DTO for creating/updating sync rules. Specifies connector name, attribute name, enabled flag, direction, filter expression, and priority.

---

### 5.6 JPA Entities (`entity/`)

#### `ConnectionConfig.java`
Persisted to the `connection_configs` table. Stores connection details for both internal (DataSynchronizer) and external (CSV, LDAP, etc.) systems. Includes URL, host, port, credentials, and a JSON `additionalProperties` field for system-specific settings.

#### `SchemaMapping.java`
Persisted to the `schema_mappings` table. Defines a single field-level mapping within a named group. Key fields:
- `sourceField` — internal field path (supports dot-notation: `attributes.department`)
- `targetField` — external field name (e.g., `department`)
- `transformExpression` — optional: `UPPER`, `LOWER`, `TRIM`
- `defaultValue` — fallback when source is null

Unique constraint: `(mappingGroupName, sourceField, targetField)`

#### `SyncRule.java`
Persisted to the `sync_rules` table. Controls whether a specific attribute is allowed to flow through to a connector. Key fields:
- `syncEnabled` — `true` to allow, `false` to block
- `direction` — `OUTBOUND`, `INBOUND`, or `BIDIRECTIONAL`
- `priority` — lower number = higher priority (for conflict resolution)

Unique constraint: `(connectorName, attributeName)`

#### `SystemType.java` (Enum)
- `INTERNAL` — the identity vault (DataSynchronizer)
- `EXTERNAL` — a target system (CSV, LDAP, AD, etc.)

#### `SyncDirection.java` (Enum)
- `OUTBOUND` — internal → external
- `INBOUND` — external → internal
- `BIDIRECTIONAL` — both directions

---

### 5.7 Repositories (`repository/`)

Spring Data JPA interfaces that auto-generate SQL implementations at runtime:

| Repository | Custom Query Methods |
|---|---|
| `ConnectionConfigRepository` | `findByName`, `findBySystemType`, `findByActiveTrue`, `existsByName` |
| `SchemaMappingRepository` | `findByMappingGroupName`, `findByTargetSystem`, `findBySourceSystemAndTargetSystem`, `deleteByMappingGroupName` |
| `SyncRuleRepository` | `findByConnectorName`, `findByConnectorNameAndSyncEnabledTrue`, `findByConnectorNameAndDirection`, `deleteByConnectorName` |

---

### 5.8 Services (`service/`)

#### `ConfigurationService.java`
The central service for all configuration CRUD operations. Key design decisions:
- All **read** methods are annotated with `@Cacheable` — results are stored in Caffeine and served from memory on subsequent calls
- All **write** methods are annotated with `@CacheEvict(allEntries = true)` — the local cache is cleared after every mutation
- After every mutation, `cacheInvalidationPublisher.publishInvalidation(cacheName)` broadcasts to Kafka so **other instances** also clear their caches
- The `ObjectMapper` is injected from Spring's auto-configured Jackson 3 bean

#### `InitialSyncService.java`
Performs the full (bulk) synchronization:
1. Creates a `RestClient` pointing to the DataSynchronizer's base URL
2. Calls `GET /api/v1/users` to fetch all users
3. For each registered connector:
   - If mapped → transforms each user via `SchemaMapper`, filters via `SyncRuleFilter`, calls `initialLoadMapped()`
   - If unmapped → passes the raw list via `initialLoad()`

#### `SchemaMapper.java`
The field-level mapping engine. Transforms source DTOs into `Map<targetField, value>` using the schema mappings from the database.

Features:
- **Flat field access**: `name` → reads `source.getName()` via reflection
- **Dot-notation**: `attributes.department` → reads `source.getAttributes().get("department")`
- **Transforms**: `UPPER`, `LOWER`, `TRIM` applied after extraction
- **Default values**: Used when source field is null or missing
- **Reverse mapping**: `reverseMap()` converts external → internal field names (for future inbound sync)

#### `SyncRuleFilter.java`
Filters mapped data according to sync rules:
- **Open-by-default**: If no rules exist for a connector, all attributes pass through
- **Closed for blocked attributes**: If rules exist, attributes with `syncEnabled=false` are stripped
- **Direction-aware**: Rules only apply when the direction matches (`BIDIRECTIONAL` covers both)
- **Priority-based**: When duplicate rules exist, the one with the lowest priority number wins

#### `CacheInvalidationPublisher.java`
Sends cache-name strings (e.g., `"connections"`, `"schemaMappings"`, `"syncRules"`) to the `config-cache-invalidation` Kafka topic. Called by `ConfigurationService` after every mutation.

---

## 6. Deployment Model — One Driver per External System

The driver follows a **one-driver-per-external-system** deployment model. Each deployment of the DataSyncDriver JAR is dedicated to a single external target system.

### Why one-to-one?

| Concern | Benefit |
|---|---|
| **Fault isolation** | A failure in the CSV driver doesn't affect an LDAP driver |
| **Independent scaling** | Scale each driver based on the load of its target system |
| **Clean Kafka partitioning** | Each driver has its own consumer group, reads all events independently |
| **Independent deployment** | Change CSV config without redeploying the LDAP driver |
| **Isolated configuration** | Each instance has only its own schema mappings and sync rules |

### How it works

Each connector class is annotated with `@ConditionalOnProperty`:

```java
@Component
@ConditionalOnProperty(name = "driver.connector.type", havingValue = "csv")
public class CsvConnector implements ExternalSystemConnector { ... }
```

The `driver.connector.type` property in `application.properties` controls which connector is active:

```properties
# Activates CsvConnector
driver.connector.type=csv
```

On startup, the application validates the active connector and logs:
```
Active connector: [CSV File] (target system: external-csv)
```

If no connector matches or multiple connectors are accidentally active, a warning is logged.

### Deploying multiple drivers

To sync to multiple external systems, deploy the same JAR with different configurations:

| Instance | `server.port` | `driver.connector.type` | `spring.kafka.consumer.group-id` |
|---|---|---|---|
| CSV Driver | 8081 | `csv` | `datasync-driver-csv` |
| JSON Driver | 8082 | `json` | `datasync-driver-json` |
| LDAP Driver | 8083 | `ldap` | `datasync-driver-ldap` |

Each instance uses a **different Kafka consumer group**, so every instance receives a complete copy of all user events and processes them independently for its target system.

### Docker Compose

The project includes a `compose.yaml` that runs two driver instances from the same image:

```bash
# 1. Build the JAR
./mvnw clean package -DskipTests

# 2. Start both drivers
docker compose up --build
```

This starts:
- **datasync-driver-csv** on port 8081 → writes to `output/sync-output.csv`
- **datasync-driver-json** on port 8082 → writes to `output/sync-output.json`

Both containers share a `./output` volume, so output files are accessible from the host. Environment variables override `application.properties` to set the connector type, Kafka consumer group, and output paths per instance.

Both connect to the DataSynchronizer's PostgreSQL and Kafka via `host.docker.internal`.

---

## 7. Multi-Instance Cache Consistency

Since multiple driver instances share the same PostgreSQL database, a cache invalidation mechanism ensures all instances serve fresh configuration data:

```
Instance A updates a schema mapping
  ├─ Saves to PostgreSQL                        (central database)
  ├─ @CacheEvict clears Instance A's cache      (local Caffeine)
  ├─ Publishes "schemaMappings" to Kafka topic   (broadcast)
  │
  ├─ Instance B receives the message
  │   └─ Clears its "schemaMappings" cache      (local Caffeine)
  │   └─ Next read hits PostgreSQL and re-caches
  │
  └─ Instance C receives the message
      └─ Clears its "schemaMappings" cache
      └─ Next read hits PostgreSQL and re-caches
```

Each instance uses a **unique consumer group** (UUID-suffixed) for the invalidation topic, guaranteeing every instance receives every message.

---

## 8. Data Flow

### 8.1 Initial (Bulk) Sync

```
POST /api/v1/sync/initial (Driver :8081)
  → RestClient calls GET /api/v1/users (DataSynchronizer :8080)
  → Receives List<UserResponse>
  → For each connector:
      → SchemaMapper transforms fields (userId→user_id, name→UPPER(full_name), ...)
      → SyncRuleFilter removes blocked attributes (ssn, salary)
      → CsvConnector writes all rows to sync-output.csv
  → Returns {"status":"completed","usersSynchronized":N}
```

### 8.2 Real-Time (Incremental) Sync

```
User created/updated/deleted in DataSynchronizer
  → DataSynchronizer publishes UserChangeEvent to Kafka topic "user-events"
  → Driver's UserEventConsumer receives the JSON message
  → Deserializes with Jackson ObjectMapper
  → For each connector:
      → SchemaMapper maps fields
      → SyncRuleFilter filters attributes
      → CsvConnector appends row to sync-output.csv
```

---

## 9. Seed Data

On every application startup, `data.sql` is executed (after Hibernate creates/updates the schema). It uses `ON CONFLICT DO NOTHING` to be idempotent. The seed data includes:

**Connections (3 rows):**
| Name | Type | Connection | Purpose |
|---|---|---|---|
| `internal-idvault` | INTERNAL | REST → localhost:8080 | DataSynchronizer API |
| `external-csv` | EXTERNAL | CSV | CSV file output |
| `external-json` | EXTERNAL | JSON | JSON file output |

**Schema Mappings — CSV (8 rows, group: `user-to-csv`):**
| Source Field | Target Field | Transform | Default |
|---|---|---|---|
| `userId` | `user_id` | — | — |
| `name` | `full_name` | UPPER | — |
| `firstName` | `first_name` | TRIM | — |
| `lastName` | `last_name` | TRIM | — |
| `emailId` | `email_address` | LOWER | — |
| `phoneNumber` | `phone` | — | `N/A` |
| `attributes.department` | `department` | — | `UNASSIGNED` |
| `attributes.title` | `job_title` | — | — |

**Schema Mappings — JSON (9 rows, group: `user-to-json`):**
| Source Field | Target Field | Transform | Default |
|---|---|---|---|
| `userId` | `id` | — | — |
| `name` | `displayName` | — | — |
| `firstName` | `givenName` | — | — |
| `lastName` | `surname` | — | — |
| `emailId` | `mail` | LOWER | — |
| `phoneNumber` | `telephoneNumber` | — | — |
| `attributes.department` | `department` | UPPER | `UNASSIGNED` |
| `attributes.title` | `jobTitle` | — | — |
| `attributes.location` | `officeLocation` | — | — |

**Sync Rules — CSV (10 rows, connector: `CSV File`):**
- 8 **allow** rules for mapped fields (OUTBOUND, priority 10–80)
- 2 **block** rules: `ssn` and `salary` (BIDIRECTIONAL, priority 5)

**Sync Rules — JSON (11 rows, connector: `JSON File`):**
- 9 **allow** rules for mapped fields (OUTBOUND, priority 10–90)
- 2 **block** rules: `ssn` and `salary` (BIDIRECTIONAL, priority 5)

---

## 10. REST API Reference

### Configuration Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/config/connections` | List all connections |
| `GET` | `/api/v1/config/connections/{id}` | Get connection by ID |
| `GET` | `/api/v1/config/connections/name/{name}` | Get connection by name |
| `GET` | `/api/v1/config/connections/type/{systemType}` | List by INTERNAL/EXTERNAL |
| `POST` | `/api/v1/config/connections` | Create connection |
| `PUT` | `/api/v1/config/connections/{id}` | Update connection |
| `DELETE` | `/api/v1/config/connections/{id}` | Delete connection |
| `GET` | `/api/v1/config/mappings` | List all schema mappings |
| `GET` | `/api/v1/config/mappings/{id}` | Get mapping by ID |
| `GET` | `/api/v1/config/mappings/group/{groupName}` | List by group |
| `GET` | `/api/v1/config/mappings/pair?sourceSystem=X&targetSystem=Y` | List by system pair |
| `POST` | `/api/v1/config/mappings` | Create single mapping |
| `POST` | `/api/v1/config/mappings/batch` | Create batch of mappings |
| `PUT` | `/api/v1/config/mappings/{id}` | Update mapping |
| `DELETE` | `/api/v1/config/mappings/{id}` | Delete mapping |
| `DELETE` | `/api/v1/config/mappings/group/{groupName}` | Delete group |
| `GET` | `/api/v1/config/sync-rules` | List all sync rules |
| `GET` | `/api/v1/config/sync-rules/{id}` | Get rule by ID |
| `GET` | `/api/v1/config/sync-rules/connector/{name}` | List by connector |
| `GET` | `/api/v1/config/sync-rules/connector/{name}/enabled` | List enabled only |
| `POST` | `/api/v1/config/sync-rules` | Create single rule |
| `POST` | `/api/v1/config/sync-rules/batch` | Create batch of rules |
| `PUT` | `/api/v1/config/sync-rules/{id}` | Update rule |
| `DELETE` | `/api/v1/config/sync-rules/{id}` | Delete rule |
| `DELETE` | `/api/v1/config/sync-rules/connector/{name}` | Delete all for connector |

### Sync Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/sync/initial` | Trigger full initial synchronization |

### Swagger UI

Available at: `http://localhost:8081/swagger-ui.html`

---

## 11. How to Run

### Prerequisites
- Java 17+
- Docker Desktop (for PostgreSQL and Kafka)

### Steps

```bash
# 1. Start infrastructure (PostgreSQL + Kafka)
cd /path/to/DataSynchronizer
docker compose up -d

# 2. Start the DataSynchronizer (identity vault)
cd /path/to/DataSynchronizer
./mvnw spring-boot:run

# 3. Start the DataSyncDriver
cd /path/to/DataSyncDriver
./mvnw clean spring-boot:run

# 4. Create users in DataSynchronizer
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","firstName":"John","lastName":"Doe","emailId":"john@example.com","phoneNumber":"555-0101","attributes":{"department":"Engineering","title":"Developer"}}'

# 5. Trigger initial sync (bulk)
curl -X POST http://localhost:8081/api/v1/sync/initial

# 6. Check CSV output
cat sync-output.csv

# 7. Create another user — Kafka will sync it automatically
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Smith","firstName":"Jane","lastName":"Smith","emailId":"jane@example.com","attributes":{"department":"Product"}}'

# 8. Verify real-time sync (wait 2-3 seconds)
cat sync-output.csv
```

---

## 12. Configuration Properties

| Property | Default | Description |
|---|---|---|
| `server.port` | `8081` | Driver HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/mydatabase` | PostgreSQL connection |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-create/update tables |
| `spring.sql.init.mode` | `always` | Run data.sql on every startup |
| `spring.jpa.defer-datasource-initialization` | `true` | Run data.sql after Hibernate DDL |
| `spring.cache.caffeine.spec` | `maximumSize=500,expireAfterWrite=30m` | Cache settings |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker |
| `driver.datasynchronizer.base-url` | `http://localhost:8080` | DataSynchronizer REST URL |
| `driver.connector.type` | `csv` | Which connector to activate (`csv`, `json`, `ldap`, etc.) |
| `driver.csv.output-path` | `sync-output.csv` | CSV output file path |
| `driver.json.output-path` | `sync-output.json` | JSON output file path |
| `driver.cache.invalidation-topic` | `config-cache-invalidation` | Kafka topic for cache invalidation |














