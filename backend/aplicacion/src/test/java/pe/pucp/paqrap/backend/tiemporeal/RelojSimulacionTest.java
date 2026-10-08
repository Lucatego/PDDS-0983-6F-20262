package pe.pucp.paqrap.backend.tiemporeal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.backend.configuracion.PropiedadesTiempoReal;

/** El reloj avanza la simulación solo mientras corre y con el tiempo real transcurrido (B-05/B-09). */
class RelojSimulacionTest {

    private static final PropiedadesTiempoReal PROPIEDADES =
            new PropiedadesTiempoReal(true, true, 5, 50, 200, 2000, "*");

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    private void pasan(double segundos) {
        nanos.addAndGet(Math.round(segundos * 1e9));
    }

    @Test
    void noAvanzaUnaEjecucionQueNoEstaEnCurso() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var reloj = new RelojSimulacion(orquestador, PROPIEDADES, nanos::get);

        reloj.avanzar();
        pasan(5);
        reloj.avanzar();

        assertThat(orquestador.reloj()).isEqualTo(SimulacionDePrueba.INICIO);
    }

    @Test
    void conviertePasoDeTiempoRealEnMinutosSimulados() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);   // 4 min simulados por segundo real
        var reloj = new RelojSimulacion(orquestador, PROPIEDADES, nanos::get);
        orquestador.iniciar();

        reloj.avanzar();                                           // primer pulso: solo fija la referencia
        assertThat(orquestador.reloj()).isEqualTo(SimulacionDePrueba.INICIO);
        pasan(1);
        reloj.avanzar();

        assertThat(orquestador.reloj()).isEqualTo(SimulacionDePrueba.INICIO.plusMinutes(4));
    }

    @Test
    void laPausaNoAcumulaTiempo() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var reloj = new RelojSimulacion(orquestador, PROPIEDADES, nanos::get);
        orquestador.iniciar();
        reloj.avanzar();
        pasan(1);
        reloj.avanzar();
        var antesDePausa = orquestador.reloj();

        orquestador.pausar();
        pasan(60);
        reloj.avanzar();
        orquestador.iniciar();
        reloj.avanzar();                                           // reanuda: fija una nueva referencia
        assertThat(orquestador.reloj()).isEqualTo(antesDePausa);
        pasan(1);
        reloj.avanzar();

        assertThat(orquestador.reloj()).isEqualTo(antesDePausa.plusMinutes(4));
    }

    @Test
    void unSaltoLargoSeAcreditaSoloHastaElTope() {
        var orquestador = SimulacionDePrueba.orquestador5d(4);
        var reloj = new RelojSimulacion(orquestador, PROPIEDADES, nanos::get);
        orquestador.iniciar();
        reloj.avanzar();

        pasan(300);                                                // el servidor estuvo detenido 5 minutos
        reloj.avanzar();

        assertThat(orquestador.reloj()).isEqualTo(SimulacionDePrueba.INICIO.plusMinutes(8));   // tope 2 s * 4
    }
}
