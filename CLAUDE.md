# CLAUDE.md — PaqRap · Centro de Operaciones (Equipo 6F · 1INF54-0983 · PUCP 2026-2)

Contexto consolidado para sesiones futuras. Resume los documentos de `context/`, el frontend
(`frontend/`), el código Java de los algoritmos (`_tmp/algoritmos/`) y las preguntas y respuestas
oficiales del curso (30/09/2026). **Si algo aquí contradice una fuente, verifica en la fuente.**

## 1. Forma de trabajo acordada con el usuario

- Idioma: **español** (documentos, commits, nombres de dominio en código).
- Avanzar **paso a paso**: terminar un paso, avisar y esperar confirmación antes del siguiente.
- **Consultar antes de proponer/adoptar cualquier framework o librería** (front o back).
- Stack fijado por el usuario: **frontend React** (ya construido) y **backend Spring Boot con Java 25**
  (documentos del curso: Java 21; código de algoritmos compilado con `--release 17`; prevalece Java 25).
- Objetivo actual: **Documento de Diseño de Estructura de Datos**, base para crear las tablas SQL.
  Cubre los datos del front y del back. Plan en §9.
- `_tmp/` es una copia temporal de solo lectura (en `.gitignore`); no modificarla.

## 2. El caso en una página

PaqRap vende un único producto **P** y lo entrega en la ciudad (retícula 70 × 50 km). Hoy planifica
a mano; el sistema debe registrar pedidos, **planificar/replanificar rutas con metaheurísticas (Java)**
y mostrar la operación en un **mapa en tiempo real multi-dispositivo**, en 3 escenarios:
**Día a día** (tiempo real), **Simulación 5D** (5 días en 30–60 min reales) y **Colapso** (hasta que
un pedido no pueda cumplirse).

Actores (CU): Operador de Pedidos, Gestor Logístico, Analista de Escenarios; actores de sistema:
Motor Planificador y Controlador de Escenarios (reloj).

## 3. Parámetros y reglas de negocio

Fuentes: `14.ana.reglas.glosario`, `03.lista.exigencias` y **Q&A oficial del curso**
(`_tmp/algoritmos/tabu/datos/referencias/c.1inf54.26-2.preguntas.respuestas_PUBLICADO.xlsx`), que
prevalece cuando corrige a los documentos del equipo.

