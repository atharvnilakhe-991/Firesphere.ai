# FireSphere.ai - Backend Core

This is the foundational Spring Boot backend for the FireSphere.ai project (Member 2).

## Technologies
* Java 21
* Spring Boot 3.2.x (Spring Web, Spring Validation, Spring Data JPA, Actuator)
* PostgreSQL
* Flyway
* OpenAPI / Swagger

## Package Structure
* `com.firesphere.backend`
  * `common` - Reusable foundation modules
    * `exception` - Global exception handling
    * `response` - Standard API responses
    * `validation` - Custom validators
    * `logging` - Request tracing and logs
    * `configuration` - Security, CORS, OpenAPI setup

## Requirements
* Java 21+
* Maven 3.9+
* PostgreSQL 15+

## Configuration
The backend is configured via Spring Boot profiles. By default, it runs with the `local` profile.
Important environment variables:
* `DB_HOST` (default: localhost)
* `DB_PORT` (default: 5432)
* `DB_NAME` (default: firesphere)
* `DB_USERNAME` (default: postgres)
* `DB_PASSWORD` (default: postgres)
* `JWT_SECRET` (REQUIRED: The JWT signing secret. Production must not use a hardcoded/default fallback. Application will fail to start if this is missing).

## How to Run

1. Make sure you have PostgreSQL running with the configured credentials, or run the tests which use an in-memory database.
2. Build the project:
   ```bash
   ./mvnw clean install
   ```
3. Run the application:
   ```bash
   JWT_SECRET="your-development-secret-here" ./mvnw spring-boot:run
   ```

## Endpoints
* **Base API Path:** `/api/v1`
* **Authentication:** `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
* **Health Check:** `GET /actuator/health`
* **OpenAPI Documentation:** `GET /v3/api-docs`
* **Swagger UI:** `GET /swagger-ui.html`

## Authentication Persistence & Schema Contract Gap
**REQUIREMENT / CONTRACT GAP**
Authentication requires a production PostgreSQL users schema. No authoritative Member 3 production users schema was available during Phase 2 implementation. 

No Member 3 migration/schema was modified or invented by Member 2. Authentication persistence currently depends on the authoritative PostgreSQL users schema that Member 3 must provide. If the authoritative schema is unavailable, H2 is used **only** for authentication tests, which does not constitute PostgreSQL production verification.

## Development Notes
- Do not invent database schema. Member 3 owns the DB schema and migrations. Place migrations in `src/main/resources/db/migration` only if part of the approved contract.
- Always return a standard `ApiErrorResponse` on failure.
- Ensure trace IDs are used in logs and error responses.
