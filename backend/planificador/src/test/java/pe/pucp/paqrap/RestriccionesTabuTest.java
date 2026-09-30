package pe.pucp.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.pucp.paqrap.EscenariosPrueba.N;
import static pe.pucp.paqrap.EscenariosPrueba.T;
import static pe.pucp.paqrap.EscenariosPrueba.estado;
import static pe.pucp.paqrap.EscenariosPrueba.parametros;
import static pe.pucp.paqrap.EscenariosPrueba.pedido;
import static pe.pucp.paqrap.EscenariosPrueba.simple;
import static pe.pucp.paqrap.EscenariosPrueba.tabu;
import static pe.pucp.paqrap.EscenariosPrueba.una;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.pucp.paqrap.estricto.caminos.GridMap;
import pe.pucp.paqrap.estricto.caminos.PathFinder;
import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Averia;
import pe.pucp.paqrap.estricto.modelo.Bloqueo;
import pe.pucp.paqrap.estricto.modelo.EstadoOperacion;
import pe.pucp.paqrap.estricto.modelo.EvaluacionSolucion;
import pe.pucp.paqrap.estricto.modelo.Mantenimiento;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.PartePedido;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.Ruta;
import pe.pucp.paqrap.estricto.modelo.Solucion;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.estricto.modelo.Vehiculo;
import pe.pucp.paqrap.estricto.servicios.EvaluadorFactibilidad;
import pe.pucp.paqrap.estricto.servicios.GestorDisponibilidad;
import pe.pucp.paqrap.tabu.AssignmentNeighborhood;
import pe.pucp.paqrap.tabu.CandidateSelector;
import pe.pucp.paqrap.tabu.Candidato;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.RoutingNeighborhood;
import pe.pucp.paqrap.tabu.TabuList;
import pe.pucp.paqrap.tabu.TabuMove;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/**
 * Regresiones de restricciones duras del núcleo común y del Tabu Search. Porta a JUnit 5 los quince grupos de
 * {@code RestriccionesTabuTest} del prototipo y la parte TS de {@code RestriccionesEstrictasTest}
 * (comparabilidad y registro de la mejor iteración), sin los casos de ALNS.
 */
class RestriccionesTabuTest {

    @Test
    @DisplayName("La lista tabú prohíbe el movimiento inverso durante la tenencia")
    void listaTabu() {
        var l = new TabuList();
        var ida = TabuMove.asignacion("p", "a", "b");
        l.registrar(ida, 5, 3);
        assertTrue(l.esTabu(TabuMove.asignacion("p", "b", "a"), 6), "inversa no tabu");
        assertTrue(l.esTabu(TabuMove.asignacion("p", "c", "a"), 8), "atributo expirado antes");
        assertFalse(l.esTabu(TabuMove.asignacion("p", "b", "a"), 9), "tabu no expira");
        var swap = TabuMove.swap("v", "a", "b");
        l.registrar(swap, 1, 2);
        assertTrue(l.esTabu(TabuMove.swap("v", "b", "a"), 2), "swap inverso");
        l.registrar(TabuMove.relocate("v", "a", 0, 2), 1, 2);
        assertTrue(l.esTabu(TabuMove.relocate("v", "a", 2, 0), 2), "relocate inverso");
    }

    @Test
    @DisplayName("La aspiración solo acepta movimientos tabú factibles que mejoran el mejor global")
    void aspiracion() {
        var l = new TabuList();
        l.registrar(TabuMove.asignacion("p", "a", "b"), 1, 3);
        var c = new Candidato(new Solucion(List.of(), List.of()), TabuMove.asignacion("p", "b", "a"));
        var selector = new CandidateSelector(l, 2, 100);
        selector.considerar(c, new EvaluacionSolucion(List.of(), List.of(), 101, 0));
        assertNull(selector.elegido(), "tabu aceptado");
        selector.considerar(c, new EvaluacionSolucion(List.of(), List.of("plazo"), 90, 0));
        assertNull(selector.elegido(), "aspiracion infactible");
        selector.considerar(c, new EvaluacionSolucion(List.of(), List.of(), 90, 0));
        assertNotNull(selector.elegido(), "aspiracion no aplicada");
    }

    @Test
    @DisplayName("Un pedido mayor que la capacidad se divide en partes entre vehículos (LE027)")
    void division() {
        var e = simple(List.of(pedido("grande", 30)));
        var resultado = tabu().planificar(e, parametros(10));
        assertTrue(resultado.evaluacion().completa(), "pedido grande no dividido");
        assertEquals(2, resultado.metricas().vehiculosUsados(), "division entre vehiculos");
        assertEquals(30, resultado.solucion().rutas().stream().flatMap(r -> r.partes().stream())
                .mapToInt(PartePedido::cantidad).sum(), "cantidad perdida");
    }

