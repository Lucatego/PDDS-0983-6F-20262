# TAREAS.md — Orquestación del trabajo

Tablero de tareas del equipo de agentes. El orquestador lo mantiene al día; cada agente solo marca
sus propias tareas. Contexto del proyecto en `CLAUDE.md`.

## 1. Roles

| Rol | Quién | Responsabilidad | Rama / worktree |
|---|---|---|---|
| Orquestador | Sesión principal | Resolver decisiones con el usuario, definir el contenido de la **fuente única del modelo de datos** (`context/24.dis.estructura.datos.v01.md`), asignar tareas, revisar e integrar ramas | `develop` (raíz del repo) |
| Redactor | Agente `redactor` | Crear y actualizar los `.md` de `context/` (fuente versionada de los entregables) con el contenido que define el orquestador, y generar el `.docx` en `docs/` (salidas, no versionada) según `FORMATO-DOCUMENTOS.md` | `docs/redaccion` → `../wt-redactor` |
| Auditor | Agente `auditor` | Comprobar que cada documento nuevo o actualizado (`.md` de `context/` y su `.docx` en `docs/`) concuerda con los documentos anteriores, considerando los cambios aprobados (DD-xx, D-xx, Q&A), y emitir alertas cuando no concuerde. Solo lee e informa; el orquestador registra las alertas en §4 | Sin rama: solo lectura sobre `develop` |
| Backend · base | Agente `back-base` | Proyecto Spring Boot (Java 25, Maven), módulo planificador, configuración común | `feature/backend-base` → `../wt-back-base` |
| Backend · persistencia | Agente `back-datos` | DDL/migraciones, entidades, repositorios, carga de archivos a la BD | `feature/backend-persistencia` → `../wt-back-datos` |
| Backend · simulación | Agente `back-sim` | Reloj, escenarios, ciclos de planificación, incidencias, indicadores | `feature/backend-simulacion` → `../wt-back-sim` |
| Backend · API | Agente `back-api` | REST `/api` y STOMP `/ws` según el contrato del frontend | `feature/backend-api` → `../wt-back-api` |

Definiciones de agente en `.claude/agents/`: `redactor` (Sonnet 5.5, esfuerzo medium), `backend` (Sonnet 5.5,
esfuerzo high) y `auditor` (Sonnet 5.5, esfuerzo high, solo lectura; primera auditoría el 02/10/2026, P-06). La primera tanda (redactor y back-base) se lanzó antes con el modelo por defecto.

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
- F2 y F3 corren en paralelo sobre el mismo `context/24.dis.estructura.datos.v01.md` (antes
  `docs/estructura-datos/modelo-datos.md`): el `.docx` no se edita a mano por separado.
- D-01, D-02, D-04 y M-05 están cerradas: B-03 (migraciones) puede empezar. Agentes en uso: **redactor** y **auditor**.

## 3. Tareas

Estados: ⬜ pendiente · 🔄 en curso · ✅ hecho · ⛔ bloqueado

### F0 — Decisiones (orquestador + usuario)

| Id | Tarea | Estado |
|---|---|---|
| D-01 | SGBD: **PostgreSQL** (30/09/2026) | ✅ |
| D-02 | Librerías del backend (02/10/2026): `spring-boot-starter-webmvc` (reemplaza a `-web`), `-validation`, `-websocket` (STOMP), `-data-jpa`, `-flyway` + `flyway-database-postgresql`, driver `org.postgresql:postgresql`; pruebas: `-test` y `-webmvc-test`. BD en AWS con credenciales en `backend/.env` | ✅ |
| D-03 | Planificador: **módulo Maven en este repo con núcleo común + Tabu Search** (TS es el algoritmo seleccionado; ALNS queda fuera) | ✅ |
| D-04 | Discrepancias de `CLAUDE.md` §8 que afectan datos → resueltas en DD-01..DD-31 | ✅ |
| D-05 | `.docx` con formato similar a los demás documentos del curso; prioridad al contenido | ✅ |

