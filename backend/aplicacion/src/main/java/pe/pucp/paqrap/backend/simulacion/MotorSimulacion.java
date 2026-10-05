package pe.pucp.paqrap.backend.simulacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.estricto.servicios.PlanificadorEstricto;

/**
 * Reloj determinista y orquestacion por ciclos Sa (B-05, LE014/026/053/058/060).
 * Las rutas comprometidas se conservan fuera de la busqueda; el nucleo recibe solo cantidades pendientes
 * y la ubicacion/disponibilidad futura de cada unidad. Una copia candidata se persiste antes de publicarla.
 */
public final class MotorSimulacion {
    public record Evento(int secuencia, LocalDateTime fecha, String tipo, String mensaje,
            String pedido, String vehiculo, String almacen, Integer cantidad) { }
    public record Ciclo(int numero, LocalDateTime fecha, ResultadoPlanificacion resultado) { }
    public record Parte(int numero, String codigo, String pedido, int cantidad, int parada) { }

    /** Estado de cantidades por pedido; llegada y fin de servicio son instantes distintos (DD-04). */
    public static final class PedidoVivo {
        public final Pedido original;
        public int pendiente;
        public int enRuta;
        public int entregada;
        public int llegada;
        public int siguienteParte = 1;
        public LocalDateTime llegadaFinal;
        public LocalDateTime entregaFinal;
        public LocalDateTime noCumplido;
        public LocalDateTime primerDespacho;
        public PedidoVivo(Pedido original) {
            this.original = original;
            pendiente = original.cantidad();
        }
        private PedidoVivo(PedidoVivo otro) {
            original = otro.original; pendiente = otro.pendiente; enRuta = otro.enRuta;
            entregada = otro.entregada; llegada = otro.llegada; siguienteParte = otro.siguienteParte;
            llegadaFinal = otro.llegadaFinal; entregaFinal = otro.entregaFinal;
            noCumplido = otro.noCumplido; primerDespacho = otro.primerDespacho;
        }
        public String estado() {
            return noCumplido != null ? "NO_CUMPLIDO" : entregada == original.cantidad() ? "ENTREGADO"
                    : enRuta > 0 ? "EN_RUTA" : "REGISTRADO";
        }
    }

    /** Viaje comprometido; banderas por parada evitan contabilizar dos veces al pausar/reanudar. */
    public static final class Viaje {
        public final int numero;
        public final int ciclo;
        public final ResultadoRuta plan;
        public final List<Parte> partes;
        public final boolean[] llegadas;
        public final boolean[] entregas;
        public boolean despachado;
        public boolean terminado;
        public Long idPersistido;
        Viaje(int numero, int ciclo, ResultadoRuta plan, List<Parte> partes) {
            this.numero = numero; this.ciclo = ciclo; this.plan = plan; this.partes = List.copyOf(partes);
            llegadas = new boolean[plan.paradas().size()]; entregas = new boolean[llegadas.length];
        }
        private Viaje(Viaje otro) {
            numero = otro.numero; ciclo = otro.ciclo; plan = otro.plan; partes = otro.partes;
            llegadas = otro.llegadas.clone(); entregas = otro.entregas.clone();
            despachado = otro.despachado; terminado = otro.terminado; idPersistido = otro.idPersistido;
        }
        public String estado() { return terminado ? "COMPLETADA" : despachado ? "EN_CURSO" : "DESPACHADA"; }
    }

    private final ConfiguracionSimulacion configuracion;
    private final List<Pedido> demanda;
    private final List<Bloqueo> bloqueos;
    private final List<Mantenimiento> mantenimientos;
    private final Map<String, Almacen> almacenes;
    private final Map<String, Integer> reservas = new LinkedHashMap<>();
    private final Map<String, PedidoVivo> pedidos = new LinkedHashMap<>();
    private final List<Vehiculo> flota;
    private final List<Viaje> viajes = new ArrayList<>();
    private final List<Ciclo> ciclos = new ArrayList<>();
    private final List<Evento> eventos = new ArrayList<>();
    private LocalDateTime reloj;
    private LocalDateTime siguienteCiclo;
    private LocalDateTime siguienteRecarga;
    private int demandaIngresada;
    private String estado = "CONFIGURADA";
    private String motivoFin;
    private String pedidoColapso;
    private long tiempoRealMs;

