# RUNBOOK — Callsagents

Guía paso a paso para levantar el stack completo desde la terminal.

---

## 1. Prerrequisitos

Antes de empezar, tenés que tener instalado:

| Herramienta | Versión mínima | Cómo verificar |
|---|---|---|
| **Git** | 2.30+ | `git --version` |
| **Docker Desktop** | 4.x con Compose v2 | `docker --version` && `docker compose version` |
| **Go** (opcional) | 1.25+ | Solo si querés actualizar `gentle-ai` o `engram` |
| **Java JDK** (opcional) | 21 | Solo si querés correr el backend sin Docker |
| **Node** (opcional) | 22 | Solo si querés correr el frontend sin Docker |

Para este producto **solo necesitás Git y Docker**. El resto del stack corre dentro de los containers.

---

## 2. Setup inicial (una sola vez por máquina)

### 2.1. Clonar el repo

```bash
cd ~/Desktop   # o donde quieras tenerlo
git clone https://github.com/Toni872/callsagents.git
cd callsagents
```

### 2.2. Crear tu `.env` local

El archivo `.env` no se commitea (está en `.gitignore`). Lo creás desde el template:

```bash
cp .env.example .env
```

Luego editá `.env` y generá un JWT secret real:

```powershell
# PowerShell — genera 32 bytes aleatorios en base64
$bytes = New-Object byte[] 32
(New-Object Random).NextBytes($bytes)
[Convert]::ToBase64String($bytes)
```

Eso te da una cadena tipo `kFH9...==` que pegás en `.env`:

```
JWT_SECRET=kFH9...==
```

> ⚠️ **Importante**: el `JWT_SECRET` que viene en `.env.example` es un placeholder inseguro. Spring Boot va a loggear un warning al arrancar. En producción esto DEBE ser único y guardado en un secret manager.

> **Dependencias externas opcionales**: para probar los flujos de WhatsApp/IA/voz necesitás credenciales Vonage, Groq y Retell en `.env` (ver `.env.example`). El login/CRUD básico funciona sin ellas.

---

## 3. Levantar el stack (cada vez que quieras arrancar)

Desde la raíz del repo:

```bash
docker compose up -d
```

Esperá ~45 segundos mientras Postgres y Redis arrancan y el backend aplica las migraciones de Flyway.

### Verificar que todo está sano

```bash
docker compose ps
```

Deberías ver los 4 contenedores con estado `healthy` o `running`:

```
NAME                   STATUS                    PORTS
callsagents-postgres   Up X minutes (healthy)    0.0.0.0:5433->5432/tcp   ← Windows usa 5433
callsagents-redis      Up X minutes (healthy)    0.0.0.0:6379->6379/tcp
callsagents-backend    Up X minutes (healthy)    0.0.0.0:8080->8080/tcp
callsagents-frontend   Up X minutes              0.0.0.0:80->80/tcp
```

> **Puerto de Postgres en Windows**: `docker-compose.override.yml` mapea Postgres a **5433:5432** (porque el Postgres nativo de Windows ya ocupa 5432). Los comandos de `psql`/clientes externos deben usar **5433** si estás en Windows (ver §5).

### Smoke test rápido (verifica que la API responde)

```bash
# 1) Health-like check (Swagger UI debe devolver 200)
curl -i http://localhost:8080/api/swagger-ui/index.html | head -1

# 2) Login (debe devolver 200 + accessToken) — admin de producción (V12/V14)
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"contact@script-9.com","password":"Calls@gents2025!"}' \
  http://localhost:8080/api/auth/login

# 3) Usar el accessToken para /auth/me (debe devolver 200 con tu perfil)
ACCESS=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"contact@script-9.com","password":"Calls@gents2025!"}' \
  http://localhost:8080/api/auth/login | jq -r .accessToken)

curl -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/auth/me
```

Si los 3 devuelven `HTTP 200`, el sistema está 100% operativo.

---

## 4. URLs y credenciales

| Servicio | URL | Notas |
|---|---|---|
| **Frontend (SPA)** | `http://localhost:80` | Login con email/password |
| **Backend (API REST)** | `http://localhost:8080/api/...` | Endpoints bajo `/api/` (context path global) |
| **Swagger UI** | `http://localhost:8080/api/swagger-ui/index.html` | Documentación interactiva |
| **PostgreSQL** | Windows: `localhost:5433` · Linux: `localhost:5432` | DB: `callsagents`, user/pass: `callsagents`/`callsagents` |
| **Redis** | `localhost:6379` | Sin password, volumen persistente |

**Credenciales seed**:

| Campo | Valor | Origen |
|---|---|---|
| Email | `contact@script-9.com` | V12 (admin de producción) |
| Password | `Calls@gents2025!` | V14 |

> ⚠️ **Estas credenciales son del seed de producción** (migraciones V12/V14). El usuario seed dev (`V2__seed_admin.sql`) vive bajo `db/migration/dev` y sólo para desarrollo local. En producción NO se usa el seed dev.

