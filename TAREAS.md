# TAREAS.md — Orquestación del trabajo

Tablero de tareas del equipo de agentes. El orquestador lo mantiene al día; cada agente solo marca
sus propias tareas. Contexto del proyecto en `CLAUDE.md`.

## 1. Roles

| Rol | Quién | Responsabilidad | Rama / worktree |
|---|---|---|---|
| Orquestador | Sesión principal | Resolver decisiones con el usuario, mantener la **fuente única del modelo de datos** (`docs/estructura-datos/modelo-datos.md`), asignar tareas, revisar e integrar ramas | `develop` (raíz del repo) |
| Redactor | Agente `redactor` | Elaborar el modelo de datos (`modelo-datos.md`) y el entregable `.docx` con formato del curso | `docs/estructura-datos` → `../wt-redactor` |
| Backend · base | Agente `back-base` | Proyecto Spring Boot (Java 25, Maven), módulo planificador, configuración común | `feature/backend-base` → `../wt-back-base` |
| Backend · persistencia | Agente `back-datos` | DDL/migraciones, entidades, repositorios, carga de archivos a la BD | `feature/backend-persistencia` → `../wt-back-datos` |
| Backend · simulación | Agente `back-sim` | Reloj, escenarios, ciclos de planificación, incidencias, indicadores | `feature/backend-simulacion` → `../wt-back-sim` |
| Backend · API | Agente `back-api` | REST `/api` y STOMP `/ws` según el contrato del frontend | `feature/backend-api` → `../wt-back-api` |

Cada agente trabaja en su propio `git worktree` (carpeta hermana del repo) para no pisar archivos.
Flujo: rama de trabajo → PR/merge a `develop` revisado por el orquestador → `main` en cada hito.

## 2. Fases y dependencias

```
F0 Decisiones ──► F1 Modelo de datos (md) ──► F2 Documento .docx
                         │
                         ├──► F3b Persistencia ──┐
F0 ──► F3a Backend base ─┼──► F3c Simulación ────┼──► F4 Integración con el frontend
                         └──► F3d API ───────────┘
```

- F3a puede empezar apenas se cierren las decisiones de stack (no depende del modelo).
- F3b, F3c y F3d esperan el modelo de datos **aprobado** (F1).
- F2 y F3 corren en paralelo sobre el mismo `modelo-datos.md`: el `.docx` no se edita a mano por separado.
- Agentes activos ahora: **redactor** y **back-base**. Los demás se lanzan cuando se cierren D-01, D-02, D-04 y M-05.

## 3. Tareas

Estados: ⬜ pendiente · 🔄 en curso · ✅ hecho · ⛔ bloqueado

### F0 — Decisiones (orquestador + usuario)

| Id | Tarea | Estado |
|---|---|---|
| D-01 | SGBD | ⬜ |
| D-02 | Frameworks del backend (ORM/acceso a datos, migraciones, WebSocket/STOMP, pruebas) | ⬜ |
| D-03 | Planificador: **módulo Maven en este repo con núcleo común + Tabu Search** (TS es el algoritmo seleccionado; ALNS queda fuera) | ✅ |
| D-04 | Discrepancias de `CLAUDE.md` §8 que afectan datos: plazo vs servicio, averías, estados, ids, tiempo, ciclo 10/15 min, flota por defecto | ⬜ |
| D-05 | `.docx` con formato similar a los demás documentos del curso; prioridad al contenido | ✅ |

### F1 — Modelo de datos (redactor; el orquestador revisa y el usuario aprueba)

| Id | Tarea | Entregable | Estado |
|---|---|---|---|
| M-01 | Inventario de datos del frontend | sección en `modelo-datos.md` | 🔄 |
| M-02 | Inventario de datos del backend y planificador | sección en `modelo-datos.md` | 🔄 |
| M-03 | Modelo conceptual (entidades y relaciones) | diagrama ER | 🔄 |
| M-04 | Modelo lógico: tablas, columnas, tipos, claves, restricciones, catálogos, índices | diccionario de datos | 🔄 |
| M-05 | Aprobación del usuario | versión 1.0 congelada | ⬜ |

### F2 — Documento de estructura de datos (redactor)

| Id | Tarea | Estado |
|---|---|---|
| R-01 | Estructura del documento con formato del curso (portada, historial, índice, referencias) | 🔄 |
| R-02 | Redactar a partir de `modelo-datos.md`; tablas del diccionario y diagrama ER | 🔄 |
| R-03 | Trazabilidad entidad ↔ LE/CU/RN | 🔄 |
| R-04 | Entregar `docs/estructura-datos/24.dis.estructura.datos.v01.docx` | 🔄 |

### F3 — Backend

| Id | Agente | Tarea | Depende de | Estado |
|---|---|---|---|---|
| B-01 | back-base | Proyecto `backend/` Spring Boot + Maven (wrapper) + Java 25, sin BD ni WebSocket todavía | — | 🔄 |
| B-02 | back-base | Módulo `planificador` (núcleo común + TS) con sus pruebas en JUnit 5 en verde | D-03 | 🔄 |
| B-03 | back-datos | Migraciones con el DDL del modelo aprobado | M-05 | ⬜ |
| B-04 | back-datos | Carga de ventas, bloqueos, mantenimiento y averías a la BD (ids deterministas) | B-03 | ⬜ |
| B-05 | back-sim | Reloj y escenarios (día a día, 5D, colapso), ciclo Sa, rutas comprometidas | B-02, M-05 | ⬜ |
| B-06 | back-sim | Incidencias (averías por tipo, mantenimiento, bloqueos) y replanificación | B-05 | ⬜ |
| B-07 | back-sim | Bitácora de eventos, indicadores y resumen por ejecución | B-05 | ⬜ |
| B-08 | back-api | Endpoints REST del contrato (`frontend/README.md`) | B-01, M-05 | ⬜ |
| B-09 | back-api | Difusión STOMP de `SimSnapshot` y `LogEvent` | B-05, B-08 | ⬜ |

### F4 — Integración

| Id | Tarea | Estado |
|---|---|---|
| I-01 | Ajustes del contrato del frontend (rutas, paradas, partes, estados unificados) | ⬜ |
| I-02 | Prueba de punta a punta con `VITE_DATA_SOURCE=server` | ⬜ |

## 4. Reglas para los agentes

- Leer `CLAUDE.md` y esta tabla antes de empezar; trabajar solo en su rama y su worktree.
- El modelo de datos se cambia **solo** en `modelo-datos.md` y lo cambia el orquestador.
- No adoptar librerías fuera de las decididas en D-02 sin consultar.
- Commits en español, en imperativo, citando LE cuando aplique. Pruebas en verde antes de pedir merge.
- `_tmp/` es de solo lectura.
