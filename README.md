# DataSyncDriver

Synchronization driver that sits between our internal identity vault (DataSynchronizer) and external systems. It pulls user data via REST for bulk loads and listens to Kafka for real-time changes, then pushes everything through configurable schema mappings into target connectors.

Right now we have CSV and JSON file connectors, but the connector interface is generic enough to add LDAP, AD, database connectors etc. down the line.

## How it works

1. **Initial sync** — hit `POST /api/v1/sync/initial` on the driver, it calls the DataSynchronizer's `/api/v1/users` endpoint, maps the fields, applies sync rules, and writes to the target system
2. **Real-time sync** — DataSynchronizer publishes `UserChangeEvent` to a `user-events` Kafka topic whenever a user is created/updated/deleted. The driver consumes these and pushes changes to connectors automatically
3. **Schema mapping** — field-level mapping with support for dot-notation (`attributes.department`), transforms (`UPPER`, `LOWER`, `TRIM`), and default values
4. **Sync rules** — control which attributes are allowed/blocked per connector, per direction (inbound/outbound), with priority-based conflict resolution

## Tech stack

- Spring Boot 4.0.4 / Java 17
- Apache Kafka (consumer + producer)
- PostgreSQL (shared config DB across driver instances)
- Caffeine cache (local, with Kafka-based cross-instance invalidation)
- Spring Data JPA + Hibernate
- Jackson 3 for JSON
- SpringDoc OpenAPI (Swagger UI)
- Docker Compose for running multiple driver instances

## Getting started

You need Docker Desktop running for PostgreSQL and Kafka.

```bash
# start postgres + kafka (from the DataSynchronizer project)
cd ../DataSynchronizer
docker compose up -d

# start the DataSynchronizer service
./mvnw spring-boot:run

# come back here and start the driver
cd ../DataSyncDriver
./mvnw clean spring-boot:run
```

Once it's up, Swagger UI is at http://localhost:8081/swagger-ui.html

### Try it out

Create a user in the DataSynchronizer:
```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","firstName":"John","lastName":"Doe","emailId":"john@example.com","phoneNumber":"555-0101","attributes":{"department":"Engineering","title":"Developer"}}'
```

Trigger bulk sync:
```bash
curl -X POST http://localhost:8081/api/v1/sync/initial
```

Check the output:
```bash
cat sync-output.csv
```

After that, any new users you create/update/delete will sync automatically through Kafka — no need to call the initial sync again.

## Running both CSV and JSON drivers (Docker Compose)

We can run two instances of the driver from the same image, each configured for a different connector:

```bash
./mvnw clean package -DskipTests
docker compose up --build
```

| Container | Port | What it does |
|---|---|---|
| `datasync-driver-csv` | 8081 | Writes to `output/sync-output.csv` |
| `datasync-driver-json` | 8082 | Writes to `output/sync-output.json` |

They use separate Kafka consumer groups so both get all events independently.

## Main API endpoints

- `POST /api/v1/sync/initial` — trigger bulk sync
- `GET/POST/PUT/DELETE /api/v1/config/connections` — manage connection configs
- `GET/POST/PUT/DELETE /api/v1/config/mappings` — manage schema mappings
- `GET/POST/PUT/DELETE /api/v1/config/sync-rules` — manage sync rules

## Config

Important bits in `application.properties`:

```properties
driver.connector.type=csv                               # which connector to activate (csv / json)
driver.datasynchronizer.base-url=http://localhost:8080   # where the identity vault lives
driver.csv.output-path=sync-output.csv
driver.json.output-path=sync-output.json
```

For the full technical design (class descriptions, data flow diagrams, seed data details etc.), see [DESIGN.md](DESIGN.md).
