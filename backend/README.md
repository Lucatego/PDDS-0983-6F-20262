# PaqRap · Backend

Backend del Centro de Operaciones de PaqRap (Equipo 6F · 1INF54-0983 · PUCP 2026-2). Proyecto Maven multimódulo
con Spring Boot y Java 25. Incluye el planificador **Tabu Search** (algoritmo seleccionado en el IEN v03) como
biblioteca Java pura.

Estado: B-01/B-04 implementados y B-05 en curso. Incluye migraciones y carga de archivos, motor determinista,
configuración persistida y preparación de entradas desde PostgreSQL. La orquestación y persistencia de ciclos/rutas,
WebSocket y endpoints del contrato del frontend siguen pendientes. No se ha aplicado el esquema al RDS.

## Requisitos

- **JDK 25** (`java -version` debe indicar 25). Probado con OpenJDK 25.0.4.
- No hace falta instalar Maven: se usa el **Maven Wrapper** (`mvnw`/`mvnw.cmd`), que descarga Maven 3.9.16 la
  primera vez en `~/.m2/wrapper`. Requiere acceso a Maven Central.
- Para ejecutar la aplicación: acceso a una base **PostgreSQL** y el archivo `backend/.env` (ver «Base de datos»).
  Las pruebas no lo necesitan.

## Base de datos

Por ahora la base está en **AWS** (PostgreSQL en RDS); un contenedor local queda pendiente (P-07 de `TAREAS.md`).
Las credenciales van en `backend/.env`, que está en `.gitignore` y **no se versiona**:

```bash
cp .env.example .env    # desde backend/, y completar los tres valores
```

| Variable | Contenido |
|---|---|
| `PAQRAP_DB_URL` | `jdbc:postgresql://<host>:5432/<base>?sslmode=require` (RDS exige SSL) |
| `PAQRAP_DB_USUARIO` | Usuario de la base |
| `PAQRAP_DB_CLAVE` | Contraseña |
| `PAQRAP_MIGRACIONES_HABILITADAS` | `false` por defecto; establecer `true` solo tras revisar el SQL |

- `application.yml` importa el `.env` con `spring.config.import` desde el directorio de trabajo o su superior, así
  que funciona con `java -jar` desde `backend/` y con `spring-boot:run` (que corre en `backend/aplicacion`). Una
  variable de entorno del sistema con el mismo nombre tiene prioridad sobre el `.env`.
- Sin credenciales la aplicación **no arranca** (error `'url' must start with "jdbc"` al crear el `dataSource`).
  El *security group* de RDS debe permitir la IP desde la que se conecta.
- El esquema lo crea **Flyway**, cuando se habilita explícitamente, con `aplicacion/src/main/resources/db/migration/V<n>__*.sql`
  (tarea B-03, a partir de `context/24.dis.estructura.datos.v01.md`). Hibernate solo **valida** el esquema
  (`ddl-auto: validate`); no crea tablas.
- Las pruebas excluyen por ahora el `DataSource`, JPA y Flyway (`aplicacion/src/test/resources/config/application.yml`);
  las pruebas contra la base de datos están pendientes (P-08).

## Comandos

Desde la carpeta `backend/` (en Windows, `mvnw.cmd` en lugar de `./mvnw`):

| Acción | Comando |
|---|---|
| Compilar y ejecutar todas las pruebas | `./mvnw -q verify` |
| Solo las pruebas del planificador | `./mvnw -q -pl planificador test` |
| Empaquetar sin pruebas | `./mvnw -q -DskipTests package` |
| Ejecutar el jar | `java -jar aplicacion/target/paqrap-backend.jar` |
| Ejecutar con Maven | `./mvnw -q -DskipTests install` (una vez) y luego `./mvnw -pl aplicacion spring-boot:run` |

El servidor escucha en `http://localhost:8080` (requiere `backend/.env`). Verificación:

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
        ├── simulacion           ConfiguracionSimulacion, MotorSimulacion y PreparacionSimulacion (B-05 en curso)
        └── persistencia         Carga de archivos y preparación/configuración persistida de ejecuciones
