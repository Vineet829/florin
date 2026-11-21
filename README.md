# Florin

Florin is an invoicing app for freelancers and small businesses,
featuring GST invoices, UPI QR codes, payment tracking, and client portals.
It is available at
[invoice.confidental.space](https://invoice.confidental.space), with a live
demo that needs no sign-up.

## Features

- **Invoices and Quotations**: Create invoices, quotations, receipts, and
  credit notes with drafts and automatic numbering. Issued documents keep
  their client, lines, and amounts, so mistakes are fixed with a void or a
  credit note.
- **GST and Tax**: Set a tax rate on each line, or turn on India GST mode for
  CGST, SGST, and IGST, HSN/SAC codes, and GSTR-1 and GSTR-3B helper files.
  Record TDS withheld by your clients.
- **Getting Paid**: Show UPI QR codes and bank details on your invoices.
  Clients can report a payment for you to confirm. Send receipts and payment
  reminders.
- **Clients**: See balances by currency, send statements, import clients
  from a CSV file, and give clients their own portal.
- **Sharing**: Download PDFs in three templates, or send documents by email,
  share link, or WhatsApp.
- **Recurring Invoices**: Repeat an invoice weekly, monthly, quarterly, or
  yearly.
- **Expenses**: Record your expenses and see your profit and loss.
- **Reports**: See income, receivables by age, sales, tax, and payments.
  Download them as CSV files, or export everything as a ZIP file.
- **Teams**: Work together in a business with owner, admin, member, and viewer
  roles, and invite people by email.
- **Overview**: Start from a dashboard whose figures open the records behind
  them.
- **Account Security**: Use two-factor sign-in or Google sign-in, see and sign
  out your devices, and review your security activity. Sign-ins and other
  sensitive requests are rate limited.

## Getting Started

The commands below are for macOS and Linux. On Windows, use WSL. You need
Docker.

### Run with Docker

```bash
cp .env.example .env    # then replace every change-me
docker compose --profile mail up -d --build
```

The first build takes a few minutes. Then open <http://localhost:8088>. Emails
go to Mailpit at <http://localhost:8027>. To stop the app, run
`docker compose --profile mail down`, and add `-v` to delete the data as well.

### First Login

1. Sign up, then open the verification email in Mailpit and follow the link.
2. Sign in.

## Contributing

Bug reports and questions are welcome as
[issues](https://github.com/Vineet829/florin/issues).
[CONTRIBUTING.md](CONTRIBUTING.md) covers the architecture, running locally,
admin access, the tests, and the project structure.

## License

[MIT](LICENSE) © 2023-2026 Vineet Singh. The bundled Noto Sans fonts are under
the [SIL Open Font License](src/main/resources/fonts/OFL.txt).
