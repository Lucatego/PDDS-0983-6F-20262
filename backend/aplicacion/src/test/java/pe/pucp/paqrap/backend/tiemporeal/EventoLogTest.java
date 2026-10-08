package pe.pucp.paqrap.backend.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion.Escenario;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion.Evento;

/** Conversión de la bitácora del motor al {@code LogEvent} del frontend (LE054, B-09). */
class EventoLogTest {

    private static final LocalDateTime MEDIANOCHE = LocalDateTime.of(2026, 9, 15, 0, 0);

    private static Evento evento(String tipo, String mensaje, String pedido, String vehiculo, String almacen) {
        return new Evento(7, MEDIANOCHE.plusHours(7).plusMinutes(30), tipo, mensaje, pedido, vehiculo, almacen, null);
    }

    @Test
    void usaLaSecuenciaComoIdYMinutosDesdeLaMedianoche() {
        var log = EventoLog.desde(evento("CICLO_PLANIFICADO", "Ciclo 3: COMPLETA", null, null, null), MEDIANOCHE,
                Escenario.SIMULACION_5D);

        assertThat(log.id()).isEqualTo(7);
        assertThat(log.simMin()).isEqualTo(450);
        assertThat(log.text()).isEqualTo("Ciclo 3: COMPLETA");
        assertThat(log.kind()).isEqualTo("accent");
    }

    @Test
    void resaltaCodigosDePedidoUnidadYAlmacen() {
        var log = EventoLog.desde(evento("PEDIDO_ENTREGADO", "Entrega de P1#2 por TA01 desde CENTRAL", "P1#2",
                "TA01", "CENTRAL"), MEDIANOCHE, Escenario.DIA_A_DIA);

        assertThat(log.text()).isEqualTo("Entrega de **P1#2** por **TA01** desde **CENTRAL**");
        assertThat(log.kind()).isEqualTo("good");
    }

    @Test
    void elColapsoEsCriticoYLoReconoceElFrontend() {
        var log = EventoLog.desde(evento("COLAPSO", "COLAPSO_PLAZO", "V202609-L00001", null, null), MEDIANOCHE,
                Escenario.COLAPSO);

        assertThat(log.kind()).isEqualTo("critical");
        // SimulationBridge.tsx busca /colapso/i en los eventos críticos para mostrar el aviso.
        assertThat(log.text()).containsIgnoringCase("colapso").contains("**V202609-L00001**");
    }

    @Test
    void elCierreDe5DUsaElTextoQueEsperaElFrontend() {
        var cierre5d = EventoLog.desde(evento("EJECUCION_FINALIZADA", "FIN_DE_HORIZONTE", null, null, null),
                MEDIANOCHE, Escenario.SIMULACION_5D);
        var otro = EventoLog.desde(evento("EJECUCION_FINALIZADA", "FIN_DE_DATOS", null, null, null), MEDIANOCHE,
                Escenario.DIA_A_DIA);

        assertThat(cierre5d.text()).contains("Simulación 5D completada");
        assertThat(otro.text()).doesNotContain("5D");
    }

    @Test
    void laDetencionManualSeLeeEnLenguajeNatural() {
        var log = EventoLog.desde(evento("EJECUCION_DETENIDA", "DETENIDA_POR_USUARIO", null, null, null), MEDIANOCHE,
                Escenario.DIA_A_DIA);

        assertThat(log.text()).isEqualTo("**Ejecución detenida** por el usuario.");
        assertThat(log.kind()).isEqualTo("warning");
    }

    @Test
    void clasificaLasIncidenciasYLasPausas() {
        assertThat(EventoLog.desde(evento("AVERIA_REGISTRADA", "Averia en TA01", null, "TA01", null), MEDIANOCHE,
                Escenario.DIA_A_DIA).kind()).isEqualTo("critical");
        assertThat(EventoLog.desde(evento("BLOQUEO_REGISTRADO", "Bloqueo", null, null, null), MEDIANOCHE,
                Escenario.DIA_A_DIA).kind()).isEqualTo("warning");
        assertThat(EventoLog.desde(evento("EJECUCION_PAUSADA", "Ejecucion pausada", null, null, null), MEDIANOCHE,
                Escenario.DIA_A_DIA).kind()).isEqualTo("warning");
    }
}