| Concepto | Valor |
|---|---|
| Mapa | Retícula 70 (X) × 50 (Y) km → 71 × 51 nodos (0..70, 0..50), aristas de 1 km, origen abajo-izquierda, doble sentido, sin diagonales. Distancia Manhattan. |
| Almacenes (Q&A, **vigente**) | Central (27,14) stock ilimitado · Nor-Oeste (12,38) · Este (57,27), intermedios de 1000 (configurable), recarga a capacidad a las 23:59:59. Stock nunca < 0. Se descuenta al despachar. Una unidad no puede volver a un almacén sin stock; puede recargar en cualquiera con stock. |
| Plazos | Regular 36 h; priorizado 4, 8, 12, 18 h. `T_limite = T_registro + Δt`. 100 % en plazo. |
| Flota | Auto (TA) 24 paq, 40 km/h, S/ 8/km · Moto (TM) 8 paq, 25 km/h, S/ 6/km · Bici (TB) 4 paq, 12 km/h, S/ 3/km. Tamaño (hoja Flota del Q&A y código): **10 TA, 15 TM, 12 TB**. Se cambia solo al inicio de una ejecución. Id `TTNN` (TA01, TM03, TB10). Todas salen del central al inicio. |
| Velocidades | Parametrizables **por tipo** y **en caliente**: aplican desde la siguiente iteración de planificación (Q&A 6, 15, 16). |
| Entrega | 1 h por entrega (también por cada entrega parcial). Carga despreciable. **Q&A 11: la hora de entrega no cuenta dentro del plazo.** |
| Entregas parciales | Permitidas (Q&A 13). El código divide pedidos en partes de ≤ 4 paquetes. |
| Turnos | 07:00, 15:00, 23:00 (8 h, configurables). Cambio de conductor donde esté la unidad, tiempo despreciable (Q&A 12). Refrigerio de 1 h: en el código su inicio cae en [turno+1 h, turno+7 h] y termina antes del cambio de turno. |
| Averías (Q&A 3) | Se registran en el visualizador. T1 (menor): 2 h inoperativa. T2 (intermedia): hasta el fin del turno siguiente; ≤ 4 h en el lugar. T3 (mayor): ≥ 2 días, vuelve en el turno 15–23; 4 h en el lugar. T2 y T3: unidad y paquetes no trasvasados se llevan instantáneamente al central. Trasvase: mencionado, sin regla cerrada. Una avería no bloquea la vía. |
| Mantenimiento (Q&A 19) | Archivo `mant.preventivo.m1.m2` (p. ej. `09.10`), registro `aaaammdd:TTNN`. Unidad no disponible de 00:00 a 23:59 de ese día; si está en ruta vuelve de inmediato (evitarlo al planificar). Un mantenimiento por día, cada unidad ~bimensual; generar archivos hasta 31/12/2029. |
| Bloqueos (Q&A 7) | Solo planificados (archivo mensual), polígonos **abiertos**. Un nodo bloqueado no se atraviesa ni admite giro: solo vuelta en U (el código cierra las 4 calles del nodo). Registro manual en Día a día (LE077). Consolidar duplicados del mismo tramo (LE086). |
| Criticidad | `T_restante = T_limite − T_actual`; se reasigna primero el de menor T_restante. |
| Semáforo | Verde/ámbar/rojo con umbrales **configurables** (contiguos, no solapados). |
| Ciclo de planificación | Sa = 10 min en DAS/IEN/código; LE026 dice 15 min (parámetro). |
| Colapso | Primer pedido que no puede cumplir su plazo → se detiene; registrar pedido, nodo y hora. |
| Pedido en riesgo | Priorizado cuyo traslado con auto supera el plazo: se marca, **no se rechaza** (LE010). |

Archivos de entrada. Los tiempos `##d##h##m` son **día del mes** del archivo (confirmado por los parsers).

| Archivo (nombre real en datos) | Formato | Ejemplo |
|---|---|---|
| Ventas `ventas.AAAAMM.txt` (Q&A: `ventas2026mm`) | `##d##h##m:posX,posY,cIdCliente,qq,hl` | `01d01h30m:56,30,c4910,02,36` |
| Bloqueos `bloqueo.AAMM.txt` (Q&A/LE: `aaaamm.bloqueadas`) | `##d##h##m-##d##h##m:x1,y1,x2,y2,…` | `01d02h22m-01d04h42m:25,45,45,45,45,40` |
| Mantenimiento `mant.preventivo.m1.m2` | `aaaammdd:TTNN` | `20260901:TA01` |
| Averías (DAS, front) | `##d##h##m:TTNN,tipo` | `01d09h30m:TA01,2` |
| Lote de pedidos (front) | `cliente,cantidad,modalidad,x,y` | `c1234,5,36,40,20` |

Volumen de datos (`_tmp/algoritmos/alns/data/`): ventas de 2026-01 a 2028-12 (36 archivos, **160 010
pedidos**, cantidad 1–10, media 5,5, ~10 000 clientes distintos; el `cIdCliente` se repite);
bloqueos ~500–700 por mes, polilíneas de 2 a 6 vértices.

## 4. Requisitos (LE v03: 100 exigencias LE001–LE100, 8 RNF)

Módulos/agrupadores de la **LE v03** (fuente de verdad para el diseño) y responsables:

| Mód. | Tema | LE | Responsable |
|---|---|---|---|
| AG01 | Registro de pedidos | 001–013 | Eliezer Villarreal |
| AG02 | Planificador de rutas | 014–027 | Eliezer Villarreal |
| AG03 | Almacenes e inventario | 028–036 | Yaser Fernández / Gandy Zinanyuca |
| AG04 | Visualizador tiempo real | 037–052 | Yaser Fernández |
| AG05 | Escenarios e indicadores | 053–066 | Gandy Zinanyuca |
| AG06 | Flota y configuración | 067–070 | Gandy Zinanyuca |
| AG07 | Incidencias | 071–086 | Lucas Alvites |
| AG08 | Replanificación | 087–100 | Lucas Alvites |

