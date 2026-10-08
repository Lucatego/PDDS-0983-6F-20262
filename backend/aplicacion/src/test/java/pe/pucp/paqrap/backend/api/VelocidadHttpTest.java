package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.backend.tiemporeal.DifusionTiempoReal;
import pe.pucp.paqrap.backend.tiemporeal.SimulacionDePrueba;

/** {@code POST /api/simulacion/velocidad} sobre una 5D configurada con base de 3 min/s (LE058). */
class VelocidadHttpTest extends BaseHttpTest {

    @TestConfiguration
    static class Configuracion {
        @Bean
        @Primary
        OrquestadorSimulacion orquestadorDePrueba() {
            return SimulacionDePrueba.orquestador5d(3.0);
        }
    }

    @Autowired private OrquestadorSimulacion orquestador;
    @MockitoSpyBean private DifusionTiempoReal difusion;

    @Test
    void factorValidoResponde204CambiaElSnapshotYPideDifusionInmediata() {
        var antes = http.get("/api/simulacion/estado");
        assertThat((Integer) JsonPath.read(antes.cuerpo(), "$.speedFactor")).isEqualTo(1);
        assertThat(((Number) JsonPath.read(antes.cuerpo(), "$.simMinPerSec")).doubleValue()).isEqualTo(3.0);

        var r = http.post("/api/simulacion/velocidad", "{\"factor\": 5}");

        assertThat(r.estado()).isEqualTo(204);
        assertThat(orquestador.motor().factorVelocidad()).isEqualTo(5);
        var despues = http.get("/api/simulacion/estado");
        assertThat((Integer) JsonPath.read(despues.cuerpo(), "$.speedFactor")).isEqualTo(5);
        assertThat(((Number) JsonPath.read(despues.cuerpo(), "$.simMinPerSec")).doubleValue()).isEqualTo(15.0);
        // El interceptor de B-09 pide la difusion tras cualquier POST exitoso.
        verify(difusion, atLeastOnce()).solicitarEstado();
    }

    @Test
    void factoresNoPermitidosResponden400ConMensaje() {
        for (String cuerpo : new String[] {"{\"factor\": 3}", "{\"factor\": 0}", "{\"factor\": 20}", "{\"factor\": 2.5}",
                "{\"factor\": null}", "{}", "{\"factor\": \"rapido\"}"}) {
            var r = http.post("/api/simulacion/velocidad", cuerpo);
            assertThat(r.estado()).as(cuerpo).isEqualTo(400);
            assertThat((String) JsonPath.read(r.cuerpo(), "$.mensaje")).as(cuerpo).isNotBlank();
        }
        assertThat(http.post("/api/simulacion/velocidad", null).estado()).isEqualTo(400);
    }
}
