# Contributing

The backend builds with Java 21 and Maven, and the client needs Node.js 24
(24.15 or newer). Docker runs PostgreSQL and Mailpit while you work, and the
backend tests start their own PostgreSQL container.

## Run locally

Create a sign-in key with `openssl rand -base64 48` and an encryption key
with `openssl rand -base64 32`, and keep them. A new encryption key can't
read the two-factor secrets and the share and portal links sealed with the
old one.

The backend's defaults already match the database and Mailpit in
`docker/spring/local.yml`, so it only needs your two keys and `CLIENT_URL`.
`CLIENT_URL` is the client's address: the backend uses it in emailed links
and redirects. It defaults to port 3000, but the client runs on 3001.

```bash
docker compose -f docker/spring/local.yml up -d
export JWT_ACCESS_SECRET_KEY="paste-your-sign-in-key-here"
export APP_ENCRYPTION_KEY="paste-your-encryption-key-here"
export CLIENT_URL=http://localhost:3001
mvn spring-boot:run
```

Leave the API running on port 8080. The client's Vite dev server listens on
port 3001 and passes `/api/v1` requests through to it:

```bash
cd florin/client
npm install
npm start
```

You'll find the app at <http://localhost:3001> and Mailpit's inbox at
<http://localhost:8026>. Signing up works as described under
[First login](README.md#first-login).

### Admin access

To make yourself an admin, run this SQL once and sign in again. Admins can
then promote other accounts in the app.

```sql
INSERT INTO user_roles (user_pkid, role)
SELECT pkid, 'Admin' FROM users WHERE lower(email) = lower('you@example.com');
```

If it prints `INSERT 0 0`, no account has that email. To open the database,
run `docker exec -it florin_postgres psql -U invoicespot -d invoicespot`
when [running locally](#run-locally), or
`docker compose exec db psql -U invoicespot -d invoicespot` with the Docker
stack.

## Run the tests

About 630 of the backend's roughly 920 tests run against PostgreSQL in
Testcontainers, so Docker has to be running for `mvn test`. The client has
about 680 Vitest tests and an ESLint check, both run from `florin/client`
once `npm install` is done:

```bash
mvn test

cd florin/client
npm run lint
npx vitest run
```

## Code overview

`src/` holds the backend: Spring Boot 3.5 on Java 21, with PostgreSQL 16 and
Flyway for the schema. Besides the REST API under `/api/v1`, it renders the
PDFs with openhtmltopdf, sends mail over SMTP and runs one scheduled job a
day (07:00 UTC by default) for recurring invoices, reminders and overdue
alerts.

`florin/client/` holds the React 18 client, which uses Vite, Redux Toolkit,
React Router and Tailwind CSS. Its Docker image serves the build with nginx
and forwards `/api` and `/uploads` to the backend, so the browser only talks
to one origin. `docker-compose.yml` runs the whole stack, while
`docker/spring/local.yml` has just PostgreSQL and Mailpit for local work.

Clients, documents, payments and expenses belong to a business. Business
endpoints take a `BusinessAccess` for the user's active business and name
the permission they need with `@Requires`, and every record they load is
checked against that business. A test fails if an endpoint leaves out its
permission, and others check that one business can't reach another's
records. Amounts are `BigDecimal`, and the server works out the totals.

Documents, recurring schedules, clients and business settings are locked
with `PESSIMISTIC_WRITE` before they change, and payments go through their
document's lock. You can run several backends on one database: ShedLock lets
only one of them run the daily job, rate limits are kept in PostgreSQL by
default, and they all need to share `UPLOAD_DIR`, where logos are saved.
Two-factor secrets, share links and client portal links are stored encrypted
with `APP_ENCRYPTION_KEY`.
