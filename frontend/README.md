# PaqRap — Centro de Operaciones (cliente web)

Cliente web del sistema PaqRap (DP1 · 1INF54 · PUCP 2026-2 · Equipo 6F). Reemplaza al prototipo
HTML de `Prototipo/DP1-G6F-Prototipo/Prototipo` con una aplicación React organizada como ERP:
monitor en vivo sobre la retícula, dashboard de indicadores y módulos de gestión.

## Stack

| Uso | Librería |
|---|---|
| Lenguaje | TypeScript 5.9 |
| Interfaz | React 19 |
| Construcción | Vite 8 |
| Mapa (retícula 70×50) | Canvas 2D propio |
| Tiempo real | `@stomp/stompjs` 7 |
| Estado global | Zustand 5 |
| REST y caché | TanStack Query 5 |
| Estilos | Tailwind CSS 4 (tokens claro/oscuro en `src/index.css`) |
| Gráficos | Apache ECharts 6 (carga diferida) |
| Navegación | React Router 8 |
| Pruebas | Vitest 5 |

Las fuentes (Inter, IBM Plex Mono) se empaquetan con el build: no hace falta internet en el laboratorio.

## Cómo correrlo

Requiere Node 20.19+ (probado con Node 22).

```bash
npm install
npm run dev        # http://localhost:5173
npm test           # pruebas de dominio y del motor
npm run build      # genera dist/ para nginx
```

### Fuente de datos

`VITE_DATA_SOURCE` (ver `.env.example`) elige de dónde sale la simulación:

- **`local`** (por defecto): el motor corre en el navegador. Sirve para demostraciones y para
  trabajar la interfaz sin backend.
- **`server`**: la interfaz se conecta al backend Spring Boot. Los comandos van por REST (`/api`)
  y el estado llega por WebSocket/STOMP (`/ws`). En desarrollo, Vite redirige ambos a
  `BACKEND_URL` (por defecto `http://localhost:8080`).

La interfaz no cambia entre modos: solo conoce la interfaz `SimulationGateway` (`src/api/gateway.ts`).

## Estructura

```
src/
  domain/        modelo, constantes del caso, tiempo/turnos, retícula (BFS), semáforo, lectores de archivos
  engine/        SimulationEngine: port del simulador del prototipo (modo local)
  api/           SimulationGateway + implementaciones local y STOMP/REST, hooks de TanStack Query
  store/         Zustand: instantánea (4 Hz), bitácora, series, preferencias de interfaz
  components/    layout ERP (menú lateral, barra superior), UI base, tablas, gráficos
  features/      monitor, indicadores, pedidos, flota, almacenes, averías, mantenimiento, bloqueos, eventos, configuración
```

El canvas del mapa lee la última instantánea sin pasar por React (60 fps) y el resto de la interfaz
se actualiza 4 veces por segundo.

## Contrato con el backend (modo `server`)

Las formas JSON son los tipos de `src/domain/types.ts` (`SimSnapshot`, `LogEvent`, `RunConfig`,
`OrderInput`, `OrderResult`, `FileLoadSummary`, `Catalogos`). Tiempos en **minutos de simulación**
desde las 00:00 del día 1; coordenadas en km de la retícula.

### Tiempo real (STOMP sobre `/ws`)

| Tópico | Mensaje | Frecuencia sugerida |
|---|---|---|
| `/topic/simulacion/estado` | `SimSnapshot` | 4–10 por segundo mientras corre; una vez tras cada comando |
| `/topic/simulacion/eventos` | `LogEvent` (texto admite `**negrita**`) | por evento |

### REST (`/api`)

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /simulacion/estado` | — | `SimSnapshot` |
| `GET /catalogos` | — | `Catalogos` |
| `POST /simulacion/configuracion` | `RunConfig` | 204 |
| `POST /simulacion/iniciar` · `/detener` · `/reiniciar` | — | 204 |
| `POST /pedidos` | `OrderInput` | `OrderResult` |
| `POST /pedidos/lote` | `OrderInput[]` | `OrderResult[]` |
| `POST /archivos/{ventas\|bloqueos\|averias\|mantenimiento}` | texto plano del archivo | `FileLoadSummary` |
| `POST /averias` | `{ vehicleId, tipo }` | 204 |
| `POST /mantenimientos` | `{ vehicleId, horas }` | 204 |
| `POST /bloqueos` | `{ nodos: Point[], horas }` | 204 |

Errores: estado HTTP 4xx/5xx con `{ "mensaje": "..." }` (también se acepta `detail` de `ProblemDetail`).
El texto se muestra tal cual al usuario.

## Diferencias con el prototipo HTML

- La simulación ya no está pegada al DOM: vive detrás de `SimulationGateway`.
- **Corrección:** los bloqueos por archivo con tramos de varios km (p. ej. `25,45,45,45`) ahora
  bloquean cada kilómetro del tramo. En el prototipo solo se registraba la arista entre los
  extremos, que no existe en la retícula, y el bloqueo no tenía efecto en las rutas.
- La simulación 5D se detiene al cumplir los 5 días (antes reiniciaba el ciclo).
- El motor avanza por tiempo real transcurrido: una pestaña en segundo plano no frena la simulación.
- Averías, mantenimientos y bloqueos conservan historial, no solo los activos.
- Cada gráfico tiene vista de tabla; los estados siempre llevan texto además de color.
