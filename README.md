# PaqRap — Centro de Operaciones

Sistema de planificación y monitoreo de entregas para la empresa **PaqRap**.
Proyecto de Diseño y Desarrollo de Software (1INF54-0983) · PUCP · 2026-2 · **Equipo 6F**.

Integrantes: Lucas Alvites Galarza · Yaser Fernández Ccerhuayo · Eliezer Villarreal Quispe ·
Gandy Zinanyuca Huillca.

## El problema

PaqRap vende un único producto (P) y se compromete a entregarlo en 36 h (regular) o en 4, 8, 12 o
18 h (priorizado). Opera con un almacén central de inventario ilimitado, dos almacenes intermedios de
1000 unidades que se recargan cada noche, y una flota de autos, motos y bicicletas. La planificación
es manual y reacciona de forma improvisada ante calles bloqueadas y averías.

La solución:

- **Registra pedidos** de forma manual, por lote o desde archivos de ventas.
- **Planifica y replanifica rutas** con dos metaheurísticas en Java (Tabu Search y ALNS), respetando
  capacidades, plazos, turnos, refrigerio, inventario, bloqueos y averías.
- **Muestra la operación en un mapa en tiempo real** (retícula de 70 × 50 km), accesible desde varios
  dispositivos a la vez.
- Ejecuta tres escenarios: **Día a día**, **Simulación de 5 días** (en 30–60 minutos reales) y
  **Simulación hasta el colapso**.
- Presenta **indicadores** de cumplimiento, costo y uso de flota con semáforo configurable.

## Arquitectura

```
Navegador (N dispositivos)            Servidor de aplicación                 Base de datos
┌──────────────────────┐  REST /api   ┌────────────────────────────────┐    ┌──────────────┐
│ React (frontend/)    │ ───────────▶ │ Spring Boot · Java 25          │ ─▶ │ SGBD         │
│ mapa, módulos, KPIs  │ ◀─────────── │ reloj y escenarios, servicios, │    │ relacional   │
└──────────────────────┘  STOMP /ws   │ planificador TS / ALNS         │    └──────────────┘
                                      └────────────────────────────────┘
```

| Componente | Tecnología | Estado |
|---|---|---|
| Cliente web | React 19, TypeScript, Vite, Tailwind, Zustand, TanStack Query, ECharts, STOMP | Construido (`frontend/`) |
| Servidor de aplicación | Spring Boot, Java 25, Maven | Pendiente |
| Planificador | Java, Tabu Search y ALNS sobre un núcleo común | Construido en el repositorio de algoritmos (`DP1-G6F-Prototipo`) |
| Base de datos | Relacional (SGBD por definir) | Pendiente: en diseño de estructura de datos |

## Estructura del repositorio

```
.
├── frontend/     Cliente web React (ver frontend/README.md)
├── backend/      Servidor Spring Boot y módulo planificador (ver backend/README.md)
├── context/      Documentación versionada en Markdown (curso y entregables del equipo)
├── docs/         Salidas generadas (.docx, PDF); carpeta local, no versionada
├── CLAUDE.md     Contexto consolidado del proyecto para asistentes de IA
├── TAREAS.md     Tablero de tareas y pendientes
├── FORMATO-DOCUMENTOS.md   Formato de los entregables .docx
└── README.md
```

## Ejecutar el frontend

Requiere Node 20.19 o superior.

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm test
npm run build
```

Por defecto la simulación corre en el navegador (`VITE_DATA_SOURCE=local`). Con
`VITE_DATA_SOURCE=server` la interfaz se conecta al backend: comandos por REST en `/api` y estado en
tiempo real por STOMP en `/ws`. El contrato completo está en [`frontend/README.md`](frontend/README.md).

## Documentación

| Documento | Contenido |
|---|---|
| `03.lista.exigencias.v03` | Requisitos funcionales LE001–LE100 y no funcionales |
| `11.ana.doc.visión.v01` | Visión, stakeholders y objetivos |
| `12.ana.casos.uso.v01` | Casos de uso CU-01 a CU-11 |
| `14.ana.reglas.glosario.v01` | Reglas de negocio y glosario |
| `21.dis.selec.algoritmos.v03` | Selección de algoritmos (Tabu Search y ALNS) |
| `22.dis.experim.v03` | Experimentación numérica TS vs ALNS |
| `23.dis.arquitectura.solucion.v01` | Arquitectura de la solución |
| `24.dis.estructura.datos.v01` | Diseño de estructura de datos (modelo de datos, PostgreSQL) |
| `51.plan.proyecto.v01` | Plan de proyecto y cronograma |
| `61.std.GUI.v01` | Estándar de interfaz gráfica |
| `62.std.programacion.v01` | Estándar de programación y uso de Git |

## Estado actual

- [x] Análisis, arquitectura y experimentación numérica
- [x] Cliente web con motor de simulación local
- [ ] Documento de diseño de estructura de datos (en curso)
- [ ] Base de datos
- [ ] Servidor Spring Boot e integración con el planificador
- [ ] Integración frontend ↔ backend (`VITE_DATA_SOURCE=server`)

## Licencia

Ver [LICENSE](LICENSE).
