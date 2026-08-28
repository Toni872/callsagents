# Deploy & Demo Runbook — Callsagents + Script9

Updated: 2026-08-18 — reflects the ACTUAL production setup (verified end-to-end).

> ⚠️ **The original claim "every push to `main` triggers auto-deploy" is FALSE.**
> Neither `callsagents-backend`, `callsagents-frontend` nor the `script9` service
> has GitHub auto-deploy enabled. Every deploy is **manual via `railway up`**.
> See [Deploying changes](#deploying-changes-important).

---

## 1. Production topology (current)

| Piece | Where | URL / identity |
|---|---|---|
| Callsagents backend | Railway project `renewed-reverence` (`5d126558-...`) | `https://callsagents-production.up.railway.app` (health: `/api/health` → `{"status":"UP"}`) |
| Callsagents frontend | Railway `renewed-reverence` | `https://callsagents-frontend-production.up.railway.app` |
| Script9 web | Railway project `beautiful-enthusiasm` (`50c50219-...`), service `script9` | `https://www.script-9.com` |
| GitHub repos | — | `Toni872/callsagents`, `Toni872/script9` |

⚠️ **`script9.com` (no guion) is NOT ours — it is a HugeDomains parking page.**
The real domain is `www.script-9.com`. Do not confuse them.

## 2. Credentials

| Environment | Email | Password |
|---|---|---|
| Local backend (`:8080`) | `admin@callsagents.local` | `<ver secrets/env CALLSAGENTS_ADMIN_PASSWORD>` |
| Production backend | `admin@callsagents.com` | `<ver secrets/env CALLSAGENTS_ADMIN_PASSWORD>` |
| **Demo (local + prod)** | `demo@callsagents.com` | `demo12345` |

The demo account (prod user id `3b29c60d-62d7-4bb7-9fa4-70196c318a2a`) is
seeded with 8 leads, 3 appointments, 1 campaign ("Campaña de verano — Inglés
intensivo", id `8256b86b`) and 1 logged call from the ficticious
"Academia Meridiano".

## 3. Demo flow (what a visitor experiences)

1. `www.script-9.com` hero button **"Probar Callsagents gratis"** →
   `https://callsagents-frontend-production.up.railway.app/dashboard?demo=1`
2. `auth.guard.ts` sees `?demo=1`, auto-logs-in with the demo account and lands
   **directly on the dashboard** (no landing page, no login form).
3. The dashboard shows the "Demo · Asistente conversacional" card (only for
   `demo@callsagents.com`) → `/assistant-demo`.
4. Scripted chat: asks the visitor's name, answers course questions (49€/mes,
   online, DELE, horarios), then offers a free level test tomorrow 10:00.
   On "Sí, agenda" it **really creates** a lead (`POST /leads`) and an
   appointment (`POST /appointments`, requires `leadId` + `userId`,
   status `PENDING`, 30 min) → "Ver cita en el calendario" → `/appointments`.

There is no real chat/voice yet (DIDWW KYC pending). The assistant is a
frontend simulator proving the capture → qualify → book loop with the
existing backend.

## 4. Deploying changes (IMPORTANT)

**Always use `railway up`, never `railway redeploy`.** `redeploy` reuses the OLD
deployment's code. `railway up` uploads the current directory and rebuilds.

```bash
# Backend (run from backend/):
cd backend
railway up --service callsagents-backend --detach

# Frontend (run from frontend/):
cd frontend
railway up --service callsagents-frontend --detach

# Script9 (run from Script9-Project/):
railway up --service script9 --detach
```

`--detach` returns immediately; watch the build at the printed Build Logs URL.

> Repo-root helper: `pwsh -NoProfile -File scripts\verify-deploy.ps1`
> (tests + local smoke + deploy + prod smoke). Verification only:
> `-SkipDeploy`.

### Gotchas that have burned us

- **`mvn clean test` always** for the backend — Lombok incremental compilation
  corrupts the build; LSP "getX() is undefined" errors are noise.
- **Angular templates**: a literal `@` in an email inside a template breaks the
  build (`NG5002 "Incomplete block"`) — use `&#64;`.
- **The landing/assistant chunks are lazy**: they do NOT appear in
  `index.html` preloads. Verify a chunk by fetching it directly
  (e.g. `/chunk-IGD27RQF.js` contains "Probar el asistente").
- **`docker compose up --build` can serve stale layers** after a failed build —
  use `docker compose build --no-cache`.
- Frontend nginx serves `dist/<project>/browser/` (Angular 17+ layout).

### Deploy verification (URL smoke)

```bash
# Backend health
Invoke-WebRequest https://callsagents-production.up.railway.app/api/health
# Frontend
Invoke-WebRequest https://callsagents-frontend-production.up.railway.app/landing
# Script9
Invoke-WebRequest https://www.script-9.com
```

After a Script9 deploy, confirm: no "Script9 Engine" string anywhere, the demo
button is in the hero, and `/casos-de-uso` returns 404.

## 5. Backend migrations & calendar fix

- Flyway runs on backend start; migration **V9** adds the Google Calendar
  `external_event_url` fix (real `htmlLink` — the `eid` must include
  `calendarId=contact@script-9.com`).
- V9 is applied in production (backend boots → migration runs; health UP proves
  it).

## 6. Script9 stack (current)

- **Next.js 15.5.23** (backport branch with security fixes) + **React 19.2.8**,
  TypeScript, Tailwind, Supabase (`@supabase/ssr`), Stripe, Resend.
- Migrated 2026-08-18 from Next 14.2.35 (EOL, 4 HIGH advisories) — commit
  `249e8fd`. Remaining `npm audit` HIGHs are postcss/sharp **bundled inside
  next@15.5.23** — only fixable by moving to Next 16.
- Branding: "Script9 Engine" fully removed (59 hits across src/docs/public/
  portfolio/scripts replaced with "Script9" or neutral copy). Legacy R2 v2
  client (`cloudflareR2.js`/`imageService.js`) and `aws-sdk` v2 deleted; only
  `src/lib/r2.ts` (v3) remains.
- Home is a single-CTA landing: **"Probar Callsagents gratis"** (demo) primary,
  "Solicitar diagnóstico gratuito" as a secondary text link (scroll to
  `#contacto`). No `/casos-de-uso` page, no header diagnostic button.

## 7. Verified demo data (local seeds, for reference)

Local backend demo user `10f19265-ba27-4b53-a70e-d25767ee14f4`; key lead ids:
Elena Ruiz `ad413da8-...`, Carlos Domínguez `4ae55e94-...`, Lucía Fernández
`c6de85c1-...`, Javier Ortega `b4420e52-...`, Marta Gil `541488db-...`,
Pablo Serrano `e6e43f3c-...`, Laura Ibáñez `aca789c2-...`, Hugo Prieto
`55fea0c5-...`. Appointments `b1e2b76b` (CONFIRMED), `f5cf8381` (PENDING),
`b98c8b50` (CONFIRMED). Campaign `e96a46f6-...`. Call `3c9f664b-...` (ENDED).

## 8. Known issues / next steps

- **DIDWW KYC pending** — waiting verification email; business identity Guion9
  must NOT be deleted (number +34 865 450 250 is attached).
- **Voice/chat are not real yet** — the demo assistant is a frontend simulator.
- **Script9 `.env.local`, `.env.test`** exist locally; `.env.example` is the
  committed reference. Never commit real env files.
- **Callsagents untracked leftovers** (pre-existing, do not commit): `.atl/`,
  `STRATEGY.md`.
