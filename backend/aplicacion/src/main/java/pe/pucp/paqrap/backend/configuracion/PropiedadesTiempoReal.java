package pe.pucp.paqrap.backend.configuracion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros del canal en tiempo real leídos de {@code paqrap.tiempo-real} (B-09, LE037/LE038, RNF de tiempo real).
 *
 * @param difusionHabilitada si es {@code false}, el backend no emite por STOMP (el endpoint {@code /ws} sigue activo)
 * @param relojHabilitado    si es {@code false}, nadie avanza el reloj de la simulación desde el backend
 * @param frecuenciaHz       difusiones de {@code SimSnapshot} por segundo mientras la ejecución corre (4 a 10 según
 *                           {@code frontend/README.md}; se admite 1 a 10)
 * @param periodoSondeoMs    cada cuántos milisegundos se revisa si hay algo que difundir
 * @param periodoRelojMs     cada cuántos milisegundos se avanza el reloj de la simulación con el tiempo real
 *                           transcurrido
 * @param maxSaltoRelojMs    tope de tiempo real que se acredita en un solo avance (evita saltos tras una pausa larga
 *                           del servidor o un ciclo de planificación lento)
 * @param origenesPermitidos patrones de origen aceptados por el WebSocket, separados por coma ({@code *} = todos; la
 *                           API aún no tiene autenticación)
 */
@Validated
@ConfigurationProperties(prefix = "paqrap.tiempo-real")
public record PropiedadesTiempoReal(
        @DefaultValue("true") boolean difusionHabilitada,
        @DefaultValue("true") boolean relojHabilitado,
        @DefaultValue("5") @Min(1) @Max(10) int frecuenciaHz,
        @DefaultValue("50") @Positive long periodoSondeoMs,
        @DefaultValue("200") @Positive long periodoRelojMs,
        @DefaultValue("2000") @Positive long maxSaltoRelojMs,
        @DefaultValue("*") @NotBlank String origenesPermitidos) {

    /**
     * Separa {@link #origenesPermitidos()} en patrones individuales.
     *
     * @return patrones de origen sin espacios sobrantes, en el orden configurado
     */
    public String[] patronesOrigen() {
        return java.util.Arrays.stream(origenesPermitidos.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
