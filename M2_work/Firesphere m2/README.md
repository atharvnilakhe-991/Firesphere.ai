# FireSphere.ai

FireSphere.ai is a multi-tenant enterprise SaaS platform for smart fire administration operations. The application is structured for Azure App Service deployment with a secure Node.js Express API backend and a production React frontend compiled into static assets.

## Enterprise project structure

- `backend/` — Express API, DB access, auth, tenant logic, and protected routes
- `frontend/` — React + Vite landing pages, dashboard, role-based UI, and SaaS experience
- `database/` — MariaDB schema and data model for multi-tenant organizations, SSO, AI workflows, incidents, and compliance
- `infra/` — Azure deployment helpers and environment configuration templates

## Azure Web App deployment model

This project is built to match a single Azure Web App deployment pattern:

- the backend runs as the app service process
- the React app is built to `frontend/dist`
- Express serves the built frontend from the same app instance
- database credentials and environment settings are kept in Azure App Settings

## Hybrid mobile app architecture

A dedicated Expo + React Native mobile client is now included under `mobile/` to support Android and iOS while preserving the existing web app.

- `frontend/` — React + Vite web application
- `mobile/` — Expo React Native mobile application
- `backend/` — Express API used by both web and mobile clients
- `database/` — MariaDB schema for enterprise tenant data

## Local development

1. Install root dependencies:
   npm install
2. Install backend dependencies:
   npm run install:backend
3. Install frontend dependencies:
   npm run install:frontend
4. Install mobile dependencies:
   npm run install:mobile
5. Start both web services together:
   npm run dev
6. Start the mobile app:
   npm run dev:mobile

## Production build for Azure

Run:

```bash
npm run build
```

Then launch the backend service:

```bash
npm start
```

## Azure App Service configuration

Set the following values in Azure App Settings:

- `DB_HOST`
- `DB_PORT`
- `DB_USER`
- `DB_PASSWORD`
- `DB_NAME`
- `PORT=8080`
- `NODE_ENV=production`

Recommended startup command in Azure Web App (Linux / Node runtime):

```bash
npm install --prefix backend && npm install --prefix frontend && npm run build --prefix frontend && npm run start --prefix backend
```

## Database

The MariaDB schema in `database/firesphere_ai_mariadb.sql` is tuned for a shared-host, multi-tenant enterprise deployment and supports organizations, user roles, SSO, API credentials, AI workflows, incidents, NOC, and security policies.
