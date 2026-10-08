package pe.pucp.paqrap.backend.tiemporeal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import pe.pucp.paqrap.backend.api.PlanificadorControlador;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTiempoReal;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;

/**
 * Difusión por STOMP del estado y la bitácora de la ejecución (B-09, LE037/LE038/LE054).
 *
 * <p>Publica en dos destinos del broker simple, con las formas JSON de {@code frontend/src/domain/types.ts}:
 * <ul>
 *   <li>{@value #DESTINO_ESTADO}: el {@code SimSnapshot} completo, que se arma con
 *       {@link PlanificadorControlador#estado()} (la misma lógica de {@code GET /api/simulacion/estado}). Se emite
 *       {@code frecuenciaHz} veces por segundo mientras la ejecución está en curso y, además, de inmediato cuando
 *       cambia el estado, hay eventos nuevos, se reemplaza la ejecución, se ejecuta un comando REST o se suscribe un
 *       cliente nuevo.</li>
 *   <li>{@value #DESTINO_EVENTOS}: un {@link EventoLog} por cada evento nuevo de la bitácora del motor (la misma
 *       secuencia que persiste B-07). Un cliente que se conecta tarde no recibe los eventos pasados.</li>
 * </ul>
 *
 * <p>Un único hilo revisa cada {@code periodoSondeoMs}. El snapshot se arma dentro del monitor del
 * {@link OrquestadorSimulacion} (todos sus métodos mutadores son {@code synchronized}) para no leer el motor a mitad de
 * un avance; el envío se hace fuera del monitor. Si el orquestador está planificando, el sondeo espera.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DifusionTiempoReal {

    /** Destino del {@code SimSnapshot} (contrato del frontend). */
    public static final String DESTINO_ESTADO = "/topic/simulacion/estado";

    /** Destino de los {@code LogEvent} (contrato del frontend). */
    public static final String DESTINO_EVENTOS = "/topic/simulacion/eventos";

    private static final Logger LOG = LoggerFactory.getLogger(DifusionTiempoReal.class);

    private final SimpMessageSendingOperations plantilla;
    private final OrquestadorSimulacion orquestador;
    private final PlanificadorControlador api;
    private final PropiedadesTiempoReal propiedades;
    private final AtomicBoolean solicitada = new AtomicBoolean();
    private ScheduledExecutorService ejecutor;

    // Estado del sondeo: solo lo toca el hilo del sondeo (o las pruebas, que llaman a difundir directamente).
    private MotorSimulacion ultimoMotor;
    private String ultimoEstado;
    private int eventosDifundidos;
    private long ultimaDifusionNanos;
    private boolean sinDifusionPrevia = true;

    /**
     * Crea la difusión.
     *
     * @param plantilla   envío de mensajes al broker STOMP
     * @param orquestador fuente del motor vigente
     * @param api         controlador REST que arma el {@code SimSnapshot}
     * @param propiedades parámetros del canal en tiempo real
     */
    public DifusionTiempoReal(SimpMessageSendingOperations plantilla, OrquestadorSimulacion orquestador,
            PlanificadorControlador api, PropiedadesTiempoReal propiedades) {
        this.plantilla = plantilla;
        this.orquestador = orquestador;
        this.api = api;
        this.propiedades = propiedades;
    }

    /** Arranca el hilo de sondeo si la difusión está habilitada. */
    @PostConstruct
    void iniciar() {
        if (!propiedades.difusionHabilitada()) {
            LOG.info("Difusión STOMP deshabilitada (paqrap.tiempo-real.difusion-habilitada=false)");
            return;
        }
        ejecutor = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "difusion-tiempo-real");
            hilo.setDaemon(true);
            return hilo;
        });
        ejecutor.scheduleWithFixedDelay(this::sondear, propiedades.periodoSondeoMs(), propiedades.periodoSondeoMs(),
                TimeUnit.MILLISECONDS);
    }

    /** Detiene el hilo de sondeo al cerrar el contexto. */
    @PreDestroy
    void detener() {
        if (ejecutor != null) ejecutor.shutdownNow();
    }

    /**
     * Pide que el próximo sondeo difunda el snapshot aunque no haya cambiado nada observable (por ejemplo, tras un
     * comando REST o al llegar un cliente). Es seguro llamarlo desde cualquier hilo.
     */
    public void solicitarEstado() {
        solicitada.set(true);
    }

    /**
     * Atiende la suscripción de un cliente nuevo a {@value #DESTINO_ESTADO}: programa un envío inmediato para que
     * reciba el estado vigente sin esperar a que la simulación corra.
     *
     * @param suscripcion evento de suscripción STOMP
     */
    @EventListener
    void alSuscribirse(SessionSubscribeEvent suscripcion) {
        if (DESTINO_ESTADO.equals(StompHeaderAccessor.wrap(suscripcion.getMessage()).getDestination())) {
            solicitarEstado();
        }
    }

    private void sondear() {
        try {
            difundir(System.nanoTime());
        } catch (RuntimeException ex) {
            // El hilo no debe morir por un fallo puntual: el siguiente sondeo reintenta.
            LOG.warn("No se pudo difundir el estado de la simulación: {}", ex.toString());
        }
    }

    /**
     * Una vuelta del sondeo: decide qué difundir y lo envía.
     *
     * @param ahoraNanos instante monótono actual (para el ritmo de {@code frecuenciaHz})
     */
    void difundir(long ahoraNanos) {
        Map<String, Object> snapshot = null;
        List<EventoLog> nuevos = new ArrayList<>();
        String estado;
        synchronized (orquestador) {
            MotorSimulacion motor = orquestador.motor();
            estado = orquestador.estado();
            boolean cambio = solicitada.getAndSet(false) || sinDifusionPrevia || motor != ultimoMotor
                    || !Objects.equals(estado, ultimoEstado);
            if (motor != ultimoMotor) {
                ultimoMotor = motor;
                eventosDifundidos = 0;
            }
            if (motor != null) {
                List<MotorSimulacion.Evento> eventos = motor.eventos();
                if (eventos.size() > eventosDifundidos) {
                    LocalDateTime medianoche = motor.configuracion().inicio().toLocalDate().atStartOfDay();
                    for (MotorSimulacion.Evento evento : eventos.subList(eventosDifundidos, eventos.size())) {
                        nuevos.add(EventoLog.desde(evento, medianoche, motor.configuracion().escenario()));
                    }
                    eventosDifundidos = eventos.size();
                }
            }
            boolean corriendo = "EN_CURSO".equals(estado);
            long periodoNanos = 1_000_000_000L / propiedades.frecuenciaHz();
            boolean periodico = corriendo && ahoraNanos - ultimaDifusionNanos >= periodoNanos;
            if (cambio || periodico || (!nuevos.isEmpty() && !corriendo)) {
                snapshot = api.estado();
                ultimaDifusionNanos = ahoraNanos;
                ultimoEstado = estado;
                sinDifusionPrevia = false;
            }
        }
        // Se tipa como Object: un Map como carga útil sería ambiguo con convertAndSend(payload, headers).
        if (snapshot != null) plantilla.convertAndSend(DESTINO_ESTADO, (Object) snapshot);
        for (EventoLog evento : nuevos) plantilla.convertAndSend(DESTINO_EVENTOS, evento);
    }
}
