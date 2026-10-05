package pe.pucp.paqrap.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.servicios.PlanificadorEstricto;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/** Verifica que el contexto de Spring arranca y que la configuración por defecto es la del experimento. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaqRapAplicacionTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioCarga repositorioCarga;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private pe.pucp.paqrap.backend.persistencia.RepositorioConfiguracionEjecucion configuracionEjecucion;

    @Autowired
    private ConfiguracionTabu configuracionTabu;

    @Autowired
    private ParametrosOperacion parametrosOperacion;

    @Autowired
    private PlanificadorEstricto planificador;

    @Test
    @DisplayName("El contexto arranca con el planificador Tabu Search")
    void contextoArranca() {
        assertThat(planificador).isInstanceOf(TabuSearchPlanner.class);
    }

    @Test
    @DisplayName("La configuración TS por defecto es TS(300, 7, 30, 400, 0, 20262)")
    void configuracionTabuPorDefecto() {
        assertThat(configuracionTabu).isEqualTo(new ConfiguracionTabu(300, 7, 30, 400, 0, 20262));
    }

    @Test
    @DisplayName("Los parámetros de operación por defecto son ParametrosOperacion.porDefecto()")
    void parametrosPorDefecto() {
        assertThat(parametrosOperacion).isEqualTo(ParametrosOperacion.porDefecto());
    }
}
