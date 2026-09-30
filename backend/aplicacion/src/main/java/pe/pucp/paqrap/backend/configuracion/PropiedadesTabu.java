package pe.pucp.paqrap.backend.configuracion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import pe.pucp.paqrap.tabu.ConfiguracionTabu;

/**
 * Parámetros del Tabu Search leídos de {@code paqrap.planificador.tabu} ({@code application.yml}).
 *
 * <p>Los valores por defecto son los del experimento del IEN v03: 300 iteraciones, tenencia 7, 30 iteraciones sin
 * mejora antes de diversificar, 400 candidatos por iteración, sin presupuesto de tiempo y semilla 20262. Con
 * {@code presupuestoMs = 0} y la misma semilla la planificación es reproducible (LE008, LE009).
 *
 * @param maxIteraciones         iteraciones máximas de la búsqueda (0 devuelve la solución inicial)
 * @param tenenciaTabu           iteraciones durante las que un movimiento inverso queda prohibido
 * @param sinMejoraMax           iteraciones sin mejora antes de diversificar
 * @param candidatosPorIteracion vecinos evaluados por iteración (se reparte entre asignación y ruteo)
 * @param presupuestoMs          límite de tiempo cooperativo en milisegundos; 0 lo desactiva
 * @param semilla                semilla del generador aleatorio
 */
@Validated
@ConfigurationProperties(prefix = "paqrap.planificador.tabu")
public record PropiedadesTabu(
        @PositiveOrZero int maxIteraciones,
        @Positive int tenenciaTabu,
        @Positive int sinMejoraMax,
        @Min(2) int candidatosPorIteracion,
        @PositiveOrZero long presupuestoMs,
        long semilla) {

    /**
     * Convierte las propiedades en la configuración del planificador.
     *
     * @return configuración inmutable del Tabu Search
     */
    public ConfiguracionTabu aConfiguracion() {
        return new ConfiguracionTabu(maxIteraciones, tenenciaTabu, sinMejoraMax, candidatosPorIteracion,
                presupuestoMs, semilla);
    }
}
