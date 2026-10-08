package pe.pucp.paqrap.backend.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import pe.pucp.paqrap.backend.api.PlanificadorControlador;
import pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion;
import pe.pucp.paqrap.backend.simulacion.OrquestadorSimulacion;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;

/**
 * Integración optativa de la persistencia de la simulación contra un PostgreSQL local (nunca el RDS ni el
 * {@code .env}). Se activa con {@code PAQRAP_PRUEBA_DB_URL=jdbc:postgresql://localhost:5433/paqrap} y, si el usuario
 * no es {@code paqrap_prueba} sin contraseña, con {@code PAQRAP_PRUEBA_DB_USUARIO} y {@code PAQRAP_PRUEBA_DB_CLAVE}.
 * Cada ejecución crea un esquema aleatorio propio (Flyway), revierte los datos de cada prueba y borra el esquema al
 * terminar, de modo que no toca las tablas de la base.
 */
@EnabledIfEnvironmentVariable(named = "PAQRAP_PRUEBA_DB_URL",
        matches = "jdbc:postgresql://(127\\.0\\.0\\.1|localhost):\\d+/[A-Za-z0-9_]+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.autoconfigure.exclude=", "spring.config.import=", "spring.flyway.enabled=true",
        "paqrap.tiempo-real.reloj-habilitado=false"
})
@Transactional
class SimulacionPostgresqlTest {
    private static final String ESQUEMA = "prueba_" + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 9, 1, 7, 0);

    @Autowired private OrquestadorSimulacion orquestador;
    @Autowired private PlanificadorControlador api;
    @Autowired private ServicioCargaArchivos carga;
    @Autowired private EntityManager entidad;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PAQRAP_PRUEBA_DB_URL") + "?currentSchema=" + ESQUEMA);
        registro.add("spring.datasource.username", SimulacionPostgresqlTest::usuario);
        registro.add("spring.datasource.password", SimulacionPostgresqlTest::clave);
        registro.add("spring.flyway.schemas", () -> ESQUEMA);
        registro.add("spring.flyway.default-schema", () -> ESQUEMA);
    }

    @AfterAll
    static void borrarEsquema() throws Exception {
        try (var conexion = DriverManager.getConnection(System.getenv("PAQRAP_PRUEBA_DB_URL"), usuario(), clave());
                var sentencia = conexion.createStatement()) {
            sentencia.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
        }
    }

    @Test
    void registraPedidoManualPorLaApiEnDiaADia() {
        orquestador.configurar(configuracion(ConfiguracionSimulacion.Escenario.DIA_A_DIA, 3.0), algoritmo());
        orquestador.iniciar();
        var resultado = api.pedido(new PlanificadorControlador.OrderInput("c1234", 5, 36, 40, 20));

        assertThat(resultado.ok()).as("motivo: %s", resultado.motivo()).isTrue();
        assertThat(resultado.id()).isNotNull();
        assertThat(numero("SELECT count(*) FROM pedido WHERE origen = 'MANUAL' AND creado_en IS NOT NULL "
                + "AND ejecucion_id = " + orquestador.ejecucionId())).isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM pedido_ejecucion WHERE ejecucion_id = " + orquestador.ejecucionId()
                + " AND estado = 'REGISTRADO' AND cantidad = 5")).isEqualTo(1);
    }

    @Test
    void persisteCiclosRutasBitacoraYResumenDeUna5dConDatosReales() {
        carga.cargar(TipoArchivo.VENTAS, "ventas202609",
                "01d07h30m:28,14,c1,3,36\n01d08h00m:30,16,c2,6,36\n01d09h00m:45,30,c3,5,12", null);
        orquestador.configurar(configuracion(ConfiguracionSimulacion.Escenario.SIMULACION_5D, 4.0), algoritmo());
        orquestador.iniciar();
        for (int i = 0; i < 12; i++) orquestador.avanzar(30);

        long id = orquestador.ejecucionId();
        assertThat(numero("SELECT count(*) FROM ciclo_planificacion WHERE ejecucion_id = " + id)).isPositive();
        assertThat(numero("SELECT count(*) FROM ruta WHERE ejecucion_id = " + id)).isPositive();
        assertThat(numero("SELECT count(*) FROM evento WHERE ejecucion_id = " + id)).isPositive();
        assertThat(numero("SELECT count(*) FROM resumen_ejecucion WHERE ejecucion_id = " + id)).isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM pedido_ejecucion WHERE ejecucion_id = " + id
                + " AND cantidad_entregada > 0")).isPositive();
    }

    private ConfiguracionSimulacion configuracion(ConfiguracionSimulacion.Escenario escenario, double aceleracion) {
        var velocidades = ParametrosOperacion.porDefecto().velocidades();
        var operacion = new ParametrosOperacion(60, false, 480, 420, 60, 420, 60, 4, 50, 1000000, velocidades);
        return new ConfiguracionSimulacion(escenario, INICIO,
                Map.of(TipoVehiculo.TA, 1, TipoVehiculo.TM, 2, TipoVehiculo.TB, 0),
                Map.of("NOROESTE", 160, "ESTE", 180), 10, aceleracion, 20262, operacion);
    }

    private static ConfiguracionTabu algoritmo() {
        return new ConfiguracionTabu(1, 7, 30, 2, 0, 20262);
    }

    private static String usuario() {
        return System.getenv().getOrDefault("PAQRAP_PRUEBA_DB_USUARIO", "paqrap_prueba");
    }

    private static String clave() {
        return System.getenv().getOrDefault("PAQRAP_PRUEBA_DB_CLAVE", "");
    }

    private long numero(String sql) {
        return ((Number) entidad.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
