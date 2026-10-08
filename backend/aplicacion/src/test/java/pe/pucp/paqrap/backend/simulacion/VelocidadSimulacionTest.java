package pe.pucp.paqrap.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.backend.tiemporeal.SimulacionDePrueba;
import pe.pucp.paqrap.estricto.modelo.Nodo;
import pe.pucp.paqrap.estricto.modelo.Pedido;

/** Velocidad del reloj de 5D y Colapso: base de 3 min/s, factor en caliente y Dia a dia fijo (LE058). */
class VelocidadSimulacionTest {

    private static final List<Pedido> PEDIDOS = List.of(
            new Pedido("P1", SimulacionDePrueba.INICIO, new Nodo(28, 14), 3, 4, "c1"));

    private static MotorSimulacion motor5dEnCurso(double base) {
        var motor = SimulacionDePrueba.motor5d(base, PEDIDOS);
        motor.iniciar();
        return motor;
    }

    private static long minutosSimulados(MotorSimulacion motor) {
        return Duration.between(SimulacionDePrueba.INICIO, motor.reloj()).toMinutes();
    }

    @Test
    void cincoDiasAX1RecorrenTresMinutosSimuladosPorSegundoReal() {
        var motor = motor5dEnCurso(3.0);
        assertThat(motor.factorVelocidad()).isEqualTo(1);
        assertThat(motor.minutosPorSegundo()).isEqualTo(3.0);

        motor.avanzar(10, SimulacionDePrueba.planificador());

        assertThat(minutosSimulados(motor)).isEqualTo(30);
        // 5 dias = 7200 min a 3 min/s son 2400 s reales, es decir 40 min.
        assertThat(5 * 1440 / 3.0 / 60).isEqualTo(40.0);
    }

    @Test
    void elFactorCambiaElAvanceDelRelojEnCaliente() {
        var motor = motor5dEnCurso(3.0);
        var planificador = SimulacionDePrueba.planificador();
        motor.avanzar(10, planificador);
        assertThat(minutosSimulados(motor)).isEqualTo(30);

        motor.establecerFactorVelocidad(5);
        assertThat(motor.minutosPorSegundo()).isEqualTo(15.0);
        motor.avanzar(10, planificador);
        assertThat(minutosSimulados(motor)).isEqualTo(30 + 150);

        motor.establecerFactorVelocidad(1);
        motor.avanzar(10, planificador);
        assertThat(minutosSimulados(motor)).isEqualTo(30 + 150 + 30);
    }

    @Test
    void elOrquestadorAplicaElFactorDesdeElSiguientePulso() {
        var orquestador = SimulacionDePrueba.orquestador5d(3.0);
        orquestador.iniciar();
        orquestador.avanzar(10);
        orquestador.establecerVelocidad(10);
        orquestador.avanzar(10);

        assertThat(minutosSimulados(orquestador.motor())).isEqualTo(30 + 300);
    }

    @Test
    void rechazaFactoresNoPermitidos() {
        var motor = motor5dEnCurso(3.0);
        for (int invalido : new int[] {0, 3, 4, 20, -1}) {
            assertThatThrownBy(() -> motor.establecerFactorVelocidad(invalido))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1, 2, 5 o 10");
        }
        assertThat(motor.factorVelocidad()).isEqualTo(1);
    }

    @Test
    void diaADiaNoAdmiteCambioDeVelocidadYSiempreVaEnTiempoReal() {
        var motor = new MotorSimulacion(SimulacionDePrueba.configuracion(
                ConfiguracionSimulacion.Escenario.DIA_A_DIA, 3.0), List.of(), SimulacionDePrueba.ALMACENES,
                List.of(), List.of());

        assertThat(motor.minutosPorSegundo()).isEqualTo(1.0 / 60);
        assertThatThrownBy(() -> motor.establecerFactorVelocidad(2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dia a dia");
        assertThat(motor.factorVelocidad()).isEqualTo(1);
    }

    @Test
    void sinSimulacionConfiguradaElOrquestadorRechazaElCambio() {
        var orquestador = new OrquestadorSimulacion(null, null, null);
        assertThatThrownBy(() -> orquestador.establecerVelocidad(2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay simulación configurada");
    }

    @Test
    void unMotorNuevoVuelveAX1() {
        var motor = motor5dEnCurso(3.0);
        motor.establecerFactorVelocidad(10);
        assertThat(motor.copiar().factorVelocidad()).isEqualTo(10);
        // Configurar o reiniciar prepara un motor nuevo: arranca en x1.
        assertThat(motor5dEnCurso(3.0).factorVelocidad()).isEqualTo(1);
    }
}