    @Test
    @DisplayName("Un pedido vencido queda pendiente y el fin de servicio respeta el plazo")
    void plazo() {
        var vencido = new Pedido("vencido", T.minusHours(5), N, 2, 4);
        var e = simple(List.of(vencido));
        var r = tabu().planificar(e, parametros(60));
        assertTrue(r.evaluacion().factible() && !r.evaluacion().completa()
                && r.metricas().paquetesPendientes() == 2, "plazo blando");
        var p = new Pedido("fin", T.minusHours(3).minusMinutes(30), N, 1, 4);
        var ev = new EvaluadorFactibilidad(simple(List.of(p)), parametros(60));
        assertFalse(ev.evaluar(una(ev)).factible(), "fin servicio fuera de plazo admitido");
    }

    @Test
    @DisplayName("Si la ruta no cabe en el turno vigente espera al inicio del siguiente")
    void turno() {
        var v = new Vehiculo("TA01", TipoVehiculo.TA, N, true, T.plusHours(7).plusMinutes(30));
        var p = new Pedido("p", T, N, 1, 18);
        var e = estado(List.of(p), List.of(v), List.of(new Almacen("A", N, 5, false)), List.of(), List.of(),
                List.of(), List.of(), Set.of("TA01"));
        var ev = new EvaluadorFactibilidad(e, parametros(60));
        var siguiente = ev.evaluar(una(ev)).rutas().get(0);
        assertTrue(siguiente.factible() && siguiente.salida().equals(T.plusHours(8)), "no espera siguiente turno");
        assertNotNull(siguiente.descansoInicio(), "turno futuro hereda descanso realizado");

        var tarde = new Vehiculo("TA01", TipoVehiculo.TA, N, true, T.plusHours(6).plusMinutes(30));
        var estandar = estado(List.of(p), List.of(tarde), e.almacenes(), List.of(), List.of(), List.of(), List.of(),
                Set.of("TA01"));
        var defecto = new EvaluadorFactibilidad(estandar, ParametrosOperacion.porDefecto());
        var turno15 = defecto.evaluar(una(defecto)).rutas().get(0);
        assertTrue(turno15.factible() && turno15.salida().equals(T.toLocalDate().atTime(15, 0)),
                "no programa turno de las 15:00");

        var noche = T.toLocalDate().atTime(6, 30);
        var pedidoNoche = new Pedido("noche", noche.minusHours(1), N, 1, 4);
        var nocturno = new EstadoOperacion(noche, List.of(pedidoNoche), List.of(new Vehiculo("TA01", N)),
                e.almacenes(), List.of(), List.of(), List.of(), List.of(), Set.of("TA01"));
        var evNoche = new EvaluadorFactibilidad(nocturno, ParametrosOperacion.porDefecto());
        var turno7 = evNoche.evaluar(una(evNoche)).rutas().get(0);
        assertTrue(turno7.factible() && turno7.salida().equals(noche.toLocalDate().atTime(7, 0)),
                "no programa turno de las 07:00");

        var urgente = new Pedido("urgente", noche.minusHours(3), N, 1, 4);
        var sinMargen = new EstadoOperacion(noche, List.of(urgente), List.of(new Vehiculo("TA01", N)),
                e.almacenes(), List.of(), List.of(), List.of(), List.of(), Set.of("TA01"));
        var evUrgente = new EvaluadorFactibilidad(sinMargen, ParametrosOperacion.porDefecto());
        assertFalse(evUrgente.evaluar(una(evUrgente)).factible(), "espera siguiente turno aunque vence el plazo");
    }

    @Test
    @DisplayName("El refrigerio dura 60 min, cae en su banda y no se superpone con un servicio")
    void descanso() {
        var e = simple(List.of(pedido("p", 2)));
        var ev = new EvaluadorFactibilidad(e, parametros(60));
        var r = ev.evaluar(una(ev)).rutas().get(0);
        assertTrue(r.factible() && r.descansoInicio() != null, "sin descanso");
        assertTrue(!r.descansoInicio().isBefore(T.plusHours(1)) && !r.descansoFin().isAfter(T.plusHours(7)),
                "banda descanso");
        assertEquals(60, Duration.between(r.descansoInicio(), r.descansoFin()).toMinutes(), "duracion descanso");
        for (var p : r.paradas()) {
            assertFalse(GestorDisponibilidad.solapa(p.llegada(), p.finServicio(), r.descansoInicio(),
                    r.descansoFin()), "servicio durante descanso");
        }
    }

