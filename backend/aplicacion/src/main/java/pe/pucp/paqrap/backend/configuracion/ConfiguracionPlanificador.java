package pe.pucp.paqrap.backend.configuracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.servicios.PlanificadorEstricto;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/**
 * Registra el planificador Tabu Search (algoritmo seleccionado en el IEN v03) y sus parámetros como beans.
 *
 * <p>{@link TabuSearchPlanner} no guarda estado entre llamadas (cada planificación crea su evaluador, su lista tabú
 * y su generador aleatorio), por lo que una sola instancia se puede compartir entre hilos (LE014–LE027).
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionPlanificador {

    /**
     * Configuración del Tabu Search a partir de {@code paqrap.planificador.tabu}.
     *
     * @param propiedades propiedades enlazadas desde la configuración externa
     * @return configuración inmutable
     */
    @Bean
    public ConfiguracionTabu configuracionTabu(PropiedadesTabu propiedades) {
        return propiedades.aConfiguracion();
    }

    /**
     * Parámetros de operación por defecto a partir de {@code paqrap.planificador.operacion}.
     *
     * @param propiedades propiedades enlazadas desde la configuración externa
     * @return parámetros inmutables
     */
    @Bean
    public ParametrosOperacion parametrosOperacion(PropiedadesOperacion propiedades) {
        return propiedades.aParametros();
    }

    /**
     * Planificador de rutas que usará el orquestador de simulación en cada ciclo.
     *
     * @param configuracion configuración del Tabu Search
     * @return implementación de {@link PlanificadorEstricto} basada en Tabu Search
     */
    @Bean
    public PlanificadorEstricto planificadorEstricto(ConfiguracionTabu configuracion) {
        return new TabuSearchPlanner(configuracion);
    }
}
