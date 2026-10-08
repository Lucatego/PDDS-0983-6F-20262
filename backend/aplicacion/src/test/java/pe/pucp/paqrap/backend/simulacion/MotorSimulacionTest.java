package pe.pucp.paqrap.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.estricto.modelo.*;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

class MotorSimulacionTest {
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 15, 7, 0);
    private static final List<Almacen> ALMACENES = List.of(new Almacen("CENTRAL", new Nodo(27, 14), 0, true),
            new Almacen("NOROESTE", new Nodo(12, 38), 1000, false),
            new Almacen("ESTE", new Nodo(57, 27), 1000, false));
    private final TabuSearchPlanner planificador = new TabuSearchPlanner(new ConfiguracionTabu(1, 7, 30, 2, 0, 20262));

    private ConfiguracionSimulacion configuracion(ConfiguracionSimulacion.Escenario escenario) {
        return new ConfiguracionSimulacion(escenario, INICIO,
                Map.of(TipoVehiculo.TA, 1, TipoVehiculo.TM, 0, TipoVehiculo.TB, 0),
                Map.of("NOROESTE", 1000, "ESTE", 1000), 10, 4, 20262,
                new ParametrosOperacion(60, false, 480, 420, 60, 420, 60, 4, 50, 1000000,
                        Map.of(TipoVehiculo.TA, 40.0, TipoVehiculo.TM, 25.0, TipoVehiculo.TB, 12.0)));
    }

    private MotorSimulacion motor(List<Pedido> pedidos) {
        return new MotorSimulacion(configuracion(ConfiguracionSimulacion.Escenario.SIMULACION_5D),
                pedidos, ALMACENES, List.of(), List.of());
    }

    @Test
    void pausaNoAvanzaYReanudaSinDuplicarEntregas() {
        var motor = motor(List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 6, 4, "c1")));
        motor.iniciar();
        motor.avanzar(1, planificador);
        int rutas = motor.viajes().size();
        assertThat(rutas).isEqualTo(1);
        assertThat(motor.pedidos().get("P1").enRuta).isEqualTo(6);
        motor.pausar();
        LocalDateTime pausa = motor.reloj();
        motor.avanzar(600, planificador);
        assertThat(motor.reloj()).isEqualTo(pausa);
        motor.iniciar();
        motor.avanzar(30, planificador);
        assertThat(motor.pedidos().get("P1").entregada).isEqualTo(6);
        assertThat(motor.pedidos().get("P1").estado()).isEqualTo("ENTREGADO");
        assertThat(motor.viajes()).hasSize(rutas);
        assertThat(motor.viajes().getFirst().partes).extracting(MotorSimulacion.Parte::codigo)
                .containsExactly("P1#1", "P1#2");
    }

    @Test
    void soloIngresaPedidosCuandoLlegaSuFecha() {
        var motor = motor(List.of(new Pedido("P1", INICIO.plusMinutes(20), new Nodo(28, 14), 1, 4)));
        motor.iniciar();
        motor.avanzar(2, planificador);
        assertThat(motor.pedidos()).isEmpty();
        motor.avanzar(3, planificador);
        assertThat(motor.pedidos()).containsKey("P1");
    }

    @Test
    void cincoDiasTerminaExactamenteSinCrearCicloFueraDelHorizonte() {
        var motor = motor(List.of());
        motor.iniciar();
        motor.avanzar(1900, planificador);
        assertThat(motor.reloj()).isEqualTo(INICIO.plusDays(5));
        assertThat(motor.estado()).isEqualTo("FINALIZADA");
        assertThat(motor.motivoFin()).isEqualTo("FIN_DE_HORIZONTE");
        assertThat(motor.ciclos()).hasSize(720);
        assertThat(motor.tiempoRealMs()).isEqualTo(1_800_000);
        assertThat(motor.eventos().stream().filter(e -> e.tipo().equals("RECARGA_ALMACEN"))).hasSize(10);
    }

    @Test
    void copiaAislaCambiosHastaQueSeConfirmaPersistencia() {
        var original = motor(List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4)));
        original.iniciar();
        var candidata = original.copiar();
        candidata.avanzar(20, planificador);
        assertThat(original.reloj()).isEqualTo(INICIO);
        assertThat(original.pedidos()).isEmpty();
        assertThat(original.viajes()).isEmpty();
        assertThat(candidata.pedidos().get("P1").entregada).isEqualTo(1);
    }

    @Test
    void diaADiaEsperaPrimerPedidoYUsaTiempoReal() {
        var motor = new MotorSimulacion(configuracion(ConfiguracionSimulacion.Escenario.DIA_A_DIA),
                List.of(), ALMACENES, List.of(), List.of());
        motor.iniciar();
        motor.avanzar(3600, planificador);
        assertThat(motor.estado()).isEqualTo("ESPERANDO_PEDIDO");
        assertThat(motor.reloj()).isEqualTo(INICIO);
        motor.registrar(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4));
        motor.avanzar(60, planificador);
        assertThat(motor.reloj()).isEqualTo(INICIO.plusMinutes(1));
    }

    @Test
    void detenerConservaResultadosYNoPermiteReiniciarMismaEjecucion() {
        var motor = motor(List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4)));
        motor.iniciar();
        motor.avanzar(20, planificador);
        motor.detener();
        assertThat(motor.estado()).isEqualTo("DETENIDA");
        assertThat(motor.pedidos().get("P1").entregada).isEqualTo(1);
        assertThatThrownBy(motor::iniciar).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void colapsoRegistraPedidoInfactible() {
        var motor = new MotorSimulacion(configuracion(ConfiguracionSimulacion.Escenario.COLAPSO),
                List.of(new Pedido("P1", INICIO, new Nodo(70, 50), 10000, 4)), ALMACENES, List.of(), List.of());
        motor.iniciar();
        motor.avanzar(0, planificador);
        assertThat(motor.estado()).isEqualTo("COLAPSADA");
        assertThat(motor.motivoFin()).isEqualTo("COLAPSO_PLANIFICACION");
        assertThat(motor.pedidoColapso()).isEqualTo("P1");
    }

    @Test
    void excluyeDemandaFueraDelHorizonteYRechazaCodigosDuplicados() {
        var motor = motor(List.of(new Pedido("ANTES", INICIO.minusMinutes(1), new Nodo(28, 14), 1, 4),
                new Pedido("LIMITE", INICIO.plusDays(5), new Nodo(28, 14), 1, 4)));
        motor.iniciar();
        motor.avanzar(1900, planificador);
        assertThat(motor.pedidos()).isEmpty();
        var pedido = new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4);
        assertThatThrownBy(() -> motor(List.of(pedido, pedido))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pedidoManualDurantePausaNoReanudaElReloj() {
        var motor = new MotorSimulacion(configuracion(ConfiguracionSimulacion.Escenario.DIA_A_DIA),
                List.of(), ALMACENES, List.of(), List.of());
        motor.iniciar();
        motor.pausar();
        motor.registrar(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4));
        motor.avanzar(600, planificador);
        assertThat(motor.estado()).isEqualTo("PAUSADA");
        assertThat(motor.tiempoRealMs()).isZero();
        motor.iniciar();
        motor.avanzar(60, planificador);
        assertThat(motor.pedidos()).containsKey("P1");
        assertThat(motor.tiempoRealMs()).isEqualTo(60_000);
    }

    @Test
    void fragmentarTicksNoCambiaEntregasEventosNiConservacionDePaquetes() {
        var demanda = List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 6, 4));
        var continuo = motor(demanda);
        var fragmentado = motor(demanda);
        continuo.iniciar();
        fragmentado.iniciar();
        continuo.avanzar(20, planificador);
        for (int i = 0; i < 80; i++) fragmentado.avanzar(0.25, planificador);
        assertThat(fragmentado.reloj()).isEqualTo(continuo.reloj());
        assertThat(fragmentado.eventos()).isEqualTo(continuo.eventos());
        var pedido = fragmentado.pedidos().get("P1");
        assertThat(pedido.pendiente + pedido.enRuta + pedido.entregada).isEqualTo(pedido.original.cantidad());
        assertThat(pedido.entregada).isEqualTo(6);
        assertThat(fragmentado.tiempoRealMs()).isEqualTo(continuo.tiempoRealMs());
    }

    @Test
    void incidenciasControladasPorConfiguracionYRegistroDinamico() {
        var configConIncidencias = new ConfiguracionSimulacion(ConfiguracionSimulacion.Escenario.SIMULACION_5D,
                INICIO, Map.of(TipoVehiculo.TA, 1, TipoVehiculo.TM, 0, TipoVehiculo.TB, 0),
                Map.of("NOROESTE", 1000, "ESTE", 1000), 10, 4, 20262,
                new ParametrosOperacion(60, false, 480, 420, 60, 420, 60, 4, 50, 1000000,
                        Map.of(TipoVehiculo.TA, 40.0, TipoVehiculo.TM, 25.0, TipoVehiculo.TB, 12.0)),
                true, true, 0.5, false);

        var motorIncidencias = new MotorSimulacion(configConIncidencias, List.of(), ALMACENES, List.of(), List.of());
        assertThat(motorIncidencias.filtroIncidencias().considerarIncidencias()).isTrue();

        var averia = new pe.pucp.paqrap.estricto.modelo.Averia("TA01", INICIO, INICIO.plusHours(2));
        motorIncidencias.registrarAveria(averia);
        assertThat(motorIncidencias.averias()).containsExactly(averia);
        assertThat(motorIncidencias.eventos()).anyMatch(e -> e.tipo().equals("AVERIA_REGISTRADA"));

        var motorSinIncidencias = motor(List.of());
        assertThat(motorSinIncidencias.filtroIncidencias().considerarIncidencias()).isFalse();
    }
}