    @Test
    @DisplayName("No se programa una ruta que se superpone con un mantenimiento")
    void mantenimiento() {
        var e = estado(List.of(pedido("p", 1)), List.of(new Vehiculo("TA01", N)),
                List.of(new Almacen("A", N, 10, false)), List.of(), List.of(),
                List.of(new Mantenimiento("TA01", T.plusMinutes(20), T.plusMinutes(40))), List.of(), Set.of());
        var ev = new EvaluadorFactibilidad(e, parametros(60));
        assertFalse(ev.evaluar(una(ev)).factible(), "mantenimiento durante ruta admitido");
        assertFalse(GestorDisponibilidad.solapa(T, T.plusHours(1), T.plusHours(1), T.plusHours(2)),
                "limite semiabierto");
    }

    @Test
    @DisplayName("No se programa una ruta que se superpone con una avería")
    void averia() {
        var e = estado(List.of(pedido("p", 1)), List.of(new Vehiculo("TA01", N)),
                List.of(new Almacen("A", N, 10, false)), List.of(),
                List.of(new Averia("TA01", T.plusMinutes(20), T.plusMinutes(40))), List.of(), List.of(), Set.of());
        var ev = new EvaluadorFactibilidad(e, parametros(60));
        assertFalse(ev.evaluar(una(ev)).factible(), "averia solapada");
    }

    @Test
    @DisplayName("El despacho agregado de un almacén no supera su stock")
    void stock() {
        var e = estado(List.of(pedido("p", 8)), List.of(new Vehiculo("TA01", N), new Vehiculo("TA02", N)),
                List.of(new Almacen("A", N, 4, false)), List.of(), List.of(), List.of(), List.of(), Set.of());
        var ev = new EvaluadorFactibilidad(e, parametros(10));
        var partes = ev.partes();
        var s = new Solucion(List.of(new Ruta("TA01", "A", List.of(partes.get(0)), false),
                new Ruta("TA02", "A", List.of(partes.get(1)), false)), List.of());
        assertFalse(ev.evaluar(s).factible(), "stock agregado");
    }

    @Test
    @DisplayName("Los caminos evitan tramos bloqueados o esperan su reapertura")
    void bloqueos() {
        var a = new Nodo(1, 1);
        var b = new Nodo(2, 1);
        var mapa = new GridMap(List.of(new Bloqueo(T.plusMinutes(1), T.plusMinutes(10), List.of(a, b))));
        var c = new PathFinder(mapa).buscar(a, b, T, 40);
        assertEquals(3, c.distanciaKm(), "bloqueo futuro no evitado");
        for (var paso : c.pasos()) {
            assertTrue(mapa.cruceValido(paso.origen(), paso.destino(), paso.salida(), paso.llegada()),
                    "arco bloqueado");
        }
        var vuelta = new PathFinder(mapa).buscar(b, a, T.plusMinutes(10), 40);
        assertEquals(1, vuelta.distanciaKm(), "retorno no independiente");
        var cierre = new GridMap(List.of(
                new Bloqueo(T, T.plusMinutes(10), List.of(new Nodo(0, 0), new Nodo(1, 0))),
                new Bloqueo(T, T.plusMinutes(10), List.of(new Nodo(0, 0), new Nodo(0, 1)))));
        var espera = new PathFinder(cierre).buscar(new Nodo(0, 0), new Nodo(1, 0), T, 40);
        assertEquals(T.plusMinutes(10), espera.pasos().get(0).salida(), "espera reapertura");
    }

    @Test
    @DisplayName("Se detectan partes duplicadas o perdidas y el estado de entrada no se muta")
    void integridad() {
        var ev = new EvaluadorFactibilidad(simple(List.of(pedido("p", 1))), parametros(10));
        var s = una(ev);
        assertFalse(ev.evaluar(new Solucion(s.rutas(), ev.partes())).factible(), "duplicacion");
        assertFalse(ev.evaluar(new Solucion(List.of(), List.of())).factible(), "perdida");
        var original = ev.estado();
        tabu().planificar(original, parametros(10));
        assertEquals(100, original.almacenes().get(0).stock(), "snapshot mutado");
    }

