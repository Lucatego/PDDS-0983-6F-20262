# PaqRap · Backend

Backend del Centro de Operaciones de PaqRap (Equipo 6F · 1INF54-0983 · PUCP 2026-2). Proyecto Maven multimódulo
con Spring Boot y Java 25. Incluye el planificador **Tabu Search** (algoritmo seleccionado en el IEN v03) como
biblioteca Java pura.

Estado: base del backend (tareas B-01 y B-02 de `TAREAS.md`). Todavía no hay base de datos, WebSocket ni los
endpoints del contrato del frontend; esas decisiones están pendientes (D-01, D-02, D-04).

## Requisitos

- **JDK 25** (`java -version` debe indicar 25). Probado con OpenJDK 25.0.4.
- No hace falta instalar Maven: se usa el **Maven Wrapper** (`mvnw`/`mvnw.cmd`), que descarga Maven 3.9.16 la
  primera vez en `~/.m2/wrapper`. Requiere acceso a Maven Central.

## Comandos

Desde la carpeta `backend/` (en Windows, `mvnw.cmd` en lugar de `./mvnw`):

| Acción | Comando |
|---|---|
| Compilar y ejecutar todas las pruebas | `./mvnw -q verify` |
| Solo las pruebas del planificador | `./mvnw -q -pl planificador test` |
| Empaquetar sin pruebas | `./mvnw -q -DskipTests package` |
| Ejecutar el jar | `java -jar aplicacion/target/paqrap-backend.jar` |
| Ejecutar con Maven | `./mvnw -q -DskipTests install` (una vez) y luego `./mvnw -pl aplicacion spring-boot:run` |

El servidor escucha en `http://localhost:8080`. Verificación:

```bash
curl http://localhost:8080/api/salud
# {"estado":"OK","version":"0.1.0-SNAPSHOT","instante":"2026-09-30T20:22:09Z"}
```

`spring-boot:run` necesita que el módulo `planificador` esté instalado en el repositorio local (`install`), porque
con `-pl aplicacion` Maven no lo compila en la misma ejecución.

### Prueba opcional con los datos reales del curso

`DatasetLoaderTest.planificaDatosReales` carga `ventas.202609.txt` y `bloqueo.2609.txt` del prototipo y planifica
una instantánea. Los archivos de datos **no** se versionan en este repositorio; la prueba se omite salvo que se
defina la variable `PAQRAP_DATOS_DIR` con la carpeta `data/` que contiene `ventas.v20260909/` y
`bloqueos.v20260909/`:

```bash
PAQRAP_DATOS_DIR=../_tmp/algoritmos/alns/data ./mvnw -q -pl planificador test -Dtest=DatasetLoaderTest
```

Las demás pruebas usan un fixture propio con datos inventados en `planificador/src/test/resources/datos/`.

## Estructura

```
backend/
├── pom.xml                  POM padre (hereda de spring-boot-starter-parent 4.1.1; release 25; UTF-8)
├── mvnw, mvnw.cmd, .mvn/    Maven Wrapper 3.3.4 → Maven 3.9.16
├── planificador/            Biblioteca Java pura (sin Spring)
│   └── src/main/java/pe/pucp/paqrap/
│       ├── estricto/modelo      Modelo inmutable (records): Pedido, Vehiculo, EstadoOperacion, Solucion, ...
│       ├── estricto/servicios   PlanificadorEstricto, EvaluadorFactibilidad, CalculadorRuta, Holguras, ...
│       ├── estricto/caminos     GridMap y PathFinder (Dijkstra temporal con bloqueos)
│       ├── estricto/datos       Parsers de ventas y bloqueos, DatasetLoader
│       └── tabu                 TabuSearchPlanner, ConfiguracionTabu, vecindarios, lista tabú
└── aplicacion/              Aplicación Spring Boot (paquete base pe.pucp.paqrap.backend)
    └── src/main/java/pe/pucp/paqrap/backend/
        ├── PaqRapAplicacion     Punto de entrada
        ├── configuracion        Propiedades del planificador, beans del TS, prefijo /api
        ├── api                  SaludControlador (GET /api/salud), ManejadorErrores, RespuestaError
        ├── servicio             ServicioPlanificacion.planificar(EstadoOperacion)
        ├── simulacion           (vacío: reloj, escenarios y ciclos Sa; tareas B-05 a B-07)
        └── persistencia         (vacío: entidades, repositorios y carga de archivos; tareas B-03 y B-04)
```

Los paquetes `simulacion` y `persistencia` solo tienen un `package-info.java` que describe lo que contendrán.

### Aplicación

- **Configuración** (`aplicacion/src/main/resources/application.yml`):
  - `paqrap.planificador.tabu.*` → `PropiedadesTabu` → bean `ConfiguracionTabu`. Valores por defecto del
    experimento: `max-iteraciones` 300, `tenencia-tabu` 7, `sin-mejora-max` 30, `candidatos-por-iteracion` 400,
    `presupuesto-ms` 0 (sin límite, reproducible), `semilla` 20262.
  - `paqrap.planificador.operacion.*` → `PropiedadesOperacion` → bean `ParametrosOperacion`, con los valores de
    `ParametrosOperacion.porDefecto()` (servicio 60 min, turnos de 480 min desde las 07:00, refrigerio de 60 min
    con inicio en [60, 420] min del turno, partes de 4, velocidades TA 40, TM 25 y TB 12 km/h).
  - Cualquier valor se puede sobrescribir por variable de entorno (`PAQRAP_PLANIFICADOR_TABU_SEMILLA=7`) o
    argumento (`--paqrap.planificador.tabu.semilla=7`). Las propiedades se validan al arrancar.
