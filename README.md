# Order Processing System

A production-ready, enterprise-grade backend system built with Spring Boot for managing orders through their lifecycle — from creation to delivery. Features caching, metrics, structured logging, audit trail, API versioning, and Docker support.

## Features

- **Order Management**: Create, retrieve, update status, list with pagination, and cancel orders
- **State Machine**: Validated status transitions (PENDING → PROCESSING → SHIPPING → DELIVERED / CANCELLED)
- **Automated Scheduler**: Advances order statuses every 60s (configurable) with threshold-based timing
- **Audit Trail**: Full status change history with timestamps and source tracking (API / SCHEDULER)
- **Caching**: Caffeine-based caching (500 entries, 5-min TTL) with automatic eviction on writes
- **Metrics & Monitoring**: Micrometer counters for orders created/cancelled/transitions, Prometheus endpoint
- **Structured Logging**: JSON + console logging with correlation IDs for distributed tracing
- **API Versioning**: All endpoints under `/api/v1/` with `ApiResponse<T>` wrapper
- **Pagination**: Paginated list endpoint with `PagedResponse<T>`
- **Health Checks**: Custom health indicator with order count + standard Spring Actuator
- **OpenAPI/Swagger**: Interactive API documentation
- **Docker**: Multi-stage Dockerfile + Docker Compose with PostgreSQL
- **CI/CD**: GitHub Actions pipeline with test reports and artifact upload

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Data Access | Spring Data JPA, Hibernate 6 |
| Database (Dev) | H2 (in-memory) |
| Database (Prod) | PostgreSQL 16 |
| Migrations | Flyway |
| Caching | Caffeine |
| Metrics | Micrometer + Prometheus |
| API Docs | SpringDoc OpenAPI 2.5.0 |
| Logging | Logback (structured JSON + console) |
| Validation | Jakarta Bean Validation |
| Build | Maven |
| Containerization | Docker (multi-stage) |
| Testing | JUnit 5 + Mockito (59 tests) |

## Project Structure

```
src/main/java/com/order/
├── config/              # CacheConfig, SchedulerConfig, OpenApiConfig, OrderSchedulerProperties
├── controller/          # REST endpoints (API v1)
├── dto/                 # Request/Response DTOs, ApiResponse, PagedResponse
├── entity/              # JPA entities (Order, OrderItem, OrderStatusHistory)
├── enums/               # OrderStatus enum
├── exception/           # Custom exceptions + global handler with correlation IDs
├── filter/              # CorrelationIdFilter (X-Correlation-Id, request timing)
├── health/              # Custom health indicator
├── mapper/              # Entity ↔ DTO conversion
├── metrics/             # Micrometer counters (created, cancelled, transitions)
├── repository/          # Spring Data JPA repositories (paginated queries)
├── scheduler/           # Automated status advancement (thread pool)
├── service/             # Business logic with caching, metrics, audit trail
├── statemachine/        # Order state transition rules
└── OrderProcessingApplication.java
```

## Design Patterns & SOLID Principles

| Pattern | Usage |
|---------|-------|
| State Machine | Enforces valid order status transitions via `OrderStateMachine` |
| Repository | Abstracts data access via Spring Data JPA |
| DTO | Decouples API contract from database entities |
| Mapper | Centralized entity ↔ DTO conversion |
| Strategy | Service interface (`OrderService`) allows swappable implementations |
| Observer | Audit trail records all status transitions |
| Facade | Controller simplifies access to underlying services |
| Builder | Lombok `@Builder` for clean object construction |
| Dependency Injection | Constructor-based injection (no field injection) |

## Order Status Flow

```
PENDING ──→ PROCESSING ──→ SHIPPING ──→ DELIVERED
   │
   └──→ CANCELLED
```

- Only **PENDING** orders can be cancelled
- Status transitions are validated — invalid transitions return `409 Conflict`
- The scheduler advances orders every 60s (only orders idle for ≥1 minute are advanced)
- All transitions are recorded in the `order_status_history` audit table

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose *(optional — for production mode)*

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn clean test
```

All 59 tests across 14 test classes should pass.

---

## Running the Application

### Option 1: JAR with H2 (Development — no external DB needed)

```bash
# Build the JAR
mvn clean package -DskipTests

# Run with dev profile (H2 in-memory database)
java -jar target/order-processing-system-1.0.0.jar --spring.profiles.active=dev
```

The app starts at **http://localhost:8080** with H2 in-memory database. Data resets on restart.

### Option 2: Maven Spring Boot Plugin (Development)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Option 3: JAR with PostgreSQL (Production)

```bash
# Build the JAR
mvn clean package -DskipTests

# Run with prod profile pointing to your PostgreSQL
java -jar target/order-processing-system-1.0.0.jar \
  --spring.profiles.active=prod \
  --DB_URL=jdbc:postgresql://localhost:5432/ordersdb \
  --DB_USERNAME=orders_user \
  --DB_PASSWORD=your_secure_password
