package pe.pucp.paqrap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Averia;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.EstadoOperacion;
import pe.pucp.paqrap.estricto.modelo.Mantenimiento;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.Ruta;
import pe.pucp.paqrap.estricto.modelo.Solucion;
import pe.pucp.paqrap.estricto.modelo.Vehiculo;
import pe.pucp.paqrap.estricto.servicios.EvaluadorFactibilidad;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/**
 * Datos y fábricas compartidas por las pruebas del planificador. Reproduce los auxiliares estáticos de
 * {@code RestriccionesTabuTest} del prototipo (carpeta {@code algoritmos/experimentacion}).
 */
final class EscenariosPrueba {

    /** Instante de referencia de las pruebas: 01/09/2026 08:00. */
    static final LocalDateTime T = LocalDate.of(2026, 9, 1).atTime(8, 0);

    /** Nodo común para vehículos, almacén y clientes en los escenarios sencillos. */
    static final Nodo N = new Nodo(25, 15);

    private EscenariosPrueba() {
    }

    /** Parámetros por defecto con turnos desde las 00:00 y el tiempo de servicio indicado. */
    static ParametrosOperacion parametros(int servicio) {
        var p = ParametrosOperacion.porDefecto();
        return new ParametrosOperacion(servicio, true, 480, 0, 60, 420, 60, 4, 50, 1e6, p.velocidades());
    }

    static EstadoOperacion estado(List<Pedido> pedidos, List<Vehiculo> flota, List<Almacen> almacenes,
            List<Bloqueo> bloqueos, List<Averia> averias, List<Mantenimiento> mantenimientos, List<Ruta> activas,
            Set<String> descanso) {
        return new EstadoOperacion(T, pedidos, flota, almacenes, bloqueos, averias, mantenimientos, activas, descanso);
    }

    /** Dos autos y un almacén con stock 100, todos en {@link #N}. */
    static EstadoOperacion simple(List<Pedido> pedidos) {
        return estado(pedidos, List.of(new Vehiculo("TA01", N), new Vehiculo("TA02", N)),
                List.of(new Almacen("A", N, 100, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
    }

    /** Pedido registrado en {@link #T}, ubicado en {@link #N} y con plazo de 4 h. */
    static Pedido pedido(String id, int cantidad) {
        return new Pedido(id, T, N, cantidad, 4);
    }

    /** Solución con todas las partes en una sola ruta de TA01 desde el almacén A. */
    static Solucion una(EvaluadorFactibilidad ev) {
        return new Solucion(List.of(new Ruta("TA01", "A", ev.partes(), false)), List.of());
    }

    /** Tabu Search con presupuesto pequeño y semilla fija (configuración de las regresiones originales). */
    static TabuSearchPlanner tabu() {
        return new TabuSearchPlanner(new ConfiguracionTabu(6, 3, 6, 100, 0, 7));
    }
}
