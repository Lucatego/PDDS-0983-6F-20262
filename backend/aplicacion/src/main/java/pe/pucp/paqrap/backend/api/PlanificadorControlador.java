package pe.pucp.paqrap.backend.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.pucp.paqrap.backend.configuracion.PropiedadesOperacion;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTabu;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTiempoReal;
import pe.pucp.paqrap.backend.persistencia.ServicioCargaArchivos;
import pe.pucp.paqrap.backend.persistencia.TipoArchivo;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.estricto.modelo.Averia;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.Mantenimiento;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;

/** REST del contrato consumido por el frontend (B-08). El prefijo /api se agrega en ConfiguracionWeb. */
@RestController
@RequestMapping
public class PlanificadorControlador {
    private static final AtomicLong PEDIDOS = new AtomicLong(System.currentTimeMillis());
    private static final Nodo CENTRAL = new Nodo(27, 14);

    private final OrquestadorSimulacion orquestador;
    private final ServicioCargaArchivos cargas;
    private final PropiedadesOperacion operacion;
    private final PropiedadesTabu tabu;
    private final PropiedadesTiempoReal tiempoReal;
    private final Map<String, Integer> conteoArchivos = new java.util.concurrent.ConcurrentHashMap<>();

    public PlanificadorControlador(OrquestadorSimulacion orquestador, ServicioCargaArchivos cargas,
            PropiedadesOperacion operacion, PropiedadesTabu tabu, PropiedadesTiempoReal tiempoReal) {
        this.orquestador = orquestador;
        this.cargas = cargas;
        this.operacion = operacion;
        this.tabu = tabu;
        this.tiempoReal = tiempoReal;
    }

    @GetMapping("/catalogos")
    public Map<String, Object> catalogos() {
        return Map.of("vehicleTypes", List.of(
                Map.of("key", "auto", "label", "Auto", "plural", "Autos", "prefix", "TA", "capacity", 24,
                        "speed", 40, "cost", 8, "colorVar", "--veh-auto", "emoji", "🚗"),
                Map.of("key", "moto", "label", "Moto", "plural", "Motos", "prefix", "TM", "capacity", 8,
                        "speed", 25, "cost", 6, "colorVar", "--veh-moto", "emoji", "🏍️"),
                Map.of("key", "bici", "label", "Bicicleta", "plural", "Bicicletas", "prefix", "TB", "capacity", 4,
                        "speed", 12, "cost", 3, "colorVar", "--veh-bici", "emoji", "🚲")),
                "fallaTypes", List.of(
                        Map.of("tipo", 1, "label", "Tipo 1 · leve", "short", "Leve", "minMin", 20, "maxMin", 40,
                                "colorVar", "--warning"),
                        Map.of("tipo", 2, "label", "Tipo 2 · moderada", "short", "Moderada", "minMin", 45,
                                "maxMin", 80, "colorVar", "--serious"),
                        Map.of("tipo", 3, "label", "Tipo 3 · grave", "short", "Grave", "minMin", 90, "maxMin", 150,
                                "colorVar", "--critical")),
                "modalidades", List.of(Map.of("horas", 36, "label", "Regular (36 h)"),
                        Map.of("horas", 18, "label", "Priorizada (18 h)"), Map.of("horas", 12, "label", "Priorizada (12 h)"),
                        Map.of("horas", 8, "label", "Priorizada (8 h)"), Map.of("horas", 4, "label", "Priorizada (4 h)")));
    }

