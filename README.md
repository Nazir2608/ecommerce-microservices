# E-Commerce Microservices Platform

A production-ready learning project demonstrating core microservices patterns with Spring Boot 3, Spring Cloud, Kafka, Redis, and Docker.

---

## Architecture Overview

```
Client
  │
  ▼
API Gateway (8080)          ← JWT auth, rate limiting, circuit breaker, CORS
  │         │
  │    ┌────┴──────────────────────────────────┐
  │    │  Service Registry (Eureka :8761)       │
  │    │  Config Server (:8888)                 │
  │    └───────────────────────────────────────┘
  │
  ├── user-service       (:8081)  PostgreSQL + Redis + Kafka
  ├── product-service    (:8085)  MongoDB + Redis cache
  ├── order-service      (:8082)  PostgreSQL + Feign + Kafka
  ├── payment-service    (:8083)  MySQL + Kafka (event-driven)
  └── notification-svc   (:8084)  Redis + Kafka (stateless)

Async Event Bus (Kafka)
  Topics: user.events · order.events · payment.events · notification.events

Observability
  Zipkin (:9411) · Prometheus (:9090) · Grafana (:3001) · ELK
```

---

## Key Microservices Concepts Implemented

### 1. Service Discovery (Eureka)
Every service registers itself at startup and discovers others by name (not IP).  
`@FeignClient("product-service")` resolves to actual instances via Eureka — no hardcoded URLs.

### 2. Centralized Configuration (Spring Cloud Config)
All `application.yml` settings live in `config-server/src/main/resources/config/`.  
Services fetch config at startup. Change `user-service.yml` → restart just that service.  
In production: point config-server at a private Git repo instead of classpath.

### 3. API Gateway (Spring Cloud Gateway)
Single entry point for all external traffic:
- JWT validation (token parsed once here, headers forwarded downstream)
- Rate limiting per IP via Redis token bucket
- Circuit breaking via Resilience4j
- Route-level CORS handling

### 4. Database-per-Service (Polyglot Persistence)
| Service | Database | Why |
|---|---|---|
| user-service | PostgreSQL | Relational, strong ACID for accounts |
| product-service | MongoDB | Flexible schema for varied product attributes |
| order-service | PostgreSQL | ACID transactions for financial records |
| payment-service | MySQL | Battle-tested for high-volume payment records |
| notification-service | None (Redis only) | Stateless, no persistent data needed |

### 5. Feign Clients + Circuit Breaker
`order-service` calls `product-service` via Feign (auto-discovers via Eureka).  
Resilience4j wraps each call — if product-service fails >50% of requests, circuit OPENS  
and fallback method returns immediately without waiting for timeouts.

**Circuit states:** `CLOSED` → `OPEN` → `HALF-OPEN` → `CLOSED`  
Monitor at: `GET /actuator/circuitbreakers`

### 6. Choreography-based Saga (Kafka)
Distributed transactions without a distributed transaction coordinator:

```
order-service          Kafka                payment-service         order-service
      │                                           │                      │
      │── ORDER_CREATED ─────────────────────────►│                      │
      │                                           │── PAYMENT_SUCCESS ──►│
      │                                           │    (or FAILED)       │
      │                                        (MySQL)                confirm/cancel
   (Postgres)                                                          (Postgres)
```

Compensation on failure: order cancelled → stock released → customer notified.

### 7. Redis Caching (Cache-Aside Pattern)
```
Request → Check Redis → HIT: return cached   (fast, no DB)
                     → MISS: query MongoDB → store in Redis → return
```
`@Cacheable`, `@CachePut`, `@CacheEvict` manage the cache automatically.  
Cache TTL configured per entity type. Stock quantities intentionally not cached.

### 8. JWT Authentication
- Access tokens: short-lived (24h), validated at API Gateway
- Refresh tokens: long-lived (7d), stored in Redis, supports rotation
- Token blacklisting on logout (stored in Redis with remaining TTL)
- User identity propagated as `X-Auth-User-Id` header to all downstream services

### 9. Idempotency
`payment-service` checks `idempotencyKey` (orderId) before processing — handles  
Kafka at-least-once delivery. Same event received twice = processed once.

### 10. Distributed Tracing (Zipkin / Micrometer)
Every request gets a `traceId`. All service-to-service calls carry this ID.  
View the full call chain at http://localhost:9411 — see latency per service.

---

## Quick Start

### Prerequisites
- Docker Desktop 4.x+
- Java 21+ (for local development without Docker)
- Maven 3.9+

### 1. Start everything

```bash
cp .env.example .env          # review and adjust if needed
chmod +x scripts/dev-start.sh
./scripts/dev-start.sh        # starts all containers
```

### 2. Verify services are up

```bash
# Eureka dashboard (all services should appear after ~60 seconds)
open http://localhost:8761

# API Gateway health
curl http://localhost:8080/actuator/health
```

### 3. Seed test data and trigger the full Saga

```bash
chmod +x scripts/seed-data.sh
./scripts/seed-data.sh
```

Then open:
- **Kafdrop** http://localhost:9000 — browse `order.events` and `payment.events` topics
- **Mailhog** http://localhost:8025 — see welcome email + order confirmation
- **Zipkin** http://localhost:9411 — trace the order-service → product-service Feign call

---

## Development Workflow (Without Docker)

Start infrastructure via Docker, run Spring services locally in your IDE:

```bash
# Start only infra
./scripts/dev-start.sh infra

# Run each service from its directory
cd user-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run &
cd order-service && mvn spring-boot:run &
```

---

## Project Structure

```
ecommerce-microservices/
│
├── config-server/              Spring Cloud Config Server (port 8888)
├── service-registry/           Eureka Server (port 8761)
├── api-gateway/                Spring Cloud Gateway (port 8080)
│   ├── filter/
│   │   └── AuthenticationFilter.java    ← JWT validation
│   └── config/
│       └── GatewayConfig.java           ← Rate limiter + fallbacks
│
├── user-service/               PostgreSQL + JWT + Kafka producer
│   ├── model/User.java                  ← JPA entity
│   ├── security/JwtTokenProvider.java   ← Token generation/validation
│   ├── service/AuthService.java         ← Register, login, refresh, logout
│   └── event/UserEventPublisher.java    ← Publishes to user.events
│
├── product-service/            MongoDB + Redis cache
│   ├── model/Product.java               ← MongoDB @Document
│   └── service/ProductService.java      ← @Cacheable, stock reservation
│
├── order-service/              PostgreSQL + Feign + Resilience4j + Kafka
│   ├── client/ProductServiceClient.java ← Feign + circuit breaker + fallback
│   ├── model/Order.java                 ← State machine (PENDING→CONFIRMED...)
│   └── service/OrderService.java        ← Saga orchestration + Kafka consumer
│
├── payment-service/            MySQL + Kafka consumer + idempotency
│   ├── model/Payment.java               ← idempotencyKey field
│   └── service/PaymentService.java      ← handleOrderEvent() + deduplication
│
├── notification-service/       Kafka multi-topic consumer + Redis dedup
│   └── service/NotificationService.java ← Email templates + idempotency
│
├── infrastructure/
│   ├── prometheus/prometheus.yml        ← Scrape config for all services
│   └── grafana/provisioning/            ← Pre-built dashboards
│
├── scripts/
│   ├── dev-start.sh                     ← Quick-start script
│   └── seed-data.sh                     ← Test data + happy-path flow
│
├── .github/workflows/ci-cd.yml          ← GitHub Actions: test → build → push → deploy
├── docker-compose.yml                   ← Full stack (all services + infra)
├── .env.example                         ← Environment variable template
└── README.md
```

---

## API Reference (via Gateway)

### Auth
```
POST /api/v1/auth/register     → Register new user
POST /api/v1/auth/login        → Login, get JWT tokens
POST /api/v1/auth/refresh      → Refresh access token
POST /api/v1/auth/logout       → Invalidate tokens (requires Bearer)
```

### Products
```
GET  /api/v1/products/{id}            → Get product by ID (cached in Redis)
GET  /api/v1/products?category=X      → List by category (paginated)
POST /api/v1/products                 → Create product (requires Bearer)
PUT  /api/v1/products/{id}            → Update product (requires Bearer)
```

### Orders (all require Bearer token)
```
POST /api/v1/orders                   → Place order (triggers Saga)
GET  /api/v1/orders/{id}              → Get order details
GET  /api/v1/orders/my-orders         → Get current user's orders
POST /api/v1/orders/{id}/cancel       → Cancel order (releases stock)
```

---

## Deployment Workflow

```
Developer pushes to main
        │
        ▼
GitHub Actions: detect-changes
        │
        ├── Only changed services → run tests (Testcontainers)
        │
        ├── Build Docker image (multi-stage, layered JAR)
        │   Cache layers in GitHub Actions Cache (fast rebuilds)
        │
        ├── Push to GitHub Container Registry (ghcr.io)
        │
        └── SSH to staging server
            └── docker compose pull + up -d
```

### Scaling a service
```bash
# Run 3 instances of order-service (Eureka load-balances automatically)
docker compose up -d --scale order-service=3
```

### Rolling update (zero downtime for stateless services)
```bash
docker compose pull order-service
docker compose up -d --no-deps order-service
```

---

## Observability Cheatsheet

| Tool | URL | What to look at |
|---|---|---|
| Eureka | http://localhost:8761 | All registered services + instances |
| Kafdrop | http://localhost:9000 | Kafka topics, consumer groups, message browser |
| Zipkin | http://localhost:9411 | Distributed traces — latency per service |
| Prometheus | http://localhost:9090 | Raw metrics — try `http_server_requests_seconds_count` |
| Grafana | http://localhost:3001 | Pre-built JVM + HTTP dashboards |
| Mailhog | http://localhost:8025 | All notification emails |
| Redis Commander | http://localhost:8090 | Browse cache keys, refresh tokens |
| Circuit Breakers | GET /actuator/circuitbreakers | State of each circuit breaker |

---

## Learning Roadmap

After building and running this project, here's what to explore next:

1. **Transactional Outbox Pattern** — guarantee Kafka publish + DB write are atomic
2. **CQRS + Event Sourcing** — separate read/write models, rebuild state from events
3. **Service Mesh (Istio)** — move auth/tracing/retries to the infrastructure layer
4. **Kubernetes deployment** — scale with HPA, use ConfigMaps instead of Config Server
5. **API versioning** — evolve APIs without breaking consumers
6. **Distributed caching with Redis Cluster** — multi-node Redis for high availability
7. **Dead Letter Topics** — handle poison-pill Kafka messages that keep failing
8. **Saga Orchestration** — compare choreography (what we built) vs orchestration