```

El único algoritmo habilitado por la aplicación es Tabu Search (`TabuSearchPlanner`); las ejecuciones con otro
algoritmo se rechazan al preparar el motor.

### Migraciones y carga de archivos (B-03/B-04)

- V1 crea las 41 tablas funcionales, FK, restricciones e índices del modelo v1.0.1. Seguridad queda postergada
  (D-06): no hay tablas `seg_*`; `registrado_por` es nullable y sin FK. V2 carga catálogos y 31 parámetros.
- `ServicioCargaArchivos.cargar(tipo, nombre, contenido, ejecucionId)` es el punto de entrada para B-08.
  Ventas, bloqueos y mantenimiento son maestros (`ejecucionId = null`). Averías requiere una ejecución
  `CONFIGURADA` y su flota ya creada; se registran como incidencias `PROGRAMADA`, con fecha relativa al inicio.
- Se aceptan los nombres oficiales y reales de ventas/bloqueos, BOM, comentarios y líneas vacías. Los códigos
  usan la línea física. Errores por línea no impiden cargar las válidas; fallas de persistencia revierten todo.
  Un nombre/periodo inválido se rechaza antes de crear la auditoría (no existe un periodo válido para registrarla).
- El SHA-256 corresponde al contenido UTF-8 recibido. Recargarlo en el mismo periodo o ejecución devuelve el
  mismo archivo y sus identificadores. Un contenido diferente para un periodo ocupado se rechaza; el reemplazo
  explícito de archivos y su autorización según uso por ejecuciones quedan fuera de esta operación.
- Mantenimiento se repite cada dos meses hasta 31/12/2029, siempre desde la fecha original. Si el día no existe
  en el mes destino, se usa el último día del mes (`LocalDate.plusMonths`). El índice fecha/unidad evita duplicados.
- No hay endpoints nuevos, simulador ni autenticación en este bloque. El mapeo JPA incluye `ArchivoCarga`;
  los registros de carga se insertan con SQL parametrizado mediante JPA. Los demás agregados se mapearán según
  los servicios de simulación que los necesiten.

Pruebas PostgreSQL optativas, sin librerías nuevas: `CargaPostgresqlTest` acepta únicamente una instancia
local desechable en `127.0.0.1:55483`, usuario `paqrap_prueba`, base `postgres`, sin contraseña. Crea un esquema
aleatorio propio, aplica Flyway y revierte los datos de cada prueba; nunca utiliza el `.env` ni RDS. El esquema
se conserva para diagnóstico y se desecha junto con la instancia. En PowerShell, desde `backend/`:

```powershell
$env:PAQRAP_PRUEBA_DB_URL = 'jdbc:postgresql://127.0.0.1:55483/postgres'
.\mvnw.cmd test
```

Sin esa variable, las pruebas PostgreSQL se omiten; las unitarias y las existentes se ejecutan sin BD.
Esta verificación local no sustituye P-08 (estrategia automatizada de BD/CI) ni acredita los 10 segundos de
carga de LE003/009 en RDS: ese rendimiento debe medirse con archivos reales y la latencia del entorno.

Verificación del 04/10/2026: 55 pruebas aprobadas y 1 opcional del planificador omitida (dataset externo no
disponible); incluye 8 de integración con PostgreSQL 18.4 y Flyway. Por disponibilidad local se usó Java 21
con `-Dmaven.compiler.release=21`; el POM sigue en Java 25 y falta repetir la verificación con ese JDK.

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

Decididas en D-02 (`TAREAS.md`). Las versiones las gestiona Spring Boot 4.1.1 (Spring Framework 7.0.9, Tomcat
11.0.24, Flyway 12.4.0, driver PostgreSQL 42.7.13, JUnit Jupiter 6.0.3, AssertJ 3.27.7).

| Módulo | Dependencia | Uso |
|---|---|---|
| aplicacion | `spring-boot-starter-webmvc` | REST bajo `/api` (reemplaza a `spring-boot-starter-web`, obsoleto en Boot 4) |
| aplicacion | `spring-boot-starter-validation` | Validación de cuerpos y propiedades |
| aplicacion | `spring-boot-starter-websocket` | STOMP en `/ws`: `SimSnapshot` y `LogEvent` (B-09) |
| aplicacion | `spring-boot-starter-data-jpa` | Entidades y repositorios sobre PostgreSQL (Hibernate) |
| aplicacion | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Migraciones versionadas del esquema |
| aplicacion | `org.postgresql:postgresql` (runtime) | Driver JDBC |
| aplicacion | `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` (test) | JUnit, AssertJ, MockMvc, `@WebMvcTest` |
| planificador | `junit-jupiter` (test) | Pruebas del planificador |

Cualquier otra librería se consulta antes de agregarla.

## Pruebas

B-05 en desarrollo: `MotorSimulacion` contiene el reloj determinista y los ciclos Sa, con rutas
comprometidas y partes estables. `RepositorioConfiguracionEjecucion` congela parámetros, semilla,
flota, turnos, velocidades y stock inicial en una transacción. Sus pruebas usan exclusivamente
PostgreSQL local optativo; la verificación del RDS corresponde al usuario. Pendientes: conectar
la orquestación, persistir ciclos/rutas y exponer los controles REST/STOMP (no disponibles todavía).
`LectorEjecucion.preparar(id)` reconstruye una entrada inmutable desde la configuración y los archivos
maestros asociados, y crea motor/planificador por ejecución sin arrancar el reloj. La integración de
esa lectura con PostgreSQL no se ejecutó en esta entrega; sigue pendiente por decisión del usuario.

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
