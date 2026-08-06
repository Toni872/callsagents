# Callsagents

Plataforma de outbound sales con IA. MVP en construcción siguiendo el playbook de ejecución Angular + Spring Boot.

## Stack

- **Frontend**: Angular
- **Backend**: Spring Boot
- **Base de datos**: PostgreSQL
- **Cache / Seguridad**: Redis (revocación de tokens + colas de campañas)
- **Auth**: Spring Security + JWT (access + refresh rotable)
- **Documentación**: Swagger / OpenAPI (springdoc-openapi)
- **Entorno local**: Docker Compose
- **CI/CD**: GitHub Actions / GitLab CI

## Estructura del repositorio

```
callsagents/
├── backend/         # API REST en Spring Boot
├── frontend/        # SPA en Angular
├── docs/            # Documentación del proyecto
├── PRD.md           # Producto: objetivo, alcance y restricciones
└── README.md        # Este archivo
```

## Estado del proyecto

| Fase | Descripción | Estado |
|---|---|---|
| 1 | Arquitectura | Cerrada (ver `docs/01-arquitectura.md`) |
| 2 | Modelo de datos | Cerrada |
| 3 | Autenticación | Cerrada |
| 4 | API de negocio | Cerrada |
| 5 | Frontend base | Cerrada |
| 6 | Integración auth frontend | Cerrada |
| 7 | Redis | Cubierto en F3 + verificado E2E en F9 |
| 8 | Swagger / OpenAPI | Cerrada |
| 9 | Docker local | Cerrada — **smoke test E2E OK (login + /me + CRUD contra Postgres+Redis)** |
| 10 | CI/CD | Cerrada |
| 11 | Tests | 81 tests pasando (17 nuevos de auth + 64 existentes) |

## Regla de oro

Una fase por vez. Una tarea por vez. Un cambio verificable por vez.