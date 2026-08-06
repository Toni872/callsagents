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

Para este MVP **solo necesitás Git y Docker**. El resto del stack corre dentro de los containers.

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
callsagents-postgres   Up X minutes (healthy)    0.0.0.0:5432->5432/tcp
callsagents-redis      Up X minutes (healthy)    0.0.0.0:6379->6379/tcp
callsagents-backend    Up X minutes (healthy)    0.0.0.0:8080->8080/tcp
callsagents-frontend   Up X minutes              0.0.0.0:80->80/tcp
```

### Smoke test rápido (verifica que la API responde)

```bash
# 1) Health-like check (Swagger UI debe devolver 200)
curl -i http://localhost:8080/api/swagger-ui/index.html | head -1

# 2) Login (debe devolver 200 + accessToken)
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@callsagents.local","password":"admin123"}' \
  http://localhost:8080/api/auth/login

# 3) Usar el accessToken para /auth/me (debe devolver 200 con tu perfil)
ACCESS=$(curl -s -X POST -H "Content-Type: application/json" \
  -d '{"email":"admin@callsagents.local","password":"admin123"}' \
  http://localhost:8080/api/auth/login | jq -r .accessToken)

curl -H "Authorization: Bearer $ACCESS" http://localhost:8080/api/auth/me
```

Si los 3 devuelven `HTTP 200`, el sistema está 100% operativo.

---

## 4. URLs y credenciales

| Servicio | URL | Notas |
|---|---|---|
| **Frontend (SPA)** | `http://localhost:80` | Login con email/password |
| **Backend (API REST)** | `http://localhost:8080/api/...` | 25 endpoints bajo `/api/` |
| **Swagger UI** | `http://localhost:8080/api/swagger-ui/index.html` | Documentación interactiva con botón "Authorize" |
| **PostgreSQL** | `localhost:5432` | DB: `callsagents`, user/pass: `callsagents`/`callsagents` |
| **Redis** | `localhost:6379` | Sin password, volumen persistente |

**Credenciales seed (DEV ONLY)**:

| Campo | Valor |
|---|---|
| Email | `admin@callsagents.local` |
| Password | `admin123` |
| Rol | `ADMIN` |

> ⚠️ **Estas credenciales están en `V2__seed_admin.sql` y son sólo para desarrollo local**. En producción se eliminan y los admins se crean por otro flujo.

---

## 5. Comandos útiles

### Ver logs en vivo

```bash
# Todos los servicios
docker compose logs -f

# Solo el backend
docker compose logs -f backend

# Solo el frontend (nginx)
docker compose logs -f frontend

# Solo la DB
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

⚠️ Esto borra TODOS los datos. Tenés que volver a aplicar las migraciones (Flyway las aplica solo al próximo `up`).

### Reconstruir imágenes (después de cambiar código)

```bash
# Solo el backend (cuando cambiás Java/Spring)
docker compose build backend
docker compose up -d backend

# Solo el frontend (cuando cambiás Angular)
docker compose build frontend
docker compose up -d frontend

# Todo desde cero (sin cache de Docker)
docker compose build --no-cache
docker compose up -d
```

### Acceder a la DB directamente

```bash
docker exec -it callsagents-postgres psql -U callsagents -d callsagents
```

Comandos útiles en `psql`:

```sql
-- Ver todos los usuarios
SELECT id, email, role, status, created_at FROM users;

-- Ver la última migración aplicada
SELECT version, description, success FROM flyway_schema_history;

-- Salir
\q
```

### Acceder a Redis directamente

```bash
docker exec -it callsagents-redis redis-cli
```

Comandos útiles:

```redis
-- Ver todas las claves
KEYS *

-- Ver un refresh token guardado
GET "refresh:<userId>:<jti>"

-- Salir
exit
```

### Ver uso de recursos

```bash
docker stats --no-stream
```

---

## 6. Troubleshooting

### "Welcome to nginx!" en `http://localhost/`

Significa que la imagen del frontend está cacheada con la página default. Solución:

```bash
docker compose build --no-cache frontend
docker compose up -d frontend
```

### Backend no arranca / Flyway falla

```bash
docker compose logs backend | grep -i "flyway\|error\|exception"
```

Si ves un error de migración SQL, el problema es del SQL. Borrá la migración problemática y volve a levantar:

```bash
docker compose down -v   # ⚠️ borra datos
docker compose up -d
```

### Login devuelve 500 con "column X is of type Y but expression is of type character varying"

Es el bug clásico de Hibernate + Postgres ENUMs. Aplicado el fix ya está. Si vuelve a aparecer tras un cambio, ejecutá:

```bash
docker compose build --no-cache backend
docker compose up -d backend
```

### El browser cachea "Welcome to nginx!" aunque el container ya tiene Angular

El cache de nginx o del browser. Probá:

- `Ctrl+Shift+R` (recarga sin caché)
- Ventana incógnita
- Limpiar cookies del sitio

### Puerto 80 o 8080 ocupado

Si ya tenés algo corriendo en esos puertos, paralo antes. Por ejemplo, en Windows:

```powershell
# Ver qué usa el puerto 80
netstat -ano | findstr :80
# Parar el proceso (reemplazar PID)
taskkill /PID <pid> /F
```

### Pérdida de conectividad DB

```bash
docker compose ps             # ver si postgres está healthy
docker compose logs postgres  # ver logs de postgres
```

Si está corrupto, reset completo:

```bash
docker compose down -v
docker volume rm callsagents_postgres_data
docker compose up -d
```

---

## 7. Reset completo desde cero (la "bomba nuclear")

Si algo está MUY roto y querés arrancar de cero:

```bash
cd ~/Desktop/callsagents
docker compose down -v                    # parar y borrar volúmenes
docker system prune -a                    # borrar imágenes dangling
docker compose build --no-cache           # reconstruir todo
docker compose up -d                       # arrancar limpio
```

⚠️ Esto borra ABSOLUTAMENTE todo: datos, imágenes cacheadas. Es 5 minutos.

---

## 8. Próximas fases del playbook

El MVP (Fases 1-9) está cerrado. Quedan dos pendientes:

| Fase | Esfuerzo | Qué es |
|---|---|---|
| **F10 CI/CD** | ~20 min | GitHub Actions: lint + test + build |
| **F11 Tests** | ~45 min | Suite de flujos críticos con Testcontainers (Postgres+Redis reales) |

Ambos se cierran cuando se decida seguir el playbook completo.

---

## 9. Estructura del repo

```
callsagents/
├── backend/             # Spring Boot 3.5.16 (Java 21, Flyway, JWT, Spring Security)
│   ├── src/main/java/com/callsagents/backend/
│   │   ├── auth/        # Auth: JWT, guards, controllers
│   │   ├── leads/       # Módulo Leads
│   │   ├── campaigns/   # Módulo Campañas
│   │   ├── calls/       # Módulo Llamadas
│   │   ├── appointments/# Módulo Citas
│   │   ├── integrations/ # Configuración de providers externos
│   │   ├── audit/       # AuditLog
│   │   └── common/      # Excepciones, audit service, DTOs comunes
│   ├── src/main/resources/
│   │   ├── application.yml       # Profiles: default, dev, test
│   │   └── db/migration/         # Flyway: V1 inicial + V2 seed admin
│   └── Dockerfile               # Multi-stage: Maven → Temurin JRE
├── frontend/            # Angular 18.2 (standalone, signals, inject())
│   ├── src/app/
│   │   ├── core/        # Auth, API, errores, layout, loading
│   │   ├── features/    # Dashboard, auth, leads, campaigns, calls, appointments
│   │   └── shared/      # Models TypeScript
│   ├── nginx.conf       # Proxy /api/ → backend:8080 + SPA routing
│   └── Dockerfile       # Multi-stage: Node 22 → nginx alpine
├── docs/                # Documentación por fase
│   ├── 01-arquitectura.md
│   └── 02-modelo-de-datos.md
├── docker-compose.yml   # Orquestación de los 4 servicios
├── .env.example         # Template de variables de entorno
├── RUNBOOK.md           # ← este archivo
└── README.md            # Estado de fases y descripción del proyecto
```

---

## 10. Stack técnico resumido

| Capa | Tecnología |
|---|---|
| **Frontend** | Angular 18.2 standalone, TypeScript 5, CSS plano |
| **Backend** | Spring Boot 3.5.16, Java 21 |
| **DB** | PostgreSQL 16 con ENUMs nativos y JSONB |
| **Caché/Auth** | Redis 7 (revocación de refresh tokens) |
| **Auth** | JWT HS256 (access 15min + refresh 7d rotable) + BCrypt |
| **Migrations** | Flyway |
| **Documentación** | springdoc-openapi 2.9 (Swagger UI) |
| **Contenedores** | Docker + Docker Compose |
| **HTTP cliente** | nginx (proxy reverse para `/api/`) |
| **Tests backend** | JUnit 5 + Mockito (64 unitarios) |
| **Versionado** | Conventional commits + Git |

---

## 11. Links útiles

| Recurso | URL |
|---|---|
| Repo GitHub | https://github.com/Toni872/callsagents |
| Spring Boot docs | https://spring.io/projects/spring-boot |
| Angular docs | https://angular.dev |
| Docker Compose docs | https://docs.docker.com/compose |
| Flyway docs | https://documentation.red-gate.com/fd |
| nimbus JOSE+JWT | https://connect2id.com/products/nimbus-jose-jwt |
| OWASP JWT cheatsheet | https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html |
