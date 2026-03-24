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

Start all services from repo root:

```bash
docker compose up -d --build
```

Services:
- Backend API: `http://localhost:8080`
- PostgreSQL: `localhost:5432` (`postgres` / `postgres`, db: `restaurant_manager`)
- pgAdmin: `http://localhost:5050` (`admin@restaurant.local` / `admin123`)
- Redis: `localhost:6379`

Stop services:

```bash
docker compose down
```
