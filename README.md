# Formula Trainer

Backend for an MVP formula-card trainer.

## Stack

- Java 21
- Spring Boot 4.1.1
- Maven Wrapper
- PostgreSQL
- Flyway
- Docker Compose

## Local Run

1. Copy `.env.example` to `.env` and adjust passwords if needed.
2. Start PostgreSQL and the application:

```bash
docker compose up --build
```

3. Alternatively, start only PostgreSQL and run the application locally:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

4. Check health:

```bash
curl http://localhost:8080/api/v1/health
```

Expected response:

```json
{"status":"UP"}
```


PostgreSQL is published on host port `55432` by default to avoid conflicts with a locally installed PostgreSQL on `5432`. The local Spring profile uses the same port by default; override `DB_URL` if you choose another port.

## Tests

```bash
./mvnw test
```

Tests use PostgreSQL through Testcontainers.

## Formatting

```bash
./mvnw formatter:format
```

To check formatting without changing files:

```bash
./mvnw formatter:validate
```

## Profiles

- `local`: default profile for local development.
- `test`: used by automated tests with Testcontainers-provided database settings.
- `prod`: reads database settings from environment variables.

Required database variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Hibernate schema generation is disabled. Database schema changes are managed by Flyway migrations.
