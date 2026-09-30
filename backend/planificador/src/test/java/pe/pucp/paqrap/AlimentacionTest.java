package pe.pucp.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.EstadoOperacion;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.Ruta;
import pe.pucp.paqrap.estricto.modelo.Vehiculo;
import pe.pucp.paqrap.estricto.servicios.EvaluadorFactibilidad;

/**
 * Fronteras del refrigerio en los tres turnos (07:00, 15:00 y 23:00). Porta la parte del núcleo de
 * {@code AlimentacionTest} del prototipo; los casos de {@code SimulacionComparada.descansosCompletados} se
 * portarán con la simulación.
 */
class AlimentacionTest {

    @ParameterizedTest(name = "pedido registrado a las {0}:00")
    @ValueSource(ints = {13, 21, 5})
    void entregaAntesDeAlimentacionTardia(int hora) {
        var par = ParametrosOperacion.porDefecto();
        var n = new Nodo(25, 15);
        var t = LocalDate.of(2026, 1, 20).atTime(hora, 0);
        var e = new EstadoOperacion(t, List.of(new Pedido("p", t, n, 1, 4)), List.of(new Vehiculo("TA01", n)),
                List.of(new Almacen("A", n, 10, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
        var ev = new EvaluadorFactibilidad(e, par);
        var rr = ev.evaluarRuta(new Ruta("TA01", "A", ev.partes(), false));
        assertTrue(rr.factible(), "ruta infactible");
        assertEquals(t.plusHours(1), rr.paradas().get(0).finServicio(), "Entrega antes de alimentacion tardia");
        assertEquals(t.plusHours(1), rr.descansoInicio(), "Alimentacion hasta cambio de turno");
        assertEquals(t.plusHours(2), rr.descansoFin(), "Alimentacion hasta cambio de turno");
    }
}
