package pe.pucp.paqrap.backend.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Integracion optativa contra instancia local desechable; nunca usa las credenciales del .env ni RDS. */
@EnabledIfEnvironmentVariable(named = "PAQRAP_PRUEBA_DB_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:55483/postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.autoconfigure.exclude=", "spring.config.import=", "spring.flyway.enabled=true"
})
@Transactional
class CargaPostgresqlTest {
    private static final String ESQUEMA = "prueba_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired private ServicioCargaArchivos servicio;
    @Autowired private EntityManager entidad;
    @Autowired private RepositorioConfiguracionEjecucion configuracionEjecucion;
    @Autowired private LectorEjecucion lectorEjecucion;

    @Test
    void preparaMotorDesdeDatosPersistidosSinIniciarNiVincularArchivos() {
        var carga = servicio.cargar(TipoArchivo.VENTAS, "ventas202609",
                "01d06h59m:28,14,c1,1,4\n01d07h00m:28,14,c2,6,4\n06d07h00m:28,14,c3,1,4", null);
        long id = configuracionEjecucion.crear(configuracionPrueba(),
                new pe.pucp.paqrap.tabu.ConfiguracionTabu(1, 7, 30, 2, 0, 20262));
        // No sustituir un stock inicial explicitamente configurado por la capacidad maxima.
        entidad.createNativeQuery("UPDATE almacen_ejecucion SET stock_inicial=50,stock_actual=50 "
                + "WHERE ejecucion_id=?1 AND almacen_id='ESTE'").setParameter(1, id).executeUpdate();
        var entrada = lectorEjecucion.preparar(id);
        assertThat(entrada.pedidos()).extracting(pe.pucp.paqrap.estricto.modelo.Pedido::id)
                .containsExactly("V202609-L00002");
        assertThat(entrada.archivos()).containsKey(carga.archivoId());
        assertThat(entrada.pedidosPersistidos()).containsKey("V202609-L00002");
        assertThat(entrada.configuracion().operacion().plazoIncluyeServicio()).isFalse();
        assertThat(entrada.algoritmo().semilla()).isEqualTo(20262);
        var motor = entrada.nuevoMotor();
        assertThat(motor.estado()).isEqualTo("CONFIGURADA");
        assertThat(motor.almacenes().stream().filter(a -> a.id().equals("ESTE")).findFirst().orElseThrow().stock())
                .isEqualTo(50);
        motor.iniciar();
        motor.avanzar(20, entrada.nuevoPlanificador());
        assertThat(motor.pedidos().get("V202609-L00002").entregada).isEqualTo(6);
        assertThat(numero("SELECT count(*) FROM ejecucion WHERE estado='CONFIGURADA'")).isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM ejecucion_archivo")).isZero();
        assertThat(lectorEjecucion.preparar(id)).isEqualTo(entrada);
    }

    @Test
    void preparaRestriccionesFuturasConVerticesOrdenadosYFiltraFlotaDeMantenimiento() {
        servicio.cargar(TipoArchivo.BLOQUEOS, "bloqueo.2609.txt",
                "01d06h00m-01d07h00m:1,1,2,1\n06d08h00m-06d09h00m:3,3,4,3,4,4", null);
        servicio.cargar(TipoArchivo.MANTENIMIENTO, "mant.preventivo.09.10.txt",
                "20260901:TA01\n20260901:TB99", null);
        long id = configuracionEjecucion.crear(configuracionPrueba(),
                new pe.pucp.paqrap.tabu.ConfiguracionTabu(1, 7, 30, 2, 0, 20262));
        var entrada = lectorEjecucion.preparar(id);
        assertThat(entrada.bloqueos()).hasSize(1);
        assertThat(entrada.bloqueos().getFirst().puntos()).containsExactly(
                new pe.pucp.paqrap.estricto.modelo.Nodo(3, 3),
                new pe.pucp.paqrap.estricto.modelo.Nodo(4, 3),
                new pe.pucp.paqrap.estricto.modelo.Nodo(4, 4));
        assertThat(entrada.mantenimientos()).isNotEmpty().allMatch(m -> m.vehiculo().equals("TA01"));
        assertThat(entrada.mantenimientos().getFirst().fin())
                .isEqualTo(java.time.LocalDate.of(2026, 9, 2).atStartOfDay());
    }

