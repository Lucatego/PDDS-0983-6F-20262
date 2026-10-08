package pe.pucp.paqrap.estricto.modelo;

import java.time.LocalDateTime;

public record Averia(String vehiculo, LocalDateTime inicio, LocalDateTime fin, short tipo) {
    public Averia(String vehiculo, LocalDateTime inicio, LocalDateTime fin) {
        this(vehiculo, inicio, fin, (short) 1);
    }

    public Averia {
        if (vehiculo == null || inicio == null || fin == null || !inicio.isBefore(fin) || tipo < 1 || tipo > 3)
            throw new IllegalArgumentException("Averia invalida");
    }
}
