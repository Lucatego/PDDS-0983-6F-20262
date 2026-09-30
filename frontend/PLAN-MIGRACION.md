# Plan de migración del prototipo HTML a React

Origen: `Prototipo/DP1-G6F-Prototipo/Prototipo` (HTML + JS sin framework, ~4 000 líneas).
Destino: `frontend/` (React 19 + TypeScript 5 + Vite + Tailwind 4), con el stack
acordado para el cliente:

| Uso | Librería |
|---|---|
| Lenguaje | TypeScript 5.x |
| Interfaz | React 19 |
| Construcción | Vite |
| Mapa (retícula 70×50) | Canvas 2D |
| Tiempo real | `@stomp/stompjs` 7.x |
| Estado global | Zustand 5.x |
| REST y caché | TanStack Query 5.x |
| Estilos | Tailwind CSS 4.x |
| Gráficos | Apache ECharts |
| Navegación | React Router |

## Bloques

El trabajo se divide en bloques independientes y verificables. Cada bloque deja la
aplicación compilando.

| # | Bloque | Contenido | Estado |
|---|---|---|---|
| B0 | Andamiaje | Vite, TypeScript, Tailwind 4, fuentes locales, alias, proxy `/api` y `/ws`, Vitest | Hecho |
| B1 | Dominio | Tipos, constantes del caso, tiempo y turnos, retícula y BFS, semáforo, lectores de archivos (`ventas`, `bloqueos`, `averías`, `mantenimiento`) | Hecho |
| B2 | Motor y pasarela | `SimulationEngine` (port de `js/sim`), interfaz `SimulationGateway` con dos implementaciones: **local** (motor en el navegador) y **servidor** (STOMP + REST contra Spring Boot) | Hecho |
| B3 | Estado y datos | Stores de Zustand (simulación, interfaz), puente de instantáneas limitado a 4 Hz para el DOM, mutaciones y consultas de TanStack Query | Hecho |
| B4 | Estructura ERP | Menú lateral por secciones, barra superior con migas de pan, control de escenario, reloj de simulación, controles de ejecución, tema claro/oscuro, notificaciones | Hecho |
| B5 | Monitor en vivo | Mapa en canvas (zoom, arrastre, pellizco, clic), capas (calles, bloqueos, rutas, estelas, almacenes, pedidos, vehículos), buscador, filtros por tipo, panel de detalle, leyenda | Hecho |
| B6 | Indicadores | Dashboard con KPIs, series temporales (SLA, pedidos activos, costo), cumplimiento por prioridad, estado de flota, almacenes; cada gráfico con vista de tabla | Hecho |
| B7 | Módulos de gestión | Pedidos (alta, lote, archivo, filtros), Flota, Averías, Mantenimiento, Bloqueos, Registro de eventos, Configuración | Hecho |
| B8 | Adaptación y accesibilidad | Menú como cajón en móvil, tablas con desplazamiento, foco visible, `Escape` en diálogos, textos en los estados (no solo color) | Hecho |
| B9 | Pruebas y documentación | Pruebas de dominio y motor con Vitest, `README.md` con el contrato del backend | Hecho |
| B10 | Integración con el backend | Implementar en Spring Boot los endpoints y tópicos del contrato (`README.md`) y correr con `VITE_DATA_SOURCE=server` | Pendiente (equipo backend) |

## Qué cambia respecto del prototipo

- **La simulación ya no está pegada a la interfaz.** Vive detrás de `SimulationGateway`.
  Hoy corre en el navegador (modo `local`); cuando exista el servidor, se cambia una
  variable de entorno y la interfaz no se toca (resuelve el riesgo R-03 del DAS).
- **El mapa deja de ser la única pantalla.** Cada módulo tiene su ruta
  (`/monitor`, `/indicadores`, `/pedidos`, …) y el menú lateral agrupa Operación,
  Gestión y Sistema.
- **La simulación 5D se detiene al completar los 5 días** en vez de reiniciar el ciclo.
- **Los formularios se abren en paneles laterales** y las cargas de archivo piden
  confirmación con vista previa, igual que en el prototipo.
- **Las fuentes se sirven desde el propio build** (sin Google Fonts), para que funcione
  en el laboratorio sin salida a internet.