- **Planificador**: bean `PlanificadorEstricto` = `TabuSearchPlanner`. No guarda estado entre llamadas, así que
  una sola instancia se comparte entre hilos.
- **`ServicioPlanificacion`**: `planificar(EstadoOperacion)` usa los parámetros configurados;
  `planificar(EstadoOperacion, ParametrosOperacion)` permite pasar los de una ejecución concreta (p. ej.
  velocidades cambiadas en caliente). La infactibilidad no se lanza como excepción: se lee en
  `resultado.metricas().estadoResultado()` (`COMPLETA`, `COLAPSO_PLANIFICACION`, `SIN_DEMANDA`).
- **API**: `ConfiguracionWeb` antepone `/api` a todo `@RestController` del paquete `pe.pucp.paqrap.backend.api`;
  los controladores nuevos declaran rutas relativas (`@GetMapping("/salud")`).
- **Errores**: `ManejadorErrores` responde siempre `{ "mensaje": "..." }` (contrato de `frontend/README.md`):
  400 para cuerpo ilegible, validación (`@Valid`) o `IllegalArgumentException` del dominio; 404 para rutas
  inexistentes; 405 para métodos no admitidos; el estado indicado para `ResponseStatusException`; 500 genérico
  (sin detalles internos) para lo demás.

### Dependencias

Solo `spring-boot-starter-web`, `spring-boot-starter-validation` y `spring-boot-starter-test` en `aplicacion`, y
`junit-jupiter` (pruebas) en `planificador`. Las versiones las gestiona Spring Boot 4.1.1 (Spring Framework 7.0.9,
Tomcat 11.0.24, JUnit Jupiter 6.0.3, AssertJ 3.27.7). Las decisiones de base de datos, migraciones, WebSocket/STOMP
y otras librerías siguen pendientes.

## Pruebas

| Módulo | Clase | Qué cubre |
|---|---|---|
| planificador | `RestriccionesTabuTest` | Lista tabú y aspiración, división de pedidos, plazo, turnos, refrigerio, mantenimiento, avería, stock, bloqueos, integridad, vecindarios, replanificación con rutas en curso, reproducibilidad, iteración de la mejor solución, validación de entrada |
| planificador | `AlimentacionTest` | Refrigerio al final de los turnos de 07:00, 15:00 y 23:00 |
| planificador | `HolguraColapsoTest` | Holgura por última parte, `COMPLETA`, `COLAPSO_PLANIFICACION`, `SIN_DEMANDA`, prioridad del objetivo |
| planificador | `DatasetLoaderTest` | Carga del fixture (ids `VAAAAMM-Lnnnnn`, almacenes, flota 10/15/12, mantenimiento), validación de nombres, TS sobre el estado cargado; datos reales opcionales |
| aplicacion | `PaqRapAplicacionTest` | Arranque del contexto y valores por defecto de la configuración |
| aplicacion | `SaludControladorTest` | `GET /api/salud` por HTTP real, prefijo `/api`, errores 404/405 con `{ mensaje }` |
| aplicacion | `ManejadorErroresTest` | Traducción de excepciones a `{ mensaje }` |
| aplicacion | `ServicioPlanificacionTest` | TS vía el servicio sobre un `EstadoOperacion` pequeño → `COMPLETA`, reproducible |

## Origen del código del planificador

El módulo `planificador` es una copia del repositorio **DP1-G6F-Prototipo**, carpeta `algoritmos/`:

| Origen | Destino |
|---|---|
| `algoritmos/comun/src/main/java/pe/pucp/paqrap/estricto/**` | `planificador/src/main/java/pe/pucp/paqrap/estricto/**` |
| `algoritmos/tabu/src/main/java/pe/pucp/paqrap/tabu/**` | `planificador/src/main/java/pe/pucp/paqrap/tabu/**` |

- Se eligió **Tabu Search** según el IEN v03 (`22.dis.experim.v03`): menor tiempo de planificación (Ta medio
  13 598 ms frente a 37 062 ms de ALNS) y mayor holgura (1093 frente a 940 min). **ALNS no se incluye.**
- Se conservan los paquetes originales (`pe.pucp.paqrap.estricto.*`, `pe.pucp.paqrap.tabu`) para mantener la
  trazabilidad con el ISA y el IEN.
- **Cambios al código original: ninguno.** Los 43 archivos son idénticos byte a byte a los del prototipo. El código
  se compilaba con `--release 17` y compila sin cambios ni advertencias (`-Xlint:all`) con `--release 25`. Por eso
  conserva su formato original (sin Javadoc completo); el código nuevo sí sigue el estándar de programación.
- Las pruebas del prototipo (`algoritmos/experimentacion/src/test/java`, con `main` propio y sin JUnit) se
  portaron a JUnit en `planificador/src/test/java/pe/pucp/paqrap/`, quitando lo relativo a ALNS, a la
  exportación CSV del experimento y a `SimulacionComparada` (se portará con la simulación).