---

## 5. Comandos útiles

### Ver logs en vivo

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

### Parar el stack (sin borrar datos)

```bash
docker compose down
```

Los datos en `postgres_data` y `redis_data` **persisten**.

### Reset COMPLETO (borra DB y caché)

```bash
docker compose down -v
```

⚠️ Esto borra TODOS los datos. Flyway re-aplica las migraciones al próximo `up`.

### Reconstruir imágenes (después de cambiar código)

```bash
docker compose build backend && docker compose up -d backend
docker compose build frontend && docker compose up -d frontend
docker compose build --no-cache && docker compose up -d
```

### Acceder a la DB directamente

⚠️ En Windows el puerto mapeado es **5433** (no 5432):

```bash
# Dentro del container (siempre funciona, cualquier puerto)
docker exec -it callsagents-postgres psql -U callsagents -d callsagents

# Desde un cliente externo / psql local:
#   Windows: use -p 5433
#   Linux:   use -p 5432
psql -h localhost -p 5433 -U callsagents -d callsagents
```

Comandos útiles en `psql`:

```sql
SELECT id, email, role, status, created_at FROM users;
SELECT version, description, success FROM flyway_schema_history;
\q
```

### Acceder a Redis directamente

```bash
docker exec -it callsagents-redis redis-cli
```

```redis
KEYS *
GET "refresh:<userId>:<jti>"
exit
```

### Ver uso de recursos

```bash
docker stats --no-stream
```

---

## 6. Troubleshooting

### "Welcome to nginx!" en `http://localhost/`

La imagen del frontend está cacheada con la página default:

```bash
docker compose build --no-cache frontend
docker compose up -d frontend
```

### Backend no arranca / Flyway falla

```bash
docker compose logs backend | grep -i "flyway\|error\|exception"
```

Si el error es de migración SQL: corrije el SQL y reseteá:

```bash
docker compose down -v   # ⚠️ borra datos
docker compose up -d
```

### Login devuelve 500 con "column X is of type Y but expression is of type character varying"

Bug clásico Hibernate + Postgres ENUMs (falta `@JdbcTypeCode(NAMED_ENUM)`). Si reaparece tras un cambio:

```bash
docker compose build --no-cache backend
docker compose up -d backend
```

### El browser cachea "Welcome to nginx!" aunque el container ya tiene Angular

- `Ctrl+Shift+R`, ventana incógnita, o limpiar cookies del sitio.

### Puerto 80 / 8080 ocupado

```powershell
netstat -ano | findstr :80
taskkill /PID <pid> /F
```

### Pérdida de conectividad DB

```bash
docker compose ps             # ver si postgres está healthy
docker compose logs postgres  # ver logs de postgres
```

Si está corrupto:

```bash
docker compose down -v
docker volume rm callsagents_postgres_data
docker compose up -d
```

---

## 7. Reset completo desde cero (la "bomba nuclear")

```bash
cd ~/Desktop/callsagents
docker compose down -v                    # parar y borrar volúmenes
docker system prune -a                    # borrar imágenes dangling
docker compose build --no-cache           # reconstruir todo
docker compose up -d                       # arrancar limpio
```

⚠️ Esto borra ABSOLUTamente todo: datos + imágenes cacheadas.

---

## 8. Deploy a Railway

> **REGLA DE ORO**: siempre deploy con **`npx railway up`**. **NUNCA uses `railway redeploy`** — `redeploy` re-ejecuta el código ANTIGUO de la imagen ya desplegada, no tu código nuevo.

Railway tiene **2 servicios** (backend + frontend) + Script9 separado. Commiteá tus cambios primero, luego:

```bash
# 1) Backend, desde la RAÍZ del repo (el Dockerfile raíz = backend, constraint de Railway)
npx railway up --service callsagents-backend

# 2) Frontend, desde el directorio frontend/ (usa frontend/Dockerfile)
cd frontend
npx railway up --service callsagents-frontend
```

URLs de producción (ver `INFRASTRUCTURE.md`):

| Servicio | URL |
|---|---|
| Backend (health) | https://callsagents-production.up.railway.app/api/health |
| Frontend (SaaS) | https://callsagents-frontend-production.up.railway.app |
| Script9 (marca) | https://www.script-9.com |

> **`RETELL_FROM_NUMBER` está vacío** en configuración. Las llamadas telefónicas salientes reales están **bloqueadas**; sólo funciona el web-call (WebRTC). Para habilitar teléfono, ajustá la variable en Railway.
>
> **Trial real**: 7 días / 50 leads (GLOBAL, no por tenant). La migración V10 dice "14-day" por error — no te confíes del comentario; documenta/usa el comportamiento real.

---

## 9. Próximas fases del producto

El MVP (fases 1-9) y el pivot SaaS ya están cerrados. Las pendientes del playbook (F10 CI/CD, F11 Testcontainers) quedaron **obsoletas/superadas**. Lo que sigue DE VERDAD:

