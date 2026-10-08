package pe.pucp.paqrap.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

class GeneradorResumenTest {
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 15, 7, 0);

    @Test
    void calculaEntregasCumplimientoTiempoYCapacidadDeLaEjecucion() {
        var configuracion = new ConfiguracionSimulacion(ConfiguracionSimulacion.Escenario.SIMULACION_5D, INICIO,
                Map.of(TipoVehiculo.TA, 1, TipoVehiculo.TM, 0, TipoVehiculo.TB, 0),
                Map.of("NOROESTE", 1000, "ESTE", 1000), 10, 4, 20262,
                new ParametrosOperacion(60, false, 480, 420, 60, 420, 60, 4, 50, 1000000,
                        Map.of(TipoVehiculo.TA, 40.0, TipoVehiculo.TM, 25.0, TipoVehiculo.TB, 12.0)));
        var almacenes = List.of(new Almacen("CENTRAL", new Nodo(27, 14), 0, true),
                new Almacen("NOROESTE", new Nodo(12, 38), 1000, false),
                new Almacen("ESTE", new Nodo(57, 27), 1000, false));
        var motor = new MotorSimulacion(configuracion,
                List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4)), almacenes, List.of(), List.of());
        motor.iniciar();
        motor.avanzar(20, new TabuSearchPlanner(new ConfiguracionTabu(1, 7, 30, 2, 0, 20262)));

        var resumen = GeneradorResumen.generar(motor, !motor.esFinal());

        assertThat(resumen.pedidosTotales()).isEqualTo(1);
        assertThat(resumen.pedidosEntregados()).isEqualTo(1);
        assertThat(resumen.pedidosEntregadosEnPlazo()).isEqualTo(1);
        assertThat(resumen.cumplimientoPct()).isEqualTo(100.0);
        assertThat(resumen.paquetesEntregados()).isEqualTo(1);
        assertThat(resumen.tiempoEntregaPromedioH()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(resumen.indicadoresPlazo().get(4).pedidosEnPlazo()).isEqualTo(1);
        assertThat(resumen.utilizacionCapacidadPct()).isGreaterThan(0);
    }
}
