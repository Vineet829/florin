# Florin

Florin handles invoicing for freelancers and small businesses: GST invoices
with UPI QR codes, payment tracking and a portal for each client. It runs at
[invoice.confidental.space](https://invoice.confidental.space), where you can
try a demo without signing up.

## What it does

- Invoices, quotations, receipts and credit notes, with drafts and automatic
  numbering. Once a document is issued, its client, lines and tax can't
  change. To correct one, void it or issue a credit note.
- A tax rate on each line, or India GST mode (CGST/SGST/IGST, HSN/SAC codes)
  with helper files for GSTR-1 and GSTR-3B. TDS withheld by clients can be
  recorded too.
- UPI QR codes and bank details on invoices. Clients can report a payment for
  you to confirm. Receipts and payment reminders go out by email.
- Client balances by currency, statements, CSV import and client portals.
- PDFs in three templates. Documents can also be shared by email, link or
  WhatsApp.
- Recurring invoices on a weekly, monthly, quarterly or yearly schedule.
- Expense tracking and a profit and loss report.
- Reports on income, receivables by age, sales, tax and payments, with CSV
  downloads, plus a ZIP export of all your data.
- Invite people to a business by email, as owner, admin, member or viewer.
- On the Overview page, every figure links to the records behind it.
- Two-factor sign-in and Google sign-in. You can see your signed-in devices
  and sign them out, and there's a log of your account's security activity.
  Sign-ins and other sensitive requests are rate limited.

## Run it yourself

You only need Docker. On Windows, run the commands in WSL.

Start with a copy of `.env.example`:

```bash
cp .env.example .env
```

Then replace every `change-me` in `.env`. `POSTGRES_PASSWORD` can be any
password. `JWT_ACCESS_SECRET_KEY` signs sign-in tokens and has to be at least
32 bytes long, so use the output of `openssl rand -base64 48`.
`APP_ENCRYPTION_KEY` must be 32 random bytes in base64, which is what
`openssl rand -base64 32` prints. If either key is left as `change-me`, the
backend won't start.

Build and start the stack, with Mailpit to catch the emails:

```bash
docker compose --profile mail up -d --build
```

Building the images takes a while the first time. The app is then at
<http://localhost:8088>, and the emails it sends show up in the Mailpit inbox
at <http://localhost:8027>. `docker compose --profile mail down` stops
everything; with `-v` it also deletes the database and the uploaded logos.

### First login

Sign up, open the verification email in Mailpit, follow the link, then sign
in.

### On a real server

- **HTTPS**: Put a reverse proxy such as Caddy or nginx in front of
  `WEB_PORT`, use its https address for both `CLIENT_URL` and `SERVER_URL`,
  and set `COOKIE_SECURE=true`.
- **Email**: Set `EMAIL_HOST` and `EMAIL_PORT` to your SMTP server and
  `SENDER_EMAIL` to the address emails come from. If the server needs a
  login, also set `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`,
  `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true` and
  `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`.
- **Google sign-in**: Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, and
  register `CLIENT_URL` followed by `/api/v1/auth/oauth2/callback/google` as
  the redirect URI. The Google button shows once both are set.
- **Monitoring**: `/actuator/health` and `/actuator/prometheus` are served on
  `MANAGEMENT_PORT`. It isn't published, so only containers in the stack can
  reach it.

## Contributing

Bugs and questions go in
[issues](https://github.com/Vineet829/florin/issues). Development setup is in
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

Florin is released under the [MIT License](LICENSE). Copyright 2023-2026
Vineet Singh. The Noto Sans fonts used in the PDFs are under the
[SIL Open Font License](src/main/resources/fonts/OFL.txt).
