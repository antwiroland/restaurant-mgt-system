# Restaurant Manager Monorepo

This repository is organized into four top-level workspaces:

- `backend/` - Spring Boot backend service
- `web/` - Next.js staff web application
- `mobile/` - Expo-style customer mobile workspace
- `documentation/` - Specs, guides, changelog, progress, phase reports

## Quick Start

### Backend

```bash
cd backend
mvn test -q
```

### Web

```bash
cd web
npm run test
npm run build
```

### Mobile

```bash
cd mobile
npm run test
npm run typecheck
```

## Documentation

- Master guide: `documentation/DEVELOPMENT_GUIDE.md`
- Deployment readiness: `documentation/DEPLOYMENT_READINESS.md`
- Live OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Live Swagger UI: `http://localhost:8080/swagger-ui.html`
- Final guide snapshot: `documentation/final_development_guide.md`
- Progress tracker: `documentation/PROJECT_PROGRESS.md`
- Changelog: `documentation/CHANGELOG.md`
- Phase reports: `documentation/reports/`

## Docker Stack

The repository includes a working Docker Compose stack for:
- Backend (`backend`)
- PostgreSQL (`postgres`)
- pgAdmin (`pgadmin`)
- Redis (`redis`)
- Zipkin (`zipkin`)
- Prometheus (`prometheus`)
- Grafana (`grafana`)

Start all services from repo root:

```bash
docker compose up -d --build
```

Services:
- Backend API: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- PostgreSQL: `localhost:5432` (`postgres` / `postgres`, db: `restaurant_manager`)
- pgAdmin: `http://localhost:5050` (`admin@restaurant.local` / `admin123`)
- Redis: `localhost:6379`
- Zipkin UI: `http://localhost:9411`
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3001` (`admin` / `admin123`)

Stop services:

```bash
docker compose down
```
