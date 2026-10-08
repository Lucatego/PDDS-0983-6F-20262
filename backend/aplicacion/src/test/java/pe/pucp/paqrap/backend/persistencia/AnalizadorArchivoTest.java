package pe.pucp.paqrap.backend.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalizadorArchivoTest {
    private final AnalizadorArchivo analizador = new AnalizadorArchivo();

    @Test
    void ventasConservaLineaFisicaYContinuaTrasRechazo() {
        var archivo = analizador.analizar(TipoArchivo.VENTAS, "ventas202609", """
                # comentario

                01d01h30m:56,30,c4910,02,36
                31d01h30m:56,30,c4910,02,36
                02d02h30m:10,20,c4910,03,4
                """, null);
        assertThat(archivo.registros()).hasSize(2);
        var venta = (ArchivoAnalizado.Venta) archivo.registros().getFirst();
        assertThat(venta.pedido().id()).isEqualTo("V202609-L00003");
        assertThat(venta.pedido().clienteId()).isEqualTo("c4910");
        assertThat(archivo.errores()).extracting(ArchivoAnalizado.ErrorLinea::linea).containsExactly(4);
    }

    @Test
    void formatosAlternativosConservanCodigo() {
        String texto = "01d00h00m:1,2,c1,1,4";
        var primero = analizador.analizar(TipoArchivo.VENTAS, "ventas.202609.txt", texto, null);
        var segundo = analizador.analizar(TipoArchivo.VENTAS, "ventas202609", texto, null);
        assertThat(primero.registros()).isEqualTo(segundo.registros());
        assertThat(primero.hash()).isEqualTo(segundo.hash());
    }

    @Test
    void bloqueosRechazaDiagonalYFechaInvalida() {
        var archivo = analizador.analizar(TipoArchivo.BLOQUEOS, "202609.bloqueadas", """
                01d00h00m-01d01h00m:1,2,3,2
                01d00h00m-01d01h00m:1,2,3,4
                01d25h00m-02d01h00m:1,2,3,2
                """, null);
        assertThat(archivo.registros()).hasSize(1);
        assertThat(archivo.errores()).hasSize(2);
    }

    @Test
    void mantenimientoDerivaAnioYValidaBimestreInclusoCambioDeAnio() {
        var archivo = analizador.analizar(TipoArchivo.MANTENIMIENTO, "mant.preventivo.12.01.txt", """
                20270101:TA01
                20261231:TM02
                20270201:TA01
                """, null);
        assertThat(archivo.anio()).isEqualTo(2026);
        assertThat(archivo.registros()).hasSize(2);
        assertThat(archivo.errores()).hasSize(1);
    }

    @Test
    void averiaUsaDiasRelativosNoDiaDelMes() {
        var archivo = analizador.analizar(TipoArchivo.AVERIAS, "averias.txt",
                "01d09h30m:TA01,2\n02d08h00m:TM01,3\n00d08h00m:TA01,1", LocalDate.of(2026, 9, 15));
        var averia = (ArchivoAnalizado.Averia) archivo.registros().getFirst();
        assertThat(averia.fecha()).isEqualTo("2026-09-15T09:30:00");
        assertThat(archivo.registros()).hasSize(2);
        assertThat(archivo.errores()).hasSize(1);
    }

    @Test
    void rechazaNombreYPeriodoAntesDePersistir() {
        assertThatThrownBy(() -> analizador.analizar(TipoArchivo.VENTAS, "ventas.202613.txt", "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analizador.analizar(TipoArchivo.VENTAS, "../ventas202609", "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analizador.analizar(TipoArchivo.MANTENIMIENTO, "mant.preventivo.09.11", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validaLimitesDeReticulaCantidadYCliente() {
        var archivo = analizador.analizar(TipoArchivo.VENTAS, "ventas202609", """
                01d00h00m:71,0,c1,1,4
                01d00h00m:0,51,c1,1,4
                01d00h00m:0,0,c1,0,4
                01d00h00m:0,0,c1,1,5
                01d00h00m:0,0,cliente-demasiado-largo,1,4
                """, null);
        assertThat(archivo.registros()).isEmpty();
        assertThat(archivo.errores()).hasSize(5);
    }
}
