package pe.pucp.paqrap.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.pucp.paqrap.backend.persistencia.ServicioCargaArchivos;
import pe.pucp.paqrap.backend.tiemporeal.SimulacionDePrueba;

/** La posicion informada de cada vehiculo en ruta debe estar sobre su ruta, nunca en su almacen de origen (LE037). */
class PosicionVehiculosTest {

    private static double coordenada(Object punto, String eje) {
        return ((Number) ((Map<?, ?>) punto).get(eje)).doubleValue();
    }

    private static double manhattan(Object a, Object b) {
        return Math.abs(coordenada(a, "x") - coordenada(b, "x")) + Math.abs(coordenada(a, "y") - coordenada(b, "y"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void laPosicionDeCadaVehiculoEnRutaEstaSobreSuRutaEnTodoMomento() {
        var orquestador = SimulacionDePrueba.orquestador5d(60.0);
        var controlador = new PlanificadorControlador(orquestador, mock(ServicioCargaArchivos.class), null, null, null);
        orquestador.iniciar();

        int vehiculosEnRutaObservados = 0;
        for (int pulso = 0; pulso < 400; pulso++) {
            orquestador.avanzar(1.0); // 60 min simulados por pulso
            var snapshot = controlador.estado();
            for (var vehiculo : (List<Map<String, Object>>) snapshot.get("vehicles")) {
                var ruta = (List<Object>) vehiculo.get("path");
                if (ruta == null || ruta.isEmpty()) continue;
                vehiculosEnRutaObservados++;
                int indice = ((Number) vehiculo.get("pathIdx")).intValue();
                Object pos = vehiculo.get("pos");
                // el vehiculo esta en el nodo del indice o en el tramo anterior/siguiente (a lo sumo 1 km de un nodo)
                double cerca = Double.MAX_VALUE;
                for (int i = Math.max(0, indice - 1); i <= Math.min(ruta.size() - 1, indice + 1); i++) {
                    cerca = Math.min(cerca, manhattan(pos, ruta.get(i)));
                }
                assertThat(cerca)
                        .as("%s en %s (%s), pathIdx %d de %d, ruta[idx]=%s", vehiculo.get("id"), pos,
                                vehiculo.get("state"), indice, ruta.size() - 1, ruta.get(Math.min(indice, ruta.size() - 1)))
                        .isLessThanOrEqualTo(1.0001);
            }
        }
        assertThat(vehiculosEnRutaObservados).isPositive();
    }
}
