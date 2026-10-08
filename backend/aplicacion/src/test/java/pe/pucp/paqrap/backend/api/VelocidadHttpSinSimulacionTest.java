package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

/** Sin ejecucion configurada el cambio de velocidad responde 400 y el snapshot inicial trae los campos nuevos. */
class VelocidadHttpSinSimulacionTest extends BaseHttpTest {

    @Test
    void sinSimulacionResponde400() {
        var r = http.post("/api/simulacion/velocidad", "{\"factor\": 2}");
        assertThat(r.estado()).isEqualTo(400);
        assertThat((String) JsonPath.read(r.cuerpo(), "$.mensaje")).contains("No hay simulación configurada");
    }

    @Test
    void snapshotInicialTraeSpeedFactorYSimMinPerSec() {
        var estado = http.get("/api/simulacion/estado");
        assertThat((Integer) JsonPath.read(estado.cuerpo(), "$.speedFactor")).isEqualTo(1);
        assertThat(((Number) JsonPath.read(estado.cuerpo(), "$.simMinPerSec")).doubleValue()).isEqualTo(1.0 / 60);
    }
}
