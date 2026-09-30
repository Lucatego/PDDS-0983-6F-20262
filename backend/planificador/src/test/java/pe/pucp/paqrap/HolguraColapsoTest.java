package pe.pucp.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.pucp.paqrap.EscenariosPrueba.N;
import static pe.pucp.paqrap.EscenariosPrueba.T;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.EstadoOperacion;
import pe.pucp.paqrap.estricto.modelo.Parada;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.PartePedido;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.ResultadoRuta;
import pe.pucp.paqrap.estricto.modelo.Ruta;
import pe.pucp.paqrap.estricto.modelo.Solucion;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.estricto.modelo.Vehiculo;
import pe.pucp.paqrap.estricto.servicios.EvaluadorFactibilidad;
import pe.pucp.paqrap.estricto.servicios.Holguras;
import pe.pucp.paqrap.estricto.servicios.PlanificadorEstricto;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/**
 * Holgura, completitud, colapso y función objetivo. Porta a JUnit 5 la parte del núcleo y de TS de
 * {@code ExperimentacionTest} del prototipo (sin ALNS ni la exportación CSV del experimento).
 */
class HolguraColapsoTest {

    private static final PlanificadorEstricto MOTOR = new TabuSearchPlanner(new ConfiguracionTabu(3, 7, 3, 100, 0, 7));

    private final Pedido pedido = new Pedido("p", T, N, 8, 4);

    private final EstadoOperacion estado = new EstadoOperacion(T, List.of(pedido), List.of(new Vehiculo("TA01", N)),
            List.of(new Almacen("A", N, 100, false)), List.of(), List.of(), List.of(), List.of(), Set.of());

    @Test
    @DisplayName("La holgura usa el fin de servicio de la última parte y omite pedidos parciales")
    void holguraPorUltimaParte() {
        var a = new PartePedido("a", pedido, 4);
        var b = new PartePedido("b", pedido, 4);
        var r1 = new ResultadoRuta(new Ruta("TA01", "A", List.of(a), false), T, T.plusMinutes(60), "A",
                List.of(new Parada(List.of(a), T, T.plusMinutes(30))), List.of(), null, null, 0, 0, List.of());
        var r2 = new ResultadoRuta(new Ruta("TA02", "A", List.of(b), false), T, T.plusMinutes(100), "A",
                List.of(new Parada(List.of(b), T.plusMinutes(40), T.plusMinutes(90))), List.of(), null, null, 0, 0,
                List.of());
        var h = Holguras.calcular(List.of(pedido), List.of(r1, r2));
        assertEquals(1, h.completos(), "Usar ultima parte y fin de servicio");
        assertEquals(150.0, h.promedioMin(), "Usar ultima parte y fin de servicio");
        assertEquals(150.0, h.minimaMin(), "Usar ultima parte y fin de servicio");
        assertNull(Holguras.calcular(List.of(pedido), List.of(r1)).promedioMin(),
                "No promediar pedido parcialmente entregado");
    }

    @Test
    @DisplayName("Una instancia sencilla termina COMPLETA con holgura de 180 min")
    void instanciaSencillaCompleta() {
        var completo = MOTOR.planificar(estado, ParametrosOperacion.porDefecto());
        assertEquals("COMPLETA", completo.metricas().estadoResultado(), "Debe completar instancia sencilla");
        assertEquals(180.0, completo.metricas().holguraPromedioMin(),
                "Descanso no debe retrasar entrega innecesariamente");
        assertNotNull(completo.metricas().taPrimeraCompletaMs(), "Tiempo primera completa");
        assertTrue(completo.metricas().taPrimeraCompletaMs() <= completo.metricas().taMs(), "Tiempo primera completa");
    }

    @Test
    @DisplayName("Sin stock se informa COLAPSO_PLANIFICACION sin holguras artificiales")
    void colapsoSinStock() {
        var sinStock = new EstadoOperacion(T, estado.pedidos(), estado.vehiculos(),
                List.of(new Almacen("A", N, 0, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
        var colapso = MOTOR.planificar(sinStock, ParametrosOperacion.porDefecto());
        assertTrue(colapso.metricas().colapso(), "Colapso sin ceros artificiales");
        assertNull(colapso.metricas().holguraPromedioMin(), "Colapso sin ceros artificiales");
        assertNull(colapso.metricas().taPrimeraCompletaMs(), "Colapso sin ceros artificiales");
    }

    @Test
    @DisplayName("Sin pedidos el resultado es SIN_DEMANDA y no cuenta como colapso")
    void sinDemanda() {
        var vacio = new EstadoOperacion(T, List.of(), estado.vehiculos(), estado.almacenes(), List.of(), List.of(),
                List.of(), List.of(), Set.of());
        var sinDemanda = MOTOR.planificar(vacio, ParametrosOperacion.porDefecto());
        assertEquals("SIN_DEMANDA", sinDemanda.metricas().estadoResultado(), "Sin demanda no es colapso");
        assertNull(sinDemanda.metricas().holguraPromedioMin(), "Sin demanda no es colapso");
    }

    @Test
    @DisplayName("Sin vehículos se informa colapso")
    void colapsoSinFlota() {
        var imposible = new EstadoOperacion(T, estado.pedidos(), List.of(), estado.almacenes(), List.of(), List.of(),
                List.of(), List.of(), Set.of());
        var fallo = MOTOR.planificar(imposible, ParametrosOperacion.porDefecto());
        assertTrue(fallo.metricas().colapso(), "Sin flota debe colapsar");
        assertNull(fallo.metricas().holguraPromedioMin(), "Holgura vacia en colapso");
        assertNull(fallo.metricas().holguraMinimaMin(), "Holgura vacia en colapso");
        assertNull(fallo.metricas().taPrimeraCompletaMs(), "Primera completa vacia en colapso");
    }

    @Test
    @DisplayName("El objetivo prioriza completitud, luego holgura, y no depende del costo")
    void funcionObjetivo() {
        var rapido = new EvaluadorFactibilidad(estado, ParametrosOperacion.porDefecto());
        var partes = rapido.partes();
        var sol = new Solucion(List.of(new Ruta("TA01", "A", partes, false)), List.of());
        var p = ParametrosOperacion.porDefecto();
        var costoso = new ParametrosOperacion(p.servicioMinutos(), true, p.turnoMinutos(), p.inicioTurnoMinuto(),
                p.descansoDesde(), p.descansoHasta(), p.descansoMinutos(), p.tamanioParte(), 1000000000, 1,
                p.velocidades());
        assertEquals(rapido.evaluar(sol).objetivo(), new EvaluadorFactibilidad(estado, costoso).evaluar(sol).objetivo(),
                "El costo no debe cambiar el objetivo");
        var parcial = new Solucion(List.of(new Ruta("TA01", "A", List.of(partes.get(0)), false)),
                List.of(partes.get(1)));
        assertTrue(rapido.evaluar(sol).objetivo() < rapido.evaluar(parcial).objetivo(), "Completitud tiene prioridad");
        var tarde = new EstadoOperacion(T, estado.pedidos(),
                List.of(new Vehiculo("TA01", TipoVehiculo.TA, N, true, T.plusMinutes(20))), estado.almacenes(),
                List.of(), List.of(), List.of(), List.of(), Set.of());
        assertTrue(rapido.evaluar(sol).objetivo() < new EvaluadorFactibilidad(tarde, ParametrosOperacion.porDefecto())
                .evaluar(sol).objetivo(), "Objetivo favorece holgura");
    }
}
