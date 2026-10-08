package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Prueba HTTP real (Tomcat en puerto aleatorio) del endpoint de salud y del formato de errores de la API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaludControladorTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioCarga repositorioCarga;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioConfiguracionEjecucion configuracionEjecucion;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.LectorEjecucion lectorEjecucion;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioSimulacion repositorioSimulacion;

    @LocalServerPort
    private int puerto;

    private RestClient cliente;

    private record Respuesta(int estado, String cuerpo) {
    }

    @BeforeEach
    void crearCliente() {
        cliente = RestClient.create("http://localhost:" + puerto);
    }

    private Respuesta llamar(HttpMethod metodo, String ruta) {
        return cliente.method(metodo).uri(ruta).accept(MediaType.APPLICATION_JSON)
                .exchange((solicitud, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        respuesta.bodyTo(String.class)));
    }

    @Test
    @DisplayName("GET /api/salud responde 200 con estado OK y versión")
    void saludResponde() {
        var r = llamar(HttpMethod.GET, "/api/salud");
        assertThat(r.estado()).isEqualTo(200);
        assertThat((String) JsonPath.read(r.cuerpo(), "$.estado")).isEqualTo("OK");
        assertThat((String) JsonPath.read(r.cuerpo(), "$.version")).isNotBlank().doesNotContain("@");
        assertThat((String) JsonPath.read(r.cuerpo(), "$.instante")).isNotBlank();
    }

    @Test
    @DisplayName("La ruta sin prefijo /api no existe")
    void sinPrefijoNoExiste() {
        assertThat(llamar(HttpMethod.GET, "/salud").estado()).isEqualTo(404);
    }

    @Test
    @DisplayName("Una ruta inexistente responde 404 con { mensaje }")
    void rutaInexistente() {
        var r = llamar(HttpMethod.GET, "/api/no-existe");
        assertThat(r.estado()).isEqualTo(404);
        assertThat((String) JsonPath.read(r.cuerpo(), "$.mensaje")).contains("no encontrado");
    }

    @Test
    @DisplayName("Un método no admitido responde 405 con { mensaje }")
    void metodoNoAdmitido() {
        var r = llamar(HttpMethod.POST, "/api/salud");
        assertThat(r.estado()).isEqualTo(405);
        assertThat((String) JsonPath.read(r.cuerpo(), "$.mensaje")).contains("POST");
    }

    @Test
    @DisplayName("GET /api/catalogos expone los tipos consumidos por la GUI")
    void catalogosContratoFrontend() {
        var r = llamar(HttpMethod.GET, "/api/catalogos");
        assertThat(r.estado()).isEqualTo(200);
        java.util.List<?> vehicleTypes = JsonPath.read(r.cuerpo(), "$.vehicleTypes");
        java.util.List<?> fallaTypes = JsonPath.read(r.cuerpo(), "$.fallaTypes");
        java.util.List<?> modalidades = JsonPath.read(r.cuerpo(), "$.modalidades");
        assertThat(vehicleTypes).hasSize(3);
        assertThat(fallaTypes).hasSize(3);
        assertThat(modalidades).hasSize(5);
    }

    @Test
    @DisplayName("GET /api/simulacion/estado entrega snapshot inicial compatible")
    void snapshotInicialContratoFrontend() {
        var r = llamar(HttpMethod.GET, "/api/simulacion/estado");
        assertThat(r.estado()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(r.cuerpo(), "$.configured")).isFalse();
        java.util.Map<?,?> files = JsonPath.read(r.cuerpo(), "$.files");
        java.util.Map<?,?> byPriority = JsonPath.read(r.cuerpo(), "$.stats.byPriority");
        assertThat(files).isNotNull();
        assertThat(byPriority).isNotNull();
        java.util.List<?> vehicles = JsonPath.read(r.cuerpo(), "$.vehicles");
        assertThat(vehicles).isEmpty();
    }
}