    @Test
    @DisplayName("Los vecindarios generan swap, relocate y transferencias sin mutar la solución")
    void vecindarios() {
        var e = simple(List.of(pedido("p", 1), pedido("q", 1), pedido("r", 1)));
        var ev = new EvaluadorFactibilidad(e, parametros(10));
        var s = una(ev);
        var tipos = EnumSet.noneOf(TabuMove.Tipo.class);
        new RoutingNeighborhood().generar(s, new Random(1), c -> {
            tipos.add(c.movimiento().tipo());
            assertNotSame(s, c.solucion(), "vecino mutable");
            return true;
        });
        assertTrue(tipos.containsAll(List.of(TabuMove.Tipo.SWAP, TabuMove.Tipo.RELOCATE)), "ruteo incompleto");
        var rs = new ArrayList<>(s.rutas());
        rs.add(new Ruta("TA02", "A", List.of(), false));
        var n = new AtomicInteger();
        new AssignmentNeighborhood().generar(new Solucion(rs, List.of()), e, new Random(1), c -> {
            if (c.movimiento().destino().equals("TA02")) {
                n.incrementAndGet();
            }
            return true;
        });
        assertTrue(n.get() > 0, "sin transferencias");
    }

    @Test
    @DisplayName("La replanificación conserva la carga despachada y reasigna ante una avería")
    void replanificacion() {
        var p = pedido("p", 4);
        var parte = new PartePedido("p#1", p, 4);
        var activa = new Ruta("TA01", "A", List.of(parte), true);
        var e = estado(List.of(p), List.of(new Vehiculo("TA01", N), new Vehiculo("TA02", N)),
                List.of(new Almacen("A", N, 0, false)), List.of(), List.of(), List.of(), List.of(activa), Set.of());
        var r = tabu().planificar(e, parametros(10));
        assertTrue(r.evaluacion().completa() && r.solucion().rutas().contains(activa),
                "no conserva carga despachada");
        var averiada = estado(List.of(p), e.vehiculos(), List.of(new Almacen("A", N, 10, false)), List.of(),
                List.of(new Averia("TA01", T, T.plusHours(8))), List.of(), List.of(activa), Set.of());
        var auxilio = tabu().planificar(averiada, parametros(10));
        assertTrue(auxilio.evaluacion().completa() && auxilio.solucion().rutas().stream()
                .anyMatch(x -> x.vehiculo().equals("TA02") && !x.partes().isEmpty()), "no reasigna averia");
    }

    @Test
    @DisplayName("Con semilla fija el TS es reproducible y no empeora la solución inicial (LE008, LE009)")
    void reproducibilidad() {
        var e = simple(List.of(pedido("p", 3), pedido("q", 5)));
        var inicial = new TabuSearchPlanner(new ConfiguracionTabu(0, 3, 3, 100, 0, 7)).planificar(e, parametros(10));
        var resultado = tabu().planificar(e, parametros(10));
        assertEquals(resultado.solucion(), tabu().planificar(e, parametros(10)).solucion(), "TS no reproducible");
        assertTrue(resultado.evaluacion().objetivo() <= inicial.evaluacion().objetivo(), "empeora inicial");
        assertTrue(resultado.metricas().candidatosEvaluados() > 0, "sin candidatos");
    }

    @Test
    @DisplayName("La iteración de la mejor solución es consistente y permite reconstruirla")
    void iteracionMejor() {
        var e = simple(List.of(pedido("p", 3), pedido("q", 5)));
        var ts0 = new TabuSearchPlanner(new ConfiguracionTabu(0, 3, 3, 100, 0, 7)).planificar(e, parametros(10));
        assertEquals(0, ts0.metricas().iteracionMejor(), "inicial debe registrarse en iteracion cero");
        var ts = tabu().planificar(e, parametros(10));
        int mejor = ts.metricas().iteracionMejor();
        assertTrue(mejor >= 0 && mejor <= ts.metricas().iteraciones(), "iteracion mejor fuera del recorrido");
        var prefijo = new TabuSearchPlanner(new ConfiguracionTabu(mejor, 3, 6, 100, 0, 7)).planificar(e,
                parametros(10));
        assertEquals(ts.solucion(), prefijo.solucion(), "TS no recupera la mejor solucion en la iteracion registrada");
        assertNotEquals(0, ts.metricas().candidatosEvaluados(), "sin candidatos");
    }

    @Test
    @DisplayName("Se rechazan pedidos futuros y plazos no permitidos en el punto de carga")
    void validacionEntrada() {
        assertThrows(IllegalArgumentException.class,
                () -> simple(List.of(new Pedido("f", T.plusMinutes(1), N, 1, 4))), "pedido futuro admitido");
        assertThrows(IllegalArgumentException.class, () -> new Pedido("x", T, N, 1, 7), "plazo no permitido");
    }
}
