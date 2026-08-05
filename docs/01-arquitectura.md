# Fase 1 — Arquitectura

## Proyecto

Callsagents — MVP de plataforma de outbound sales con IA.
Stack: Angular SPA · Spring Boot · PostgreSQL · Redis · JWT · Swagger · Docker Compose · CI/CD.

## 1. Alcance y objetivo del producto

Plataforma web para que un equipo comercial ejecute campañas outbound de manera sistemática. Permite cargar leads, lanzar campañas, registrar resultados de llamadas, hacer handoff a humano y agendar citas.

El MVP **no** incluye motor propio de IA de voz. La IA es un **servicio auxiliar** integrable (provider externo tipo Vapi / Retell / Bland) que se enchufa cuando hay un caso de uso confirmado. El MVP valida **flujo de negocio**, no calidad máxima del modelo.

## 2. Módulos del backend y responsabilidad

| Módulo | Responsabilidad |
|---|---|
| **Auth** | Login, refresh, logout, roles. JWT + Redis para revocación. |
| **Leads** | CRUD de leads, importación masiva, filtros, segmentación. |
| **Campañas** | Crear, lanzar, pausar, monitorear campañas. |
| **Llamadas** | Registro de llamadas, estados (contestada/buzón/no responde), duración, outcome, grabación (URL), notas. |
| **Asignaciones** | Asignar leads a operadores, colas de trabajo. |
| **Citas** | Agendar, confirmar, cancelar citas. |
| **Integraciones** | Conector con provider de voz externo (Vapi/Retell) vía API + webhooks. |
| **Reportes** | Métricas básicas por campaña y por operador. |

## 3. Entidades principales (nombradas, NO modeladas todavía — eso es Fase 2)

`User` · `Role` · `Lead` · `Campaign` · `CampaignLead` · `Call` · `CallOutcome` · `Appointment` · `IntegrationConfig` · `AuditLog`

## 4. Endpoints principales (esbozo de alto nivel)

```
POST   /api/auth/login
POST   /api/auth/refresh
POST   /api/auth/logout

GET    /api/leads               (filtros + paginación)
POST   /api/leads               (carga individual)
POST   /api/leads/import        (carga masiva CSV)

GET    /api/campaigns
POST   /api/campaigns
POST   /api/campaigns/{id}/launch
POST   /api/campaigns/{id}/pause

GET    /api/calls
POST   /api/calls               (registrar resultado manual)
GET    /api/calls/{id}

POST   /api/appointments

POST   /api/integrations/voice  (webhook entrante del provider)
```

## 5. Roles y permisos (alto nivel)

| Rol | Permisos |
|---|---|
| **ADMIN** | Todo: usuarios, campañas, integraciones, reportes globales. |
| **SUPERVISOR** | Ver todas las campañas y operadores de su equipo, reportes. |
| **AGENT** | Ver SOLO sus leads asignados, registrar llamadas, agendar citas. |

## 6. Redis: dónde y por qué (justificación caso por caso)

| Uso | Caso | TTL |
|---|---|---|
| **Revocación de refresh tokens** | F3 — confirmado | = expiración refresh token |
| **Colas de campañas** (jobs pendientes de llamada) | Orquestación de lanzamiento | configurable |
| **Rate limit / throttling de endpoints** | Protección básica | ventana móvil |
| ~~Caché de consultas~~ | ~~NO todavía~~ | No hay caso confirmado en MVP |

## 7. Estrategia de entorno local y despliegue

- **Local**: Docker Compose con 4 servicios (`postgres`, `redis`, `backend`, `frontend`).
- **Staging/Prod**: fuera del alcance del MVP — se decide cuando exista staging validado (Fase 10).
- **CI/CD**: GitHub Actions (decisión por defecto, ajustable a GitLab CI si el repo lo requiere).
- **Secretes**: variables de entorno, **nunca** en código ni en compose commiteado.

## 8. Estructura de carpetas (acordada)

```
callsagents/
├── backend/         # Spring Boot (Java)
├── frontend/        # Angular
├── docs/            # Documentación por fase
├── PRD.md
└── README.md
```

Monorepo simple. Sin Nx, sin Turborepo. Si crece (equipos separados), se separan después.

## Lo que NO definimos todavía (queda para su fase)

- **Modelo de datos final** — Fase 2.
- **Estructura de paquetes interna de Spring Boot** — sale de las convenciones estándar + Clean Architecture por módulo (se arma al escribir código).
- **Provider de voz concreto** (Vapi / Retell / Bland) — se evalúa cuando integremos (Fase 4).