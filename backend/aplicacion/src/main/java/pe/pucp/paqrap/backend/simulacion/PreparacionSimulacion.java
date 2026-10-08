package pe.pucp.paqrap.backend.simulacion;

import java.util.List;
import java.util.Map;
import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.Mantenimiento;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/** Entrada consistente obtenida de PostgreSQL antes del inicio; no representa una ejecucion iniciada (LE008/053). */
public record PreparacionSimulacion(long ejecucionId, ConfiguracionSimulacion configuracion,
        ConfiguracionTabu algoritmo, List<Pedido> pedidos, List<Almacen> almacenes,
        List<Bloqueo> bloqueos, List<Mantenimiento> mantenimientos, Map<String, Long> pedidosPersistidos,
        Map<Long, String> archivos) {
    public PreparacionSimulacion {
        pedidos = List.copyOf(pedidos);
        almacenes = List.copyOf(almacenes);
        bloqueos = List.copyOf(bloqueos);
        mantenimientos = List.copyOf(mantenimientos);
        pedidosPersistidos = Map.copyOf(pedidosPersistidos);
        archivos = Map.copyOf(archivos);
    }

    public MotorSimulacion nuevoMotor() {
        return new MotorSimulacion(configuracion, pedidos, almacenes, bloqueos, mantenimientos);
    }

    public TabuSearchPlanner nuevoPlanificador() {
        return new TabuSearchPlanner(algoritmo);
    }
}
