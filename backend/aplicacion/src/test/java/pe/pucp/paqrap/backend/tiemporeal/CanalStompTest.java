package pe.pucp.paqrap.backend.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;

/**
 * Prueba de extremo a extremo del canal STOMP (B-09): servidor real en puerto aleatorio, cliente STOMP sobre
 * WebSocket nativo (como {@code @stomp/stompjs} con {@code brokerURL}) y una simulación 5D real con el reloj del
 * backend. Las pruebas comparten el contexto y la ejecución, por eso van ordenadas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "paqrap.tiempo-real.frecuencia-hz=10",
        "paqrap.tiempo-real.periodo-sondeo-ms=20",
        "paqrap.tiempo-real.periodo-reloj-ms=50"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CanalStompTest {

    /** La ejecución de la prueba reemplaza al orquestador real: 5D a 60 min simulados por segundo real. */
    @TestConfiguration
    static class ConfiguracionPrueba {
        @Bean
        @Primary
        OrquestadorSimulacion orquestadorDePrueba() {
            return SimulacionDePrueba.orquestador5d(60);
        }
    }

    @MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioCarga repositorioCarga;

    @MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioConfiguracionEjecucion configuracionEjecucion;

    @MockitoBean
    private pe.pucp.paqrap.backend.persistencia.LectorEjecucion lectorEjecucion;

    @MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioSimulacion repositorioSimulacion;

    @LocalServerPort
    private int puerto;

    @Autowired
    private OrquestadorSimulacion orquestador;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<StompSession> sesiones = new ArrayList<>();
    private WebSocketStompClient cliente;
    private ThreadPoolTaskScheduler latidos;

    private record Conexion(StompSession sesion, BlockingQueue<Map<String, Object>> estados,
            BlockingQueue<Map<String, Object>> eventos, StompHeaders conectado) {
    }

    @BeforeEach
    void crearCliente() {
        latidos = new ThreadPoolTaskScheduler();
        latidos.initialize();
        cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new JacksonJsonMessageConverter());
        cliente.setTaskScheduler(latidos);
        cliente.setDefaultHeartbeat(new long[] {10_000, 10_000});   // como stompjs en el frontend
    }

    @AfterEach
    void cerrar() {
        sesiones.forEach(StompSession::disconnect);
        sesiones.clear();
        cliente.stop();
        latidos.shutdown();
    }

    private Conexion conectar() throws Exception {
        var conectado = new AtomicReference<StompHeaders>();
        var sesion = cliente.connectAsync("ws://localhost:" + puerto + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession sesion, StompHeaders cabeceras) {
                conectado.set(cabeceras);
            }
        }).get(10, TimeUnit.SECONDS);
        sesiones.add(sesion);
        var estados = new LinkedBlockingQueue<Map<String, Object>>();
        var eventos = new LinkedBlockingQueue<Map<String, Object>>();
        sesion.subscribe(DifusionTiempoReal.DESTINO_ESTADO, manejador(estados));
        sesion.subscribe(DifusionTiempoReal.DESTINO_EVENTOS, manejador(eventos));
        return new Conexion(sesion, estados, eventos, conectado.get());
    }

    private static StompFrameHandler manejador(BlockingQueue<Map<String, Object>> cola) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders cabeceras) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders cabeceras, Object carga) {
                cola.add((Map<String, Object>) carga);
            }
        };
    }

    private int post(String ruta) throws Exception {
        var solicitud = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api" + ruta))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        return http.send(solicitud, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Espera (hasta el plazo) el primer mensaje que cumpla la condición, descartando los anteriores. */
    private static Map<String, Object> esperar(BlockingQueue<Map<String, Object>> cola, long plazoMs,
            java.util.function.Predicate<Map<String, Object>> condicion) throws InterruptedException {
        long limite = System.nanoTime() + plazoMs * 1_000_000L;
        while (System.nanoTime() < limite) {
            var mensaje = cola.poll(100, TimeUnit.MILLISECONDS);
            if (mensaje != null && condicion.test(mensaje)) return mensaje;
        }
        return null;
    }

    @Test
    @Order(1)
    void elEndpointNegociaHeartbeatYEnviaElEstadoActualAlConectar() throws Exception {
        var conexion = conectar();

        assertThat(conexion.conectado().getFirst("heart-beat")).isEqualTo("10000,10000");
        var inicial = esperar(conexion.estados(), 3_000, m -> true);
        assertThat(inicial).as("snapshot al conectar").isNotNull();
        assertThat(inicial).containsEntry("configured", true).containsEntry("running", false)
                .containsEntry("scenario", "5d");
    }

    @Test
    @Order(2)
    void difundeSnapshotsAlRitmoYEventosMientrasCorreLaSimulacion() throws Exception {
        var conexion = conectar();
        assertThat(esperar(conexion.estados(), 3_000, m -> true)).isNotNull();
        conexion.estados().clear();
        conexion.eventos().clear();

        assertThat(post("/simulacion/iniciar")).isEqualTo(204);

        // Envío inmediato tras el comando, sin esperar al periodo.
        var enCurso = esperar(conexion.estados(), 1_000, m -> Boolean.TRUE.equals(m.get("running")));
        assertThat(enCurso).as("snapshot con running=true tras iniciar").isNotNull();

        // Mientras corre: 10 Hz configurados -> unos 15 en 1,5 s (con holgura por el planificador y la carga de CI).
        conexion.estados().clear();
        Thread.sleep(1_500);
        var recibidos = new ArrayList<Map<String, Object>>();
        conexion.estados().drainTo(recibidos);
        assertThat(recibidos.size()).as("snapshots en 1,5 s").isBetween(5, 17);
        var minutos = recibidos.stream().map(m -> ((Number) m.get("simMin")).longValue()).toList();
        assertThat(minutos).isSorted();
        assertThat(minutos.getLast()).as("el reloj del backend avanza").isGreaterThan(minutos.getFirst());
        assertThat(recibidos.getLast()).containsEntry("running", true);

        // Bitácora: LogEvent con la forma del contrato, ids crecientes y sin repetidos.
        var eventos = new ArrayList<Map<String, Object>>();
        conexion.eventos().drainTo(eventos);
        assertThat(eventos).isNotEmpty();
        var ids = eventos.stream().map(e -> ((Number) e.get("id")).longValue()).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates();
        for (var evento : eventos) {
            assertThat(evento.keySet()).containsExactlyInAnyOrder("id", "simMin", "text", "kind");
            assertThat((String) evento.get("kind")).isIn("good", "warning", "critical", "accent");
        }
        assertThat(eventos).extracting(e -> (String) e.get("text")).anyMatch(t -> t.contains("Ciclo"));
    }

    @Test
    @Order(3)
    void unClienteTardioRecibeElEstadoVigenteSinReiniciarLaEjecucion() throws Exception {
        long antes = orquestador.reloj().getMinute() + 60L * orquestador.reloj().getHour();
        var tardio = conectar();

        var snapshot = esperar(tardio.estados(), 3_000, m -> true);

        assertThat(snapshot).as("snapshot para el cliente tardío").isNotNull();
        assertThat(snapshot).containsEntry("configured", true).containsEntry("running", true);
        assertThat(((Number) snapshot.get("simMin")).longValue()).isGreaterThanOrEqualTo(antes);
    }

    @Test
    @Order(4)
    void detenerSeDifundeAlInstanteYCesaElPulso() throws Exception {
        var conexion = conectar();
        assertThat(esperar(conexion.estados(), 3_000, m -> true)).isNotNull();

        assertThat(post("/simulacion/detener")).isEqualTo(204);

        var final_ = esperar(conexion.estados(), 1_000, m -> Boolean.TRUE.equals(m.get("finished")));
        assertThat(final_).as("snapshot con finished=true").isNotNull();
        assertThat(final_).containsEntry("running", false);

        Thread.sleep(600);                    // deja salir los mensajes en vuelo
        conexion.estados().clear();
        Thread.sleep(1_000);
        assertThat(conexion.estados()).as("sin snapshots periódicos con la ejecución detenida").isEmpty();
        assertThat(orquestador.estado()).isEqualTo("DETENIDA");
    }
}
