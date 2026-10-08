package pe.pucp.paqrap.backend.tiemporeal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTiempoReal;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;

/**
 * Pulso que hace avanzar el reloj de la simulación con el tiempo real transcurrido (LE014/LE026/LE053, B-05/B-09).
 *
 * <p>{@link OrquestadorSimulacion#avanzar(double)} ya sabe convertir segundos reales en minutos simulados según el
 * escenario; este componente solo lo invoca periódicamente mientras la ejecución está {@code EN_CURSO}. Las pausas no
 * acumulan tiempo y el tiempo que tarda una planificación no se acredita al siguiente avance, de modo que el reloj se
 * congela mientras planifica (igual que {@code SimulacionComparada}).
 */
@Component
public class RelojSimulacion {

    private static final Logger LOG = LoggerFactory.getLogger(RelojSimulacion.class);

    private final OrquestadorSimulacion orquestador;
    private final PropiedadesTiempoReal propiedades;
    private final LongSupplier fuenteNanos;
    private ScheduledExecutorService ejecutor;
    private long ultimoNanos;
    private boolean corriendo;

    /**
     * Crea el pulso del reloj.
     *
     * @param orquestador ciclo de vida de la ejecución
     * @param propiedades parámetros del canal en tiempo real
     */
    @Autowired
    public RelojSimulacion(OrquestadorSimulacion orquestador, PropiedadesTiempoReal propiedades) {
        this(orquestador, propiedades, System::nanoTime);
    }

    RelojSimulacion(OrquestadorSimulacion orquestador, PropiedadesTiempoReal propiedades, LongSupplier fuenteNanos) {
        this.orquestador = orquestador;
        this.propiedades = propiedades;
        this.fuenteNanos = fuenteNanos;
    }

    /** Arranca el hilo del reloj si está habilitado. */
    @PostConstruct
    void iniciar() {
        if (!propiedades.relojHabilitado()) {
            LOG.info("Reloj de simulación deshabilitado (paqrap.tiempo-real.reloj-habilitado=false)");
            return;
        }
        ejecutor = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "reloj-simulacion");
            hilo.setDaemon(true);
            return hilo;
        });
        ejecutor.scheduleWithFixedDelay(this::pulsar, propiedades.periodoRelojMs(), propiedades.periodoRelojMs(),
                TimeUnit.MILLISECONDS);
    }

    /** Detiene el hilo del reloj al cerrar el contexto. */
    @PreDestroy
    void detener() {
        if (ejecutor != null) ejecutor.shutdownNow();
    }

    private void pulsar() {
        try {
            avanzar();
        } catch (RuntimeException ex) {
            // Un fallo puntual (p. ej. de persistencia) no debe matar el hilo; el siguiente pulso reintenta.
            LOG.warn("No se pudo avanzar la simulación: {}", ex.toString());
        }
    }

    /**
     * Un pulso: si la ejecución está en curso, avanza el motor con el tiempo real desde el pulso anterior.
     */
    void avanzar() {
        if (!"EN_CURSO".equals(orquestador.estado())) {
            corriendo = false;
            return;
        }
        long ahora = fuenteNanos.getAsLong();
        if (!corriendo) {
            corriendo = true;
            ultimoNanos = ahora;
            return;
        }
        double segundos = Math.min((ahora - ultimoNanos) / 1e9, propiedades.maxSaltoRelojMs() / 1000.0);
        try {
            if (segundos > 0) orquestador.avanzar(segundos);
        } finally {
            ultimoNanos = fuenteNanos.getAsLong();
        }
    }
}