    public MotorSimulacion(ConfiguracionSimulacion configuracion, List<Pedido> demanda, List<Almacen> almacenes,
            List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos) {
        this.configuracion = configuracion;
        this.demanda = new ArrayList<>(demanda.stream().filter(p -> !p.fechaRegistro().isBefore(configuracion.inicio()))
                .filter(p -> configuracion.finHorizonte() == null
                        || p.fechaRegistro().isBefore(configuracion.finHorizonte()))
                .sorted(Comparator.comparing(Pedido::fechaRegistro).thenComparing(Pedido::id)).toList());
        if (this.demanda.stream().map(Pedido::id).distinct().count() != this.demanda.size()) {
            throw new IllegalArgumentException("Pedidos duplicados en la demanda");
        }
        this.bloqueos = List.copyOf(bloqueos);
        this.mantenimientos = List.copyOf(mantenimientos);
        this.almacenes = new LinkedHashMap<>();
        for (var almacen : almacenes) {
            int capacidad = almacen.ilimitado() ? 0 : configuracion.capacidades().get(almacen.id());
            this.almacenes.put(almacen.id(), new Almacen(almacen.id(), almacen.nodo(), capacidad, almacen.ilimitado()));
            reservas.put(almacen.id(), 0);
        }
        var central = this.almacenes.get("CENTRAL");
        if (central == null || !central.ilimitado()) {
            throw new IllegalArgumentException("Se requiere un almacen central ilimitado");
        }
        flota = new ArrayList<>();
        for (var tipo : TipoVehiculo.values()) {
            for (int i = 1; i <= configuracion.flota().get(tipo); i++) {
                flota.add(new Vehiculo(String.format(java.util.Locale.ROOT, "%s%02d", tipo, i),
                        tipo, central.nodo(), true, configuracion.inicio()));
            }
        }
        reloj = configuracion.inicio();
        siguienteCiclo = reloj;
        siguienteRecarga = reloj.toLocalDate().atTime(23, 59, 59);
        if (siguienteRecarga.isBefore(reloj)) siguienteRecarga = siguienteRecarga.plusDays(1);
        evento("EJECUCION_CONFIGURADA", "Ejecucion configurada", null, null, null, null);
    }

    private MotorSimulacion(MotorSimulacion otro) {
        configuracion = otro.configuracion; demanda = new ArrayList<>(otro.demanda);
        bloqueos = otro.bloqueos; mantenimientos = otro.mantenimientos; flota = otro.flota;
        almacenes = new LinkedHashMap<>(otro.almacenes); reservas.putAll(otro.reservas);
        otro.pedidos.forEach((id, pedido) -> pedidos.put(id, new PedidoVivo(pedido)));
        otro.viajes.forEach(viaje -> viajes.add(new Viaje(viaje)));
        ciclos.addAll(otro.ciclos); eventos.addAll(otro.eventos);
        reloj = otro.reloj; siguienteCiclo = otro.siguienteCiclo; siguienteRecarga = otro.siguienteRecarga;
        demandaIngresada = otro.demandaIngresada; estado = otro.estado; motivoFin = otro.motivoFin;
        pedidoColapso = otro.pedidoColapso; tiempoRealMs = otro.tiempoRealMs;
    }

    public MotorSimulacion copiar() { return new MotorSimulacion(this); }

    public void iniciar() {
        if (!Set.of("CONFIGURADA", "PAUSADA", "ESPERANDO_PEDIDO").contains(estado)) {
            throw new IllegalArgumentException("La ejecucion no puede iniciarse en estado " + estado);
        }
        boolean reanudar = estado.equals("PAUSADA");
        estado = configuracion.escenario() == ConfiguracionSimulacion.Escenario.DIA_A_DIA && demanda.isEmpty()
                ? "ESPERANDO_PEDIDO" : "EN_CURSO";
        evento(reanudar ? "EJECUCION_REANUDADA" : "EJECUCION_INICIADA", "Ejecucion " + estado,
                null, null, null, null);
    }

