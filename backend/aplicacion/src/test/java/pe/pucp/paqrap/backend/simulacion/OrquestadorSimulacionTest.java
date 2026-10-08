package pe.pucp.paqrap.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.backend.persistencia.LectorEjecucion;
import pe.pucp.paqrap.backend.persistencia.RepositorioSimulacion;
import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

class OrquestadorSimulacionTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 15, 7, 0);
    private static final List<Almacen> ALMACENES = List.of(
            new Almacen("CENTRAL", new Nodo(27, 14), 0, true),
            new Almacen("NOROESTE", new Nodo(12, 38), 1000, false),
            new Almacen("ESTE", new Nodo(57, 27), 1000, false));

    private LectorEjecucion lector;
    private RepositorioSimulacion repositorio;
    private OrquestadorSimulacion orquestador;

    private ConfiguracionSimulacion configuracion(ConfiguracionSimulacion.Escenario escenario) {
        return new ConfiguracionSimulacion(escenario, INICIO,
                Map.of(TipoVehiculo.TA, 1, TipoVehiculo.TM, 0, TipoVehiculo.TB, 0),
                Map.of("NOROESTE", 1000, "ESTE", 1000), 10, 4, 20262,
                new ParametrosOperacion(60, false, 480, 420, 60, 420, 60, 4, 50, 1000000,
                        Map.of(TipoVehiculo.TA, 40.0, TipoVehiculo.TM, 25.0, TipoVehiculo.TB, 12.0)));
    }

    private PreparacionSimulacion preparacion(List<Pedido> pedidos) {
        return new PreparacionSimulacion(100L, configuracion(ConfiguracionSimulacion.Escenario.SIMULACION_5D),
                new ConfiguracionTabu(1, 7, 30, 2, 0, 20262), pedidos, ALMACENES, List.of(), List.of(),
                Map.of("P1", 1L), Map.of(1L, "hash"));
    }

    @BeforeEach
    void setup() {
        lector = mock(LectorEjecucion.class);
        repositorio = mock(RepositorioSimulacion.class);
        orquestador = new OrquestadorSimulacion(lector, repositorio, null);
    }

    @Test
    void prepararEIniciarTransicionaEstadosYNotificaRepositorio() {
        var prep = preparacion(List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4)));
        when(lector.preparar(100L)).thenReturn(prep);
        when(repositorio.obtenerMapeoPedidosEjecucion(100L)).thenReturn(Map.of("P1", 10L));
        when(repositorio.obtenerMapeoVehiculos(100L)).thenReturn(Map.of("TA01", 101L));

        orquestador.preparar(100L);
        assertThat(orquestador.estado()).isEqualTo("CONFIGURADA");
        assertThat(orquestador.esActiva()).isFalse();

        orquestador.iniciar();
        assertThat(orquestador.estado()).isEqualTo("EN_CURSO");
        assertThat(orquestador.esActiva()).isTrue();
        verify(repositorio).inicializarEjecucion(100L, prep);

        orquestador.pausar();
        assertThat(orquestador.estado()).isEqualTo("PAUSADA");
        assertThat(orquestador.esActiva()).isTrue();

        orquestador.detener();
        assertThat(orquestador.estado()).isEqualTo("DETENIDA");
        assertThat(orquestador.esActiva()).isFalse();
    }

    @Test
    void avanzarPersisteCiclosYRutasGeneradas() {
        var prep = preparacion(List.of(new Pedido("P1", INICIO, new Nodo(28, 14), 1, 4)));
        when(lector.preparar(100L)).thenReturn(prep);
        when(repositorio.obtenerMapeoPedidosEjecucion(100L)).thenReturn(Map.of("P1", 10L));
        when(repositorio.obtenerMapeoVehiculos(100L)).thenReturn(Map.of("TA01", 101L));

        orquestador.preparar(100L);
        orquestador.iniciar();

        orquestador.avanzar(20.0);
        assertThat(orquestador.motor().ciclos()).isNotEmpty();
        verify(repositorio, org.mockito.Mockito.atLeastOnce()).persistirCicloYRutas(eq(100L), any(), anyList(), any(), any());
        verify(repositorio, org.mockito.Mockito.atLeastOnce()).persistirProgreso(eq(100L), eq(orquestador.motor()), any(), any());
    }

    @Test
    void diaADiaPermiteRegistrarPedidoManual() {
        var configDia = configuracion(ConfiguracionSimulacion.Escenario.DIA_A_DIA);
        var prep = new PreparacionSimulacion(101L, configDia,
                new ConfiguracionTabu(1, 7, 30, 2, 0, 20262), List.of(), ALMACENES, List.of(), List.of(),
                Map.of(), Map.of());
        when(lector.preparar(101L)).thenReturn(prep);
        when(repositorio.registrarPedidoManual(eq(101L), any())).thenReturn(50L);

        orquestador.preparar(101L);
        orquestador.iniciar();
        assertThat(orquestador.estado()).isEqualTo("ESPERANDO_PEDIDO");

        var nuevo = new Pedido("MAN-1", INICIO, new Nodo(30, 20), 2, 4);
        orquestador.registrarPedido(nuevo);
        assertThat(orquestador.estado()).isEqualTo("EN_CURSO");
        verify(repositorio).registrarPedidoManual(101L, nuevo);
    }
}
