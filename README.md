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
| 5 | Frontend base | Pendiente |
| 6 | Integración auth frontend | Pendiente |
| 7 | Redis | Parcial (entra en F3) |
| 8 | Swagger / OpenAPI | Pendiente |
| 9 | Docker local | Pendiente |
| 10 | CI/CD | Pendiente |
| 11 | Tests | Parcial (unit de JwtService en F3) |

## Regla de oro

Una fase por vez. Una tarea por vez. Un cambio verificable por vez.