    public void pausar() {
        if (!Set.of("EN_CURSO", "ESPERANDO_PEDIDO").contains(estado)) {
            throw new IllegalArgumentException("Solo se puede pausar una ejecucion activa");
        }
        estado = "PAUSADA";
        evento("EJECUCION_PAUSADA", "Ejecucion pausada", null, null, null, null);
    }

    public void detener() {
        if (esFinal()) return;
        finalizar("DETENIDA", "DETENIDA_POR_USUARIO");
    }

    /** Incorpora pedidos manuales al instante del reloj; despierta el escenario Dia a dia. */
    public void registrar(Pedido pedido) {
        if (esFinal() || configuracion.escenario() != ConfiguracionSimulacion.Escenario.DIA_A_DIA) {
            throw new IllegalArgumentException("Registro manual disponible solo en Dia a dia activo");
        }
        if (demanda.stream().anyMatch(p -> p.id().equals(pedido.id())) || !pedido.fechaRegistro().equals(reloj)) {
            throw new IllegalArgumentException("Codigo duplicado o fecha distinta del reloj");
        }
        demanda.add(pedido);
        if (estado.equals("ESPERANDO_PEDIDO")) estado = "EN_CURSO";
        siguienteCiclo = reloj;
    }

    /** Avanza sin saltar ciclos ni eventos: pausas no acumulan tiempo y el horizonte 5D es exacto. */
    public void avanzar(double segundosReales, PlanificadorEstricto planificador) {
        if (!Double.isFinite(segundosReales) || segundosReales < 0) {
            throw new IllegalArgumentException("Tiempo real invalido");
        }
        if (!estado.equals("EN_CURSO")) return;
        LocalDateTime inicioAvance = reloj;
        LocalDateTime destino = reloj.plusNanos(Math.round(segundosReales
                * configuracion.minutosPorSegundo() * 60_000_000_000.0));
        if (configuracion.finHorizonte() != null && destino.isAfter(configuracion.finHorizonte())) {
            destino = configuracion.finHorizonte();
        }
        while (estado.equals("EN_CURSO")) {
            procesarOperacion();
            comprobarPlazos();
            if (!estado.equals("EN_CURSO")) break;
            if (configuracion.finHorizonte() != null && !reloj.isBefore(configuracion.finHorizonte())) {
                finalizar("FINALIZADA", "FIN_DE_HORIZONTE");
                break;
            }
            if (!reloj.isBefore(siguienteCiclo)) {
                planificar(planificador);
                siguienteCiclo = siguienteCiclo.plusMinutes(configuracion.saMinutos());
                procesarOperacion();
            }
            if (!estado.equals("EN_CURSO") || !reloj.isBefore(destino)) break;
            // Resolucion maxima de un minuto para deteccion de incumplimientos (LE005).
            LocalDateTime paso = minimo(destino, reloj.plusMinutes(1));
            paso = minimo(paso, siguienteCiclo);
            paso = minimo(paso, siguienteRecarga);
            reloj = siguienteEvento(paso);
        }
        // Solo cuenta el intervalo consumido: un tick puede sobrepasar el horizonte o detectar colapso.
        tiempoRealMs += Math.round(Duration.between(inicioAvance, reloj).toNanos()
                / (configuracion.minutosPorSegundo() * 60_000_000.0));
    }

