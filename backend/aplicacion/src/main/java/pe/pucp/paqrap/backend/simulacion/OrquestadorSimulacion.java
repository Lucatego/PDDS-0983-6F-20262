package pe.pucp.paqrap.backend.simulacion;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.pucp.paqrap.backend.persistencia.LectorEjecucion;
import pe.pucp.paqrap.backend.persistencia.RepositorioConfiguracionEjecucion;
import pe.pucp.paqrap.backend.persistencia.RepositorioSimulacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/**
 * Orquestador del ciclo de vida y reloj de la simulación (B-05, LE014/026/053/060).
 * Conecta {@link LectorEjecucion}, {@link MotorSimulacion}, {@link TabuSearchPlanner}
 * y persiste resultados mediante {@link RepositorioSimulacion}.
 */
@Service
public class OrquestadorSimulacion {

    private static final Logger LOG = LoggerFactory.getLogger(OrquestadorSimulacion.class);

    private final LectorEjecucion lector;
    private final RepositorioSimulacion repositorio;
    private final RepositorioConfiguracionEjecucion repoConfig;

    private Long ejecucionId;
    private PreparacionSimulacion preparacion;
    private MotorSimulacion motor;
    private TabuSearchPlanner planificador;
    private Map<String, Long> codigosAPedidoEjecucionId = new LinkedHashMap<>();
    private Map<String, Long> vehiculosId = new LinkedHashMap<>();
    private int ultimoCicloPersistido = -1;

    @Autowired
    public OrquestadorSimulacion(
            @Autowired(required = false) LectorEjecucion lector,
            @Autowired(required = false) RepositorioSimulacion repositorio,
            @Autowired(required = false) RepositorioConfiguracionEjecucion repoConfig) {
        this.lector = lector;
        this.repositorio = repositorio;
        this.repoConfig = repoConfig;
    }

    /** Constructor para pruebas unitarias con motor y planificador explícitos. */
    public OrquestadorSimulacion(MotorSimulacion motor, TabuSearchPlanner planificador) {
        this(null, null, null);
        this.motor = Objects.requireNonNull(motor);
        this.planificador = Objects.requireNonNull(planificador);
    }

    /**
     * Configura y congela una nueva ejecución en base de datos.
     */
    public synchronized long configurar(ConfiguracionSimulacion configuracion, ConfiguracionTabu algoritmo) {
        if (repoConfig == null) {
            throw new IllegalStateException("Repositorio de configuración no disponible");
        }
        long id = repoConfig.crear(configuracion, algoritmo);
        preparar(id);
        return id;
    }

    /**
     * Carga y prepara el motor y planificador para la ejecución indicada.
     */
    public synchronized void preparar(long id) {
        if (lector == null) {
            throw new IllegalStateException("Lector de ejecución no disponible");
        }
        this.preparacion = lector.preparar(id);
        this.ejecucionId = id;
        this.motor = preparacion.nuevoMotor();
        this.planificador = preparacion.nuevoPlanificador();
        this.ultimoCicloPersistido = -1;

        if (repositorio != null) {
            this.codigosAPedidoEjecucionId = repositorio.obtenerMapeoPedidosEjecucion(id);
            this.vehiculosId = repositorio.obtenerMapeoVehiculos(id);
        }
        LOG.info("Ejecución {} preparada en estado CONFIGURADA", id);
    }

    /**
     * Inicia o reanuda la simulación.
     */
    public synchronized void iniciar() {
        validarMotorInicializado();
        boolean eraPausada = "PAUSADA".equals(motor.estado());
        motor.iniciar();

        if (repositorio != null && ejecucionId != null && !eraPausada) {
            repositorio.inicializarEjecucion(ejecucionId, preparacion);
            this.codigosAPedidoEjecucionId = repositorio.obtenerMapeoPedidosEjecucion(ejecucionId);
            this.vehiculosId = repositorio.obtenerMapeoVehiculos(ejecucionId);
        } else if (repositorio != null && ejecucionId != null) {
            repositorio.persistirProgreso(ejecucionId, motor, codigosAPedidoEjecucionId, vehiculosId);
        }
        LOG.info("Ejecución {} iniciada/reanudada, estado: {}", ejecucionId, motor.estado());
    }