LE con impacto directo en datos persistentes: LE002 (hora límite), LE005 (no cumplido), LE008/009
(reproducibilidad: mismos ids con mismo archivo), LE012 (no borrar pedidos ya planificados),
LE035 (historial de inventario), LE054 (bitácora de eventos por ejecución), LE056/061–064 (resumen e
indicadores por ejecución, exportables), LE060 (conservar resultados al detener), LE067–070 (config.
por ejecución), LE074/082/083/085 (historial de incidencias), LE080 (origen del bloqueo), LE091
(trazabilidad de reasignación), LE093 (holgura tras reprogramar).

La guía de evaluación del curso (hoja «Guía» del Q&A) pregunta cómo se gestiona la BD (RDBMS), si la
data histórica/futura se **carga a la BD antes de la simulación** y si la carga es independiente
para los 3 escenarios; también pide la última planificación completa en el reporte final.

## 5. Arquitectura (fuente: `23.dis.arquitectura.solucion`)

- Cliente-servidor en capas. Cliente web (React) ↔ servidor Spring Boot (REST + canal en tiempo real)
  ↔ BD relacional (SGBD **pendiente**, DA-10). Todo corre en el laboratorio, sin servicios externos.
- Planificador = biblioteca Java detrás de `PlanificadorEstricto` (§6). El reloj, los ciclos (Sa), la
  carga de archivos y la persistencia son responsabilidad del backend, no de los algoritmos.
- Experimentación (IEN v03, 40 corridas): **TS gana** en tiempo de planificación (Ta medio 13 598 ms
  vs 37 062 ms) y holgura (1093 vs 940 min); ALNS usa mejor la flota.
- Entidades del DAS (base del esquema): Pedido/PartePedido, Vehiculo/TipoVehiculo, Almacen, Nodo,
  Bloqueo, Averia/Mantenimiento, Ruta/Parada, Solucion, EstadoOperacion, ParametrosOperacion,
  ResultadoPlanificacion + parámetros de escenario, eventos e indicadores por ejecución.
- Seguridad: autenticación y roles por perfil **propuesta**, no implementada.

## 6. Código del planificador (`_tmp/algoritmos/`, repo `DP1-G6F-Prototipo`)

Estructura. **Vigente** = núcleo común + TS + ALNS estricto + experimentación. **Histórico** =
`alns/src/pe/...` (ALNS original con su simulador mensual; no mezclar, pero tiene modelo de estados útil).

| Carpeta | Paquete | Contenido |
|---|---|---|
| `comun/src/main/java` | `pe.pucp.paqrap.estricto.{modelo,servicios,caminos,datos}` | Modelo inmutable (records), evaluador, calculador de rutas, caminos temporales, parsers |
| `tabu/src/main/java` | `pe.pucp.paqrap.tabu` | `TabuSearchPlanner`, `ConfiguracionTabu`, vecindarios, lista tabú |
| `alns/src/main/java` | `pe.pucp.paqrap.alns.estricto` | `ALNSPlanner`, `ConfiguracionALNS` (reusa `SelectorAdaptativo`, `CriterioAceptacion` del histórico) |
| `experimentacion/src/main/java` | `pe.pucp.paqrap` | `SimulacionComparada` (reloj por ciclos = modelo del backend), `CompararAlgoritmos`, ejecutores, pruebas |
| `alns/src/pe/pucp/paqrap` | `modelo, solucion, simulacion, …` | ALNS histórico (Sa/K/Sc, viajes múltiples con recarga, 5 destrucciones) |
| `alns/data/` | — | Ventas y bloqueos 2026–2028, `mant.preventivo.09.10.txt` |

Contrato: `PlanificadorEstricto.planificar(EstadoOperacion, ParametrosOperacion) → ResultadoPlanificacion`.
Tiempos como **`LocalDateTime` absoluto** (el front usa minutos desde el día 1).

