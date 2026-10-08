package pe.pucp.paqrap.backend.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import pe.pucp.paqrap.backend.api.PlanificadorControlador;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTiempoReal;
import pe.pucp.paqrap.backend.persistencia.LectorEjecucion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.backend.simulacion.PreparacionSimulacion;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;

/** Reglas de la difusión: cuándo se emite el snapshot y cómo se emiten los eventos (B-09, LE054). */
class DifusionTiempoRealTest {

    private static final long MS = 1_000_000L;

    private record Envio(String destino, Object carga) {
    }

    private final List<Envio> enviados = new ArrayList<>();
    private SimpMessageSendingOperations plantilla;

    @BeforeEach
    void preparar() {
        plantilla = mock(SimpMessageSendingOperations.class);
        doAnswer(invocacion -> {
            enviados.add(new Envio(invocacion.getArgument(0), invocacion.getArgument(1)));
            return null;
        }).when(plantilla).convertAndSend(anyString(), any(Object.class));
    }

    private DifusionTiempoReal difusion(OrquestadorSimulacion orquestador) {
        // 5 Hz: un snapshot cada 200 ms mientras corre.
        return new DifusionTiempoReal(plantilla, orquestador, new PlanificadorControlador(orquestador, null, null,
                null, null), new PropiedadesTiempoReal(true, true, 5, 50, 200, 2000, 3.0, "*"));
    }

    private List<Envio> a(String destino) {
        return enviados.stream().filter(e -> destino.equals(e.destino())).toList();
    }

    @Test
    void sinEjecucionEnviaUnSnapshotInicialYLuegoCalla() {
        var orquestador = new OrquestadorSimulacion(null, null, null);
        var difusion = difusion(orquestador);

        difusion.difundir(0);
        difusion.difundir(500 * MS);
        difusion.difundir(5_000 * MS);

        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).hasSize(1);
        assertThat(a(DifusionTiempoReal.DESTINO_EVENTOS)).isEmpty();
        @SuppressWarnings("unchecked")
        var snapshot = (Map<String, Object>) a(DifusionTiempoReal.DESTINO_ESTADO).getFirst().carga();
        assertThat(snapshot).containsEntry("configured", false).containsEntry("running", false);
    }

    @Test
    void unaSolicitudProvocaUnEnvioInmediatoAunqueNoCorra() {
        var orquestador = new OrquestadorSimulacion(null, null, null);
        var difusion = difusion(orquestador);
        difusion.difundir(0);
        enviados.clear();

        difusion.solicitarEstado();
        difusion.difundir(10 * MS);
        difusion.difundir(20 * MS);

        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).hasSize(1);
    }

    @Test
    void mientrasCorreEmiteAlRitmoDeLaFrecuenciaConfigurada() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var difusion = difusion(orquestador);
        orquestador.iniciar();

        difusion.difundir(0);                  // cambio de motor/estado: envío inmediato
        difusion.difundir(100 * MS);           // dentro del periodo de 200 ms: nada
        difusion.difundir(190 * MS);
        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).hasSize(1);

        difusion.difundir(200 * MS);           // cumple el periodo
        difusion.difundir(350 * MS);
        difusion.difundir(400 * MS);           // otro periodo
        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).hasSize(3);
    }

    @Test
    void elSnapshotDifundidoEsElDeLaApiRest() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var difusion = difusion(orquestador);
        orquestador.iniciar();

        difusion.difundir(0);

        @SuppressWarnings("unchecked")
        var snapshot = (Map<String, Object>) a(DifusionTiempoReal.DESTINO_ESTADO).getFirst().carga();
        var esperado = new PlanificadorControlador(orquestador, null, null, null, null).estado();
        assertThat(snapshot.keySet()).isEqualTo(esperado.keySet());
        assertThat(snapshot).containsEntry("scenario", "5d").containsEntry("configured", true)
                .containsEntry("running", true).containsEntry("epochDate", "2026-09-15");
    }

    @Test
    void cadaEventoSeEmiteUnaSolaVezYEnOrden() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var difusion = difusion(orquestador);

        difusion.difundir(0);                  // "Ejecucion configurada"
        orquestador.iniciar();                 // "Ejecucion EN_CURSO"
        orquestador.avanzar(3);                // planifica y despacha: más eventos
        difusion.difundir(300 * MS);
        difusion.difundir(600 * MS);
        orquestador.pausar();
        difusion.difundir(900 * MS);

        var ids = a(DifusionTiempoReal.DESTINO_EVENTOS).stream().map(e -> ((EventoLog) e.carga()).id()).toList();
        assertThat(ids).isNotEmpty().doesNotHaveDuplicates().isSorted();
        assertThat(ids.getFirst()).isEqualTo(1);
        assertThat(ids).hasSize(orquestador.motor().eventos().size());
        assertThat(a(DifusionTiempoReal.DESTINO_EVENTOS)).extracting(e -> ((EventoLog) e.carga()).text())
                .anyMatch(texto -> texto.contains("Ciclo 1"));
    }

    @Test
    void unCambioDeEstadoSeDifundeAlInstanteYLaPausaDetieneElPulso() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var difusion = difusion(orquestador);
        orquestador.iniciar();
        difusion.difundir(0);
        enviados.clear();

        orquestador.pausar();
        difusion.difundir(10 * MS);
        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).hasSize(1);
        @SuppressWarnings("unchecked")
        var pausado = (Map<String, Object>) a(DifusionTiempoReal.DESTINO_ESTADO).getFirst().carga();
        assertThat(pausado).containsEntry("running", false);

        enviados.clear();
        for (int i = 1; i <= 20; i++) difusion.difundir((10 + i * 100L) * MS);
        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).isEmpty();
    }

    @Test
    void unaEjecucionNuevaReiniciaLaBitacoraDifundida() {
        var lector = mock(LectorEjecucion.class);
        var inicio = SimulacionDePrueba.INICIO;
        var preparacion = new PreparacionSimulacion(1L,
                SimulacionDePrueba.configuracion(
                        pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion.Escenario.SIMULACION_5D, 4),
                new ConfiguracionTabu(1, 7, 30, 2, 0, 20262),
                List.of(new Pedido("P1", inicio, new Nodo(28, 14), 1, 4)), SimulacionDePrueba.ALMACENES, List.of(),
                List.of(), Map.of(), Map.of());
        when(lector.preparar(1L)).thenReturn(preparacion);
        var orquestador = new OrquestadorSimulacion(lector, null, null);
        var difusion = difusion(orquestador);

        orquestador.preparar(1L);
        difusion.difundir(0);
        orquestador.preparar(1L);              // reemplaza el motor, como un POST /simulacion/configuracion
        difusion.difundir(50 * MS);

        var ids = a(DifusionTiempoReal.DESTINO_EVENTOS).stream().map(e -> ((EventoLog) e.carga()).id()).toList();
        assertThat(ids).containsExactly(1L, 1L);
        assertThat(a(DifusionTiempoReal.DESTINO_ESTADO)).hasSize(2);
    }
}
