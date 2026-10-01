package pe.pucp.paqrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.pucp.paqrap.estricto.datos.DatasetLoader;
import pe.pucp.paqrap.estricto.modelo.Almacen;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.ParametrosOperacion;
import pe.pucp.paqrap.estricto.modelo.Pedido;
import pe.pucp.paqrap.estricto.modelo.TipoVehiculo;
import pe.pucp.paqrap.tabu.ConfiguracionTabu;
import pe.pucp.paqrap.tabu.TabuSearchPlanner;

/**
 * Integración de {@link DatasetLoader} con el Tabu Search. Usa un fixture propio en
 * {@code src/test/resources/datos}; la prueba con archivos reales del curso solo corre si la variable de entorno
 * {@value #VARIABLE_DATOS} apunta a la carpeta {@code data/} del prototipo (los datos no se versionan aquí).
 */
class DatasetLoaderTest {

    /** Variable de entorno con la carpeta que contiene {@code ventas.v20260909/} y {@code bloqueos.v20260909/}. */
    static final String VARIABLE_DATOS = "PAQRAP_DATOS_DIR";

    private static final LocalDateTime INSTANTE = LocalDateTime.of(2026, 9, 1, 8, 0);

    private static Path recurso(String nombre) throws URISyntaxException {
        return Path.of(DatasetLoaderTest.class.getResource("/datos/" + nombre).toURI());
    }

    private static DatasetLoader.Carga cargarFixture() throws IOException, URISyntaxException {
        return new DatasetLoader().cargar(recurso("ventas.202609.txt"), recurso("bloqueo.2609.txt"),
                recurso("mant.preventivo.09.10.txt"), INSTANTE, 24, 400);
    }

    @Test
    @DisplayName("Carga el fixture con ids deterministas, flota y almacenes oficiales (LE008, LE009)")
    void cargaFixture() throws Exception {
        var carga = cargarFixture();
        assertEquals(5, carga.pedidosLeidos(), "pedidos leidos");
        assertEquals(1, carga.futurosExcluidos(), "pedidos futuros");
        assertEquals(1, carga.fueraHorizonte(), "pedidos fuera del horizonte");
        assertEquals(0, carga.fueraLimite(), "pedidos fuera del limite");
        assertEquals(2, carga.bloqueosLeidos(), "bloqueos leidos");

        var estado = carga.estado();
        assertEquals(List.of("V202609-L00004", "V202609-L00005", "V202609-L00003"),
                estado.pedidos().stream().map(Pedido::id).toList(), "ids por archivo y linea, ordenados por plazo");
        assertEquals(List.of(new Nodo(27, 14), new Nodo(12, 38), new Nodo(57, 27)),
                estado.almacenes().stream().map(Almacen::nodo).toList(), "Coordenadas oficiales de almacenes");
        assertEquals(10, estado.vehiculos().stream().filter(v -> v.tipo() == TipoVehiculo.TA).count(), "autos");
        assertEquals(15, estado.vehiculos().stream().filter(v -> v.tipo() == TipoVehiculo.TM).count(), "motos");
        assertEquals(12, estado.vehiculos().stream().filter(v -> v.tipo() == TipoVehiculo.TB).count(), "bicicletas");
        assertEquals(1, estado.mantenimientos().size(), "Conservar soporte operativo de mantenimiento");
        assertEquals(estado, cargarFixture().estado(), "La misma entrada produce el mismo estado");
    }

    @Test
    @DisplayName("Rechaza archivos cuyo nombre no corresponde al mes del instante")
    void validaNombres() throws Exception {
        var loader = new DatasetLoader();
        assertThrows(IllegalArgumentException.class, () -> loader.cargarExperimental(recurso("ventas.202609.txt"),
                recurso("bloqueo.2609.txt"), INSTANTE.plusMonths(1), 24, 400), "instante de otro mes");
        assertThrows(IllegalArgumentException.class, () -> loader.cargarExperimental(recurso("bloqueo.2609.txt"),
                recurso("bloqueo.2609.txt"), INSTANTE, 24, 400), "nombre de ventas invalido");
    }

    @Test
    @DisplayName("El TS planifica el estado cargado de forma completa y reproducible")
    void planificaFixture() throws Exception {
        var estado = cargarFixture().estado();
        var motor = new TabuSearchPlanner(new ConfiguracionTabu(10, 7, 30, 400, 0, 20262));
        var uno = motor.planificar(estado, ParametrosOperacion.porDefecto());
        var dos = motor.planificar(estado, ParametrosOperacion.porDefecto());
        assertEquals("COMPLETA", uno.metricas().estadoResultado(), "fixture debe completarse");
        assertTrue(uno.solucion().rutas().stream().noneMatch(r -> r.vehiculo().equals("TA01") && !r.partes().isEmpty()),
                "TA01 esta en mantenimiento todo el dia");
        assertEquals(uno.solucion(), dos.solucion(), "Reproducibilidad");
        assertEquals(uno.metricas().holguraPromedioMin(), dos.metricas().holguraPromedioMin(), "Reproducibilidad");
    }

    @Test
    @DisplayName("Planifica una instantánea de los archivos reales de septiembre de 2026 (opcional)")
    void planificaDatosReales() throws Exception {
        String carpeta = System.getenv(VARIABLE_DATOS);
        assumeTrue(carpeta != null && !carpeta.isBlank(), VARIABLE_DATOS + " no definida");
        var ventas = Path.of(carpeta, "ventas.v20260909", "ventas.202609.txt");
        var bloqueos = Path.of(carpeta, "bloqueos.v20260909", "bloqueo.2609.txt");
        assumeTrue(Files.isRegularFile(ventas) && Files.isRegularFile(bloqueos), "archivos reales no encontrados");

        var carga = new DatasetLoader().cargarExperimental(ventas, bloqueos, INSTANTE, 24, 20);
        assertTrue(carga.pedidosLeidos() > 0 && carga.bloqueosLeidos() > 0, "archivos vacios");
        assertTrue(carga.estado().pedidos().size() <= 20, "limite de pedidos");
        var resultado = new TabuSearchPlanner(new ConfiguracionTabu(10, 7, 30, 400, 0, 20262))
                .planificar(carga.estado(), ParametrosOperacion.porDefecto());
        assertTrue(resultado.evaluacion().factible(), "salida infactible");
        assertEquals(carga.estado().pedidos().size(), resultado.metricas().pedidosTotales(), "pedidos considerados");
    }
}
