package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import pe.pucp.paqrap.estricto.modelo.Nodo;

/** Pruebas unitarias de la traducción de excepciones a {@code { "mensaje": "..." }}. */
class ManejadorErroresTest {

    private final ManejadorErrores manejador = new ManejadorErrores();

    @Test
    @DisplayName("Una validación del dominio responde 400 con su mensaje")
    void validacionDelDominio() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Nodo(71, 0));
        var respuesta = manejador.argumentoIlegal(ex);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().mensaje()).contains("Nodo fuera");
    }

    @Test
    @DisplayName("Un ResponseStatusException conserva su estado y motivo")
    void conEstado() {
        var respuesta = manejador.conEstado(new ResponseStatusException(HttpStatus.CONFLICT, "Simulación en curso"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().mensaje()).isEqualTo("Simulación en curso");
    }

    @Test
    @DisplayName("Un error no previsto responde 500 sin exponer detalles")
    void errorInterno() {
        var respuesta = manejador.errorNoPrevisto(new IllegalStateException("detalle interno"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().mensaje()).doesNotContain("detalle interno");
    }
}
