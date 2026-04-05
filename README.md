# E-Commerce Microservices Platform

A production-ready learning project demonstrating core microservices patterns with Spring Boot 3, Spring Cloud, Kafka, Redis, and Docker.

---

## Architecture Overview

```
Client (Port 80)
  │
  ▼
Nginx (Port 80)             ← Reverse proxy, Gzip compression, SSL (if configured)
  │
  ▼
API Gateway (Port 8080)     ← JWT auth, rate limiting, circuit breaker, CORS
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

Observability & Logging
  Zipkin (:9411) · Prometheus (:9090) · Grafana (:3001) · Logstash · ELK
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
docker compose up -d          # starts all containers
```

### 2. Verify services are up

```bash
# Eureka dashboard (all services should appear after ~60 seconds)
# User: eurekauser, Pass: eurekapassword
open http://localhost:8761

# API Gateway health
curl http://localhost:8080/actuator/health
```

### 3. Monitoring and Observability

- **Kafdrop** http://localhost:9000 — browse `order.events` and `payment.events` topics
- **Mailhog** http://localhost:8025 — see welcome email + order confirmation
- **Zipkin** http://localhost:9411 — trace the order-service → product-service Feign call
- **Grafana** http://localhost:3001 — view metrics dashboards (admin / admin)
- **Nginx** http://localhost:80 — external entry point (forwards to API Gateway)

---

## Development Workflow (Without Docker)

Start infrastructure via Docker, run Spring services locally in your IDE:

```bash
# Start only infrastructure
docker compose up -d postgres mongodb orderdb mysql redis zookeeper kafka zipkin mailhog
```

Then run each service using Maven:
```bash
cd user-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
```

---

## Project Structure

```
ecommerce-microservices/
│
├── config-server/              Spring Cloud Config Server (port 8888)
├── service-registry/           Eureka Server (port 8761)
├── api-gateway/                Spring Cloud Gateway (port 8080)
│
├── user-service/               PostgreSQL + Redis + Kafka
├── product-service/            MongoDB + Redis cache
├── order-service/              PostgreSQL + Feign + Kafka
├── payment-service/            MySQL + Kafka
├── notification-service/       Kafka consumer + Redis
│
├── infrastructure/
│   ├── nginx/                  Reverse proxy configuration
│   ├── logstash/               Log pipeline for ELK
│   ├── prometheus/             Scrape config for all services
│   └── grafana/                Pre-built dashboards
│
├── scripts/
│   ├── deploy.sh               CI/CD deployment script
│   ├── rollback.sh             Emergency rollback script
│   └── server-setup.sh         Initial server provisioning
│
├── docs/                       Additional documentation & templates
│
├── .github/workflows/          CI/CD GitHub Actions pipelines
├── docker-compose.yml          Full stack configuration
├── .env.example                Environment variable template
└── pom.xml                     Root project descriptor (Java 21, Spring Boot 3.2)
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
GitHub Actions: [ci-all-services.yml]
        │
        ├── Build & Test each service (Maven + Testcontainers)
        │
        ├── Build Docker image (multi-stage)
        │
        ├── Push to GHCR (ghcr.io/nazir/...)
        │
        └── SSH to Staging Server (deploy.sh)
            └── docker compose pull + up -d
```

### Manual Controls
- `pr-checks.yml`: Runs on every Pull Request to ensure quality.
- `release.yml`: Triggered by creating a new tag (e.g., `v1.0.0`).
- `deploy.sh`: Handles zero-downtime restarts and health checks on the server.

### GitHub Secrets Required
To enable deployments, configure the following in your repo:
- `STAGING_HOST`, `STAGING_USER`, `STAGING_SSH_KEY`
- See [docs/github-secrets-setup.md](docs/github-secrets-setup.md) for full details.

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

