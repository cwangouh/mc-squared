# Formula Trainer

Backend for an MVP formula-card trainer.

## Stack

- Java 21
- Spring Boot 4.1.1
- Maven Wrapper
- PostgreSQL
- Flyway
- Docker Compose
- AWS SDK for S3-compatible object storage

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

## Image Storage

Local development stores uploaded images in `MEDIA_STORAGE_PATH`. This is the default:

```env
MEDIA_STORAGE_TYPE=local
MEDIA_STORAGE_PATH=./data/media
```

Production can use a private S3-compatible bucket. Create the bucket with your storage provider, then put its access details in `.env.prod`:

```env
MEDIA_STORAGE_TYPE=s3
S3_ENDPOINT=https://s3.example.com
S3_REGION=region-1
S3_BUCKET=<bucket-name>
S3_ACCESS_KEY=<access-key>
S3_SECRET_KEY=<secret-key>
S3_PATH_STYLE_ACCESS=true
```

Do not commit `.env` or S3 credentials. The application uploads, reads, and deletes objects through the S3 API. Browser clients continue to use `/api/v1/media/{id}`, so the bucket can remain private and does not require browser CORS configuration.

The bucket must already exist; the application does not attempt to create one. Existing files from `MEDIA_STORAGE_PATH` are not migrated automatically when storage is switched to S3.

## Production Run

Production uses an external PostgreSQL database and does not start a database container. Copy the production environment template on the server and fill in the real credentials:

```bash
cp .env.prod.example .env.prod
```

The database and database user must already exist. `DB_URL` should use the host, port, database name, and SSL mode supplied by the database provider. Flyway applies pending migrations when the application starts.

The CI pipeline should publish the application image to a container registry and set `APP_IMAGE` to that image. Start or update the backend with:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

Check the deployment with:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 app
curl http://127.0.0.1:8080/api/v1/health
```

The backend port is bound to loopback only. A reverse proxy running on the VPS can expose it over HTTPS. If the reverse proxy itself runs in Docker, place both services on a shared Docker network instead of publishing the backend port.

## Authentication Flow

Fetch a CSRF token before changing requests:

```bash
curl -c cookies.txt http://localhost:8080/api/v1/auth/csrf
```

Send the returned `token` as `X-CSRF-TOKEN` when logging in:

```bash
curl -b cookies.txt -c cookies.txt \
  -H 'Content-Type: application/json' \
  -H 'X-CSRF-TOKEN: <token>' \
  -d '{"username":"admin","password":"admin"}' \
  http://localhost:8080/api/v1/auth/login
```

Fetch a fresh CSRF token after login before protected `POST`, `PUT`, or `DELETE` requests.
