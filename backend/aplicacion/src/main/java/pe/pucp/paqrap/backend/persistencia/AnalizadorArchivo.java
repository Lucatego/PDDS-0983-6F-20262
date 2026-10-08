package pe.pucp.paqrap.backend.persistencia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import pe.pucp.paqrap.estricto.datos.BloqueoParser;
import pe.pucp.paqrap.estricto.datos.PedidoParser;
import pe.pucp.paqrap.estricto.modelo.Pedido;

/** Valida fechas, formatos oficiales y nombres reales, conservando la linea fisica (LE008/009/011). */
public final class AnalizadorArchivo {
    private static final Pattern VENTAS = Pattern.compile("ventas\\.?(\\d{4})(\\d{2})(?:\\.txt)?");
    private static final Pattern BLOQUEOS = Pattern.compile(
            "(?:bloqueo\\.(\\d{2})(\\d{2})(?:\\.txt)?|(\\d{4})(\\d{2})\\.bloqueadas)");
    private static final Pattern MANTENIMIENTO = Pattern.compile(
            "mant\\.preventivo\\.(\\d{1,2})\\.(\\d{1,2})(?:\\.txt)?");
    private static final Pattern AVERIA = Pattern.compile("(\\d{1,2})d(\\d{1,2})h(\\d{1,2})m:(T[AMB][0-9]{2}),([123])");

    /** Analiza sin efectos externos; para averias el dia 01 es la fecha de inicio de la ejecucion (DD-20). */
    public ArchivoAnalizado analizar(TipoArchivo tipo, String nombre, String contenido, LocalDate inicio) {
        if (tipo == null || nombre == null || nombre.isBlank() || nombre.length() > 120
                || nombre.contains("/") || nombre.contains("\\") || contenido == null) {
            throw new IllegalArgumentException("Tipo, nombre simple (maximo 120 caracteres) y contenido requeridos");
        }
        Integer anio = null;
        Integer mes = null;
        Integer mesFin = null;
        switch (tipo) {
            case VENTAS -> {
                var matcher = VENTAS.matcher(nombre);
                exigir(matcher.matches(), "Nombre de ventas invalido");
                anio = Integer.parseInt(matcher.group(1));
                mes = Integer.parseInt(matcher.group(2));
            }
            case BLOQUEOS -> {
                var matcher = BLOQUEOS.matcher(nombre);
                exigir(matcher.matches(), "Nombre de bloqueos invalido");
                anio = matcher.group(1) != null ? 2000 + Integer.parseInt(matcher.group(1))
                        : Integer.parseInt(matcher.group(3));
                mes = Integer.parseInt(matcher.group(matcher.group(1) != null ? 2 : 4));
            }
            case MANTENIMIENTO -> {
                var matcher = MANTENIMIENTO.matcher(nombre);
                exigir(matcher.matches(), "Nombre de mantenimiento invalido");
                mes = Integer.parseInt(matcher.group(1));
                mesFin = Integer.parseInt(matcher.group(2));
                exigir(mes >= 1 && mes <= 12 && mesFin == mes % 12 + 1, "Se requiere un bimestre consecutivo");
                // El nombre no lleva anio: se obtiene de la primera fecha valida del bimestre.
                for (String linea : contenido.lines().toList()) {
                    String limpia = limpiar(linea);
                    if (limpia.matches("[0-9]{8}:T[AMB][0-9]{2}")) {
                        try {
                            LocalDate fecha = LocalDate.parse(limpia.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
                            if (fecha.getMonthValue() == mes || fecha.getMonthValue() == mesFin) {
                                anio = fecha.getYear() - (mes == 12 && fecha.getMonthValue() == 1 ? 1 : 0);
                                break;
                            }
                        } catch (DateTimeException ignorada) {
                            // La linea se registrara como rechazada en la segunda pasada.
                        }
                    }
                }
                exigir(anio != null, "No se pudo determinar el anio del mantenimiento");
            }
            case AVERIAS -> exigir(inicio != null, "Averias requiere fecha de inicio de ejecucion");
        }
        if (anio != null) {
            exigir(anio >= 2000 && anio <= 2100 && mes >= 1 && mes <= 12, "Periodo fuera de rango");
        }
        var registros = new ArrayList<ArchivoAnalizado.Registro>();
        var errores = new ArrayList<ArchivoAnalizado.ErrorLinea>();
        int numero = 0;
        for (String original : contenido.lines().toList()) {
            numero++;
            String linea = limpiar(original);
            if (linea.isEmpty() || linea.startsWith("#")) {
                continue;
            }
            try {
                exigir(linea.indexOf('\0') < 0, "Caracter nulo no permitido");
                switch (tipo) {
                    case VENTAS -> {
                        Pedido pedido = new PedidoParser().parsear(linea, anio, mes);
                        exigir(pedido.clienteId().length() <= 20, "Codigo de cliente demasiado largo");
                        String codigo = String.format(Locale.ROOT, "V%04d%02d-L%05d", anio, mes, numero);
                        registros.add(new ArchivoAnalizado.Venta(numero, new Pedido(codigo, pedido.fechaRegistro(),
                                pedido.ubicacion(), pedido.cantidad(), pedido.plazoHoras(), pedido.clienteId())));
                    }
                    case BLOQUEOS -> {
                        var bloqueo = new BloqueoParser().parsear(linea, anio, mes);
                        exigir(bloqueo.puntos().size() <= Short.MAX_VALUE, "Demasiados vertices");
                        registros.add(new ArchivoAnalizado.Cierre(numero,
                                String.format(Locale.ROOT, "B%02d%02d-L%05d", anio % 100, mes, numero), bloqueo));
                    }
                    case MANTENIMIENTO -> {
                        exigir(linea.matches("[0-9]{8}:T[AMB][0-9]{2}"), "Mantenimiento: aaaammdd:TTNN");
                        LocalDate fecha = LocalDate.parse(linea.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
                        YearMonth primero = YearMonth.of(anio, mes);
                        exigir(YearMonth.from(fecha).equals(primero)
                                || YearMonth.from(fecha).equals(primero.plusMonths(1)), "Fecha fuera del bimestre");
                        registros.add(new ArchivoAnalizado.Mantenimiento(numero, fecha, linea.substring(9)));
                    }
                    case AVERIAS -> {
                        var matcher = AVERIA.matcher(linea);
                        exigir(matcher.matches(), "Averia: ##d##h##m:TTNN,tipo (1, 2 o 3)");
                        int dia = Integer.parseInt(matcher.group(1));
                        exigir(dia >= 1, "Dia relativo debe ser al menos 1");
                        LocalDateTime fecha = inicio.plusDays(dia - 1L).atTime(
                                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                        registros.add(new ArchivoAnalizado.Averia(numero, fecha, matcher.group(4),
                                Short.parseShort(matcher.group(5))));
                    }
                }
            } catch (IllegalArgumentException | DateTimeException error) {
                errores.add(new ArchivoAnalizado.ErrorLinea(numero, truncar(original, 500),
                        truncar(error.getMessage(), 300)));
            }
        }
        return new ArchivoAnalizado(tipo, nombre, anio, mes, mesFin, hash(contenido), registros, errores);
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    private static String limpiar(String linea) {
        return linea.replace("\uFEFF", "").trim();
    }

    private static String truncar(String texto, int maximo) {
        String seguro = texto == null ? "Dato invalido" : texto.replace("\0", "\\0");
        return seguro.substring(0, Math.min(seguro.length(), maximo));
    }

    private static String hash(String contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no disponible", error);
        }
    }
}
