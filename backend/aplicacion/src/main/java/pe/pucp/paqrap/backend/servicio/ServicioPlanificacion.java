package pe.pucp.paqrap.backend.servicio;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pe.pucp.paqrap.estricto.modelo.EstadoOperacion;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.ResultadoPlanificacion;
import pe.pucp.paqrap.estricto.servicios.PlanificadorEstricto;

/**
 * Fachada delgada sobre el {@link PlanificadorEstricto}. La usará el orquestador de simulación en cada ciclo de
 * planificación (Sa) para planificar o replanificar a partir de una instantánea {@link EstadoOperacion}
 * (LE014–LE027, LE087–LE100).
 *
 * <p>La infactibilidad no se trata como excepción: se informa en
 * {@code ResultadoPlanificacion.metricas().estadoResultado()} ({@code COMPLETA}, {@code COLAPSO_PLANIFICACION} o
 * {@code SIN_DEMANDA}).
 */
@Service
public class ServicioPlanificacion {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioPlanificacion.class);

    private final PlanificadorEstricto planificador;
    private final ParametrosOperacion parametrosPorDefecto;

    /**
     * Crea el servicio con el planificador y los parámetros configurados.
     *
     * @param planificador         implementación del planificador (Tabu Search)
     * @param parametrosPorDefecto parámetros de operación de {@code application.yml}
     */
    public ServicioPlanificacion(PlanificadorEstricto planificador, ParametrosOperacion parametrosPorDefecto) {
        this.planificador = Objects.requireNonNull(planificador);
        this.parametrosPorDefecto = Objects.requireNonNull(parametrosPorDefecto);
    }

    /**
     * Planifica con los parámetros de operación configurados por defecto.
     *
     * @param estado instantánea inmutable de la operación (solo cantidades pendientes)
     * @return resultado con la solución, su evaluación y las métricas
     */
    public ResultadoPlanificacion planificar(EstadoOperacion estado) {
        return planificar(estado, parametrosPorDefecto);
    }

    /**
     * Planifica con parámetros explícitos, p. ej. los de una ejecución concreta o velocidades cambiadas en caliente
     * (aplican desde la siguiente planificación, respuestas 6, 15 y 16 del Q&amp;A).
     *
     * @param estado     instantánea inmutable de la operación
     * @param parametros parámetros de operación a usar en esta planificación
     * @return resultado con la solución, su evaluación y las métricas
     */
    public ResultadoPlanificacion planificar(EstadoOperacion estado, ParametrosOperacion parametros) {
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(parametros, "parametros");
        var resultado = planificador.planificar(estado, parametros);
        var metricas = resultado.metricas();
        LOG.debug("Planificación {} en {}: {} pedidos, {} paquetes pendientes, Ta={} ms, parada={}",
                resultado.algoritmo(), estado.instante(), metricas.pedidosTotales(), metricas.paquetesPendientes(),
                Math.round(metricas.taMs()), metricas.parada());
        return resultado;
    }

    /**
     * Parámetros de operación configurados por defecto.
     *
     * @return parámetros de {@code application.yml}
     */
    public ParametrosOperacion parametrosPorDefecto() {
        return parametrosPorDefecto;
    }
}