### F1 — Modelo de datos (redactor; el orquestador revisa y el usuario aprueba)

| Id | Tarea | Entregable | Estado |
|---|---|---|---|
| M-01 | Inventario de datos del frontend | sección en `24.dis.estructura.datos.v01.md` | ✅ v1.0 aprobado |
| M-02 | Inventario de datos del backend y planificador | sección en `24.dis.estructura.datos.v01.md` | ✅ v1.0 aprobado |
| M-03 | Modelo conceptual (entidades y relaciones) | diagrama ER | ✅ v1.0 aprobado |
| M-04 | Modelo lógico: tablas, columnas, tipos, claves, restricciones, catálogos, índices | diccionario de datos | ✅ v1.0 aprobado |
| M-05 | Aprobación del usuario (decisiones DD-01..DD-31, §11 de `24.dis.estructura.datos.v01.md`), condicionada a que se ajusten a las especificaciones y al negocio | versión 1.0 aprobada (30/09/2026); v1.0.1 el 02/10/2026 (corrección editorial, A-01) | ✅ |

### F2 — Documento de estructura de datos (redactor)

| Id | Tarea | Estado |
|---|---|---|
| R-01 | Estructura del documento con formato del curso (portada, historial, índice, referencias) | ✅ v1.0.1 |
| R-02 | Redactar a partir de `24.dis.estructura.datos.v01.md`; tablas del diccionario y diagrama ER | ✅ v1.0.1 |
| R-03 | Trazabilidad entidad ↔ LE/CU/RN | ✅ v1.0.1 |
| R-04 | Entregar `docs/estructura-datos/24.dis.estructura.datos.v01.docx` (salida local, no versionada) | ✅ v1.0.1 (regenerado el 02/10/2026, 73 páginas) |

### F3 — Backend

| Id | Agente | Tarea | Depende de | Estado |
|---|---|---|---|---|
| B-01 | back-base | Proyecto `backend/` Spring Boot 4.1.1 + Maven (wrapper) + Java 25, sin BD ni WebSocket todavía | — | ✅ |
| B-02 | back-base | Módulo `planificador` (núcleo común + TS) con sus pruebas en JUnit en verde (41 pruebas) | D-03 | ✅ |
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

## 4. Pendientes para revisar con el usuario

