package pe.pucp.paqrap.backend.api;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Cliente HTTP minimo para las pruebas de la API sobre un servidor real en puerto aleatorio. */
final class ClienteHttpDePrueba {

    record Respuesta(int estado, String cuerpo) {
    }

    private final RestClient cliente;

    ClienteHttpDePrueba(int puerto) {
        cliente = RestClient.create("http://localhost:" + puerto);
    }

    Respuesta get(String ruta) {
        return cliente.get().uri(ruta).accept(MediaType.APPLICATION_JSON)
                .exchange((solicitud, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        respuesta.bodyTo(String.class)));
    }

    Respuesta post(String ruta, String json) {
        var solicitud = cliente.post().uri(ruta).accept(MediaType.APPLICATION_JSON);
        if (json != null) solicitud = solicitud.contentType(MediaType.APPLICATION_JSON).body(json);
        return solicitud.exchange((s, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                respuesta.bodyTo(String.class)));
    }
}
