# Order Processing System

A production-ready backend system built with Spring Boot for managing orders through their lifecycle — from creation to delivery.

## Features

- Create orders with multiple items
- Retrieve order details by ID
- Update order status with validated state transitions
- List all orders with optional status filtering
- Cancel pending orders
- Automated status advancement (PENDING → PROCESSING → SHIPPING → DELIVERED) every 1 minute via scheduler

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.5**
- **Spring Data JPA** — data access
- **H2 Database** — in-memory (swappable to PostgreSQL/MySQL)
- **Lombok** — boilerplate reduction
- **Jakarta Bean Validation** — request validation
- **JUnit 5 + Mockito** — 57 unit tests

## Project Structure

```
src/main/java/com/order/
├── controller/          # REST endpoints
├── dto/                 # Request/Response DTOs
├── entity/              # JPA entities (Order, OrderItem)
├── enums/               # OrderStatus enum
├── exception/           # Custom exceptions + global handler
├── mapper/              # Entity ↔ DTO conversion
├── repository/          # Spring Data JPA repositories
├── scheduler/           # Automated status advancement
├── service/             # Business logic (interface + impl)
├── statemachine/        # Order state transition rules
└── OrderProcessingApplication.java
```

## Design Patterns

| Pattern | Usage |
|---------|-------|
| State Machine | Enforces valid order status transitions |
| Repository | Abstracts data access via Spring Data JPA |
| DTO | Decouples API contract from database entities |
| Mapper | Centralized entity ↔ DTO conversion |
| Strategy | Service interface allows swappable implementations |
| Dependency Injection | Constructor-based injection via Spring IoC |
| Facade | Controller simplifies access to underlying services |

## Order Status Flow

```
PENDING ──→ PROCESSING ──→ SHIPPING ──→ DELIVERED
   │
   └──→ CANCELLED
```

- Only **PENDING** orders can be cancelled
- Status transitions are validated — invalid transitions return `409 Conflict`
- The scheduler automatically advances orders every 1 minute (only orders that have been in their current status for at least 1 minute are advanced)

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Build & Test

```bash
mvn clean test
```

### Run

```bash
mvn spring-boot:run
```

The application starts at `http://localhost:8080`.

## API Endpoints

### Create Order

```
POST /api/orders
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
GET /api/orders/{id}
```

**Response:** `200 OK`

### Update Order Status

```
PATCH /api/orders/{id}/status
Content-Type: application/json

{ "status": "PROCESSING" }
```

**Response:** `200 OK` or `409 Conflict` (invalid transition)

### List All Orders

```
GET /api/orders
GET /api/orders?status=PENDING
```

**Response:** `200 OK`

### Cancel Order

```
POST /api/orders/{id}/cancel
```

**Response:** `200 OK` or `409 Conflict` (not in PENDING status)

## Configuration

Key settings in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | Server port |
| `order.scheduler.interval-ms` | `60000` | Scheduler interval in milliseconds |
| `spring.datasource.url` | `jdbc:h2:mem:ordersdb` | Database URL |
| `logging.file.name` | `logs/order-processing.log` | Log file path |

## H2 Console

Available at `http://localhost:8080/h2-console` while the app is running.

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:ordersdb` |
| Username | `sa` |
| Password | *(empty)* |

## CI/CD

GitHub Actions workflow at `.github/workflows/ci.yml` runs on every push to `main`/`develop` and on PRs to `main`:

1. Sets up JDK 17
2. Builds and runs all tests (`mvn clean verify`)
3. Uploads test reports and JAR artifact
