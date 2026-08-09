# Handoff Guide — Callsagents

> **Read this first if you're接手 this project.** It answers: what is this, how do I run it, where do I make changes, and what's already done vs. what's missing.

---

## 1. What is this

Callsagents is an **outbound sales platform** (à la Calligence) built for one client as an MVP demo. The product helps a sales team run outbound calling campaigns: manage leads, run campaigns, log calls, schedule appointments, and (planned) integrate with voice AI providers for automated calls.

**This is NOT production-ready.** It's a working MVP that runs locally via Docker. Several features (calendar sync, voice AI, deploy, observability) are scaffolded but not production-hardened.

## 2. Stack (frozen — don't change without team discussion)

| Layer | Technology | Version |
|---|---|---|
| Frontend | Angular standalone components, signals, inject() | 18.2 |
| Backend | Spring Boot, Java | 3.5.16, 21 |
| DB | PostgreSQL with native ENUMs + JSONB | 16 |
| Cache + Auth state | Redis | 7 |
| Auth | JWT (HS256, access 15min / refresh 7d rotatable) + BCrypt | nimbus-jose-jwt 10.0.2 |
| ORM | Hibernate with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` for Postgres ENUMs | 6.x |
| Migrations | Flyway (V1–V5 so far) | 11.7.2 |
| Docs | springdoc-openapi (Swagger UI) | 2.9.0 |
| Containerization | Docker Compose (4 services) | Docker 29+ |
| CI | GitHub Actions (backend + frontend workflows) | n/a |

**Read the playbook if you haven't**: `C:\Users\Antonio\Desktop\Callsagents\RUNBOOK.md` covers operational how-to. This document is the developer onboarding complement.

## 3. Prerequisites for your dev machine

| Tool | Required version | Check |
|---|---|---|
| Docker Desktop | 4.x with Compose v2 | `docker --version && docker compose version` |
| Git | 2.30+ | `git --version` |
| Java JDK | 21 (Temurin recommended) | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Node | 22 LTS | `node --version` |
| npm | 10+ | `npm --version` |
| Go | 1.25+ (only for `gentle-ai` tooling) | `go version` |

## 4. First-time setup

```bash
git clone https://github.com/Toni872/callsagents.git
cd callsagents

# Copy and edit .env
cp .env.example .env
# Edit .env: set JWT_SECRET and ENCRYPTION_KEY (use openssl rand -base64 32 for both)

# Start the stack
docker compose up -d
```

Wait ~60s for the backend to apply Flyway migrations and start. Check health:

```bash
docker compose ps
# All 4 should show 'healthy' (frontend may show 'Up' without healthcheck)

# Smoke test
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@callsagents.local","password":"admin123"}' \
  http://localhost:8080/api/auth/login
```

Open in browser: `http://localhost/`. Login with `admin@callsagents.local` / `admin123`.

## 5. Repository structure

