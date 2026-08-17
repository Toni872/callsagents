# Railway Deploy Guide — Callsagents

This is the step-by-step playbook for deploying Callsagents to Railway. Once done, every push to `main` triggers auto-deploy via GitHub.

## Prerequisites (one-time)

1. **GitHub account connected to Railway** (Settings → Integrations)
2. **A Railway project** (we use `renewed-reverence`)
3. **Postgres + Redis plugins** added to the project (already done in `renewed-reverence`)

## Why 4 services?

The system needs:
- `Postgres` (managed) → DB for the backend
- `Redis` (managed) → JWT refresh tokens + session state
- `callsagents-backend` (Dockerfile) → Spring Boot API
- `callsagents-frontend` (Dockerfile) → Angular SPA + nginx reverse proxy

The backend Dockerfile **must live at the repo ROOT** (Railway looks for `Dockerfile` at the root by default). The frontend Dockerfile lives at `frontend/Dockerfile` and Railway uses a custom `dockerfilePath`.

## Step-by-step setup (in Railway dashboard)

### 1. Add the backend service

1. Go to https://railway.app/project/`renewed-reverence`
2. Click **+ New** → **GitHub Repo** → select `Toni872/callsagents`
3. Name it `callsagents-backend`
4. Railway will detect the root `Dockerfile` and start building (first build will FAIL — expected, no env vars yet)
5. After build fails, go to **Variables** and add:

```bash
JWT_SECRET=<openssl rand -base64 32>
ENCRYPTION_KEY=<openssl rand -base64 32>
APP_CALENDAR_GOOGLE_CLIENT_ID=<your-google-client-id>
APP_CALENDAR_GOOGLE_CLIENT_SECRET=<your-google-client-secret>
GOOGLE_REDIRECT_URI=https://<your-backend-domain>/api/calendar/oauth/callback/google
VAPI_API_KEY=<your-vapi-api-key>
VAPI_ASSISTANT_ID=<your-vapi-assistant-id>
VAPI_PHONE_NUMBER_ID=<your-vapi-phone-id>
```

6. **Reference variables** from Postgres and Redis plugins (Railway exposes them automatically, but you may need to add explicit references if not auto-injected):
   - `SPRING_DATASOURCE_URL=jdbc:${Postgres.DATABASE_URL}` (use Railway's variable reference syntax)
   - `SPRING_DATASOURCE_USERNAME=${Postgres.PGUSER}`
   - `SPRING_DATASOURCE_PASSWORD=${Postgres.PGPASSWORD}`
   - `SPRING_DATA_REDIS_URL=${Redis.REDIS_URL}`

7. **Deploy** (click the deploy button). The backend should now start successfully, run Flyway V1–V7 migrations, and bind to port 8080.

8. **Generate a public domain** for the backend:
   - In the backend service settings, click **Generate Domain**
   - This gives you a URL like `callsagents-backend-production.up.railway.app`
   - **Save this URL** — you need it for the frontend

### 2. Add the frontend service

1. In the same project, click **+ New** → **GitHub Repo** → select `Toni872/callsagents` again
2. Name it `callsagents-frontend`
3. **Important**: in Settings → Build, change the **Dockerfile Path** to `frontend/Dockerfile` (NOT the default root)
4. Add the variable:
   - `BACKEND_HOST` = the public domain you generated in step 8 above (without `https://` — just the hostname)
   - `BACKEND_PORT` = `443` (since Railway terminates TLS on the public domain)
5. Deploy
6. **Generate a public domain** for the frontend
7. This is the URL you share with users

### 3. Verify

Open the frontend URL in your browser. You should see the login page. Sign in with:
- email: `admin@callsagents.local`
- password: `admin123`

**Important**: in step 2, the backend hasn't run the V2 migration yet (which seeds the admin user). Wait for the first deploy to finish successfully, then either:
- Manually run a SQL query to insert the admin user, OR
- The V2 migration will run automatically and create the user

If the V2 migration doesn't run because Flyway needs the schema first, run this SQL via the Postgres plugin's "Query" tab:

```sql
SELECT email FROM users;
-- if empty, the V2 migration needs to be applied manually:
INSERT INTO users (id, email, password_hash, full_name, role, status, last_login_at, created_at, updated_at)
VALUES (gen_random_uuid(), 'admin@callsagents.local', '$2b$10$aIOArdvrDwK6ba/xsGqCRu0Di6DId49C6IaZYu4Cy.Gx5EIY3sbTy', 'Admin', 'ADMIN', 'ACTIVE', NULL, NOW(), NOW())
ON CONFLICT (email) DO NOTHING;
```

## How it works (architecture)

```
Internet → Railway edge (TLS) → callsagents-frontend (nginx :80)
                                    ↓ proxy /api/* via $BACKEND_HOST
                                    ↓
                                  callsagents-backend (Spring Boot :8080)
                                    ↓ JDBC
                                  Postgres (DATABASE_URL injected)
                                    ↓ Lettuce
                                  Redis (REDIS_URL injected)
```

## Auto-deploy from GitHub

Once the services are configured:
- Every push to `main` triggers a rebuild of both backend AND frontend
- The GitHub Actions CI (`.github/workflows/backend-ci.yml` and `frontend-ci.yml`) runs tests on PRs
- Production deploys to Railway run on every push to `main` (no manual trigger needed)
- Failed builds don't disrupt running services (Railway keeps the previous working deployment until the new one succeeds)

## Common issues and fixes

### "Failed to determine a suitable driver class" in backend logs
The Postgres plugin's `DATABASE_URL` isn't reaching the backend. Make sure:
- You added the reference variables (`SPRING_DATASOURCE_URL=jdbc:${Postgres.DATABASE_URL}` etc.) in the backend service Variables
- The Postgres plugin is in the same environment (production) as the backend service

### Frontend shows "Welcome to nginx!" instead of Callsagents
The frontend container was built with an old image. The COPY path in `frontend/Dockerfile` is correct for Angular 17+ (`dist/<project>/browser/`). Trigger a manual rebuild:
- In the frontend service → Deployments → click ⋯ on the latest → Redeploy

### Login works but dashboard shows 0 leads
The admin seed user wasn't created. Check `SELECT * FROM users;` in the Postgres plugin. If empty, the V2 migration didn't run. The most likely cause: backend tried to start BEFORE the database was reachable, so Flyway didn't apply. Redeploy the backend after Postgres is healthy.

### "JWT_SECRET is NOT configured" warning in logs
The `JWT_SECRET` variable is missing. Generate one with `openssl rand -base64 32` and set it in the backend service Variables. Restart by redeploying.

## Cost estimation (Railway free tier)

- Postgres (256MB-1GB): included in free tier with limits
- Redis: included in free tier with limits
- Backend (1 vCPU, 512MB-1GB): ~$5/month after free tier
- Frontend (1 vCPU, 512MB): ~$5/month after free tier

For development/demo purposes, Railway's free tier should cover it. For production with real traffic, budget ~$15-20/month for both app services.

## Cleanup if you need to start over

```bash
# List services
railway service list

# Delete a service
railway service rm --service <name> --yes
```

To delete the entire project: Settings → Danger → Delete Project.
