package pe.pucp.paqrap.backend.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Base de las pruebas HTTP de la API: servidor real en puerto aleatorio, sin base de datos ni reloj propio. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "paqrap.tiempo-real.reloj-habilitado=false")
abstract class BaseHttpTest {

    @MockitoBean private pe.pucp.paqrap.backend.persistencia.RepositorioCarga repositorioCarga;
    @MockitoBean private pe.pucp.paqrap.backend.persistencia.RepositorioConfiguracionEjecucion configuracionEjecucion;
    @MockitoBean private pe.pucp.paqrap.backend.persistencia.LectorEjecucion lectorEjecucion;
    @MockitoBean private pe.pucp.paqrap.backend.persistencia.RepositorioSimulacion repositorioSimulacion;

    @LocalServerPort private int puerto;
    protected ClienteHttpDePrueba http;

    @BeforeEach
    void crearCliente() {
        http = new ClienteHttpDePrueba(puerto);
    }
}
