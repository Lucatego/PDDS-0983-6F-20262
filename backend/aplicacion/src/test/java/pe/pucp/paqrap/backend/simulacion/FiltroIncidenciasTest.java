package pe.pucp.paqrap.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.estricto.modelo.Averia;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.Mantenimiento;
import pe.pucp.paqrap.estricto.modelo.Nodo;

class FiltroIncidenciasTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 15, 8, 0);

    @Test
    void siNoConsideraIncidenciasRetornaVacio() {
        var filtro = new FiltroIncidencias(false);

        var bloqueo = new Bloqueo(AHORA.minusMinutes(10), AHORA.plusMinutes(20), List.of(new Nodo(1, 1), new Nodo(1, 2)));
        var averia = new Averia("TA01", AHORA.minusMinutes(5), AHORA.plusMinutes(50));
        var mant = new Mantenimiento("TA01", AHORA.minusMinutes(5), AHORA.plusMinutes(50));

        assertThat(filtro.filtrarBloqueos(AHORA, List.of(bloqueo), 10)).isEmpty();
        assertThat(filtro.filtrarAverias(AHORA, List.of(averia), 10, Set.of("TA01"))).isEmpty();
        assertThat(filtro.filtrarMantenimientos(AHORA, List.of(mant), 10, Set.of("TA01"))).isEmpty();
    }

    @Test
    void filtraBloqueosPorVentanaYExcluyePasadosYLejanos() {
        var filtro = new FiltroIncidencias(true);

        var pasado = new Bloqueo(AHORA.minusHours(2), AHORA.minusMinutes(1), List.of(new Nodo(1, 1), new Nodo(1, 2)));
        var activo = new Bloqueo(AHORA.minusMinutes(10), AHORA.plusMinutes(20), List.of(new Nodo(2, 2), new Nodo(2, 3)));
        var enVentana = new Bloqueo(AHORA.plusMinutes(5), AHORA.plusMinutes(35), List.of(new Nodo(3, 3), new Nodo(3, 4)));
        var futuroLejano = new Bloqueo(AHORA.plusHours(1), AHORA.plusHours(2), List.of(new Nodo(4, 4), new Nodo(4, 5)));

        var resultado = filtro.filtrarBloqueos(AHORA, List.of(pasado, activo, enVentana, futuroLejano), 10);
        assertThat(resultado).containsExactly(activo, enVentana);
    }

    @Test
    void filtraAveriasYDescartaVehiculosDesconocidos() {
        var filtro = new FiltroIncidencias(true);

        var activaValida = new Averia("TA01", AHORA.minusMinutes(10), AHORA.plusHours(1));
        var activaInvalida = new Averia("TA99", AHORA.minusMinutes(10), AHORA.plusHours(1));
        var lejana = new Averia("TA01", AHORA.plusHours(3), AHORA.plusHours(5));

        var resultado = filtro.filtrarAverias(AHORA, List.of(activaValida, activaInvalida, lejana), 15, Set.of("TA01"));
        assertThat(resultado).containsExactly(activaValida);
    }

    @Test
    void filtraMantenimientosYDescartaPasados() {
        var filtro = new FiltroIncidencias(true);

        var pasado = new Mantenimiento("TA01", AHORA.minusHours(5), AHORA.minusHours(1));
        var activo = new Mantenimiento("TA01", AHORA.minusMinutes(30), AHORA.plusHours(4));

        var resultado = filtro.filtrarMantenimientos(AHORA, List.of(pasado, activo), 10, Set.of("TA01"));
        assertThat(resultado).containsExactly(activo);
    }
}