```

Flyway will automatically create/migrate the database schema on startup.

### Option 4: Docker Compose (Production — recommended)

```bash
docker-compose up -d --build
```

This starts:
- **PostgreSQL 16** on port 5432
- **Order Processing App** on port 8080 with `prod` profile
- Health checks, restart policies, volume persistence

To stop:
```bash
docker-compose down
```

### Option 5: Docker Image Only (bring your own DB)

```bash
# Build image
docker build -t order-processing-system .

# Run with env vars
docker run -d -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://your-db-host:5432/ordersdb \
  -e DB_USERNAME=orders_user \
  -e DB_PASSWORD=your_secure_password \
  order-processing-system
```

---

## Spring Profiles

| Profile | Database | Flyway | Logging | Use Case |
|---------|----------|--------|---------|----------|
| `dev` | H2 (in-memory) | Disabled | DEBUG, console | Local development |
| `prod` | PostgreSQL | Enabled | INFO, JSON file | Production deployment |

---

## API Endpoints

Base path: `/api/v1/orders`

All responses are wrapped in `ApiResponse<T>`:
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": { ... },
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-04-24T12:00:00"
}
```

### Create Order

```
POST /api/v1/orders
Content-Type: application/json

{
  "items": [
    { "productName": "Laptop", "quantity": 1, "unitPrice": 999.99 },
    { "productName": "Mouse", "quantity": 2, "unitPrice": 29.99 }
  ]
}
```

**Response:** `201 Created`

### Get Order by ID

```
GET /api/v1/orders/{id}
```

**Response:** `200 OK`

### Update Order Status

```
PATCH /api/v1/orders/{id}/status
Content-Type: application/json

{ "status": "PROCESSING" }
```

**Response:** `200 OK` or `409 Conflict` (invalid transition)

### List Orders (Paginated)

```
GET /api/v1/orders
GET /api/v1/orders?status=PENDING
GET /api/v1/orders?page=0&size=20
GET /api/v1/orders?status=SHIPPING&page=1&size=10
```

**Response:** `200 OK` with `PagedResponse<OrderResponse>`:
```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "last": false
  }
}
```

### Cancel Order

```
POST /api/v1/orders/{id}/cancel
```

**Response:** `200 OK` or `409 Conflict` (not in PENDING status)

---

## Useful URLs (Dev Mode)

| URL | Purpose |
|-----|---------|
| http://localhost:8080/swagger-ui.html | Interactive API docs (try APIs from browser) |
| http://localhost:8080/h2-console | H2 database console |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/actuator/metrics | Available metrics |
| http://localhost:8080/actuator/prometheus | Prometheus scrape endpoint |
| http://localhost:8080/api-docs | OpenAPI JSON spec |

### H2 Console Connection

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:ordersdb` |
| Username | `sa` |
| Password | *(empty)* |

---

## Configuration

Key settings in `application.yml` (all overridable via environment variables):

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `server.port` | `SERVER_PORT` | `8080` | Server port |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `dev` | Active profile |
| `order.scheduler.interval-ms` | `ORDER_SCHEDULER_INTERVAL` | `60000` | Scheduler interval (ms) |
| `order.scheduler.advance-threshold-minutes` | `ORDER_ADVANCE_THRESHOLD` | `1` | Min idle time before advancing |
| `spring.datasource.url` | `DB_URL` | *(profile-dependent)* | Database JDBC URL |
| `spring.datasource.username` | `DB_USERNAME` | `sa` (dev) | Database username |
| `spring.datasource.password` | `DB_PASSWORD` | *(empty)* (dev) | Database password |
| `spring.datasource.hikari.maximum-pool-size` | `DB_POOL_SIZE` | `20` (prod) | Connection pool size |

---

## Observability

### Correlation IDs
Every request gets a unique `X-Correlation-Id` header (auto-generated or passed by caller). It appears in all log entries and error responses for end-to-end tracing.

### Metrics (Micrometer)
- `orders.created.total` — counter of orders created
- `orders.cancelled.total` — counter of orders cancelled
- `orders.status.transitions` — counter tagged by `from` and `to` status

### Structured Logging
- **Console**: Human-readable with correlation ID
- **File**: `logs/order-processing.log` (rolling, 100MB max per file, 1GB total)
- **JSON File**: `logs/order-processing.json` (for ELK/Splunk ingestion)

### Health Check
`GET /actuator/health` returns custom `orderSystem` indicator with total order count.

---

## CI/CD

GitHub Actions workflow at `.github/workflows/ci.yml` runs on every push to `main`/`develop` and on PRs to `main`:

1. Sets up JDK 17 (Temurin)
2. Caches Maven dependencies
3. Builds and runs all tests (`mvn clean verify`)
4. Uploads test reports and JAR artifact

---

## Production Checklist

- [ ] Use Docker secrets, Vault, or K8s secrets for `DB_PASSWORD` — never hardcode
- [ ] Configure external PostgreSQL with SSL
- [ ] Set up Prometheus to scrape `/actuator/prometheus`
- [ ] Ship JSON logs to ELK/Splunk/CloudWatch
- [ ] Configure alerting on `orders.status.transitions` and health endpoint
- [ ] Tune `DB_POOL_SIZE` based on expected concurrency
- [ ] Set `ORDER_SCHEDULER_INTERVAL` and `ORDER_ADVANCE_THRESHOLD` for your SLA
