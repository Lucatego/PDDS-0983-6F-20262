package pe.pucp.paqrap.backend.simulacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import pe.pucp.paqrap.estricto.modelo.Averia;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.Mantenimiento;

/**
 * Filtra las restricciones e incidencias activas para el instante y horizonte de planificación (B-06).
 * Mantiene a Tabu Search desacoplado de la simulación, entregando únicamente las incidencias vigentes.
 */
public final class FiltroIncidencias {

    private final boolean considerarIncidencias;

    public FiltroIncidencias(boolean considerarIncidencias) {
        this.considerarIncidencias = considerarIncidencias;
    }

    public boolean considerarIncidencias() {
        return considerarIncidencias;
    }

    /**
     * Filtra los bloqueos vigentes en el instante o que inician dentro de la ventana del ciclo.
     */
    public List<Bloqueo> filtrarBloqueos(LocalDateTime reloj, List<Bloqueo> bloqueos, int ventanaMinutos) {
        if (!considerarIncidencias || bloqueos == null || bloqueos.isEmpty()) {
            return List.of();
        }
        LocalDateTime limite = reloj.plusMinutes(ventanaMinutos);
        return bloqueos.stream()
                .filter(b -> !b.fin().isBefore(reloj) && !b.inicio().isAfter(limite))
                .toList();
    }

    /**
     * Filtra las averías que están activas o inician en la ventana del ciclo para vehículos válidos.
     */
    public List<Averia> filtrarAverias(LocalDateTime reloj, List<Averia> averias, int ventanaMinutos, Set<String> vehiculosValidos) {
        if (!considerarIncidencias || averias == null || averias.isEmpty()) {
            return List.of();
        }
        LocalDateTime limite = reloj.plusMinutes(ventanaMinutos);
        return averias.stream()
                .filter(a -> vehiculosValidos.contains(a.vehiculo()))
                .filter(a -> !a.fin().isBefore(reloj) && !a.inicio().isAfter(limite))
                .toList();
    }

    /**
     * Filtra los mantenimientos vigentes para las unidades conocidas.
     */
    public List<Mantenimiento> filtrarMantenimientos(LocalDateTime reloj, List<Mantenimiento> mantenimientos,
            int ventanaMinutos, Set<String> vehiculosValidos) {
        if (!considerarIncidencias || mantenimientos == null || mantenimientos.isEmpty()) {
            return List.of();
        }
        LocalDateTime limite = reloj.plusMinutes(ventanaMinutos);
        return mantenimientos.stream()
                .filter(m -> vehiculosValidos.contains(m.vehiculo()))
                .filter(m -> !m.fin().isBefore(reloj) && !m.inicio().isAfter(limite))
                .toList();
    }
}