```
callsagents/
├── RUNBOOK.md                          # Operational how-to (start/stop/troubleshoot)
├── docs/                               # Architecture + per-phase notes
│   ├── 00-handoff.md                   # ← you are here
│   ├── 01-arquitectura.md              # Phase 1 design
│   ├── 02-modelo-de-datos.md           # Phase 2 design (entities)
│   ├── rdd-workflow.md                 # How to use the gentle-ai RDD gate
│   ├── 03-fase-3-auth.md               # ← TODO: backfill
│   ├── 04-fase-4-api.md                # ← TODO
│   ├── ...                              # 12, 13, 14, 15 per phase
│   └── 99-known-issues.md              # Bugs, workarounds, known sharp edges
├── backend/                            # Spring Boot app
│   ├── src/main/java/com/callsagents/backend/
│   │   ├── auth/                       # JWT, security, login/refresh/logout
│   │   ├── calendar/                   # Google/Outlook sync (Fase 14)
│   │   ├── calls/                      # Call logging (Fase 4)
│   │   ├── campaigns/                  # Campaigns + CampaignLead link table
│   │   ├── leads/                      # Lead CRUD (Fase 4)
│   │   ├── appointments/               # Appointments (Fase 4 + calendar sync hook)
│   │   ├── users/                      # User management (Fase 12)
│   │   ├── dashboard/                  # Executive dashboard (Fase 13)
│   │   ├── common/                     # DTOs, exceptions, audit
│   │   ├── audit/                      # AuditLog entity
│   │   ├── integrations/               # IntegrationConfig entity
│   │   └── Application.java            # Spring Boot entrypoint
│   ├── src/main/resources/
│   │   ├── application.yml             # Common config
│   │   ├── application-dev.yml         # Dev profile (uses local DB)
│   │   ├── application-test.yml        # Test profile
│   │   └── db/migration/               # Flyway migrations V1–V5
│   │       ├── V1__initial_schema.sql
│   │       ├── V2__seed_admin.sql
│   │       ├── V3__campaign_leads_audit_timestamps.sql
│   │       ├── V4__calendar_integrations.sql
│   │       └── V5__appointment_external_sync.sql
│   ├── src/test/                       # 100 unit tests
│   └── Dockerfile                      # Multi-stage Maven → Temurin JRE
├── frontend/                           # Angular app
│   ├── src/app/
│   │   ├── core/
│   │   │   ├── api/                    # HTTP services (Lead, Campaign, Auth, etc.)
│   │   │   ├── auth/                   # Auth service, interceptors, guards
│   │   │   ├── errors/                 # Error service, toast host
│   │   │   ├── layout/                 # MainLayoutComponent (sidebar + header)
│   │   │   └── loading/                # Loading service + interceptor
│   │   ├── features/                   # Lazy-loaded feature modules
│   │   │   ├── dashboard/
│   │   │   ├── auth/login/
│   │   │   ├── leads/{list,detail,form}/
│   │   │   ├── campaigns/{list,detail,form}/
│   │   │   ├── calls/{list,detail,form}/
│   │   │   ├── appointments/{list,detail,form}/
│   │   │   ├── users/                  # User management (Fase 12)
│   │   │   └── settings/calendar/      # Calendar settings (Fase 14)
│   │   ├── shared/models/              # TypeScript interfaces matching backend DTOs
│   │   ├── app.config.ts               # Interceptors + APP_INITIALIZER
│   │   ├── app.routes.ts               # Top-level routing (lazy loads)
│   │   └── app.component.ts            # <router-outlet>
│   ├── nginx.conf                      # Reverse proxy + SPA fallback
│   └── Dockerfile                      # Multi-stage Node 22 → nginx
├── docker-compose.yml                  # 4 services + volumes
└── .env.example                        # Template for .env
```

## 6. Conventions (must follow)

### Java / Spring Boot