    @PostMapping("/simulacion/configuracion")
    public ResponseEntity<Void> configurar(@RequestBody RunConfig cfg) {
        if (cfg.startDate() == null || cfg.startTime() == null || cfg.fleet() == null || cfg.capacities() == null || cfg.shiftStarts() == null
                || cfg.shiftStarts().length != 3) throw new IllegalArgumentException("Configuración incompleta");
        int inicioTurno = cfg.shiftStarts()[0];
        for (int i = 0; i < 3; i++) {
            if (cfg.shiftStarts()[i] != (inicioTurno + i * 480) % 1440)
                throw new IllegalArgumentException("Los turnos deben estar separados por 8 horas");
        }
        var flota = new java.util.EnumMap<TipoVehiculo, Integer>(TipoVehiculo.class);
        flota.put(TipoVehiculo.TA, cfg.fleet().auto());
        flota.put(TipoVehiculo.TM, cfg.fleet().moto());
        flota.put(TipoVehiculo.TB, cfg.fleet().bici());
        var caps = Map.of("NOROESTE", cfg.capacities().noroeste(), "ESTE", cfg.capacities().este());
        var escenario = switch (cfg.scenario()) {
            case "diaria" -> ConfiguracionSimulacion.Escenario.DIA_A_DIA;
            case "5d" -> ConfiguracionSimulacion.Escenario.SIMULACION_5D;
            case "colapso" -> ConfiguracionSimulacion.Escenario.COLAPSO;
            default -> throw new IllegalArgumentException("Escenario no reconocido");
        };
        var inicio = LocalDate.parse(cfg.startDate()).atTime(LocalTime.parse(cfg.startTime()));
        var params = operacion.aParametros();
        params = new pe.pucp.paqrap.estricto.modelo.ParametrosOperacion(params.servicioMinutos(),
                params.plazoIncluyeServicio(), params.turnoMinutos(), inicioTurno, params.descansoDesde(),
                params.descansoHasta(), params.descansoMinutos(), params.tamanioParte(), params.costoFijoVehiculo(),
                params.penalizacionPaquetePendiente(), params.velocidades());
        var configuracion = new ConfiguracionSimulacion(escenario, inicio, flota, caps, 60,
                escenario == ConfiguracionSimulacion.Escenario.DIA_A_DIA ? 1.0 / 60 : tiempoReal.minutosPorSegundoBase(),
                tabu.semilla(), params, Boolean.TRUE.equals(cfg.considerarIncidencias()), false, 0, false);
        orquestador.configurar(configuracion, tabu.aConfiguracion());
        conteoArchivos.clear();
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia en caliente el multiplicador de velocidad de 5D y Colapso (LE058). El efecto se aplica desde el siguiente
     * pulso del reloj; Día a día no admite cambio.
     */
    @PostMapping("/simulacion/velocidad")
    public ResponseEntity<Void> velocidad(@RequestBody VelocidadInput input) {
        Double factor = input == null ? null : input.factor();
        if (factor == null || factor != Math.rint(factor)) {
            throw new IllegalArgumentException("El factor de velocidad debe ser 1, 2, 5 o 10");
        }
        orquestador.establecerVelocidad(factor.intValue());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/simulacion/iniciar") public ResponseEntity<Void> iniciar() { orquestador.iniciar(); return ResponseEntity.noContent().build(); }
    @PostMapping("/simulacion/detener") public ResponseEntity<Void> detener() { orquestador.detener(); return ResponseEntity.noContent().build(); }
    @PostMapping("/simulacion/reiniciar") public ResponseEntity<Void> reiniciar() { orquestador.reiniciar(); conteoArchivos.clear(); return ResponseEntity.noContent().build(); }

    @PostMapping("/pedidos")
    public OrderResult pedido(@RequestBody OrderInput input) { return registrar(input); }

    @PostMapping("/pedidos/lote")
    public List<OrderResult> pedidos(@RequestBody List<OrderInput> inputs) { return inputs.stream().map(this::registrar).toList(); }

    private OrderResult registrar(OrderInput input) {
        try {
            if (input == null || input.clientId() == null || input.clientId().isBlank() || input.clientId().length() > 20
                    || input.qty() < 1 || input.qty() > 24 || input.x() < 0 || input.x() > 70 || input.y() < 0 || input.y() > 50
                    || !List.of(4, 8, 12, 18, 36).contains(input.hourLimit()))
                throw new IllegalArgumentException("Datos de pedido fuera de rango");
            var motor = motor();
            long pedidoId = PEDIDOS.incrementAndGet();
            String id = "M" + pedidoId;
            var pedido = new Pedido(id, motor.reloj(), new Nodo(input.x(), input.y()), input.qty(), input.hourLimit(), input.clientId());
            double dist = Math.hypot(input.x() - CENTRAL.x(), input.y() - CENTRAL.y());
            boolean riesgo = input.hourLimit() < dist / 40.0;
            orquestador.registrarPedido(pedido);
            return new OrderResult(true, pedidoId, riesgo, null);
        } catch (RuntimeException ex) { return new OrderResult(false, null, false, ex.getMessage()); }
    }

    @PostMapping("/archivos/{tipo}")
    public Map<String, Object> cargar(@PathVariable String tipo, @RequestBody String contenido,
            @RequestHeader(value = "X-Nombre-Archivo", required = false) String nombre,
            @RequestParam(value = "nombre", required = false) String nombreQuery) {
        TipoArchivo clase = switch (tipo.toLowerCase()) {
            case "ventas" -> TipoArchivo.VENTAS; case "bloqueos" -> TipoArchivo.BLOQUEOS;
            case "averias" -> TipoArchivo.AVERIAS; case "mantenimiento" -> TipoArchivo.MANTENIMIENTO;
            default -> throw new IllegalArgumentException("Tipo de archivo no reconocido");
        };
        String filename = nombre != null ? nombre : nombreQuery;
        if (filename == null || filename.isBlank()) filename = nombreSugerido(clase, contenido);
        Long ejecucion = clase == TipoArchivo.AVERIAS ? orquestador.ejecucionId() : null;
        var resultado = cargas.cargar(clase, filename, contenido, ejecucion);
        conteoArchivos.put(tipo.toLowerCase(), resultado.aceptados());
        return Map.of("kind", tipo.toLowerCase(), "count", resultado.aceptados(), "immediate", 0,
                "message", resultado.mensaje() == null ? "Archivo procesado" : resultado.mensaje());
    }

    private String nombreSugerido(TipoArchivo tipo, String contenido) {
        LocalDate fecha = orquestador.motor() == null ? LocalDate.now() : orquestador.motor().configuracion().inicio().toLocalDate();
        return switch (tipo) {
            case VENTAS -> "ventas" + fecha.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")) + ".txt";
            case BLOQUEOS -> "bloqueo." + fecha.format(java.time.format.DateTimeFormatter.ofPattern("MMyy")) + ".txt";
            case AVERIAS -> "averias.txt";
            case MANTENIMIENTO -> {
                var first = contenido.lines().map(String::trim).filter(s -> s.matches("\\d{8}:T[AMB]\\d{2}"))
                        .findFirst().orElse(null);
                if (first == null) throw new IllegalArgumentException("Archivo de mantenimiento sin fecha válida");
                int month = Integer.parseInt(first.substring(4, 6));
                yield "mant.preventivo." + month + "." + (month % 12 + 1) + ".txt";
            }
        };
    }

    @PostMapping("/averias") public ResponseEntity<Void> averia(@RequestBody AveriaInput input) {
        var motor = motor();
        validarVehiculoActivo(motor, input.vehicleId());
        validarVehiculoEnRuta(motor, input.vehicleId());
        TipoVehiculo.desdeCodigo(input.vehicleId());
        if (input.tipo() < 1 || input.tipo() > 3) throw new IllegalArgumentException("Tipo de avería inválido");
        String regla = switch (input.tipo()) {
            case 1 -> "DURACION_FIJA"; case 2 -> "FIN_TURNO_SIGUIENTE"; case 3 -> "DIAS_Y_TURNO";
            default -> throw new IllegalArgumentException("Tipo de avería inválido");
        };
        LocalDateTime fin = pe.pucp.paqrap.backend.simulacion.ReglaAveria.calcularFin(regla, motor.reloj(),
                input.tipo() == 1 ? 120 : null, input.tipo() == 3 ? 2 : null,
                input.tipo() == 3 ? 900 : null, motor.configuracion().operacion().turnoMinutos(),
                motor.configuracion().operacion().inicioTurnoMinuto());
        var averia = new Averia(input.vehicleId().toUpperCase(), motor.reloj(), fin, (short) input.tipo());
        orquestador.registrarAveria(averia);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mantenimientos") public ResponseEntity<Void> mantenimiento(@RequestBody MantenimientoInput input) {
        var motor = motor(); validarVehiculoActivo(motor, input.vehicleId()); validarVehiculoDisponible(motor, input.vehicleId()); TipoVehiculo.desdeCodigo(input.vehicleId());
        if (input.horas() <= 0 || input.horas() > 720) throw new IllegalArgumentException("Duración de mantenimiento fuera de rango");
        orquestador.registrarMantenimiento(new Mantenimiento(input.vehicleId().toUpperCase(), motor.reloj(), motor.reloj().plusHours(input.horas())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bloqueos") public ResponseEntity<Void> bloqueo(@RequestBody BloqueoInput input) {
        var motor = motor();
        if (input.nodos() == null || input.nodos().size() < 2 || input.horas() <= 0 || input.horas() > 720)
            throw new IllegalArgumentException("Bloqueo inválido");
        var nodos = input.nodos().stream().map(p -> new Nodo(p.x(), p.y())).toList();
        orquestador.registrarBloqueo(new Bloqueo(motor.reloj(), motor.reloj().plusHours(input.horas()), nodos));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/simulacion/estado")
    public Map<String, Object> estado() {
        MotorSimulacion m = orquestador.motor();
        if (m == null) {
            var filesVacios = filesSnapshot();
            return Map.ofEntries(Map.entry("scenario", "diaria"), Map.entry("configured", false), Map.entry("running", false),
                    Map.entry("waitingFirstOrder", false), Map.entry("collapsed", false), Map.entry("finished", false),
                    Map.entry("simMin", 0), Map.entry("speedFactor", 1), Map.entry("simMinPerSec", 1.0 / 60),
                    Map.entry("runStartSimMin", 0), Map.entry("cycleDay", 1),
                    Map.entry("epochDate", LocalDate.now().toString()), Map.entry("runElapsedMs", 0), Map.entry("shiftStarts", List.of(420, 900, 1380)),
                    Map.entry("fleet", Map.of("auto", 0, "moto", 0, "bici", 0)), Map.entry("vehicles", List.of()),
                    Map.entry("orders", List.of()), Map.entry("orderHistory", List.of()), Map.entry("incidents", List.of()),
                    Map.entry("incidentHistory", List.of()), Map.entry("warehouses", List.of()),
                    Map.entry("stats", Map.of("deliveredTotal", 0, "deliveredToday", 0, "onTime", 0, "late", 0,
                            "cost", 0, "distanceKm", 0, "byPriority", Map.of(), "bySector", Map.of())),
                    Map.entry("flashes", List.of()), Map.entry("files", filesVacios));
        }
        var cfg = m.configuracion();
        var vehicles = new ArrayList<Map<String,Object>>();
        for (var v : m.flotaProyectada()) vehicles.add(mapearVehiculo(m, v));
        var pedidosAbiertos = new ArrayList<Map<String,Object>>(); var historialPedidos = new ArrayList<Map<String,Object>>();
        int entregados = 0, aTiempo = 0, tarde = 0, entregadosHoy = 0;
        var porSector = new LinkedHashMap<String,int[]>();
        for (var p : m.pedidos().values()) {
            long id = apiId(p.original.id());
            var order = new LinkedHashMap<String,Object>(); order.put("id", id); order.put("clientId", p.original.clienteId());
            order.put("pos", Map.of("x",p.original.ubicacion().x(),"y",p.original.ubicacion().y())); order.put("qty",p.original.cantidad());
            order.put("priority",p.original.plazoHoras()); order.put("createdAt",ChronoUnit.MINUTES.between(cfg.inicio().toLocalDate().atStartOfDay(),p.original.fechaRegistro()));
            order.put("deadline",ChronoUnit.MINUTES.between(cfg.inicio().toLocalDate().atStartOfDay(),p.original.deadline()));
            order.put("status",p.enRuta > 0 ? "assigned":"pending"); order.put("reprogramado",false);
            double distanciaCentral = Math.hypot(p.original.ubicacion().x()-CENTRAL.x(),p.original.ubicacion().y()-CENTRAL.y());
            order.put("enRiesgo",p.original.plazoHoras() < distanciaCentral / 40.0);
            var asignacion = m.viajes().stream().filter(v -> v.partes.stream().anyMatch(parte -> parte.pedido().equals(p.original.id())))
                    .max(java.util.Comparator.comparing((MotorSimulacion.Viaje v) -> v.despachado).thenComparing(v -> v.plan.salida())).orElse(null);
            order.put("vehicleId",asignacion == null ? null : asignacion.plan.ruta().vehiculo());
            order.put("warehouseId",asignacion == null ? null : asignacion.plan.ruta().almacenOrigen().toLowerCase());
            if (p.entregada == p.original.cantidad() || p.noCumplido != null) {
                String estadoFinal = p.noCumplido != null ? "no cumplido" : "entregado";
                order.put("estadoFinal",estadoFinal); order.put("closedAt",ChronoUnit.MINUTES.between(cfg.inicio().toLocalDate().atStartOfDay(),p.noCumplido != null ? p.noCumplido : p.entregaFinal));
                historialPedidos.add(order);
                if (p.entregada == p.original.cantidad()) {
                    entregados++; boolean enPlazo = p.noCumplido == null; if(enPlazo) aTiempo++; else tarde++;
                    if(p.entregaFinal != null && p.entregaFinal.toLocalDate().equals(m.reloj().toLocalDate())) entregadosHoy++;
                    int sx = Math.min(60, Math.floorDiv(p.original.ubicacion().x(),10)*10), sy = Math.min(40,Math.floorDiv(p.original.ubicacion().y(),10)*10);
                    int[] bucket = porSector.computeIfAbsent("Sector ("+sx+"–"+(sx+10)+", "+sy+"–"+(sy+10)+")", k -> new int[2]);
                    bucket[0]++; if(enPlazo) bucket[1]++;
                }
            } else pedidosAbiertos.add(order);
        }
        var buckets = new LinkedHashMap<String,Map<String,Integer>>();
        for (int h : List.of(4,8,12,18,36)) {
            int totalPlazo = (int)m.pedidos().values().stream().filter(p -> p.original.plazoHoras()==h && p.entregada==p.original.cantidad()).count();
            int enPlazo = (int)m.pedidos().values().stream().filter(p -> p.original.plazoHoras()==h && p.entregada==p.original.cantidad() && p.noCumplido==null).count();
            buckets.put(Integer.toString(h), Map.of("delivered",totalPlazo,"onTime",enPlazo));
        }
        double costo = m.viajes().stream().mapToDouble(v -> v.plan.costo()).sum();
        double distancia = m.viajes().stream().mapToDouble(v -> v.plan.distanciaKm()).sum();
        var sectores = new LinkedHashMap<String,Map<String,Integer>>();
        porSector.forEach((sector,bucket) -> sectores.put(sector, Map.of("delivered",bucket[0],"onTime",bucket[1])));
        var stats = Map.of("deliveredTotal",entregados,"deliveredToday",entregadosHoy,"onTime",aTiempo,"late",tarde,"cost",costo,"distanceKm",distancia,"byPriority",buckets,"bySector",sectores);
        var flashes = m.eventos().stream().filter(e -> e.tipo().equals("PEDIDO_ENTREGADO") && e.pedido() != null
                && ChronoUnit.MINUTES.between(e.fecha(), m.reloj()) < 22)
                .map(e -> m.pedidos().get(e.pedido())).filter(java.util.Objects::nonNull)
                .map(p -> Map.<String,Object>of("pos", Map.of("x",p.original.ubicacion().x(),"y",p.original.ubicacion().y()),
                        "onTime",p.noCumplido==null,"born",ChronoUnit.MINUTES.between(cfg.inicio().toLocalDate().atStartOfDay(),p.entregaFinal)))
                .toList();
        var incidentes = new ArrayList<Map<String,Object>>(); var historialIncidentes = new ArrayList<Map<String,Object>>();
        var posiciones = new LinkedHashMap<String, Nodo>(); m.flotaProyectada().forEach(v -> posiciones.put(v.codigo(), v.ubicacionInicial()));
        int incidenteId = 1;
        for (var b : m.bloqueos()) {
            var nodos = b.puntos().stream().map(n -> Map.of("x", n.x(), "y", n.y())).toList();
            var aristas = new ArrayList<String>();
            for (int i=1;i<b.puntos().size();i++) aristas.add(edgeKey(b.puntos().get(i-1), b.puntos().get(i)));
            var item = incidenteBase("bloqueo", incidenteId++, b.inicio(), b.fin(), cfg.inicio(), "manual");
            item.put("nodes", nodos); item.put("edges", aristas); item.put("id", incidenteId-1);
            clasificarIncidente(item, b.inicio(), b.fin(), m.reloj(), incidentes, historialIncidentes);
        }
        for (var a : m.averias()) {
            var p = posiciones.getOrDefault(a.vehiculo(), CENTRAL);
            var item = incidenteBase("falla", incidenteId++, a.inicio(), a.fin(), cfg.inicio(), "manual");
            item.put("id", incidenteId-1); item.put("vehicleId", a.vehiculo()); item.put("tipo", a.tipo()); item.put("pos", Map.of("x",p.x(),"y",p.y()));
            clasificarIncidente(item, a.inicio(), a.fin(), m.reloj(), incidentes, historialIncidentes);
        }
        for (var mt : m.mantenimientos()) {
            var p = posiciones.getOrDefault(mt.vehiculo(), CENTRAL);
            var item = incidenteBase("mantenimiento", incidenteId++, mt.inicio(), mt.fin(), cfg.inicio(), "manual");
            item.put("id", incidenteId-1); item.put("vehicleId", mt.vehiculo()); item.put("pos", Map.of("x",p.x(),"y",p.y()));
            item.put("horas", ChronoUnit.HOURS.between(mt.inicio(), mt.fin()));
            clasificarIncidente(item, mt.inicio(), mt.fin(), m.reloj(), incidentes, historialIncidentes);
        }
        long salidasHoyCentral = m.viajes().stream().filter(v -> v.plan.ruta().almacenOrigen().equals("CENTRAL")
                && v.plan.salida().toLocalDate().equals(m.reloj().toLocalDate())).count();
        long salidasHoyNoroeste = m.viajes().stream().filter(v -> v.plan.ruta().almacenOrigen().equals("NOROESTE")
                && v.plan.salida().toLocalDate().equals(m.reloj().toLocalDate())).count();
        long salidasHoyEste = m.viajes().stream().filter(v -> v.plan.ruta().almacenOrigen().equals("ESTE")
                && v.plan.salida().toLocalDate().equals(m.reloj().toLocalDate())).count();
        var stockActual = new LinkedHashMap<String,Integer>(); m.almacenes().forEach(a -> stockActual.put(a.id(), a.stock()));
        var warehouses = List.of(Map.of("id","central","name","Almacén Central","shortName","Central","pos",Map.of("x",27,"y",14),"infinite",true,"capacity",0,"stock",0,"dispatchedToday",salidasHoyCentral),Map.of("id","noroeste","name","Almacén Nor-Oeste","shortName","Nor-Oeste","pos",Map.of("x",12,"y",38),"infinite",false,"capacity",cfg.capacidades().get("NOROESTE"),"stock",stockActual.getOrDefault("NOROESTE",0),"dispatchedToday",salidasHoyNoroeste),Map.of("id","este","name","Almacén Este","shortName","Este","pos",Map.of("x",57,"y",27),"infinite",false,"capacity",cfg.capacidades().get("ESTE"),"stock",stockActual.getOrDefault("ESTE",0),"dispatchedToday",salidasHoyEste));
        String scenario = switch(cfg.escenario()){case DIA_A_DIA->"diaria";case SIMULACION_5D->"5d";case COLAPSO->"colapso";};
        var files = filesSnapshot();
        return Map.ofEntries(Map.entry("scenario",scenario),Map.entry("configured",true),Map.entry("running",m.estado().equals("EN_CURSO")),Map.entry("waitingFirstOrder",m.estado().equals("ESPERANDO_PEDIDO")),Map.entry("collapsed",m.estado().equals("COLAPSADA")),Map.entry("finished",m.esFinal()),Map.entry("simMin",ChronoUnit.MINUTES.between(cfg.inicio().toLocalDate().atStartOfDay(),m.reloj())),Map.entry("speedFactor",m.factorVelocidad()),Map.entry("simMinPerSec",m.minutosPorSegundo()),Map.entry("runStartSimMin",ChronoUnit.MINUTES.between(cfg.inicio().toLocalDate().atStartOfDay(),cfg.inicio())),Map.entry("cycleDay",(int)(ChronoUnit.DAYS.between(cfg.inicio().toLocalDate(),m.reloj().toLocalDate())+1)),Map.entry("epochDate",cfg.inicio().toLocalDate().toString()),Map.entry("runElapsedMs",m.tiempoRealMs()),Map.entry("shiftStarts",List.of(cfg.operacion().inicioTurnoMinuto(),(cfg.operacion().inicioTurnoMinuto()+480)%1440,(cfg.operacion().inicioTurnoMinuto()+960)%1440)),Map.entry("fleet",Map.of("auto",cfg.flota().get(TipoVehiculo.TA),"moto",cfg.flota().get(TipoVehiculo.TM),"bici",cfg.flota().get(TipoVehiculo.TB))),Map.entry("vehicles",vehicles),Map.entry("orders",pedidosAbiertos),Map.entry("orderHistory",historialPedidos),Map.entry("incidents",incidentes),Map.entry("incidentHistory",historialIncidentes),Map.entry("warehouses",warehouses),Map.entry("stats",stats),Map.entry("flashes",flashes),Map.entry("files",files));
    }

    private static LinkedHashMap<String,Object> incidenteBase(String kind, int id, LocalDateTime inicio, LocalDateTime fin,
            LocalDateTime epoch, String origin) {
        var result = new LinkedHashMap<String,Object>(); result.put("kind",kind); result.put("id",id);
        result.put("since",ChronoUnit.MINUTES.between(epoch.toLocalDate().atStartOfDay(),inicio));
        result.put("until",ChronoUnit.MINUTES.between(epoch.toLocalDate().atStartOfDay(),fin)); result.put("origin",origin);
        return result;
    }

    private static void clasificarIncidente(Map<String,Object> incident, LocalDateTime inicio, LocalDateTime fin,
            LocalDateTime reloj, List<Map<String,Object>> activos, List<Map<String,Object>> historial) {
        if (!fin.isAfter(reloj)) historial.add(0, incident);
        else activos.add(incident);
    }

    private static String edgeKey(Nodo a, Nodo b) {
        String primero = a.x()+","+a.y(), segundo=b.x()+","+b.y();
        return primero.compareTo(segundo) <= 0 ? primero+"-"+segundo : segundo+"-"+primero;
    }

    private static Map<String,Object> mapearVehiculo(MotorSimulacion motor, pe.pucp.paqrap.estricto.modelo.Vehiculo unidad) {
        var result = new LinkedHashMap<String,Object>();
        result.put("id", unidad.codigo()); result.put("type", switch(unidad.tipo()){case TA->"auto";case TM->"moto";case TB->"bici";});
        result.put("capacity", unidad.tipo().capacidad()); result.put("speed", unidad.tipo().velocidadKmh()); result.put("costPerKm", unidad.tipo().costoPorKm());
        result.put("home", "central"); result.put("state", "idle"); result.put("pos", point(unidad.ubicacionInicial()));
        result.put("path", null); result.put("pathIdx", 0); result.put("orderId", null); result.put("timer", 0);
        result.put("heading", 0); result.put("trail", List.of()); result.put("returnTarget", null);
        var viaje = motor.viajes().stream().filter(v -> v.plan.ruta().vehiculo().equals(unidad.codigo()) && !v.terminado)
                .max(java.util.Comparator.comparing((MotorSimulacion.Viaje v) -> v.despachado)
                        .thenComparing(v -> v.plan.salida())).orElse(null);
        if (viaje == null) { aplicarEstadoIncidencia(motor, unidad.codigo(), result); return result; }
        result.put("home", viaje.plan.ruta().almacenOrigen().toLowerCase());
        result.put("returnTarget", viaje.plan.almacenRetorno().toLowerCase());
        if (!viaje.despachado) { aplicarEstadoIncidencia(motor, unidad.codigo(), result); return result; }
        var pasos = viaje.plan.caminos().stream().flatMap(c -> c.pasos().stream()).toList();
        var ruta = new ArrayList<Nodo>();
        if (!viaje.plan.caminos().isEmpty()) {
            ruta.add(viaje.plan.caminos().getFirst().origen());
            for (var camino : viaje.plan.caminos()) for (var paso : camino.pasos()) ruta.add(paso.destino());
        }
        var puntos = ruta.stream().map(PlanificadorControlador::point).toList();
        result.put("path", puntos);
        int pasoIdx = 0; Nodo posicion = ruta.isEmpty() ? unidad.ubicacionInicial() : ruta.getFirst();
        double heading = 0; boolean encontrado = false;
        for (int i=0;i<pasos.size();i++) {
            var paso = pasos.get(i);
            if (motor.reloj().isBefore(paso.salida())) { posicion = paso.origen(); pasoIdx = i; encontrado = true; break; }
            if (!motor.reloj().isBefore(paso.llegada())) { posicion = paso.destino(); pasoIdx = i+1; continue; }
            long duracion = Math.max(1, ChronoUnit.MILLIS.between(paso.salida(), paso.llegada()));
            double avance = (double)ChronoUnit.MILLIS.between(paso.salida(), motor.reloj()) / duracion;
            posicion = new Nodo(paso.origen().x(), paso.origen().y());
            result.put("pos", Map.of("x", paso.origen().x() + (paso.destino().x()-paso.origen().x())*avance,
                    "y", paso.origen().y() + (paso.destino().y()-paso.origen().y())*avance));
            heading = Math.atan2(paso.destino().y()-paso.origen().y(), paso.destino().x()-paso.origen().x());
            pasoIdx = i; encontrado = true; break;
        }
        if (!encontrado || !(result.get("pos") instanceof Map<?,?>)) result.put("pos", point(posicion));
        result.put("pathIdx", Math.min(pasoIdx, Math.max(0, puntos.size()-1))); result.put("heading", heading);
        String state = "toClient"; int stop = 0;
        for (int i=0;i<viaje.plan.paradas().size();i++) {
            var parada = viaje.plan.paradas().get(i);
            if (!motor.reloj().isBefore(parada.llegada()) && motor.reloj().isBefore(parada.finServicio())) {
                state = "atClient"; stop = i; result.put("timer", Math.max(0, ChronoUnit.MINUTES.between(motor.reloj(), parada.finServicio())));
                if (i < viaje.plan.caminos().size()) result.put("pos", point(viaje.plan.caminos().get(i).coordenadas().getLast()));
                break;
            }
            if (!motor.reloj().isBefore(parada.finServicio())) stop = i+1;
        }
        if (stop >= viaje.plan.paradas().size() && !motor.reloj().isBefore(viaje.plan.paradas().getLast().finServicio())) state = "returning";
        result.put("state", state);
        if (!viaje.partes.isEmpty()) {
            int paradaActual = Math.min(stop, viaje.plan.paradas().size()-1);
            var parte = viaje.partes.stream().filter(p -> p.parada() == paradaActual).findFirst().orElse(viaje.partes.getFirst());
            result.put("orderId", apiId(parte.pedido()));
        }
        aplicarEstadoIncidencia(motor, unidad.codigo(), result);
        return result;
    }

    private static void aplicarEstadoIncidencia(MotorSimulacion motor, String codigo, Map<String,Object> vehicle) {
        boolean averiado = motor.averias().stream().anyMatch(a -> a.vehiculo().equals(codigo)
                && !motor.reloj().isBefore(a.inicio()) && motor.reloj().isBefore(a.fin()));
        boolean mantenimiento = motor.mantenimientos().stream().anyMatch(mt -> mt.vehiculo().equals(codigo)
                && !motor.reloj().isBefore(mt.inicio()) && motor.reloj().isBefore(mt.fin()));
        if (averiado) vehicle.put("state", "broken");
        else if (mantenimiento) vehicle.put("state", "maintenance");
    }

    private static Map<String,Object> point(Nodo n) { return Map.of("x", n.x(), "y", n.y()); }
    private static long apiId(String codigo) {
        if (codigo != null && codigo.matches("M\\d+")) return Long.parseLong(codigo.substring(1));
        return Integer.toUnsignedLong(codigo == null ? 0 : codigo.hashCode());
    }

    private MotorSimulacion motor() { if (orquestador.motor()==null) throw new IllegalStateException("No hay simulación configurada"); return orquestador.motor(); }
    private Map<String,Object> filesSnapshot() {
        var files = new LinkedHashMap<String,Object>();
        for (String kind : List.of("ventas", "bloqueos", "averias", "mantenimiento")) files.put(kind, conteoArchivos.get(kind));
        return files;
    }
    private static void validarVehiculoActivo(MotorSimulacion motor, String codigo) {
        if (motor.esFinal()) throw new IllegalStateException("La ejecución ya finalizó");
        boolean existe = motor.flotaProyectada().stream().anyMatch(v -> v.codigo().equalsIgnoreCase(codigo));
        if (!existe) throw new IllegalArgumentException("La unidad no pertenece a la flota de la ejecución");
    }
    private static void validarVehiculoEnRuta(MotorSimulacion motor, String codigo) {
        boolean enRuta = motor.viajes().stream().anyMatch(v -> v.despachado && !v.terminado
                && v.plan.ruta().vehiculo().equalsIgnoreCase(codigo));
        if (!enRuta) throw new IllegalArgumentException("Solo se registran averías en unidades que están en ruta");
    }
    private static void validarVehiculoDisponible(MotorSimulacion motor, String codigo) {
        boolean ocupado = motor.viajes().stream().anyMatch(v -> v.despachado && !v.terminado
                && v.plan.ruta().vehiculo().equalsIgnoreCase(codigo));
        if (ocupado) throw new IllegalArgumentException("Solo se programa mantenimiento a unidades disponibles en almacén");
    }
    public record RunConfig(String scenario, String startDate, String startTime, Fleet fleet, Capacities capacities, int[] shiftStarts, Boolean considerarIncidencias) { }
    public record VelocidadInput(Double factor) { }
    public record Fleet(int auto,int moto,int bici) { }
    public record Capacities(int noroeste,int este) { }
    public record OrderInput(String clientId,int qty,int hourLimit,int x,int y) { }
    public record OrderResult(boolean ok,Long id,boolean enRiesgo,String motivo) { }
    public record AveriaInput(String vehicleId,int tipo) { }
    public record MantenimientoInput(String vehicleId,int horas) { }
    public record PointInput(int x,int y) { }
    public record BloqueoInput(List<PointInput> nodos,int horas) { }
}
