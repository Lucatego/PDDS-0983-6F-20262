package pe.pucp.paqrap.backend.api;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint mínimo de verificación: {@code GET /api/salud}. Permite comprobar que el servidor está en línea y qué
 * versión está desplegada (RNF de disponibilidad en el laboratorio).
 */
@RestController
public class SaludControlador {

    private final String version;

    /**
     * Crea el controlador con la versión del artefacto.
     *
     * @param version versión tomada de {@code paqrap.version} (filtrada desde el POM al compilar)
     */
    public SaludControlador(@Value("${paqrap.version}") String version) {
        this.version = version;
    }

    /**
     * Devuelve el estado del servicio.
     *
     * @return estado {@code OK}, versión e instante del servidor
     */
    @GetMapping("/salud")
    public RespuestaSalud salud() {
        return new RespuestaSalud("OK", version, Instant.now());
    }

    /**
     * Cuerpo de la respuesta de salud.
     *
     * @param estado   estado del servicio ({@code OK} si responde)
     * @param version  versión del backend
     * @param instante instante del servidor en UTC
     */
    public record RespuestaSalud(String estado, String version, Instant instante) {
    }
}