Modelo del núcleo (`estricto.modelo`, todos `record`):

| Tipo | Campos |
|---|---|
| `Nodo` | `x` 0–70, `y` 0–50 |
| `TipoVehiculo` (enum) | `TA/TM/TB` con descripción, capacidad, velocidadKmh, costoPorKm |
| `Vehiculo` | `codigo` (regex `T[AMB][0-9]{2}`), `tipo`, `ubicacionInicial: Nodo`, `disponible`, `disponibleDesde` |
| `Almacen` | `id` (`CENTRAL`, `NOROESTE`, `ESTE`), `nodo`, `stock`, `ilimitado` (sin capacidad: la tiene el backend) |
| `Pedido` | `id`, `fechaRegistro`, `ubicacion: Nodo`, `cantidad` (>0), `plazoHoras` ∈ {4,8,12,18,36}, `clienteId`; `deadline()` derivado |
| `PartePedido` | `id` (`<pedidoId>#n`, estable entre ciclos), `pedido`, `cantidad` |
| `Bloqueo` | `inicio`, `fin` ([inicio, fin)), `puntos: List<Nodo>` (tramos H/V) |
| `Averia` / `Mantenimiento` | `vehiculo`, `inicio`, `fin` (sin tipo de avería) |
| `Ruta` | `vehiculo`, `almacenOrigen`, `partes` (orden = secuencia), `enCurso` |
| `Solucion` | `rutas`, `pendientes: List<PartePedido>` |
| `EstadoOperacion` | `instante`, `pedidos` (solo cantidad pendiente), `vehiculos`, `almacenes`, `bloqueos`, `averias`, `mantenimientos`, `rutasEnCurso`, `descansoRealizado: Set<codigo>` |
| `ParametrosOperacion` | `servicioMinutos` 60, `plazoIncluyeServicio` true, `turnoMinutos` 480, `inicioTurnoMinuto` 420, `descansoDesde` 60, `descansoHasta` 420, `descansoMinutos` 60, `tamanioParte` 4, `costoFijoVehiculo` 50, `penalizacionPaquetePendiente` 1e6, `velocidades` por tipo |
| `ResultadoRuta` | `ruta`, `salida`, `fin`, `almacenRetorno`, `paradas`, `caminos`, `descansoInicio/Fin`, `distanciaKm`, `costo`, `errores` |
| `Parada` | `partes` (consecutivas del mismo pedido), `llegada`, `finServicio` |
| `Camino` / `PasoCamino` | `origen`, `inicio`, `llegada`, `pasos` (origen, destino, salida, llegada por arista de 1 km) |
| `EvaluacionSolucion` | `rutas: ResultadoRuta[]`, `errores`, `objetivo`, `paquetesPendientes` |
| `MetricasResultado` | taMs, objetivo, costoOperacion, distanciaKm, tiempoRutasMinutos, cumplimientoPedidos/Paquetes, vehiculosUsados, utilizacionCapacidad, iteraciones, iteracionMejor, candidatosEvaluados, insercionesIniciales, pedidosTotales/Completos, paquetesPendientes, parada, estadoResultado (`COMPLETA`/`COLAPSO_PLANIFICACION`/`SIN_DEMANDA`), holguraPromedio/MinimaMin, taPrimeraCompletaMs, iteracionPrimeraCompleta |
| `ResultadoPlanificacion` | `algoritmo`, `solucion`, `evaluacion`, `metricas` |

Configuraciones: `ConfiguracionTabu(maxIteraciones, tenenciaTabu, sinMejoraMax, candidatosPorIteracion,
presupuestoMs, semilla)` y `ConfiguracionALNS(maxIteraciones, sinMejoraMax, destruccionMax, segmento,
reaccion, aceptacionInicial, presupuestoMs, semilla)`. Experimento: TS(300, 7, 30, 400, 0, s),
ALNS(300, 300, 4, 5, 0.7, 0.05, 0, s). Objetivo común: `paquetesPendientes + 0,5 − atan(h̄/60)/π`.

