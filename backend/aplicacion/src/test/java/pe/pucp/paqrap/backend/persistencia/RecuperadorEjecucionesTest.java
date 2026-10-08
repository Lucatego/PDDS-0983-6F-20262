package pe.pucp.paqrap.backend.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** Cierre de ejecuciones activas heredadas al arrancar el servidor (LE060). */
class RecuperadorEjecucionesTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RepositorioConfiguracionEjecucion> proveedor(RepositorioConfiguracionEjecucion repo) {
        ObjectProvider<RepositorioConfiguracionEjecucion> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(repo);
        return proveedor;
    }

    @Test
    void alArrancarCierraLasEjecucionesActivasHeredadas() {
        var repo = mock(RepositorioConfiguracionEjecucion.class);
        when(repo.cerrarEjecucionesHuerfanas(RecuperadorEjecuciones.MENSAJE)).thenReturn(2);

        assertThat(new RecuperadorEjecuciones(proveedor(repo), true).cerrarHuerfanas()).isEqualTo(2);
        verify(repo).cerrarEjecucionesHuerfanas(RecuperadorEjecuciones.MENSAJE);
    }

    @Test
    void desactivadoNoTocaLaBase() {
        var repo = mock(RepositorioConfiguracionEjecucion.class);

        assertThat(new RecuperadorEjecuciones(proveedor(repo), false).cerrarHuerfanas()).isZero();
        verify(repo, never()).cerrarEjecucionesHuerfanas(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sinBaseDeDatosNoFalla() {
        assertThat(new RecuperadorEjecuciones(proveedor(null), true).cerrarHuerfanas()).isZero();
    }
}
