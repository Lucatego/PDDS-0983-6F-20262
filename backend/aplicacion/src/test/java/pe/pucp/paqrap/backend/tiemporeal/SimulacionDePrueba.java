package pe.pucp.paqrap.backend.tiemporeal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/** Fabrica motores y orquestadores pequeños y deterministas para las pruebas del canal en tiempo real. */
final class SimulacionDePrueba {

    static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 15, 7, 0);
    static final List<Almacen> ALMACENES = List.of(new Almacen("CENTRAL", new Nodo(27, 14), 0, true),
            new Almacen("NOROESTE", new Nodo(12, 38), 1000, false),
            new Almacen("ESTE", new Nodo(57, 27), 1000, false));

    private SimulacionDePrueba() {
    }

    static ConfiguracionSimulacion configuracion(ConfiguracionSimulacion.Escenario escenario, double aceleracion) {
        return new ConfiguracionSimulacion(escenario, INICIO,
                Map.of(TipoVehiculo.TA, 1, TipoVehiculo.TM, 0, TipoVehiculo.TB, 0),
                Map.of("NOROESTE", 1000, "ESTE", 1000), 10, aceleracion, 20262,
                new ParametrosOperacion(60, false, 480, 420, 60, 420, 60, 4, 50, 1000000,
                        Map.of(TipoVehiculo.TA, 40.0, TipoVehiculo.TM, 25.0, TipoVehiculo.TB, 12.0)));
    }

    static MotorSimulacion motor5d(double aceleracion, List<Pedido> pedidos) {
        return new MotorSimulacion(configuracion(ConfiguracionSimulacion.Escenario.SIMULACION_5D, aceleracion),
                pedidos, ALMACENES, List.of(), List.of());
    }

    static TabuSearchPlanner planificador() {
        return new TabuSearchPlanner(new ConfiguracionTabu(1, 7, 30, 2, 0, 20262));
    }

    static OrquestadorSimulacion orquestador5d(double aceleracion) {
        var pedidos = List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 3, 4, "c1"),
                new Pedido("P2", INICIO.plusMinutes(30), new Nodo(30, 16), 2, 8, "c2"));
        return new OrquestadorSimulacion(motor5d(aceleracion, pedidos), planificador());
    }
}
