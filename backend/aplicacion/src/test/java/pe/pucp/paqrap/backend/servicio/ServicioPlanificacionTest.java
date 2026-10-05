package pe.pucp.paqrap.backend.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.EstadoOperacion;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.Vehiculo;

/** Ejecuta el Tabu Search configurado en Spring sobre una instantánea pequeña de la operación. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ServicioPlanificacionTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioCarga repositorioCarga;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioConfiguracionEjecucion configuracionEjecucion;

    private static final LocalDateTime INSTANTE = LocalDateTime.of(2026, 9, 1, 8, 0);
    private static final Nodo CENTRAL = new Nodo(27, 14);

    @Autowired
    private ServicioPlanificacion servicio;

    private static EstadoOperacion estadoPequenio() {
        var almacenes = List.of(new Almacen("CENTRAL", CENTRAL, 0, true),
                new Almacen("NOROESTE", new Nodo(12, 38), 1000, false),
                new Almacen("ESTE", new Nodo(57, 27), 1000, false));
        var flota = List.of(new Vehiculo("TA01", CENTRAL), new Vehiculo("TM01", CENTRAL),
                new Vehiculo("TB01", CENTRAL));
        var pedidos = List.of(
                new Pedido("P1", INSTANTE.minusMinutes(30), new Nodo(30, 20), 6, 8, "c0001"),
                new Pedido("P2", INSTANTE.minusMinutes(10), new Nodo(20, 10), 3, 4, "c0002"),
                new Pedido("P3", INSTANTE, new Nodo(15, 35), 10, 36, "c0003"));
        return new EstadoOperacion(INSTANTE, pedidos, flota, almacenes, List.of(), List.of(), List.of(), List.of(),
                Set.of());
    }

    @Test
    @DisplayName("Planifica una instantánea pequeña y obtiene un resultado COMPLETA")
    void planificaCompleta() {
        var resultado = servicio.planificar(estadoPequenio());
        assertThat(resultado.algoritmo()).isEqualTo("TS-estricto");
        assertThat(resultado.evaluacion().factible()).isTrue();
        assertThat(resultado.metricas().estadoResultado()).isEqualTo("COMPLETA");
        assertThat(resultado.metricas().pedidosCompletos()).isEqualTo(3);
        assertThat(resultado.metricas().paquetesPendientes()).isZero();
        assertThat(resultado.metricas().holguraMinimaMin()).isPositive();
    }

    @Test
    @DisplayName("Con la semilla configurada la planificación es reproducible (LE008, LE009)")
    void reproducible() {
        var estado = estadoPequenio();
        assertThat(servicio.planificar(estado).solucion()).isEqualTo(servicio.planificar(estado).solucion());
    }
}
