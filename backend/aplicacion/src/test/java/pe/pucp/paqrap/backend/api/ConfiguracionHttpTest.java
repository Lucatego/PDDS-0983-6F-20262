package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;

/** {@code POST /api/simulacion/configuracion}: campo opcional {@code considerarIncidencias} y velocidad base. */
class ConfiguracionHttpTest extends BaseHttpTest {

    /** Cuerpo exacto que antes devolvia 400 en la prueba manual (sin considerarIncidencias). */
    private static final String CUERPO_SIN_CAMPO = "{\"scenario\":\"diaria\",\"startDate\":\"2026-10-08\","
            + "\"startTime\":\"08:00\",\"fleet\":{\"auto\":10,\"moto\":15,\"bici\":12},"
            + "\"capacities\":{\"noroeste\":1000,\"este\":1000},\"shiftStarts\":[420,900,1380]}";

    @MockitoBean private OrquestadorSimulacion orquestador;

    private ConfiguracionSimulacion enviar(String cuerpo, String escenario) {
        reset(orquestador);
        var r = http.post("/api/simulacion/configuracion", cuerpo.replace("\"diaria\"", "\"" + escenario + "\""));
        assertThat(r.estado()).as(r.cuerpo()).isEqualTo(204);
        var captor = ArgumentCaptor.forClass(ConfiguracionSimulacion.class);
        verify(orquestador).configurar(captor.capture(), any(ConfiguracionTabu.class));
        return captor.getValue();
    }

    @Test
    void sinConsiderarIncidenciasResponde204YAsumeFalse() {
        assertThat(enviar(CUERPO_SIN_CAMPO, "diaria").considerarIncidencias()).isFalse();
    }

    @Test
    void conConsiderarIncidenciasTrueFalseONuloResponde204() {
        var verdadero = CUERPO_SIN_CAMPO.replace("\"shiftStarts\"", "\"considerarIncidencias\":true,\"shiftStarts\"");
        var falso = CUERPO_SIN_CAMPO.replace("\"shiftStarts\"", "\"considerarIncidencias\":false,\"shiftStarts\"");
        var nulo = CUERPO_SIN_CAMPO.replace("\"shiftStarts\"", "\"considerarIncidencias\":null,\"shiftStarts\"");
        assertThat(enviar(verdadero, "diaria").considerarIncidencias()).isTrue();
        assertThat(enviar(falso, "diaria").considerarIncidencias()).isFalse();
        assertThat(enviar(nulo, "diaria").considerarIncidencias()).isFalse();
    }

    @Test
    void laVelocidadBaseEs3MinutosPorSegundoEn5dYColapsoYTiempoRealEnDiaria() {
        assertThat(enviar(CUERPO_SIN_CAMPO, "5d").aceleracion()).isEqualTo(3.0);
        assertThat(enviar(CUERPO_SIN_CAMPO, "colapso").aceleracion()).isEqualTo(3.0);
        assertThat(enviar(CUERPO_SIN_CAMPO, "diaria").aceleracion()).isEqualTo(1.0 / 60);
    }
}