- **Standalone, single-class-per-file** style throughout
- **Lombok** for entities (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`)
- **Records** for DTOs (Java 16+)
- **Lombok `@Builder` with `@AllArgsConstructor`** for entities
- **ENUMs**: `@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)` — this combo is REQUIRED because we use Postgres native ENUMs
- **Audit fields**: every entity has `created_at` (not nullable, `@PrePersist`) and `updated_at` (`@PreUpdate`)
- **Soft delete**: NO. We hard delete + AuditLog. If you need soft, add a `@Where(clause="deleted=false")` pattern.
- **UUIDs as PKs** everywhere, generated by `@UuidGenerator`
- **Controllers**: thin — only inject `Authentication` (not `@AuthenticationPrincipal UserDetails`) and call services
- **Services**: contain all logic; controllers must NOT have business logic
- **DTOs**: ALWAYS use DTOs at controller boundary. Never expose entities directly (e.g. `Lead` has internal `assignedTo UUID`; `LeadResponse` should hide it unless needed)
- **Tests**: Mockito + JUnit 5. Test services with `@ExtendWith(MockitoExtension.class)` and pure mocks. Avoid `@SpringBootTest` (it needs a DB and is slow); use `@WebMvcTest` only if you need controller wiring.

### Frontend (Angular)

- **Standalone components** (no NgModules)
- **`ChangeDetectionStrategy.OnPush`** on every component
- **Signals** for component state (`signal`, `computed`)
- **`inject()`** for DI (not constructor injection)
- **Templates use the new control flow**: `@if`, `@for`, `@switch` (no `*ngIf`, `*ngFor`)
- **Native `<dialog>` HTML5** for modals (no Material/Angular CDK)
- **Services in `core/api/`** one per backend module; never inject `HttpClient` directly in a component
- **Models in `shared/models/`** as TypeScript interfaces matching backend DTOs
- **Lazy load** feature modules: `loadComponent: () => import('...')`
- **CSS**: use CSS variables from `src/styles.css` (`--color-primary`, `--spacing-4`, etc.). No Tailwind, no Material.

### Git

- **Conventional commits**: `feat(scope): description`, `fix(scope): description`, `docs:`, `chore:`
- **One commit per logical change** — don't bundle 3 phases into one commit
- **Commit messages** include a body explaining WHY (not just what)
- **Branch from `main`**, PR into `main`. CI runs on every PR.

### Receipt-Driven Development (RDD)

RDD is enabled on this repo. Workflow per commit:

```bash
# 1. Edit files
# 2. Start a review (freezes the candidate + generates a receipt)
gentle-ai review start --projection workspace --cwd "C:\Users\Antonio\Desktop\Callsagents"
# 3. Commit
git add .
git commit -m "..."
# 4. Push (delivery gate validates the receipt)
git push origin main
```

Read `docs/rdd-workflow.md` for full details. If the push fails with "candidate has drifted", you modified files after `review start` — abort and re-start.

## 7. Where to add what

| You want to... | Touch this | Be careful |
|---|---|---|
| Add a new REST endpoint | `backend/.../{module}/controller/`, service, dto | Update `pom.xml` only if you need new deps. Add `@PreAuthorize` to controller. Add `@Schema` to DTO for Swagger. |
| Add a field to an existing entity | `backend/.../{module}/entity/Entity.java` + new `V{n}__...sql` migration | DO NOT edit the V1–V5 migrations. Always add a new migration. If the field is NOT NULL, set a sensible DEFAULT in the SQL. |
| Add a new ENUM value | New `V{n}__...sql`: `ALTER TYPE foo ADD VALUE 'BAR';` | Postgres ENUMs are immutable. `ALTER TYPE` is the only way. Restart the backend after. |
| Add a new role beyond ADMIN/SUPERVISOR/AGENT | Entity `UserRole` + `SecurityConfig` + tests | High-impact change. The auth flow uses these roles in @PreAuthorize across the codebase. |
| Add a new nav item in the sidebar | `core/layout/main-layout/main-layout.component.ts` (`navItems` array) | Add a corresponding route in `app.routes.ts`. |
| Add a new page/feature | `features/{feature}/` with `*.routes.ts` and a component | Lazy-load the route. Follow the existing `users/` or `appointments/` layout. |
| Add a new migration | `backend/src/main/resources/db/migration/V{n}__description.sql` | Follow the V{n}__snake_case.sql naming. The number must be sequential (V1 → V2 → ...). |
| Change the JWT secret | `application.yml` (via `app.jwt.secret`) and `.env` (`JWT_SECRET`) | The secret must be ≥32 bytes / 256 bits. Generate with `openssl rand -base64 32`. |
| Add OAuth provider (Google/Outlook) | `backend/.../calendar/service/{Provider}.java` | Implement the `CalendarProvider` interface. See `GoogleCalendarProvider` for reference. |

## 8. Tests

- **100 unit tests** currently passing. Run with `mvn test` from `backend/`.
- Test naming: `{MethodName}_when{State}_then{Expected}` (e.g. `login_invalidCredentials_throwsBadCredentials`)
- Always test: happy path, validation failure, edge cases (null/blank/empty), security (forbidden/forbidden role)
- See `CalendarSyncServiceTest` for the most recent pattern: `@ExtendWith(MockitoExtension.class)` + mocks + `@MockitoSettings(strictness = Strictness.LENIENT)` for stubs that may not be invoked on every test path.

## 9. State of the project (what's done vs missing)

| Phase | What | Status |
|---|---|---|
| 1 | Architecture (modules, contracts) | ✅ done |
| 2 | Data model (8 entities, 5 migrations) | ✅ done |
| 3 | Auth backend (JWT + Spring Security) | ✅ done |
| 4 | CRUD API (21 endpoints across 5 modules) | ✅ done |
| 5 | Frontend base (Angular shell, layouts, API services) | ✅ done |
| 6 | Auth frontend (interceptors, guards, login UI) | ✅ done |
| 7 | Redis (token revocation) | ✅ done |
| 8 | Swagger/OpenAPI | ✅ done |
| 9 | Docker Compose + E2E smoke test verified | ✅ done |
| 10 | CI/CD (GitHub Actions: backend + frontend) | ✅ done |
| 11 | Unit tests (100 passing) | ✅ done |
| 12 | User management (CRUD + frontend UI) | ✅ done |
| 13 | Executive dashboard with real metrics | ✅ done |
| 14 | Calendar sync backend (Google/Outlook) | ✅ done |
| 15 | **Voice AI integration (Vapi/Retell)** | 🔄 next |
| - | Calendar sync frontend UI | ✅ done in F14 |
| - | Deploy to staging (Render/Fly.io) | ❌ not done — not in playbook, deferred until product is validated |
| - | Testcontainers (Postgres+Redis real in CI) | ❌ not done — low priority |
| - | Observability (logs centralization, metrics) | ❌ not done — deferred until traffic |
| - | Production security hardening (secret manager, rate limit) | ❌ not done — deferred until deploy |

## 10. Known issues and sharp edges

1. **CampaignLead entity was missing `created_at`/`updated_at` initially** — fixed in F13 with V3 migration + entity update. Don't recreate that bug.
2. **Hibernate + Postgres ENUMs**: ALWAYS use `@Enumerated(STRING) + @JdbcTypeCode(NAMED_ENUM)`. Without NAMED_ENUM, Hibernate sends VARCHAR for ENUM columns and Postgres rejects with "column X is of type Y but expression is of type character varying".
3. **AuthController `/me` endpoint must use `Authentication.getName()`** — NOT `@AuthenticationPrincipal UserDetails`. The `JwtAuthenticationFilter` sets the principal as a String, not a UserDetails. Using `@AuthenticationPrincipal UserDetails` returns null.
4. **EncryptionService is MVP-tolerant**: if `ENCRYPTION_KEY` is empty, the bean still instantiates with `ready=false` so the rest of the app boots. Encrypt/decrypt throw at runtime. Calendar endpoints return 503.
5. **Console 401 noise**: `GET /api/auth/me` fires on `MainLayoutComponent` mount even when not logged in. This produces 401 errors in the browser console. Cosmetic only.
6. **`/api/admin/seed-demo-data` is idempotent** — safe to call multiple times. Returns `seeded: false` if data already exists.
7. **No soft delete**. Hard delete + AuditLog. If you need to undelete, restore from AuditLog.
8. **Outbound sync to Google is one-way**. Calendar → Callsagents (webhook-based) is NOT implemented. The `external_event_id` is stored but never used for updates or deletes from external events.

## 11. Glossary

- **Fase/Phase**: A self-contained vertical slice of the product (F1–F15)
- **Candidate**: The set of changes the developer is about to commit, frozen for review
- **Receipt**: SHA-256 hash of the candidate. RDD validates the commit against this.
- **Provider**: External SaaS integration (Google, Outlook, Vapi, Retell)
- **Token storage**: localStorage in the Angular frontend
- **Refresh rotation**: Each `/auth/refresh` issues a new refresh token AND revokes the old one (in Redis). Reuse detection revokes the entire session.
- **Migrations**: Numbered SQL files in `db/migration/` that Flyway applies in order. NEVER edit a shipped migration; add a new one.

## 12. Roadmap (next)

The user's priority order, in professional judgment:

1. **Voice AI integration** (Fase 15) — Vapi or Retell. The differentiator vs Calligence.
2. **Local validation end-to-end** — test the whole product with real credentials (Google calendar, voice AI) before thinking about deploy.
3. **Deploy to staging** (Render / Fly.io) — only when the product is validated. Not in the original playbook.
4. **Testcontainers** in CI — real Postgres + Redis in integration tests. After deploy.
5. **Observability** — only with traffic.
6. **Production security hardening** — only with deploy.

---

**If you get stuck**: read RUNBOOK.md (operational), this file (developer onboarding), and the per-phase docs in `docs/`. When in doubt, follow the existing patterns in nearby code (especially `users/` and `appointments/` — they're the cleanest examples).