    @Test
    void diaADiaNoConsumeDemandaHistoricaYRechazaPrepararEstadoNoConfigurado() {
        servicio.cargar(TipoArchivo.VENTAS, "ventas202609", "01d07h00m:28,14,c1,1,4", null);
        var base = configuracionPrueba();
        var diaria = new pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion(
                pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion.Escenario.DIA_A_DIA,
                base.inicio(), base.flota(), base.capacidades(), base.saMinutos(), base.aceleracion(),
                base.semilla(), base.operacion());
        long id = configuracionEjecucion.crear(diaria,
                new pe.pucp.paqrap.tabu.ConfiguracionTabu(1, 7, 30, 2, 0, 20262));
        var entrada = lectorEjecucion.preparar(id);
        assertThat(entrada.pedidos()).isEmpty();
        assertThat(entrada.archivos()).isEmpty();
        assertThat(entrada.configuracion().aceleracion()).isEqualTo(0.0167);
        var motor = entrada.nuevoMotor();
        motor.iniciar();
        assertThat(motor.estado()).isEqualTo("ESPERANDO_PEDIDO");
        entidad.createNativeQuery("UPDATE ejecucion SET estado='PAUSADA' WHERE id=?1")
                .setParameter(1, id).executeUpdate();
        assertThatThrownBy(() -> lectorEjecucion.preparar(id))
                .isInstanceOf(LectorEjecucion.PreparacionInvalidaException.class);
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("PAQRAP_PRUEBA_DB_URL")
                + "?currentSchema=" + ESQUEMA);
        registro.add("spring.datasource.username", () -> "paqrap_prueba");
        registro.add("spring.datasource.password", () -> "");
        registro.add("spring.flyway.schemas", () -> ESQUEMA);
        registro.add("spring.flyway.default-schema", () -> ESQUEMA);
    }

    @Test
    void configuracionPersisteFlotaTurnosParametrosYPermiteCargarAverias() {
        var configuracion = configuracionPrueba();
        long id = configuracionEjecucion.crear(configuracion,
                new pe.pucp.paqrap.tabu.ConfiguracionTabu(1, 7, 30, 2, 0, 20262));
        assertThat(numero("SELECT count(*) FROM vehiculo WHERE ejecucion_id = " + id)).isEqualTo(3);
        assertThat(numero("SELECT count(*) FROM turno_ejecucion WHERE ejecucion_id = " + id)).isEqualTo(3);
        assertThat(numero("SELECT count(*) FROM velocidad_historial WHERE ejecucion_id = " + id)).isEqualTo(3);
        assertThat(numero("SELECT stock_actual FROM almacen_ejecucion WHERE almacen_id = 'ESTE'"))
                .isEqualTo(180);
        assertThat(numero("SELECT count(*) FROM configuracion_ejecucion WHERE NOT plazo_incluye_servicio"))
                .isEqualTo(1);
        var carga = servicio.cargar(TipoArchivo.AVERIAS, "averias.txt", "01d09h30m:TA01,2", id);
        assertThat(carga.aceptados()).isEqualTo(1);
        assertThat(carga.rechazados()).isZero();
    }

    @Test
    void configuracionRechazaSegundaEjecucionActiva() {
        var algoritmo = new pe.pucp.paqrap.tabu.ConfiguracionTabu(1, 7, 30, 2, 0, 20262);
        configuracionEjecucion.crear(configuracionPrueba(), algoritmo);
        assertThatThrownBy(() -> configuracionEjecucion.crear(configuracionPrueba(), algoritmo))
                .isInstanceOf(RepositorioConfiguracionEjecucion.EjecucionActivaException.class)
                .hasMessageContaining("ejecucion activa");
        assertThat(numero("SELECT count(*) FROM ejecucion")).isEqualTo(1);
    }

    private pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion configuracionPrueba() {
        var parametros = pe.pucp.paqrap.estricto.modelo.ParametrosOperacion.porDefecto();
        var operacion = new pe.pucp.paqrap.estricto.modelo.ParametrosOperacion(60, false, 480, 420,
                60, 420, 60, 4, 50, 1000000, parametros.velocidades());
        return new pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion(
                pe.pucp.paqrap.backend.simulacion.ConfiguracionSimulacion.Escenario.SIMULACION_5D,
                java.time.LocalDateTime.of(2026, 9, 1, 7, 0),
                java.util.Map.of(pe.pucp.paqrap.estricto.modelo.TipoVehiculo.TA, 1,
                        pe.pucp.paqrap.estricto.modelo.TipoVehiculo.TM, 2,
                        pe.pucp.paqrap.estricto.modelo.TipoVehiculo.TB, 0),
                java.util.Map.of("NOROESTE", 160, "ESTE", 180), 10, 4, 20262, operacion);
    }

    @Test
    void migracionesCrean41TablasSinSeguridadY31Parametros() {
        assertThat(numero("SELECT count(*) FROM information_schema.tables WHERE table_schema = '"
                + ESQUEMA + "' AND table_name <> 'flyway_schema_history'")).isEqualTo(41);
        assertThat(numero("SELECT count(*) FROM parametro_sistema")).isEqualTo(31);
        assertThat(numero("SELECT count(*) FROM cat_tipo_evento")).isEqualTo(35);
    }

    @Test
    void ventasPersistenErroresYReintentoDevuelveMismoId() {
        String contenido = "01d00h00m:1,2,c1,2,4\nmal\0\n01d02h00m:3,4,c1,1,36";
        var resultado = servicio.cargar(TipoArchivo.VENTAS, "ventas202609", contenido, null);
        entidad.flush();
        var repetido = servicio.cargar(TipoArchivo.VENTAS, "ventas.202609.txt", contenido, null);
        assertThat(resultado.aceptados()).isEqualTo(2);
        assertThat(resultado.rechazados()).isEqualTo(1);
        assertThat(resultado.estado()).isEqualTo("CON_ERRORES");
        assertThat(repetido.archivoId()).isEqualTo(resultado.archivoId());
        assertThat(repetido.reutilizado()).isTrue();
        assertThat(numero("SELECT count(*) FROM pedido")).isEqualTo(2);
        assertThat(numero("SELECT count(*) FROM archivo_carga_error WHERE numero_linea = 2")).isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM pedido WHERE "
                + "fecha_limite = fecha_registro + plazo_horas * interval '1 hour'"))
                .isEqualTo(2);
    }

    @Test
    void noReemplazaArchivoConOtroContenido() {
        servicio.cargar(TipoArchivo.VENTAS, "ventas202609", "01d00h00m:1,2,c1,2,4", null);
        assertThatThrownBy(() -> servicio.cargar(TipoArchivo.VENTAS, "ventas202609",
                "01d00h00m:1,2,c1,3,4", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no se reemplazan");
    }

    @Test
    void bloqueosPersistenVerticesOrdenados() {
        servicio.cargar(TipoArchivo.BLOQUEOS, "bloqueo.2609.txt",
                "01d00h00m-01d01h00m:1,2,3,2,3,4", null);
        assertThat(numero("SELECT longitud_km FROM bloqueo")).isEqualTo(4);
        assertThat(numero("SELECT count(*) FROM bloqueo_vertice")).isEqualTo(3);
        assertThat(numero("SELECT y FROM bloqueo_vertice WHERE orden = 3")).isEqualTo(4);
    }

    @Test
    void mantenimientoExpandeHasta2029SinDerivaNiDuplicados() {
        servicio.cargar(TipoArchivo.MANTENIMIENTO, "mant.preventivo.09.10.txt", "20261031:TA01", null);
        assertThat(numero("SELECT count(*) FROM mantenimiento_programado WHERE fecha > DATE '2029-12-31'"))
                .isZero();
        assertThat(numero("SELECT count(*) FROM mantenimiento_programado WHERE fecha = DATE '2027-02-28'"))
                .isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM mantenimiento_programado WHERE fecha = DATE '2027-08-31'"))
                .isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM mantenimiento_programado WHERE origen = 'ARCHIVO'"))
                .isEqualTo(1);
    }

    @Test
    void averiasSonPropiasDeEjecucionYRechazanUnidadInexistente() {
        long ejecucion = crearEjecucion();
        var resultado = servicio.cargar(TipoArchivo.AVERIAS, "averias.txt",
                "01d09h30m:TA01,2\n01d10h00m:TM99,1", ejecucion);
        entidad.flush();
        assertThat(resultado.aceptados()).isEqualTo(1);
        assertThat(resultado.rechazados()).isEqualTo(1);
        assertThat(numero("SELECT count(*) FROM incidencia WHERE estado='PROGRAMADA' "
                + "AND fecha_programada = TIMESTAMP '2026-09-15 09:30:00'")).isEqualTo(1);
        assertThat(servicio.cargar(TipoArchivo.AVERIAS, "averias.txt",
                "01d09h30m:TA01,2\n01d10h00m:TM99,1", ejecucion).reutilizado()).isTrue();
    }

    @Test
    void esquemaImpideStockNegativo() {
        long ejecucion = crearEjecucion();
        assertThatThrownBy(() -> entidad.createNativeQuery("INSERT INTO almacen_ejecucion "
                + "(ejecucion_id,almacen_id,capacidad,stock_inicial,stock_actual) VALUES ("
                + ejecucion + ",'ESTE',1000,1000,-1)").executeUpdate()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void esquemaImpideSegundaEjecucionActiva() {
        crearEjecucion();
        assertThatThrownBy(this::crearEjecucion).isInstanceOf(RuntimeException.class);
    }

    private long crearEjecucion() {
        long id = ((Number) entidad.createNativeQuery("""
                INSERT INTO ejecucion (escenario,fecha_inicio,fecha_actual,fecha_real_creacion)
                VALUES ('SIMULACION_5D','2026-09-15','2026-09-15',CURRENT_TIMESTAMP) RETURNING id
                """).getSingleResult()).longValue();
        entidad.createNativeQuery("""
                INSERT INTO vehiculo (ejecucion_id,codigo,tipo_vehiculo,ubicacion_x,ubicacion_y,
                    disponible_desde,fecha_ultimo_cambio_estado)
                VALUES (?1,'TA01','TA',27,14,'2026-09-15','2026-09-15')
                """).setParameter(1, id).executeUpdate();
        return id;
    }

    private long numero(String sql) {
        return ((Number) entidad.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