| Id | Pendiente | Detalle | Cuándo |
|---|---|---|---|
| P-01 | Alinear `plazo-incluye-servicio` del backend | `backend/aplicacion/src/main/resources/application.yml` tiene `plazo-incluye-servicio: true` (valor del código y de la experimentación). DD-04 aprobó `false` por defecto (Q&A 11) como parámetro **por ejecución**. Cambiarlo cuando la configuración se lea de `configuracion_ejecucion`; decidir si el valor global del yml pasa a `false` o se elimina. La experimentación (IEN v03) no se rehace por ahora. | Al programar B-05 (simulación) |
| P-02 | Regenerar el `.docx` cuando cambie el modelo | El `.docx` se genera en `docs/` desde `context/24.dis.estructura.datos.v01.md` con el script del redactor y el formato de `FORMATO-DOCUMENTOS.md`. El script no está versionado: está en `docs/estructura-datos/generar_docx.js` (Node + librería `docx`, con `escudo-pucp.png` y `diagrama-er-vertical.png` al lado) y, si se pierde, se pide de nuevo al agente `redactor`. El índice es texto fijo: se recalcula al regenerar. | Con cada cambio del modelo |
| P-03 | ✅ Cerrado (02/10/2026, D-02) · Librerías del backend | Propuesta: Spring Data JPA, Flyway, `spring-boot-starter-websocket` (STOMP), `spring-boot-starter-webmvc-test`, y reemplazar `spring-boot-starter-web` (obsoleto en Boot 4) por `spring-boot-starter-webmvc`. No afecta al modelo de datos. | Antes de lanzar B-03 |
| P-04 | Correcciones en documentos del equipo | RN-INC-CAL-01 cita LE111/LE112 (no existen); LE091 dice «cliente» donde debería decir unidad; LE026 tiene 15 min por defecto (el modelo usa 10); LE049 y RN-INV-RES-01 tienen almacenes desactualizados; CU-05 copia a CU-04. | Próxima versión de LE/RN/CU |
| P-05 | Ajustes del frontend al modelo | Ver §9.2 de `24.dis.estructura.datos.v01.md`: códigos de pedido, flota por defecto 10/15/12, duración de averías del Q&A, refrigerio del código, umbrales del semáforo desde el servidor, 5D en ~30 min, tiempos `##d` como día del mes. | Tarea I-01 |
| P-06 | ✅ Cerrado (02/10/2026, commit 57f8564; auditoría: CONCUERDA CON OBSERVACIONES) · Encabezado del modelo de datos desactualizado | `context/24.dis.estructura.datos.v01.md` (línea 7) aún dice que las decisiones DD-xx están «pendientes de aprobación del usuario», pero M-05 las aprobó el 30/09/2026. Corregir el texto y regenerar el `.docx`. | Próximo cambio del modelo |
| P-07 | Contenedor local de PostgreSQL | Por ahora la BD está en AWS (credenciales en `backend/.env`). Más adelante crear un contenedor (p. ej. `compose.yaml` con PostgreSQL) para desarrollo y laboratorio, sin servicios externos (DAS). | Más adelante |
| P-08 | Pruebas contra la base de datos | Las pruebas excluyen hoy DataSource, JPA y Flyway (`backend/aplicacion/src/test/resources/config/application.yml`). Definir cómo probar contra PostgreSQL (`@DataJpaTest`, Testcontainers u otra; consultar librerías) y quitar las exclusiones. | Cuando avise el usuario |
| A-01 | ✅ Cerrado (02/10/2026) · Auditoría P-06 · OBSERVACIÓN | `24.dis.estructura.datos.v01`: el historial tenía dos filas con versión 1.0 (30/09 y 02/10). Decisión del usuario: la corrección editorial sube el documento a **1.0.1** (commit 20a110a; `.docx` regenerado, 73 páginas). Auditoría: CONCUERDA CON OBSERVACIONES. | Resuelto |
| A-02 | Auditoría P-06 · OBSERVACIÓN | Índice del `.docx`: el número de página va con un tabulador literal en el texto y solo se verificó en LibreOffice (73 páginas). Revisar en Word; ver `FORMATO-DOCUMENTOS.md` §6. | Más adelante (no urgente) |

Las alertas del agente `auditor` se registran en esta tabla con id `A-nn` (gravedad, documentos y secciones
implicados, acción sugerida) y se cierran con el usuario.

## 5. Reglas para los agentes

- Leer `CLAUDE.md` y esta tabla antes de empezar; trabajar solo en su rama y su worktree.
- El modelo de datos se cambia **solo** en `context/24.dis.estructura.datos.v01.md`: el orquestador decide el
  cambio con el usuario y el redactor lo escribe. Los agentes de backend no lo modifican.
- `context/` es la única documentación versionada; `docs/` es una carpeta local de salidas (`.docx`, PDF) en
  `.gitignore` y no existe en los worktrees. Detalle en `CLAUDE.md` §1.
- Después de que el redactor cree o actualice un documento, el orquestador lanza al `auditor` antes de darlo
  por cerrado; un veredicto `NO CONCUERDA` impide cerrar la tarea hasta resolver sus alertas críticas.
- No adoptar librerías fuera de las decididas en D-02 sin consultar.
- Commits con el formato `tipo: descripción` (`fix`, `feat`, `docs`, `refactor`, `test`; ver `CLAUDE.md` §10),
  en español, en imperativo, citando LE cuando aplique. Pruebas en verde antes de pedir merge.
- `_tmp/` es de solo lectura.
