package pe.pucp.paqrap.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReglaAveriaTest {
    private static final int TURNO = 480;
    private static final int INICIO_TURNO = 420;

    @Test
    void tipoUnoDuraDosHoras() {
        var inicio = LocalDateTime.of(2026, 10, 8, 10, 0);
        assertThat(ReglaAveria.calcularFin("DURACION_FIJA", inicio, 120, null, null, TURNO, INICIO_TURNO))
                .isEqualTo(inicio.plusHours(2));
    }

    @Test
    void tipoDosFinalizaAlCerrarElTurnoSiguiente() {
        var inicio = LocalDateTime.of(2026, 10, 8, 10, 0);
        assertThat(ReglaAveria.calcularFin("FIN_TURNO_SIGUIENTE", inicio, null, null, null, TURNO, INICIO_TURNO))
                .isEqualTo(LocalDateTime.of(2026, 10, 8, 23, 0));
    }

    @Test
    void tipoTresRetornaAlPrimerTurnoConfiguradoTrasDosDias() {
        var inicio = LocalDateTime.of(2026, 10, 8, 16, 0);
        assertThat(ReglaAveria.calcularFin("DIAS_Y_TURNO", inicio, null, 2, 900, TURNO, INICIO_TURNO))
                .isEqualTo(LocalDateTime.of(2026, 10, 11, 15, 0));
    }
}