    /** Conserva el orden temporal, incluso cuando hay varias entregas dentro del mismo minuto. */
    private LocalDateTime siguienteEvento(LocalDateTime limite) {
        LocalDateTime siguiente = limite;
        if (demandaIngresada < demanda.size()) {
            LocalDateTime ingreso = demanda.get(demandaIngresada).fechaRegistro();
            if (ingreso.isAfter(reloj)) siguiente = minimo(siguiente, ingreso);
        }
        for (var viaje : viajes) {
            if (viaje.terminado) continue;
            if (!viaje.despachado && viaje.plan.salida().isAfter(reloj)) {
                siguiente = minimo(siguiente, viaje.plan.salida());
            }
            for (int i = 0; i < viaje.plan.paradas().size(); i++) {
                var parada = viaje.plan.paradas().get(i);
                if (!viaje.llegadas[i] && parada.llegada().isAfter(reloj)) {
                    siguiente = minimo(siguiente, parada.llegada());
                }
                if (!viaje.entregas[i] && parada.finServicio().isAfter(reloj)) {
                    siguiente = minimo(siguiente, parada.finServicio());
                }
            }
            if (viaje.plan.fin().isAfter(reloj)) siguiente = minimo(siguiente, viaje.plan.fin());
        }
        return siguiente;
    }

    private void procesarOperacion() {
        while (demandaIngresada < demanda.size() && !demanda.get(demandaIngresada).fechaRegistro().isAfter(reloj)) {
            Pedido pedido = demanda.get(demandaIngresada++);
            pedidos.put(pedido.id(), new PedidoVivo(pedido));
            evento("PEDIDO_REGISTRADO", "Pedido " + pedido.id() + " ingresado", pedido.id(), null, null, null);
        }
        if (!reloj.isBefore(siguienteRecarga)) {
            for (var almacen : new ArrayList<>(almacenes.values())) {
                if (!almacen.ilimitado()) {
                    int capacidad = configuracion.capacidades().get(almacen.id());
                    almacenes.put(almacen.id(), new Almacen(almacen.id(), almacen.nodo(), capacidad, false));
                    evento("RECARGA_ALMACEN", "Recarga de " + almacen.id(), null, null, almacen.id(),
                            capacidad - almacen.stock());
                }
            }
            siguienteRecarga = siguienteRecarga.plusDays(1);
        }
        for (var viaje : viajes) {
            if (viaje.terminado) continue;
            if (!viaje.despachado && !viaje.plan.salida().isAfter(reloj)) {
                viaje.despachado = true;
                String origen = viaje.plan.ruta().almacenOrigen();
                var almacen = almacenes.get(origen);
                int carga = viaje.plan.ruta().carga();
                reservas.merge(origen, -carga, Integer::sum);
                if (!almacen.ilimitado()) {
                    almacenes.put(origen, new Almacen(origen, almacen.nodo(), almacen.stock() - carga, false));
                }
                for (var parte : viaje.partes) {
                    var pedido = pedidos.get(parte.pedido());
                    if (pedido.primerDespacho == null) pedido.primerDespacho = viaje.plan.salida();
                }
                evento("RUTA_DESPACHADA", "Ruta " + viaje.numero + " despachada", null,
                        viaje.plan.ruta().vehiculo(), origen, carga);
            }
            for (int i = 0; i < viaje.plan.paradas().size(); i++) {
                var parada = viaje.plan.paradas().get(i);
                for (var parte : viaje.partes) {
                    if (parte.parada() != i) continue;
                    var pedido = pedidos.get(parte.pedido());
                    if (!viaje.llegadas[i] && !parada.llegada().isAfter(reloj)) {
                        pedido.llegada += parte.cantidad();
                        pedido.llegadaFinal = pedido.llegadaFinal == null
                                || parada.llegada().isAfter(pedido.llegadaFinal) ? parada.llegada() : pedido.llegadaFinal;
                    }
                    if (!viaje.entregas[i] && !parada.finServicio().isAfter(reloj)) {
                        pedido.enRuta -= parte.cantidad();
                        pedido.entregada += parte.cantidad();
                        pedido.entregaFinal = pedido.entregaFinal == null
                                || parada.finServicio().isAfter(pedido.entregaFinal)
                                ? parada.finServicio() : pedido.entregaFinal;
                        evento(pedido.entregada == pedido.original.cantidad() ? "PEDIDO_ENTREGADO"
                                : "PEDIDO_ENTREGA_PARCIAL", "Entrega de " + parte.codigo(), parte.pedido(),
                                viaje.plan.ruta().vehiculo(), null, parte.cantidad());
                    }
                }
                if (!parada.llegada().isAfter(reloj)) viaje.llegadas[i] = true;
                if (!parada.finServicio().isAfter(reloj)) viaje.entregas[i] = true;
            }
            if (!viaje.plan.fin().isAfter(reloj)) {
                viaje.terminado = true;
                evento("VEHICULO_RETORNO", "Retorno de " + viaje.plan.ruta().vehiculo(), null,
                        viaje.plan.ruta().vehiculo(), viaje.plan.almacenRetorno(), null);
            }
        }
    }