Comportamiento relevante para los datos:
- Ids deterministas: pedido = `VAAAAMM-Lnnnnn` (archivo + línea) → cumple LE008/009; el cliente no es clave.
- `DatasetLoader` fija almacenes y flota (10/15/12) por código; el backend deberá leerlos de la BD.
- Ruta del núcleo = **un solo viaje** por vehículo y ciclo (carga ≤ capacidad); parte del almacén de
  origen elegido y vuelve al almacén de llegada más temprana (no verifica stock del almacén de retorno).
  Viajes múltiples con recarga solo existen en el ALNS histórico.
- `SimulacionComparada` (ciclo Sa): ingresa pedidos con registro ≤ t; planifica; **compromete** las rutas
  que salen antes de t+Sa (no se deshacen); descuenta stock al despachar; entrega = fin de servicio de la
  última parte; vehículo queda en el almacén de retorno con `disponibleDesde = fin`; repone stock al
  cambiar de día; termina en `FIN_DE_DATOS`, `LIMITE_DE_CICLOS` o `COLAPSO_PLANIFICACION` (con diagnóstico).
  Exporta por ciclo y un resumen por corrida (buen molde para tablas de ejecución e indicadores).
- Estados del modelo histórico (candidatos para unificar): Pedido `REGISTRADO, ASIGNADO, EN_RUTA,
  ENTREGADO, REASIGNADO, NO_CUMPLIDO`; Vehículo `DISPONIBLE, EN_RUTA, AVERIADO, EN_MANTENIMIENTO,
  EN_ALIMENTACION`. Avería histórica: TIPO_1 120 min, TIPO_2 360, TIPO_3 1440 (≠ reglas del Q&A).
- No implementado aún en el núcleo: tipos de avería, trasvase, traslado al central, uso real de
  `rutasEnCurso` (la simulación pasa `List.of()`), bitácora de eventos. Averías y mantenimiento se
  excluyeron de la experimentación.

## 7. Frontend (`frontend/`) — estado y contrato

React 19 + TS 5.9 + Vite 8 + Tailwind 4 + Zustand 5 + TanStack Query 5 + ECharts 6 + React Router 8 +
`@stomp/stompjs` 7 + Vitest. Node ≥ 20.19. Detalle en `frontend/README.md` y `frontend/PLAN-MIGRACION.md`.

- `VITE_DATA_SOURCE=local` (defecto): motor simplificado en el navegador (`src/engine/SimulationEngine.ts`,
  asignación golosa **1 pedido por vehículo**, no usa TS/ALNS). `server`: REST `/api` + STOMP `/ws`.
- La UI solo conoce `SimulationGateway` (`src/api/gateway.ts`). Pantallas: monitor, indicadores,
  pedidos, flota, almacenes, averías, mantenimiento, bloqueos, eventos, configuración.
- **Contrato JSON** = tipos de `src/domain/types.ts`: `SimSnapshot` (estado completo difundido 4–10 Hz
  por `/topic/simulacion/estado`), `LogEvent` (`/topic/simulacion/eventos`), `RunConfig`, `OrderInput`,
  `OrderResult`, `FileLoadSummary`, `Catalogos`. Tiempos en **minutos de simulación desde las 00:00
  del día 1**; coordenadas en km. Errores `{ "mensaje": "..." }`.
- REST: `GET /simulacion/estado`, `GET /catalogos`, `POST /simulacion/{configuracion|iniciar|detener|reiniciar}`,
  `POST /pedidos`, `POST /pedidos/lote`, `POST /archivos/{ventas|bloqueos|averias|mantenimiento}` (texto
  plano), `POST /averias {vehicleId,tipo}`, `POST /mantenimientos {vehicleId,horas}`, `POST /bloqueos {nodos,horas}`.