    /**
     * Pausa la ejecución activa.
     */
    public synchronized void pausar() {
        validarMotorInicializado();
        motor.pausar();
        if (repositorio != null && ejecucionId != null) {
            repositorio.persistirProgreso(ejecucionId, motor, codigosAPedidoEjecucionId, vehiculosId);
        }
        LOG.info("Ejecución {} pausada", ejecucionId);
    }

    /**
     * Detiene la ejecución.
     */
    public synchronized void detener() {
        if (motor == null) return;
        motor.detener();
        if (repositorio != null && ejecucionId != null) {
            repositorio.persistirProgreso(ejecucionId, motor, codigosAPedidoEjecucionId, vehiculosId);
        }
        LOG.info("Ejecución {} detenida", ejecucionId);
    }

    /**
     * Reinicia la simulación actual si está configurada.
     */
    public synchronized void reiniciar() {
        if (ejecucionId != null) {
            detener();
            preparar(ejecucionId);
            LOG.info("Ejecución {} reiniciada", ejecucionId);
        }
    }

    /**
     * Avanza el reloj de simulación según el tiempo real transcurrido.
     * Persiste ciclos y rutas generados.
     */
    public synchronized void avanzar(double segundosReales) {
        if (motor == null || planificador == null) return;
        if (!"EN_CURSO".equals(motor.estado())) return;

        int viajesAntes = motor.viajes().size();
        motor.avanzar(segundosReales, planificador);

        if (repositorio != null && ejecucionId != null) {
            // Persistir ciclos nuevos
            var ciclos = motor.ciclos();
            for (int i = ultimoCicloPersistido + 1; i < ciclos.size(); i++) {
                var ciclo = ciclos.get(i);
                var viajesCiclo = motor.viajes().stream()
                        .filter(v -> v.ciclo == ciclo.numero() && v.idPersistido == null)
                        .toList();
                repositorio.persistirCicloYRutas(ejecucionId, ciclo, viajesCiclo, codigosAPedidoEjecucionId, vehiculosId);
                ultimoCicloPersistido = i;
            }

            // Actualizar progreso general de pedidos, viajes y ejecución
            repositorio.persistirProgreso(ejecucionId, motor, codigosAPedidoEjecucionId, vehiculosId);
        }
    }

    /**
     * Registra un pedido manual en tiempo de ejecución (escenario Día a día).
     */
    public synchronized void registrarPedido(Pedido pedido) {
        validarMotorInicializado();
        motor.registrar(pedido);
        if (repositorio != null && ejecucionId != null) {
            long pedidoEjecId = repositorio.registrarPedidoManual(ejecucionId, pedido);
            codigosAPedidoEjecucionId.put(pedido.id(), pedidoEjecId);
        }
        LOG.info("Pedido {} registrado en ejecución {}", pedido.id(), ejecucionId);
    }

    public synchronized MotorSimulacion motor() {
        return motor;
    }

    public synchronized PreparacionSimulacion preparacion() {
        return preparacion;
    }

    public synchronized Long ejecucionId() {
        return ejecucionId;
    }

    public synchronized String estado() {
        return motor != null ? motor.estado() : "NO_CONFIGURADA";
    }

    public synchronized boolean esActiva() {
        return motor != null && ("EN_CURSO".equals(motor.estado()) || "ESPERANDO_PEDIDO".equals(motor.estado())
                || "PAUSADA".equals(motor.estado()));
    }

    public synchronized LocalDateTime reloj() {
        return motor != null ? motor.reloj() : null;
    }

    private void validarMotorInicializado() {
        if (motor == null) {
            throw new IllegalStateException("No hay una simulación preparada o configurada");
        }
    }
}