    private void comprobarPlazos() {
        for (var pedido : pedidos.values()) {
            int cumplidas = configuracion.operacion().plazoIncluyeServicio() ? pedido.entregada : pedido.llegada;
            LocalDateTime ultima = configuracion.operacion().plazoIncluyeServicio()
                    ? pedido.entregaFinal : pedido.llegadaFinal;
            if (pedido.noCumplido == null && reloj.isAfter(pedido.original.deadline())
                    && (cumplidas < pedido.original.cantidad()
                    || (ultima != null && ultima.isAfter(pedido.original.deadline())))) {
                pedido.noCumplido = reloj;
                evento("PEDIDO_NO_CUMPLIDO", "Vencio " + pedido.original.id(), pedido.original.id(), null, null, null);
                if (configuracion.escenario() == ConfiguracionSimulacion.Escenario.COLAPSO) {
                    pedidoColapso = pedido.original.id();
                    finalizar("COLAPSADA", "COLAPSO_PLAZO");
                    return;
                }
            }
        }
    }

    private void planificar(PlanificadorEstricto planificador) {
        var pendientes = pedidos.values().stream().filter(p -> p.pendiente > 0 && p.noCumplido == null)
                .map(p -> new Pedido(p.original.id(), p.original.fechaRegistro(), p.original.ubicacion(),
                        p.pendiente, p.original.plazoHoras(), p.original.clienteId())).toList();
        var disponibles = almacenes.values().stream().map(a -> new Almacen(a.id(), a.nodo(),
                a.ilimitado() ? 0 : a.stock() - reservas.get(a.id()), a.ilimitado())).toList();
        var codigos = flota.stream().map(Vehiculo::codigo).collect(java.util.stream.Collectors.toSet());
        var descansados = new LinkedHashSet<String>();
        LocalDateTime inicioTurno = inicioTurnoActual();
        for (var viaje : viajes) {
            if (viaje.plan.descansoFin() != null && !viaje.plan.descansoFin().isAfter(reloj)
                    && !viaje.plan.descansoInicio().isBefore(inicioTurno)) {
                descansados.add(viaje.plan.ruta().vehiculo());
            }
        }
        var entrada = new EstadoOperacion(reloj, pendientes, flotaProyectada(), disponibles, bloqueos, List.of(),
                mantenimientos.stream().filter(m -> codigos.contains(m.vehiculo())).toList(), List.of(), descansados);
        ResultadoPlanificacion resultado = planificador.planificar(entrada, configuracion.operacion());
        if (!resultado.evaluacion().factible()) {
            throw new IllegalStateException("El planificador devolvio rutas inviables");
        }
        int numero = ciclos.size();
        ciclos.add(new Ciclo(numero, reloj, resultado));
        evento("CICLO_PLANIFICADO", "Ciclo " + numero + ": " + resultado.metricas().estadoResultado(),
                null, null, null, null);
        if (resultado.metricas().colapso()
                && configuracion.escenario() == ConfiguracionSimulacion.Escenario.COLAPSO) {
            pedidoColapso = resultado.solucion().pendientes().isEmpty() ? null
                    : resultado.solucion().pendientes().getFirst().pedido().id();
            finalizar("COLAPSADA", "COLAPSO_PLANIFICACION");
            return;
        }
        for (var ruta : resultado.evaluacion().rutas()) {
            if (ruta.ruta().partes().isEmpty() || !ruta.salida().isBefore(reloj.plusMinutes(configuracion.saMinutos()))
                    || (configuracion.finHorizonte() != null && !ruta.salida().isBefore(configuracion.finHorizonte()))) {
                continue;
            }
            var partes = new ArrayList<Parte>();
            for (int i = 0; i < ruta.paradas().size(); i++) {
                for (var parte : ruta.paradas().get(i).partes()) {
                    var pedido = pedidos.get(parte.pedido().id());
                    int correlativo = pedido.siguienteParte++;
                    partes.add(new Parte(correlativo, pedido.original.id() + "#" + correlativo,
                            pedido.original.id(), parte.cantidad(), i));
                    pedido.pendiente -= parte.cantidad();
                    pedido.enRuta += parte.cantidad();
                }
            }
            reservas.merge(ruta.ruta().almacenOrigen(), ruta.ruta().carga(), Integer::sum);
            viajes.add(new Viaje(viajes.size() + 1, numero, ruta, partes));
        }
        if (configuracion.escenario() == ConfiguracionSimulacion.Escenario.COLAPSO
                && demandaIngresada == demanda.size() && viajes.stream().allMatch(v -> v.terminado)
                && pedidos.values().stream().allMatch(p -> p.entregada == p.original.cantidad())) {
            finalizar("FINALIZADA", "FIN_DE_DATOS");
        }
    }