- Tipos clave: `Vehicle{id,type,capacity,speed,costPerKm,home,state,pos,path,orderId,…}` con estados
  `idle|break|toClient|atClient|returning|broken|maintenance`; `Order{id(numérico),clientId,pos,qty,
  priority(h),createdAt,deadline,status(pending|assigned),reprogramado,enRiesgo,vehicleId,warehouseId}`;
  `ClosedOrder{estadoFinal(entregado|no cumplido),closedAt}`; `Incident` = bloqueo|falla|mantenimiento
  con `since/until/origin(archivo|manual|aleatorio)`; `Warehouse{id(central|noroeste|este),pos,infinite,
  capacity,stock,dispatchedToday}`; `Stats{deliveredTotal,onTime,late,cost,distanceKm,byPriority,bySector}`.
- Solo en el navegador (localStorage): tema, **umbrales del semáforo** (70 % / 35 %), filtros del mapa.

## 8. Discrepancias (resolver al diseñar los datos)

Resueltas por el Q&A oficial:
- ✅ Posición de almacenes: vigente Central (27,14), Este (57,27). LE049 y RN-INV-RES-01 están desactualizados.
- ✅ `##d` de los archivos = día del mes del archivo. El motor local del front lo toma relativo a la fecha
  de inicio de la corrida (a corregir en el front si se mantiene el modo local).

Abiertas:
1. ✅ **Java 25** y **PostgreSQL** (DD-31; D-01 cerrada). DAS/estándar/IEN aún dicen Java 21.
2. ✅ **Plazo y servicio** (DD-04): parámetro por ejecución, por defecto `false` (Q&A 11: basta llegar antes
   del límite). La experimentación (con `true`) no se rehace por ahora; `backend/application.yml` aún dice `true`.
3. **Averías**: Q&A (2 h / fin del turno siguiente / ≥ 2 días + traslado al central) vs front (20–150 min
   aleatorios) vs ALNS histórico (120/360/1440 min) vs núcleo (solo intervalo, sin tipo).
4. **Hoja Flota**: muestra velocidades 20 / 40 / 14 km/h (auto/moto/bici), distinto de 40/25/12 del
   enunciado y del código. Probable error de la hoja; confirmar.
5. **Tamaño de flota por defecto**: Q&A y código 10/15/12; front 6/10/8.
6. **Estados**: pedido (LE039, LE013, CU, front, histórico) y vehículo (front vs histórico) sin máquina única.
7. **Pedidos y partes**: front limita cantidad 1–24 y 1 pedido por vehículo; núcleo divide en partes ≤ 4
   y agrupa varias partes/pedidos por ruta (LE027). El contrato del front no tiene Ruta/Parada/Parte.
8. **Ids**: pedido numérico secuencial desde 1000 (front) vs `VAAAAMM-Lnnnnn` (código). Almacenes
   `central` vs `CENTRAL` vs `ALM-CENTRAL`.
9. **Tiempo**: minutos relativos (front) vs `LocalDateTime` (núcleo).
10. ✅ **Ciclo de planificación** (DD-12): Sa configurable por ejecución en los tres escenarios, por defecto 10 min.
11. ✅ **Refrigerio** (DD-13): se mantiene el código, inicio en [turno + 1 h, turno + 7 h]; el front (4.ª–5.ª hora) debe alinearse.
12. **Semáforo**: LE028/RNF04 lo piden como parámetro de ejecución; el front lo guarda por navegador.
13. **Stock inicial**: front 76 % / 84 %; código 1000 (lleno).
14. **Distancia**: el front usa euclidiana en `dist()` para cercanía/riesgo.
15. **Nombres de archivo**: Q&A/LE (`aaaamm.bloqueadas`, `ventas2026mm`) vs datos reales (`bloqueo.AAMM.txt`, `ventas.AAAAMM.txt`).
16. **Numeración AG**: la LE v03 difiere de Visión/DAS. Usar la LE v03 salvo indicación contraria.
17. **CU-05** (Gestión de almacenes) es copia de CU-04.
18. **Estructura de repo**: el plan pide `/src/planificador`, `/src/visualizador`, `/data`…; el repo usa `frontend/`.

## 9. Plan de trabajo actual

