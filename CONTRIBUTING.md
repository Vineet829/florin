# Contributing

Bug reports and questions are welcome as
[issues](https://github.com/Vineet829/florin/issues).

To work on the code, you need Docker, Java 21, Maven, and Node.js 24 (24.15 or
newer). The commands below are for macOS and Linux. On Windows, use WSL.

## Architecture

- **Client**: A React 18 single-page app built with Vite, Redux Toolkit, React
  Router, and Tailwind CSS. In Docker, nginx serves it and passes `/api`
  requests to the backend on the same origin.
- **Backend**: A Java 21 and Spring Boot 3.5 API on PostgreSQL 16, with Flyway
  migrations. It renders PDFs, sends email over SMTP, and runs a daily job for
  recurring invoices, reminders, and overdue alerts.
- **Data**: Clients, documents, payments, and expenses belong to a business.
  The API checks the user's membership and role on every request, and tests
  make sure no business can reach another's data.
- **Money**: Amounts are stored as exact decimals, and totals are calculated
  on the server.
- **Concurrency**: Concurrent edits and the daily job can't undo each other's
  changes to documents and their payments, recurring invoices, clients, or
  settings, because those records are locked while they change.
- **Scaling**: The app is built to run on several servers. The daily job runs
  on one of them at a time, rate limits are shared through PostgreSQL, and
  uploaded logos go to a folder the servers must share (`UPLOAD_DIR`).
- **Encryption**: Two-factor secrets and share links are encrypted with an
  app-wide key.

## Run Locally

Create a sign-in key with `openssl rand -base64 48` and an encryption key
with `openssl rand -base64 32`, and keep them. A new encryption key can't
read the two-factor secrets and share links sealed with the old one.

```bash
# Terminal 1: PostgreSQL and Mailpit, then the API on port 8080
docker compose -f docker/spring/local.yml up -d
export JWT_ACCESS_SECRET_KEY="paste-your-sign-in-key-here"
export APP_ENCRYPTION_KEY="paste-your-encryption-key-here"
export CLIENT_URL=http://localhost:3001    # the client's address, for links
mvn spring-boot:run
```

```bash
# Terminal 2: the client on port 3001
cd florin/client
npm install
npm start
```

Open <http://localhost:3001>. Emails go to Mailpit at <http://localhost:8026>.
The [README](README.md#first-login) describes the first login.

### Admin Access

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

## Tests

The backend has over 900 tests, over 600 of which run against a real
PostgreSQL database through Testcontainers. The client has over 650 tests with
Vitest.

```bash
mvn test                  # backend, needs Docker for PostgreSQL

cd florin/client          # after npm install
npm run lint
npx vitest run
```

## Project Structure

- `src/`: The Spring Boot backend.
- `florin/client/`: The React client.
- `docker/spring/local.yml`: PostgreSQL and Mailpit for local development.