    public List<Vehiculo> flotaProyectada() {
        var resultado = new ArrayList<Vehiculo>();
        for (var vehiculo : flota) {
            var ultimo = viajes.stream().filter(v -> v.plan.ruta().vehiculo().equals(vehiculo.codigo()))
                    .max(Comparator.comparing(v -> v.plan.fin())).orElse(null);
            resultado.add(ultimo == null ? vehiculo : new Vehiculo(vehiculo.codigo(), vehiculo.tipo(),
                    almacenes.get(ultimo.plan.almacenRetorno()).nodo(), true, ultimo.plan.fin()));
        }
        return List.copyOf(resultado);
    }

    private LocalDateTime inicioTurnoActual() {
        LocalDateTime primero = reloj.toLocalDate().atStartOfDay().plusMinutes(
                configuracion.operacion().inicioTurnoMinuto());
        long turnos = Math.floorDiv(Duration.between(primero, reloj).toMinutes(),
                configuracion.operacion().turnoMinutos());
        return primero.plusMinutes(turnos * configuracion.operacion().turnoMinutos());
    }

    private void finalizar(String nuevoEstado, String motivo) {
        estado = nuevoEstado; motivoFin = motivo;
        evento(nuevoEstado.equals("COLAPSADA") ? "COLAPSO" : nuevoEstado.equals("DETENIDA")
                ? "EJECUCION_DETENIDA" : "EJECUCION_FINALIZADA", motivo, pedidoColapso, null, null, null);
    }

    private void evento(String tipo, String mensaje, String pedido, String vehiculo, String almacen, Integer cantidad) {
        eventos.add(new Evento(eventos.size() + 1, reloj, tipo, mensaje, pedido, vehiculo, almacen, cantidad));
    }

    private static LocalDateTime minimo(LocalDateTime primero, LocalDateTime segundo) {
        return primero.isBefore(segundo) ? primero : segundo;
    }

    public ConfiguracionSimulacion configuracion() { return configuracion; }
    public LocalDateTime reloj() { return reloj; }
    public String estado() { return estado; }
    public String motivoFin() { return motivoFin; }
    public String pedidoColapso() { return pedidoColapso; }
    public long tiempoRealMs() { return tiempoRealMs; }
    public Map<String, PedidoVivo> pedidos() { return java.util.Collections.unmodifiableMap(pedidos); }
    public List<Viaje> viajes() { return List.copyOf(viajes); }
    public List<Ciclo> ciclos() { return List.copyOf(ciclos); }
    public List<Evento> eventos() { return List.copyOf(eventos); }
    public List<Almacen> almacenes() { return List.copyOf(almacenes.values()); }
    public List<Bloqueo> bloqueos() { return bloqueos; }
    public List<Mantenimiento> mantenimientos() { return mantenimientos; }
    public boolean esFinal() { return Set.of("FINALIZADA", "COLAPSADA", "DETENIDA", "ERROR").contains(estado); }
}