| Fase | Qué es | Estado |
|---|---|---|
| **Escalation Orchestrator** | WhatsApp follow-up → timeout → Retell outbound voice (ADR-009). Primer scheduler real del código. | 🔜 próximo |
| **Producción de voz** | Número Vonage pagado + `RETELL_FROM_NUMBER` (requiere acción del usuario) | 🔜 |
| **Primer piloto / dogfood** | Captar 5 clientes con Script9 | 🔜 |
| **Stripe billing** | Facturación + trial por tenant | 🔜 |

---

## 10. Estructura del repo

```
callsagents/
├── backend/             # Spring Boot 3.5.16 (Java 21, Flyway, JWT, Spring Security)
│   ├── src/main/java/com/callsagents/backend/
│   │   ├── auth/        # Auth: JWT, guards, login/refresh/logout, Google OAuth
│   │   ├── leads/       # Leads CRUD + CSV + filtros
│   │   ├── chat/        # ChatService (Caffeine) + /chat
│   │   ├── whatsapp/    # Vonage + WhatsAppAiChatbotService + GroqService (+ Twilio legacy)
│   │   ├── voice/       # VoiceProvider (Retell/Vapi), web-call, webhooks
│   │   ├── business/    # BusinessProfile + BusinessPromptComposer + widget-config
│   │   ├── campaigns/   # LEGACY outbound
│   │   ├── calls/       # LEGACY llamadas
│   │   ├── appointments/# LEGACY citas
│   │   ├── calendar/    # LEGACY/PARCIAL (Google; Outlook stub)
│   │   ├── users/       # LEGACY gestión de usuarios
│   │   ├── dashboard/   # LEGACY /dashboard/summary
│   │   ├── integrations/# LEGACY IntegrationConfig
│   │   ├── audit/       # AuditLog
│   │   ├── common/      # Excepciones, ApiError, PaginationUtils, RateLimitFilter
│   │   └── config/      # Config beans (app.*)
│   ├── src/main/resources/
│   │   ├── application.yml       # Config (app.voice, app.vonage, app.groq)
│   │   └── db/migration/         # Flyway V1..V16 (V13 gap; V2 en db/migration/dev)
│   └── Dockerfile               # Backend (Railway root constraint)
├── frontend/            # Angular 18.2 (standalone, signals, inject())
│   ├── src/app/
│   │   ├── core/        # Auth, API, errores, layout, loading
│   │   ├── features/    # dashboard, leads, campaigns, calls, voice-calls,
│   │   │                #   appointments, users, settings, auth, onboarding, chat-widget, landing, terms, privacy, widget
│   │   └── shared/      # Models TypeScript
│   ├── nginx.conf       # Proxy /api/ + Origin-strip + COOP/COEP + SPA
│   └── Dockerfile       # Node 22 → nginx alpine
├── docs/                # Arquitectura, modelo, ADRs, handoff, railway, rdd
├── docker-compose.yml   # 4 servicios: postgres16, redis7, backend, frontend
├── docker-compose.override.yml  # Windows: postgres 5433:5432
├── .env.example         # Template de variables
├── RUNBOOK.md           # ← este archivo
└── README.md            # Índice del proyecto + estado real
```

---

## 11. Stack técnico resumido

| Capa | Tecnología |
|---|---|
| **Frontend** | Angular 18.2 standalone, TypeScript 5, CSS plano |
| **Backend** | Spring Boot 3.5.16, Java 21 |
| **DB** | PostgreSQL 16 con ENUMs nativos y JSONB |
| **Caché/Auth** | Redis 7 (revocación de refresh tokens) |
| **Auth** | JWT HS256 (access 15min + refresh 7d rotable) + BCrypt + Google OAuth |
| **Migrations** | Flyway (V1–V16; V13 gap; V2 dev) |
| **Documentación** | springdoc-openapi 2.9 (Swagger UI) |
| **Contenedores** | Docker + Docker Compose (dev) · Railway (prod) |
| **HTTP cliente** | nginx (proxy `/api/` + Origin-strip) |
| **Chatbot LLM** | Groq (`openai/gpt-oss-20b`) |
| **WhatsApp** | Vonage (sandbox dev / pagado prod) |
| **Voz** | Retell AI vía `VoiceProvider` (Retell/Vapi) |
| **Tests backend** | JUnit 5 + Mockito **(214 @Test / 22 clases)** |
| **Versionado** | Conventional commits + Git |

---

## 12. Links útiles

| Recurso | URL |
|---|---|
| Repo GitHub | https://github.com/Toni872/callsagents |
| Spring Boot docs | https://spring.io/projects/spring-boot |
| Angular docs | https://angular.dev |
| Docker Compose docs | https://docs.docker.com/compose |
| Flyway docs | https://documentation.red-gate.com/fd |
| Railway docs | https://docs.railway.com |
| nimbus JOSE+JWT | https://connect2id.com/products/nimbus-jose-jwt |
| OWASP JWT cheatsheet | https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html |
