package pe.pucp.paqrap.backend.persistencia;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Auditoria persistida de una carga; los datos maestros se insertan dentro de su transaccion (LE008/011). */
@Entity
@Table(name = "archivo_carga")
public class ArchivoCarga {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipoArchivo;
    private String nombreOriginal;
    private Short anio;
    private Short mes;
    private Short mesFin;
    private Long ejecucionId;
    @jakarta.persistence.Column(columnDefinition = "char(64)")
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String hashSha256;
    private int totalLineas;
    private int registrosValidos;
    private int registrosRechazados;
    private String estado;
    private String mensaje;
    private Integer duracionMs;
    private OffsetDateTime fechaRealCarga;

    protected ArchivoCarga() { }

    ArchivoCarga(ArchivoAnalizado archivo, Long ejecucionId) {
        tipoArchivo = archivo.tipo().name();
        nombreOriginal = archivo.nombre();
        anio = archivo.anio() == null ? null : archivo.anio().shortValue();
        mes = archivo.mes() == null ? null : archivo.mes().shortValue();
        mesFin = archivo.mesFin() == null ? null : archivo.mesFin().shortValue();
        this.ejecucionId = ejecucionId;
        hashSha256 = archivo.hash();
        estado = "PROCESANDO";
        fechaRealCarga = OffsetDateTime.now();
    }

    void finalizar(int validos, int rechazados, long duracion) {
        registrosValidos = validos;
        registrosRechazados = rechazados;
        totalLineas = validos + rechazados;
        estado = validos == 0 ? "RECHAZADO" : rechazados == 0 ? "CARGADO" : "CON_ERRORES";
        mensaje = validos + " registros aceptados; " + rechazados + " rechazados";
        duracionMs = (int) Math.min(Integer.MAX_VALUE, duracion);
    }

    public Long getId() { return id; }
    public String getHashSha256() { return hashSha256; }
    public String getEstado() { return estado; }
    public int getRegistrosValidos() { return registrosValidos; }
    public int getRegistrosRechazados() { return registrosRechazados; }
    public String getMensaje() { return mensaje; }
}
