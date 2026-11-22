# Florin frontend

Built with Vite; use Node.js 24 (24.15 or newer). Run the Spring Boot backend
on port 8080, then run `npm start` in this directory and open
http://localhost:3001. Development API requests use `/api/v1` and the
development server proxies them to the local backend. The dev proxy also makes
the refresh cookie work over plain HTTP locally. Production cookie settings
don't change.

The Guest buttons on login and registration use the demo account in that
backend's database. It must be active and have a verified email.

To use a different API, set `VITE_API_URL` (including `/api/v1`) in
`.env.local` and restart the development server. Production builds call
`/api/v1` on their own origin (the Docker image's nginx forwards it to the
backend) unless `VITE_API_URL` is set at build time.

`npm run build` writes the production build to `dist/`, and `npm run preview`
serves it on port 3001 with the same proxy, so it uses the local backend.
`npm test` runs Vitest; `npm run lint` runs ESLint.
