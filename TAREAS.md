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
- D-01, D-02, D-04 y M-05 están cerradas y la conexión al RDS está verificada (02/10/2026). Al 08/10/2026, B-03 a
  B-07 y B-09 están en `main` (PR #4 y #5), B-08 sigue en curso y los contenedores están listos (P-07): **lo siguiente
  es I-01/I-02** (integración con el front), el hallazgo de `POST /api/bloqueos` y alinear modelo y código (P-09).
- **Corte vertical para la semana 08** (acordado el 02/10/2026): B-03 → B-04 → B-05 → B-07 (resumen) → B-08/B-09 →
  I-01/I-02, con el escenario 5D. B-06 (incidencias por tipo, trasvase) y el escenario Colapso completo van después.

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
| D-06 | Seguridad postergada por el usuario (04/10/2026). V1 omite `seg_usuario`, `seg_rol`, `seg_usuario_rol`; `registrado_por` queda nullable y sin FK conforme a DD-30. Retomar en otra migración; no bloquea B-03 | ⬜ postergada |

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
| B-03 | back-datos | Migraciones Flyway: `V1__esquema.sql` (41 tablas; seguridad postergada) y `V2__datos_iniciales.sql` (catálogos §7). Verificadas con Flyway en PostgreSQL 18.4 local; activación explícita y revisión antes del RDS | M-05 | 🔄 validado localmente; pendiente revisión/aplicación al RDS |
| B-04 | back-datos | Servicio JPA de carga de ventas, bloqueos, mantenimiento y averías; códigos por línea, reintentos idempotentes y errores por línea. Pruebas locales en verde. API queda en B-08; rendimiento con archivos reales en RDS por medir | B-03 | 🔄 implementado y probado localmente; validación en entorno objetivo pendiente |
| B-05 | back-sim | Reloj y escenarios (día a día, 5D, colapso), ciclo Sa, rutas comprometidas | B-02, M-05 | ✅ orquestador y persistencia de ciclos/rutas completados (`OrquestadorSimulacion`, `RepositorioSimulacion`); 13 pruebas de simulación en verde |
| B-06 | back-sim | Incidencias (averías por tipo, mantenimiento, bloqueos) y replanificación | B-05 | ✅ Averías tipadas, regla de indisponibilidad por tipo, indicador por ejecución separado de averías aleatorias, filtrado/replanificación de demanda no comprometida; pruebas unitarias verdes. V3 y carga RDS quedan pendientes de la verificación manual del usuario. |
| B-07 | back-sim | Bitácora de eventos, indicadores y resumen por ejecución | B-05 | ✅ eventos, consolidado parcial/final e indicadores por plazo persistidos idempotentemente; pruebas unitarias aisladas |
| B-08 | back-api | Endpoints REST del contrato (`frontend/README.md`) | B-01, M-05 | 🔄 Rutas REST y snapshot dinámico implementados; pruebas HTTP de `GET /catalogos` y snapshot inicial pasan. Pendiente cobertura HTTP de mutaciones/carga/configuración y recuperar conteos/procedencia de archivos desde persistencia al reiniciar. No requiere RDS para el cierre. 08/10 (PR #5): `POST /simulacion/velocidad`, pruebas HTTP de configuración y velocidad, pedido manual corregido, reiniciar sin 500 y cierre de ejecuciones huérfanas al arrancar; **abierto:** `POST /bloqueos` → 500 (ver §4) |
| B-09 | back-api | Difusión STOMP de `SimSnapshot` y `LogEvent` | B-05, B-08 | ✅ en `main` (PR #5, 08/10/2026); conexión verificada desde la GUI en contenedor (`http://localhost`, «Servidor conectado · STOMP»). Detalle: endpoint `/ws`, `/topic/simulacion/estado` a 5 Hz configurable (1–10) mientras corre y de inmediato tras cada comando REST, cambio de estado o suscripción, `/topic/simulacion/eventos` con la bitácora del motor; incluye `RelojSimulacion` (nadie invocaba `OrquestadorSimulacion.avanzar`). 21 pruebas nuevas (unitarias y STOMP extremo a extremo con 5D real). Pendiente: probar con el front real (I-02) y medir el costo de persistir en cada pulso del reloj sobre el RDS |

### F4 — Integración

| Id | Tarea | Estado |
|---|---|---|
| I-01 | Ajustes del contrato del frontend (rutas, paradas, partes, estados unificados) | ⬜ |
| I-02 | Prueba de punta a punta GUI → Planificador → Visualizador con `VITE_DATA_SOURCE=server`, para día a día, 5D y colapso por separado. Verificar configuración, inicio, pausa/reanudación, rutas, pedidos, reloj y cierre correspondiente; datos recibidos del backend, sin motor local de respaldo. Depende de B-05, B-07, B-08, B-09 e I-01 | ⬜ |
| I-03 | Prueba multidispositivo y conexión tardía: para cada uno de los tres escenarios, conectar un segundo navegador/dispositivo durante una ejecución y comprobar que recibe el estado vigente y las actualizaciones posteriores, concordantes con el primer cliente, sin reiniciar la ejecución. Depende de I-02 | ⬜ |
| I-04 | Prueba de desconexión/reconexión del Visualizador: interrumpir la conexión de un cliente y recuperarla; comprobar resincronización con el servidor, sin duplicar eventos, pedidos ni entregas y sin afectar al otro cliente. Repetir por escenario. Depende de I-03 | ⬜ |
| I-05 | Prueba de humo de la solución desplegada: desde la URL web y un segundo dispositivo, verificar carga de GUI, acceso REST, conexión STOMP y visualización de una ejecución de cada escenario. Registrar URL, versión y resultado. Depende de I-02..I-04 y de disponer de un despliegue accesible; no incluye realizar el despliegue ni verificar el RDS | ⬜ |

**Criterio recomendado para B-06 (07/10/2026, por confirmar al implementarlo):** ofrecer la consideración de
incidencias como configuración por ejecución, controlada por el módulo de simulación al construir el estado
entregado al planificador. Tabu Search permanece independiente y recibe solo las restricciones/incidencias activas.
Persistir el valor efectivo y exponerlo por el contrato API para mantener reproducibilidad. En esta iteración,
mantenerlas fuera del alcance funcional y no mostrar un interruptor sin efecto real. Este criterio no modifica aún
el modelo de datos aprobado ni cierra una decisión de esquema.

Alcance de estas pruebas del entregable: usar casos reproducibles sin averías ni bloqueos durante el
periodo simulado; B-06 y D-06 no son requisitos para aprobarlas. Registrar caso, resultado esperado,
resultado obtenido y evidencia por escenario. Probar los tres escenarios no exige ejecutarlos
simultáneamente: se conserva la restricción actual de una ejecución activa. El primer corte 5D no
cierra la validación de los otros dos escenarios. Este mapeo agrega solo pruebas de implementación;
no agrega tareas de elaboración del diagrama ni de ejecución del despliegue.

## 4. Pendientes para revisar con el usuario

El usuario realizará manualmente la verificación del RDS (04/10/2026). No se consulta ni modifica ese
entorno desde esta tarea; la validación local no bloquea el avance secuencial del corte vertical.
B-05 incorpora reloj, pausa/reanudación, horizonte 5D exacto, partes estables, reservas de stock,
recarga diaria y detección básica de colapso. La configuración se guarda atómicamente con flota,
turnos, velocidades y almacenes. Aún no está conectado a REST/STOMP ni persiste resultados del motor.
El lector B-05 obtiene los parámetros efectivos, semilla TS, stock inicial, pedidos del horizonte,
bloqueos futuros, mantenimientos de la flota y huellas de archivos; construye el motor solo en memoria
en estado CONFIGURADA, sin iniciar ni vincular aún los archivos a la ejecución.

Verificación del 04/10/2026 en `feature/backend-persistencia`: 55 pruebas aprobadas y 1 prueba opcional del
planificador omitida por falta del dataset externo; incluye 7 pruebas del analizador y 8 de PostgreSQL/Flyway.
Se ejecutó con Java 21 y `-Dmaven.compiler.release=21` por disponibilidad local; el proyecto conserva Java 25.
Falta repetir con el JDK 25 del proyecto. No se modificó el RDS ni se incorporaron librerías nuevas.
El 07/10/2026 se completó y validó B-05: `OrquestadorSimulacion` coordina el ciclo de vida del motor y Tabu Search,
mientras `RepositorioSimulacion` persiste ciclos, rutas, paradas, partes de pedidos y movimientos de stock.
Se añadieron 3 pruebas unitarias aisladas en `OrquestadorSimulacionTest` (todas en verde; 45 pruebas aprobadas en el reactor).
El 07/10/2026 B-06 volvió a pendiente para completar el alcance de incidencias tipadas y replanificación. El 08/10/2026
se completó: averías tipadas, regla de indisponibilidad por tipo, opción por ejecución independiente de la generación
aleatoria, y filtrado/replanificación de demanda aún no comprometida. Se añadieron pruebas unitarias y la migración V3;
su aplicación/carga en RDS queda para la verificación manual del usuario.
El 07/10/2026 se completó B-07: `OrquestadorSimulacion` persiste eventos nuevos y actualiza el resumen e indicadores
por plazo al iniciar, avanzar, pausar/detener y registrar operaciones manuales. El 08/10/2026 se integró el desglose
de averías tipadas de B-06. El resumen calcula cumplimiento, holgura y tiempos de entrega, uso de flota/capacidad,
reprogramaciones e incidencias activas. Se añadieron pruebas unitarias de cálculo y persistencia orquestada; sin
pruebas de PostgreSQL local ni RDS.
Suite del backend en Java 21 (`-Dmaven.compiler.release=21`): 88 ejecutadas, 0 fallas y 14 omitidas (13 de PostgreSQL
y 1 por dataset externo opcional); `git diff --check` sin errores.

**08/10/2026, estado de `main`:** PR #4 (`feature/backend-persistencia`, B-03..B-08) y PR #5
(`feature/backend-velocidad`, B-09 y lo descrito abajo) fusionados. Suite con Java 21: 131 pruebas, 0 fallas, 16
omitidas. Rama `feature/contenedores`: `compose.yaml` raíz con BD + backend (Java 25) + frontend (nginx); verificado
con Flyway V1–V3 sobre la BD del contenedor y la GUI conectada por STOMP (P-07 cerrado).

**Rama `feature/backend-velocidad` (08/10/2026), sobre B-08/B-09:**
- `POST /api/pedidos` fallaba porque `RepositorioSimulacion.registrarPedidoManual` insertaba `pedido.fecha_real_registro`
  (no existe; la columna es `creado_en`, NOT NULL sin valor por defecto, por lo que el INSERT ahora la llena con
  `CURRENT_TIMESTAMP`) y omitía `ejecucion_id`, que el CHECK de `pedido` exige para origen MANUAL. Sin migración (opción A).
  `registrar()` del controlador convierte cualquier excepción en `OrderResult{ok:false}`, por lo que el fallo no se veía como 500.
  `SimulacionPostgresqlTest` (optativa, PostgreSQL local con `PAQRAP_PRUEBA_DB_URL=jdbc:postgresql://localhost:5433/paqrap`)
  registra un pedido por la API y corre una 5D completa que ejercita los demás INSERT/UPDATE de la simulación: no hay más
  columnas inexistentes. **Hallazgo abierto:** `RepositorioSimulacion.registrarIncidencia` viola `incidencia_check2` para
  bloqueos manuales (`POST /api/bloqueos` → 500): el CHECK exige `bloqueo_id` (hay que insertar antes en `bloqueo`/`bloqueo_vertice`);
  además guarda el fin previsto en `fecha_fin` (la columna de fin previsto es `fecha_fin_prevista`). El mantenimiento manual sí persiste;
  la avería manual no se probó contra la BD (necesita una unidad en ruta).
- Control de velocidad (contrato acordado con el front): base `paqrap.tiempo-real.minutos-por-segundo-base` = 3,0 min simulados/s
  para 5D y Colapso (antes 10,0 fijo; 5 días ≈ 40 min reales a x1); Día a día sigue en 1/60 y no admite cambio.
  `POST /api/simulacion/velocidad {"factor":1|2|5|10}` aplica base × factor en caliente (204; 400 con `{mensaje}` si el
  factor no es válido, el escenario es diaria o no hay simulación). El factor vive en `MotorSimulacion` y vuelve a 1 al configurar o
  reiniciar (motor nuevo). `SimSnapshot` incluye `speedFactor` y `simMinPerSec` (1/60 en diaria). `configuracion_ejecucion.aceleracion_reloj`
  conserva la velocidad **base** (3,0; 1/60 en diaria) y no se actualiza con el factor: es un control operativo transitorio y,
  si se guardara el valor efectivo, un reinicio lo tomaría como nueva base. Pendiente decidir si el cambio debe quedar en la bitácora (LE054; requeriría un tipo de evento nuevo).
- `POST /api/simulacion/configuracion`: `considerarIncidencias` es opcional (ausente o `null` → false). La causa del 400 era el
  `boolean` primitivo de `RunConfig`: Jackson 3 falla con campos primitivos ausentes. El cuerpo exacto de la prueba manual
  (con `shiftStarts` como `int[]`) ahora responde 204. Los demás DTO con primitivos (`OrderInput`, `AveriaInput`, `Fleet`, etc.) siguen
  exigiendo todos sus campos.

| Id | Pendiente | Detalle | Cuándo |
|---|---|---|---|
| P-01 | Alinear `plazo-incluye-servicio` del backend | `backend/aplicacion/src/main/resources/application.yml` tiene `plazo-incluye-servicio: true` (valor del código y de la experimentación). DD-04 aprobó `false` por defecto (Q&A 11) como parámetro **por ejecución**. Cambiarlo cuando la configuración se lea de `configuracion_ejecucion`; decidir si el valor global del yml pasa a `false` o se elimina. La experimentación (IEN v03) no se rehace por ahora. | Al programar B-05 (simulación) |
| P-02 | Regenerar el `.docx` cuando cambie el modelo | El `.docx` se genera en `docs/` desde `context/24.dis.estructura.datos.v01.md` con el script del redactor y el formato de `FORMATO-DOCUMENTOS.md`. El script no está versionado: está en `docs/estructura-datos/generar_docx.js` (Node + librería `docx`, con `escudo-pucp.png` y `diagrama-er-vertical.png` al lado) y, si se pierde, se pide de nuevo al agente `redactor`. El índice es texto fijo: se recalcula al regenerar. | Con cada cambio del modelo |
| P-03 | ✅ Cerrado (02/10/2026, D-02) · Librerías del backend | Propuesta: Spring Data JPA, Flyway, `spring-boot-starter-websocket` (STOMP), `spring-boot-starter-webmvc-test`, y reemplazar `spring-boot-starter-web` (obsoleto en Boot 4) por `spring-boot-starter-webmvc`. No afecta al modelo de datos. | Antes de lanzar B-03 |
| P-04 | Correcciones en documentos del equipo | RN-INC-CAL-01 cita LE111/LE112 (no existen); LE091 dice «cliente» donde debería decir unidad; LE026 tiene 15 min por defecto (el modelo usa 10); LE049 y RN-INV-RES-01 tienen almacenes desactualizados; CU-05 copia a CU-04. | Próxima versión de LE/RN/CU |
| P-05 | Ajustes del frontend al modelo | Ver §9.2 de `24.dis.estructura.datos.v01.md`: códigos de pedido, flota por defecto 10/15/12, duración de averías del Q&A, refrigerio del código, umbrales del semáforo desde el servidor, 5D en ~30 min, tiempos `##d` como día del mes. | Tarea I-01 |
| P-06 | ✅ Cerrado (02/10/2026, commit 57f8564; auditoría: CONCUERDA CON OBSERVACIONES) · Encabezado del modelo de datos desactualizado | `context/24.dis.estructura.datos.v01.md` (línea 7) aún dice que las decisiones DD-xx están «pendientes de aprobación del usuario», pero M-05 las aprobó el 30/09/2026. Corregir el texto y regenerar el `.docx`. | Próximo cambio del modelo |
| P-07 | ✅ Cerrado (08/10/2026) · Contenedores locales | `backend/compose.yaml` (Gandy): PostgreSQL 18 en el puerto 5433. `compose.yaml` de la raíz (rama `feature/contenedores`): BD + backend (`eclipse-temurin:25`, Flyway activado contra la BD local) + frontend (`nginx:1.28-alpine`, proxy de `/api` y `/ws`). Verificado: V1–V3 aplicadas, GUI en `http://localhost` conectada por STOMP. Uso en `README.md` («Ejecutar todo con Docker»). El RDS sigue sin modificarse. | Resuelto |
| P-08 | Pruebas contra la base de datos | Las pruebas excluyen hoy DataSource, JPA y Flyway (`backend/aplicacion/src/test/resources/config/application.yml`). Definir cómo probar contra PostgreSQL (`@DataJpaTest`, Testcontainers u otra; consultar librerías) y quitar las exclusiones. | Cuando avise el usuario |
| P-09 | Código vs. modelo de datos v1.0.1 (08/10/2026) | (a) `V3__considerar_incidencias.sql` agrega `configuracion_ejecucion.considerar_incidencias` BOOLEAN NOT NULL DEFAULT FALSE (criterio de B-06), ausente en `context/24.dis.estructura.datos.v01.md`. (b) DD-26 y V2 fijan `ACELERACION_5D` = 4,0 (5D ≈ 30 min), pero el backend usa `paqrap.tiempo-real.minutos-por-segundo-base` = 3,0 (≈ 40 min) y el factor ×1/×2/×5/×10 en caliente, no persistido; `aceleracion_reloj` guarda la base. Decidir: registrar en el modelo (nueva versión, DD-32 y DD-26 revisada, redactor + auditor) o alinear el código. | Antes de la siguiente versión del modelo |
| P-10 | DAS desactualizado (08/10/2026) | `context/23.dis.arquitectura.solucion.v01.md` (v1.0): dice Java 21 (vigente Java 25, DD-31), R-02 deja sin definir servidor, canal y SGBD (ya decididos: Spring Boot 4, STOMP, PostgreSQL) y la vista de despliegue (§4.6, Figura 5, Tabla 10) no incluye los contenedores (`compose.yaml`: nginx, backend JRE 25, PostgreSQL 18). Actualizar con redactor + auditor y regenerar el `.docx`. | Próxima versión del DAS |
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
