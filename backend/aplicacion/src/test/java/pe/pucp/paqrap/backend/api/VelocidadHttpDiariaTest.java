package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.backend.simulacion.MotorSimulacion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.backend.tiemporeal.SimulacionDePrueba;

/** Dia a dia corre en tiempo real y rechaza el cambio de velocidad con 400. */
class VelocidadHttpDiariaTest extends BaseHttpTest {

    @TestConfiguration
    static class Configuracion {
        @Bean
        @Primary
        OrquestadorSimulacion orquestadorDePrueba() {
            var motor = new MotorSimulacion(SimulacionDePrueba.configuracion(
                    ConfiguracionSimulacion.Escenario.DIA_A_DIA, 3.0), List.of(), SimulacionDePrueba.ALMACENES,
                    List.of(), List.of());
            return new OrquestadorSimulacion(motor, SimulacionDePrueba.planificador());
        }
    }

    @Test
    void diariaResponde400ySuSnapshotVaEnTiempoReal() {
        var r = http.post("/api/simulacion/velocidad", "{\"factor\": 2}");
        assertThat(r.estado()).isEqualTo(400);
        assertThat((String) JsonPath.read(r.cuerpo(), "$.mensaje")).contains("Dia a dia");

        var estado = http.get("/api/simulacion/estado");
        assertThat((String) JsonPath.read(estado.cuerpo(), "$.scenario")).isEqualTo("diaria");
        assertThat((Integer) JsonPath.read(estado.cuerpo(), "$.speedFactor")).isEqualTo(1);
        assertThat(((Number) JsonPath.read(estado.cuerpo(), "$.simMinPerSec")).doubleValue())
                .isEqualTo(1.0 / 60);
    }
}