Paso 1 ✅ Consolidar contexto (este archivo + `README.md`).
Paso 1b ✅ Analizar el código Java de TS/ALNS y el Q&A oficial (§3, §6, §8).
Paso 1c ✅ Backend base (`backend/`: Spring Boot 4.1.1, Java 25, módulo `planificador` con núcleo + TS) y
  modelo de datos v1.0 **aprobado** (`docs/estructura-datos/modelo-datos.md` + `.docx`, 44 tablas, DD-01..DD-31,
  PostgreSQL). Tablero y pendientes (P-01..P-05) en `TAREAS.md`.
Pasos siguientes (confirmar cada uno con el usuario):
2. Inventario de datos del front (tipos, estados, catálogos, qué es persistente vs efímero).
3. Inventario de datos del back (modelo del núcleo, parámetros, ejecuciones, ciclos, bitácora, indicadores).
4. Resolver discrepancias de §8 con el usuario.
5. Modelo conceptual → lógico (entidades, atributos, tipos, claves, relaciones, restricciones, catálogos).
6. Documento de Diseño de Estructura de Datos (base para el DDL SQL). SGBD a decidir con el usuario.

Calendario: semana 07 (29 sep–01 oct) = Documentación de Diseño completa; semana 08 (06–08 oct) =
solución integrada `sol.integrada.sem08`.

## 10. Estándares a respetar (`62.std.programacion`, `61.std.GUI`)

- Java: nombres de dominio en español; PascalCase clases, camelCase métodos/campos, `MAYUSCULAS_CON_GUION`
  constantes; 4 espacios, K&R, 120 col; Javadoc citando LE/RNF; colecciones con orden estable
  (`LinkedHashMap`) por reproducibilidad; infactibilidad = estado, no excepción; excepciones de dominio
  específicas; validar en el punto de carga; Maven + Spring Boot.
- Git: ramas `feature/AGxx-descripcion`, PR con revisión por pares, main siempre ejecutable, tags por
  hito. Commits en imperativo citando LE cuando aplique.
- GUI: tokens de color claro/oscuro, estados nunca solo por color, confirmación modal en Reiniciar.

## 11. Mapa de fuentes

`context/` (no versionado en git). Los `.md` traen imágenes base64: filtrar con
`sed -E '/^\[image[0-9]+\]: <data:image/d' archivo.md`.

| Archivo | Contenido |
|---|---|
| `03.lista.exigencias.v03.md` | LE001–LE100 y RNF01–08 (**fuente principal de requisitos**) |
| `11.ana.doc.visión.v01.md` | Visión, stakeholders, objetivos OBJ-01..08, restricciones |
| `12.ana.casos.uso.v01.md` | CU-01..CU-11 y actores |
| `14.ana.reglas.glosario.v01.md` | Reglas RN-* y glosario (**fuente de fórmulas**) |
| `21.dis.selec.algoritmos.v03.md` | ISA: TS y ALNS, pseudocódigo, operadores |
| `22.dis.experim.v03.md` | IEN: experimento TS vs ALNS, EstadoOperacion, función objetivo |
| `23.dis.arquitectura.solucion.v01.md` | DAS: vistas, entidades, decisiones DA-01..10, riesgos |
| `51.plan.proyecto.v01.md` | Plan, cronograma por semana, roles, estructura de repo |
| `61.std.GUI.v01.md` | Estándar de interfaz (≈1,3 MB por imágenes) |
| `62.std.programacion.v01.md` | Estándar de programación y Git |

`_tmp/algoritmos/` (copia temporal, ignorada por git):

| Archivo | Contenido |
|---|---|
| `README.md`, `METADATOS-PRUEBAS.md` | Uso, reglas experimentales, métricas y valores ausentes |
| `DISENO-ALGORITMOS.md` | Diseño del ALNS (histórico): estructuras, operadores, viajes múltiples, simulación |
| `experimentacion/SIMULACION-COMPARADA.md` | Simulación por ciclos, bloqueos por nodo, alimentación |
| `tabu/datos/referencias/*.xlsx` | **Q&A oficial del curso** (hojas PR_Proyecto, Guía, Mapa, Flota) |
| `experimentacion/resultados/` | CSV por ciclo y resúmenes de campañas (molde de indicadores) |
