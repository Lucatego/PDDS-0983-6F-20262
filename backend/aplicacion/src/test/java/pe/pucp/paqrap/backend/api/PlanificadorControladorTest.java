package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.backend.persistencia.ServicioCargaArchivos;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;

class PlanificadorControladorTest {

    @Test
    void catalogosCumpleLosGruposDelContratoFrontend() {
        var controlador = new PlanificadorControlador(null, null, null, null, null);

        var catalogos = controlador.catalogos();

        assertThat((java.util.List<?>) catalogos.get("vehicleTypes")).hasSize(3);
        assertThat((java.util.List<?>) catalogos.get("fallaTypes")).hasSize(3);
        assertThat((java.util.List<?>) catalogos.get("modalidades")).hasSize(5);
    }

    @Test
    void estadoSinEjecucionAunTieneLaFormaCompletaDeSnapshot() {
        var orquestador = mock(OrquestadorSimulacion.class);
        when(orquestador.motor()).thenReturn(null);
        var controlador = new PlanificadorControlador(orquestador, mock(ServicioCargaArchivos.class), null, null, null);

        var snapshot = controlador.estado();

        assertThat(snapshot).containsKeys("scenario", "configured", "running", "waitingFirstOrder", "collapsed",
                "finished", "simMin", "runStartSimMin", "cycleDay", "epochDate", "runElapsedMs", "shiftStarts",
                "fleet", "vehicles", "orders", "orderHistory", "incidents", "incidentHistory", "warehouses",
                "stats", "flashes", "files");
        assertThat(snapshot.get("configured")).isEqualTo(false);
    }

    @Test
    void pedidoFueraDeRangoDevuelveResultadoFallidoSinAccesoABaseDeDatos() {
        var controlador = new PlanificadorControlador(null, null, null, null, null);

        var resultado = controlador.pedido(new PlanificadorControlador.OrderInput("cliente", 25, 36, 1, 1));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.motivo()).contains("rango");
    }
}
