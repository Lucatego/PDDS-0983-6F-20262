-- Modelo aprobado v1.0.1, secciones 6 y 10. PostgreSQL.
-- D-06: seguridad postergada; registrado_por queda nullable y sin FK.
-- Revisar antes de aplicar al RDS. No editar una migracion ya aplicada.
-- Las restricciones entre filas (flota, polilineas, capacidad de recarga)
-- se validan en los servicios, como establece el modelo.

CREATE TABLE cat_escenario (
    codigo VARCHAR(20) NOT NULL CHECK (codigo IN ('DIA_A_DIA', 'SIMULACION_5D', 'COLAPSO')),
    clave_front VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(60) NOT NULL,
    nombre_corto VARCHAR(30) NOT NULL,
    descripcion VARCHAR(400),
    duracion_dias SMALLINT CHECK (duracion_dias > 0),
    usa_reloj_real BOOLEAN NOT NULL DEFAULT FALSE,
    aceleracion_defecto NUMERIC(10,4) NOT NULL CHECK (aceleracion_defecto > 0),
    usa_archivos_maestros BOOLEAN NOT NULL,
    detiene_en_colapso BOOLEAN NOT NULL,
    orden SMALLINT NOT NULL,
    PRIMARY KEY (codigo)
);

CREATE TABLE cat_modalidad_entrega (
    plazo_horas SMALLINT NOT NULL CHECK (plazo_horas IN (4, 8, 12, 18, 36)),
    tipo VARCHAR(12) NOT NULL CHECK (tipo IN ('REGULAR', 'PRIORIZADA')),
    etiqueta VARCHAR(40) NOT NULL,
    orden SMALLINT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (plazo_horas)
);

CREATE TABLE cat_tipo_vehiculo (
    codigo CHAR(2) NOT NULL CHECK (codigo IN ('TA', 'TM', 'TB')),
    clave_front VARCHAR(10) NOT NULL UNIQUE,
    nombre VARCHAR(30) NOT NULL,
    nombre_plural VARCHAR(30) NOT NULL,
    capacidad_defecto SMALLINT NOT NULL CHECK (capacidad_defecto > 0),
    velocidad_defecto_kmh NUMERIC(6,2) NOT NULL CHECK (velocidad_defecto_kmh BETWEEN 1 AND 300),
    costo_km_defecto NUMERIC(8,2) NOT NULL CHECK (costo_km_defecto >= 0),
    cantidad_defecto SMALLINT NOT NULL CHECK (cantidad_defecto BETWEEN 0 AND 99),
    orden SMALLINT NOT NULL,
    PRIMARY KEY (codigo)
);

CREATE TABLE cat_tipo_averia (
    tipo SMALLINT NOT NULL CHECK (tipo IN (1, 2, 3)),
    nombre VARCHAR(30) NOT NULL,
    descripcion VARCHAR(300) NOT NULL,
    regla_fin VARCHAR(20) NOT NULL CHECK (regla_fin IN ('DURACION_FIJA', 'FIN_TURNO_SIGUIENTE', 'DIAS_Y_TURNO')),
    minutos_inoperativa INTEGER CHECK (minutos_inoperativa > 0),
    minutos_max_en_lugar INTEGER NOT NULL CHECK (minutos_max_en_lugar >= 0),
    traslado_central BOOLEAN NOT NULL,
    dias_minimos SMALLINT CHECK (dias_minimos > 0),
    minuto_inicio_turno_retorno SMALLINT CHECK (minuto_inicio_turno_retorno BETWEEN 0 AND 1439),
    peso_generacion NUMERIC(6,3) NOT NULL DEFAULT 1 CHECK (peso_generacion >= 0),
    PRIMARY KEY (tipo),
    CHECK (regla_fin <> 'DURACION_FIJA' OR minutos_inoperativa IS NOT NULL),
    CHECK (regla_fin <> 'DIAS_Y_TURNO' OR (dias_minimos IS NOT NULL AND minuto_inicio_turno_retorno IS NOT NULL))
);

CREATE TABLE cat_estado_pedido (
    codigo VARCHAR(20) NOT NULL CHECK (codigo IN ('REGISTRADO', 'PLANIFICADO', 'REPROGRAMADO', 'EN_RUTA', 'ENTREGADO', 'NO_CUMPLIDO', 'ANULADO')),
    etiqueta VARCHAR(40) NOT NULL,
    clave_front VARCHAR(20) NOT NULL,
    es_final BOOLEAN NOT NULL,
    tono VARCHAR(12) NOT NULL CHECK (tono IN ('EXITO', 'ADVERTENCIA', 'CRITICO', 'INFORMATIVO', 'NEUTRO')),
    orden SMALLINT NOT NULL,
    PRIMARY KEY (codigo)
);

CREATE TABLE cat_estado_vehiculo (
    codigo VARCHAR(20) NOT NULL CHECK (codigo IN ('DISPONIBLE', 'EN_REFRIGERIO', 'EN_RUTA', 'ENTREGANDO', 'RETORNANDO', 'AVERIADO', 'EN_MANTENIMIENTO')),
    etiqueta VARCHAR(40) NOT NULL,
    clave_front VARCHAR(12) NOT NULL UNIQUE,
    es_asignable BOOLEAN NOT NULL,
    en_mapa BOOLEAN NOT NULL,
    tono VARCHAR(12) NOT NULL CHECK (tono IN ('EXITO', 'ADVERTENCIA', 'CRITICO', 'INFORMATIVO', 'NEUTRO')),
    PRIMARY KEY (codigo)
);

CREATE TABLE cat_estado_ejecucion (
    codigo VARCHAR(20) NOT NULL CHECK (codigo IN ('CONFIGURADA', 'ESPERANDO_PEDIDO', 'EN_CURSO', 'PAUSADA', 'FINALIZADA', 'COLAPSADA', 'DETENIDA', 'ERROR')),
    etiqueta VARCHAR(40) NOT NULL,
    es_activa BOOLEAN NOT NULL,
    es_final BOOLEAN NOT NULL,
    PRIMARY KEY (codigo)
);

CREATE TABLE cat_motivo_fin (
    codigo VARCHAR(30) NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    es_colapso BOOLEAN NOT NULL,
    es_error BOOLEAN NOT NULL,
    PRIMARY KEY (codigo)
);

CREATE TABLE cat_tipo_evento (
    codigo VARCHAR(40) NOT NULL,
    categoria VARCHAR(15) NOT NULL CHECK (categoria IN ('EJECUCION', 'CARGA', 'PEDIDO', 'PLANIFICACION', 'INVENTARIO', 'INCIDENCIA', 'FLOTA', 'PARAMETRO')),
    nivel_defecto VARCHAR(12) NOT NULL CHECK (nivel_defecto IN ('EXITO', 'ADVERTENCIA', 'CRITICO', 'INFORMATIVO')),
    descripcion VARCHAR(200) NOT NULL,
    genera_alerta BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (codigo)
);

CREATE TABLE parametro_sistema (
    codigo VARCHAR(60) NOT NULL,
    valor VARCHAR(200) NOT NULL,
    tipo_dato VARCHAR(10) NOT NULL CHECK (tipo_dato IN ('ENTERO', 'DECIMAL', 'BOOLEANO', 'TEXTO', 'HORA')),
    unidad VARCHAR(20),
    descripcion VARCHAR(300) NOT NULL,
    trazabilidad VARCHAR(100),
    actualizado_en TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (codigo)
);

CREATE TABLE almacen (
    id VARCHAR(12) NOT NULL CHECK (id IN ('CENTRAL', 'NOROESTE', 'ESTE')),
    clave_front VARCHAR(12) NOT NULL,
    nombre VARCHAR(60) NOT NULL,
    nombre_corto VARCHAR(20) NOT NULL,
    tipo VARCHAR(12) NOT NULL CHECK (tipo IN ('CENTRAL', 'INTERMEDIO')),
    x SMALLINT NOT NULL CHECK (x BETWEEN 0 AND 70),
    y SMALLINT NOT NULL CHECK (y BETWEEN 0 AND 50),
    es_ilimitado BOOLEAN NOT NULL,
    capacidad_defecto INTEGER CHECK (capacidad_defecto > 0),
    es_origen_flota BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (x, y),
    UNIQUE (clave_front),
    CHECK (es_ilimitado = (capacidad_defecto IS NULL))
);

CREATE TABLE archivo_carga (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    tipo_archivo VARCHAR(15) NOT NULL CHECK (tipo_archivo IN ('VENTAS', 'BLOQUEOS', 'MANTENIMIENTO', 'AVERIAS', 'LOTE_PEDIDOS')),
    nombre_original VARCHAR(120) NOT NULL,
    anio SMALLINT CHECK (anio BETWEEN 2000 AND 2100),
    mes SMALLINT CHECK (mes BETWEEN 1 AND 12),
    mes_fin SMALLINT CHECK (mes_fin BETWEEN 1 AND 12),
    ejecucion_id BIGINT,
    hash_sha256 CHAR(64) NOT NULL,
    total_lineas INTEGER NOT NULL DEFAULT 0 CHECK (total_lineas >= 0),
    registros_validos INTEGER NOT NULL DEFAULT 0 CHECK (registros_validos >= 0),
    registros_rechazados INTEGER NOT NULL DEFAULT 0 CHECK (registros_rechazados >= 0),
    estado VARCHAR(12) NOT NULL DEFAULT 'PROCESANDO' CHECK (estado IN ('PROCESANDO', 'CARGADO', 'CON_ERRORES', 'RECHAZADO', 'REEMPLAZADO')),
    mensaje VARCHAR(400),
    duracion_ms INTEGER CHECK (duracion_ms >= 0),
    ruta_almacenamiento VARCHAR(260),
    fecha_real_carga TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    registrado_por BIGINT,
    PRIMARY KEY (id),
    CHECK (tipo_archivo NOT IN ('VENTAS','BLOQUEOS') OR (anio IS NOT NULL AND mes IS NOT NULL AND ejecucion_id IS NULL)),
    CHECK (tipo_archivo NOT IN ('AVERIAS','LOTE_PEDIDOS') OR ejecucion_id IS NOT NULL),
    CHECK (tipo_archivo <> 'MANTENIMIENTO' OR (anio IS NOT NULL AND mes IS NOT NULL AND mes_fin IS NOT NULL AND ejecucion_id IS NULL)),
    CHECK (registros_validos + registros_rechazados = total_lineas)
);

CREATE TABLE archivo_carga_error (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    archivo_id BIGINT NOT NULL,
    numero_linea INTEGER NOT NULL CHECK (numero_linea >= 0),
    contenido VARCHAR(500),
    motivo VARCHAR(300) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (archivo_id, numero_linea)
);

CREATE TABLE pedido (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    codigo VARCHAR(20) NOT NULL,
    origen VARCHAR(10) NOT NULL CHECK (origen IN ('ARCHIVO', 'MANUAL', 'LOTE')),
    archivo_id BIGINT,
    numero_linea INTEGER CHECK (numero_linea > 0),
    ejecucion_id BIGINT,
    cliente_codigo VARCHAR(20) NOT NULL,
    fecha_registro TIMESTAMP(3) NOT NULL,
    destino_x SMALLINT NOT NULL CHECK (destino_x BETWEEN 0 AND 70),
    destino_y SMALLINT NOT NULL CHECK (destino_y BETWEEN 0 AND 50),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    plazo_horas SMALLINT NOT NULL,
    fecha_limite TIMESTAMP(3) NOT NULL GENERATED ALWAYS AS (fecha_registro + plazo_horas * INTERVAL '1 hour') STORED,
    registrado_por BIGINT,
    creado_en TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (codigo),
    UNIQUE (archivo_id, numero_linea),
    CHECK ((origen = 'ARCHIVO') = (ejecucion_id IS NULL)),
    CHECK (origen <> 'ARCHIVO' OR (archivo_id IS NOT NULL AND numero_linea IS NOT NULL))
);

CREATE TABLE bloqueo (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    codigo VARCHAR(20) NOT NULL,
    origen VARCHAR(10) NOT NULL CHECK (origen IN ('ARCHIVO', 'MANUAL')),
    archivo_id BIGINT,
    numero_linea INTEGER CHECK (numero_linea > 0),
    ejecucion_id BIGINT,
    fecha_inicio TIMESTAMP(3) NOT NULL,
    fecha_fin TIMESTAMP(3) NOT NULL CHECK (fecha_fin > fecha_inicio),
    num_vertices SMALLINT NOT NULL CHECK (num_vertices >= 2),
    longitud_km INTEGER NOT NULL CHECK (longitud_km > 0),
    registrado_por BIGINT,
    creado_en TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (codigo),
    UNIQUE (archivo_id, numero_linea),
    CHECK ((origen = 'ARCHIVO') = (ejecucion_id IS NULL)),
    CHECK (origen <> 'ARCHIVO' OR (archivo_id IS NOT NULL AND numero_linea IS NOT NULL))
);

CREATE TABLE bloqueo_vertice (
    bloqueo_id BIGINT NOT NULL,
    orden SMALLINT NOT NULL CHECK (orden >= 1),
    x SMALLINT NOT NULL CHECK (x BETWEEN 0 AND 70),
    y SMALLINT NOT NULL CHECK (y BETWEEN 0 AND 50),
    PRIMARY KEY (bloqueo_id, orden)
);

CREATE TABLE mantenimiento_programado (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    fecha DATE NOT NULL,
    vehiculo_codigo VARCHAR(4) NOT NULL CHECK (vehiculo_codigo ~ '^T[AMB][0-9]{2}$'),
    origen VARCHAR(10) NOT NULL CHECK (origen IN ('ARCHIVO', 'GENERADO')),
    archivo_id BIGINT,
    numero_linea INTEGER CHECK (numero_linea > 0),
    creado_en TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (fecha, vehiculo_codigo)
);

CREATE TABLE ejecucion (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    nombre VARCHAR(80),
    escenario VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'CONFIGURADA',
    fecha_inicio TIMESTAMP(3) NOT NULL,
    fecha_fin_prevista TIMESTAMP(3) CHECK (fecha_fin_prevista > fecha_inicio),
    fecha_actual TIMESTAMP(3) NOT NULL CHECK (fecha_actual >= fecha_inicio),
    fecha_fin TIMESTAMP(3),
    motivo_fin VARCHAR(30),
    mensaje_fin VARCHAR(400),
    semilla BIGINT NOT NULL DEFAULT 20262,
    hash_instancia CHAR(64),
    version_software VARCHAR(40),
    colapso_pedido_id BIGINT,
    colapso_fecha TIMESTAMP(3),
    colapso_x SMALLINT CHECK (colapso_x BETWEEN 0 AND 70),
    colapso_y SMALLINT CHECK (colapso_y BETWEEN 0 AND 50),
    colapso_pedidos_no_atendidos INTEGER CHECK (colapso_pedidos_no_atendidos >= 0),
    diagnostico_colapso TEXT,
    fecha_real_creacion TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    fecha_real_inicio TIMESTAMP(3) WITH TIME ZONE,
    fecha_real_fin TIMESTAMP(3) WITH TIME ZONE,
    duracion_real_ms BIGINT NOT NULL DEFAULT 0 CHECK (duracion_real_ms >= 0),
    registrado_por BIGINT,
    version INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CHECK (estado NOT IN ('FINALIZADA','COLAPSADA','DETENIDA','ERROR') OR (motivo_fin IS NOT NULL AND fecha_fin IS NOT NULL)),
    CHECK (motivo_fin NOT IN ('COLAPSO_PLANIFICACION','COLAPSO_PLAZO') OR colapso_fecha IS NOT NULL)
);

CREATE TABLE configuracion_ejecucion (
    ejecucion_id BIGINT NOT NULL,
    sa_minutos SMALLINT NOT NULL DEFAULT 10 CHECK (sa_minutos > 0),
    sc_minutos SMALLINT NOT NULL DEFAULT 0 CHECK (sc_minutos >= 0),
    aceleracion_reloj NUMERIC(10,4) NOT NULL CHECK (aceleracion_reloj > 0),
    duracion_dias SMALLINT CHECK (duracion_dias > 0),
    max_ciclos INTEGER NOT NULL DEFAULT 0 CHECK (max_ciclos >= 0),
    factor_carga NUMERIC(6,3) NOT NULL DEFAULT 1 CHECK (factor_carga > 0),
    servicio_minutos SMALLINT NOT NULL DEFAULT 60 CHECK (servicio_minutos >= 0),
    plazo_incluye_servicio BOOLEAN NOT NULL DEFAULT FALSE,
    turno_minutos SMALLINT NOT NULL DEFAULT 480 CHECK (turno_minutos > 0),
    descanso_desde_min SMALLINT NOT NULL DEFAULT 60 CHECK (descanso_desde_min >= 0),
    descanso_hasta_min SMALLINT NOT NULL DEFAULT 420 CHECK (descanso_hasta_min >= descanso_desde_min),
    descanso_minutos SMALLINT NOT NULL DEFAULT 60 CHECK (descanso_minutos > 0),
    tamanio_parte SMALLINT NOT NULL DEFAULT 4 CHECK (tamanio_parte > 0),
    costo_fijo_vehiculo NUMERIC(10,2) NOT NULL DEFAULT 50 CHECK (costo_fijo_vehiculo >= 0),
    penalizacion_paquete_pendiente NUMERIC(14,2) NOT NULL DEFAULT 1000000 CHECK (penalizacion_paquete_pendiente > 0),
    semaforo_verde_min_pct NUMERIC(5,2) NOT NULL DEFAULT 70 CHECK (semaforo_verde_min_pct BETWEEN 0 AND 100),
    semaforo_ambar_min_pct NUMERIC(5,2) NOT NULL DEFAULT 35 CHECK (semaforo_ambar_min_pct BETWEEN 0 AND 100),
    alerta_stock_pct NUMERIC(5,2) NOT NULL DEFAULT 20 CHECK (alerta_stock_pct BETWEEN 0 AND 100),
    hora_recarga TIME NOT NULL DEFAULT '23:59:59',
    averias_aleatorias BOOLEAN NOT NULL DEFAULT FALSE,
    tasa_averias_dia NUMERIC(8,4) NOT NULL DEFAULT 0 CHECK (tasa_averias_dia >= 0),
    trasvase_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    trasvase_minutos SMALLINT DEFAULT 30 CHECK (trasvase_minutos >= 0),
    PRIMARY KEY (ejecucion_id),
    CHECK (1440 % turno_minutos = 0),
    CHECK (descanso_hasta_min >= descanso_desde_min),
    CHECK (descanso_hasta_min + descanso_minutos <= turno_minutos),
    CHECK (semaforo_ambar_min_pct < semaforo_verde_min_pct),
    CHECK (NOT trasvase_habilitado OR trasvase_minutos IS NOT NULL)
);

CREATE TABLE configuracion_algoritmo (
    ejecucion_id BIGINT NOT NULL,
    algoritmo VARCHAR(10) NOT NULL DEFAULT 'TS' CHECK (algoritmo IN ('TS', 'ALNS')),
    max_iteraciones INTEGER NOT NULL DEFAULT 300 CHECK (max_iteraciones >= 0),
    sin_mejora_max INTEGER NOT NULL DEFAULT 30 CHECK (sin_mejora_max > 0),
    presupuesto_ms BIGINT NOT NULL DEFAULT 0 CHECK (presupuesto_ms >= 0),
    tenencia_tabu INTEGER DEFAULT 7 CHECK (tenencia_tabu > 0),
    candidatos_por_iteracion INTEGER DEFAULT 400 CHECK (candidatos_por_iteracion >= 2),
    destruccion_max INTEGER CHECK (destruccion_max > 0),
    segmento INTEGER CHECK (segmento > 0),
    reaccion NUMERIC(6,4) CHECK (reaccion BETWEEN 0 AND 1),
    aceptacion_inicial NUMERIC(8,5) CHECK (aceptacion_inicial >= 0),
    PRIMARY KEY (ejecucion_id),
    CHECK (algoritmo <> 'TS' OR (tenencia_tabu IS NOT NULL AND candidatos_por_iteracion IS NOT NULL)),
    CHECK (algoritmo <> 'ALNS' OR (destruccion_max IS NOT NULL AND segmento IS NOT NULL AND reaccion IS NOT NULL AND aceptacion_inicial IS NOT NULL))
);

CREATE TABLE turno_ejecucion (
    ejecucion_id BIGINT NOT NULL,
    numero SMALLINT NOT NULL CHECK (numero >= 1),
    minuto_inicio SMALLINT NOT NULL CHECK (minuto_inicio BETWEEN 0 AND 1439),
    duracion_min SMALLINT NOT NULL DEFAULT 480 CHECK (duracion_min > 0),
    PRIMARY KEY (ejecucion_id, numero),
    UNIQUE (ejecucion_id, minuto_inicio)
);

CREATE TABLE flota_ejecucion (
    ejecucion_id BIGINT NOT NULL,
    tipo_vehiculo CHAR(2) NOT NULL,
    cantidad SMALLINT NOT NULL CHECK (cantidad BETWEEN 0 AND 99),
    capacidad SMALLINT NOT NULL CHECK (capacidad > 0),
    costo_km NUMERIC(8,2) NOT NULL CHECK (costo_km >= 0),
    PRIMARY KEY (ejecucion_id, tipo_vehiculo)
);

CREATE TABLE velocidad_historial (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    tipo_vehiculo CHAR(2) NOT NULL,
    velocidad_kmh NUMERIC(6,2) NOT NULL CHECK (velocidad_kmh BETWEEN 1 AND 300),
    fecha_registro TIMESTAMP(3) NOT NULL,
    fecha_vigencia TIMESTAMP(3) NOT NULL CHECK (fecha_vigencia >= fecha_registro),
    ciclo_id BIGINT,
    fecha_real_registro TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    registrado_por BIGINT,
    PRIMARY KEY (id),
    UNIQUE (ejecucion_id, tipo_vehiculo, fecha_vigencia)
);

CREATE TABLE ejecucion_archivo (
    ejecucion_id BIGINT NOT NULL,
    archivo_id BIGINT NOT NULL,
    PRIMARY KEY (ejecucion_id, archivo_id)
);

CREATE TABLE almacen_ejecucion (
    ejecucion_id BIGINT NOT NULL,
    almacen_id VARCHAR(12) NOT NULL,
    capacidad INTEGER CHECK (capacidad > 0),
    stock_inicial INTEGER CHECK (stock_inicial BETWEEN 0 AND capacidad),
    stock_actual INTEGER CHECK (stock_actual BETWEEN 0 AND capacidad),
    despachado_dia INTEGER NOT NULL DEFAULT 0 CHECK (despachado_dia >= 0),
    despachado_total INTEGER NOT NULL DEFAULT 0 CHECK (despachado_total >= 0),
    fecha_ultima_recarga TIMESTAMP(3),
    version INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (ejecucion_id, almacen_id),
    CHECK ((capacidad IS NULL) = (stock_inicial IS NULL)),
    CHECK ((capacidad IS NULL) = (stock_actual IS NULL))
);

CREATE TABLE vehiculo (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    codigo VARCHAR(4) NOT NULL CHECK (codigo ~ '^T[AMB][0-9]{2}$'),
    tipo_vehiculo CHAR(2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    ubicacion_x SMALLINT NOT NULL CHECK (ubicacion_x BETWEEN 0 AND 70),
    ubicacion_y SMALLINT NOT NULL CHECK (ubicacion_y BETWEEN 0 AND 50),
    almacen_actual_id VARCHAR(12) DEFAULT 'CENTRAL',
    disponible_desde TIMESTAMP(3) NOT NULL,
    carga_actual INTEGER NOT NULL DEFAULT 0 CHECK (carga_actual >= 0),
    fecha_fin_ultimo_descanso TIMESTAMP(3),
    fecha_ultimo_cambio_estado TIMESTAMP(3) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (ejecucion_id, codigo),
    CHECK (tipo_vehiculo = substring(codigo FROM 1 FOR 2))
);

CREATE TABLE pedido_ejecucion (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    pedido_id BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO',
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    cantidad_pendiente INTEGER NOT NULL CHECK (cantidad_pendiente >= 0),
    cantidad_en_ruta INTEGER NOT NULL DEFAULT 0 CHECK (cantidad_en_ruta >= 0),
    cantidad_entregada INTEGER NOT NULL DEFAULT 0 CHECK (cantidad_entregada >= 0),
    fecha_ingreso TIMESTAMP(3) NOT NULL,
    fecha_limite TIMESTAMP(3) NOT NULL,
    en_riesgo BOOLEAN NOT NULL DEFAULT FALSE,
    veces_reprogramado SMALLINT NOT NULL DEFAULT 0 CHECK (veces_reprogramado >= 0),
    fecha_primer_despacho TIMESTAMP(3),
    fecha_llegada_final TIMESTAMP(3),
    fecha_entrega TIMESTAMP(3),
    holgura_min NUMERIC(12,3),
    en_plazo BOOLEAN,
    fecha_no_cumplido TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE (ejecucion_id, pedido_id),
    CHECK (cantidad_pendiente::bigint + cantidad_en_ruta + cantidad_entregada = cantidad),
    CHECK (estado <> 'ENTREGADO' OR (cantidad_entregada = cantidad AND en_plazo IS TRUE)),
    CHECK (estado <> 'NO_CUMPLIDO' OR fecha_no_cumplido IS NOT NULL)
);

CREATE TABLE parte_pedido (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    pedido_ejecucion_id BIGINT NOT NULL,
    numero SMALLINT NOT NULL CHECK (numero >= 1),
    codigo VARCHAR(26) NOT NULL,
    cantidad SMALLINT NOT NULL CHECK (cantidad > 0),
    estado VARCHAR(12) NOT NULL DEFAULT 'EN_RUTA' CHECK (estado IN ('EN_RUTA', 'ENTREGADA', 'RETIRADA')),
    ruta_actual_id BIGINT,
    almacen_origen_id VARCHAR(12) NOT NULL,
    fecha_despacho TIMESTAMP(3) NOT NULL,
    fecha_llegada TIMESTAMP(3),
    fecha_entrega TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE (ejecucion_id, codigo),
    UNIQUE (pedido_ejecucion_id, numero)
);

CREATE TABLE ciclo_planificacion (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    numero INTEGER NOT NULL CHECK (numero >= 0),
    fecha_ciclo TIMESTAMP(3) NOT NULL,
    disparador VARCHAR(20) NOT NULL DEFAULT 'PERIODICO' CHECK (disparador IN ('INICIO', 'PERIODICO', 'INCIDENCIA', 'CAMBIO_PARAMETRO', 'MANUAL')),
    incidencia_id BIGINT,
    algoritmo VARCHAR(10) NOT NULL CHECK (algoritmo IN ('TS', 'ALNS')),
    estado_resultado VARCHAR(25) NOT NULL CHECK (estado_resultado IN ('COMPLETA', 'COLAPSO_PLANIFICACION', 'SIN_DEMANDA', 'ERROR')),
    motivo_parada VARCHAR(40),
    fecha_real_inicio TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    fecha_real_fin TIMESTAMP(3) WITH TIME ZONE,
    ta_ms NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (ta_ms >= 0),
    ta_primera_completa_ms NUMERIC(14,3),
    iteraciones INTEGER CHECK (iteraciones >= 0),
    iteracion_mejor INTEGER CHECK (iteracion_mejor >= 0),
    iteracion_primera_completa INTEGER CHECK (iteracion_primera_completa >= 0),
    candidatos_evaluados BIGINT CHECK (candidatos_evaluados >= 0),
    inserciones_iniciales BIGINT CHECK (inserciones_iniciales >= 0),
    objetivo NUMERIC(20,6),
    costo_plan NUMERIC(14,2) CHECK (costo_plan >= 0),
    distancia_plan_km NUMERIC(12,3) CHECK (distancia_plan_km >= 0),
    tiempo_plan_rutas_min NUMERIC(14,3) CHECK (tiempo_plan_rutas_min >= 0),
    vehiculos_plan INTEGER CHECK (vehiculos_plan >= 0),
    utilizacion_capacidad_plan_pct NUMERIC(6,2) CHECK (utilizacion_capacidad_plan_pct BETWEEN 0 AND 100),
    pedidos_plan INTEGER CHECK (pedidos_plan >= 0),
    pedidos_completos_plan INTEGER CHECK (pedidos_completos_plan >= 0),
    cumplimiento_pedidos_pct NUMERIC(6,2) CHECK (cumplimiento_pedidos_pct BETWEEN 0 AND 100),
    cumplimiento_paquetes_pct NUMERIC(6,2) CHECK (cumplimiento_paquetes_pct BETWEEN 0 AND 100),
    paquetes_sin_plan INTEGER NOT NULL DEFAULT 0 CHECK (paquetes_sin_plan >= 0),
    holgura_plan_promedio_min NUMERIC(12,3),
    holgura_plan_minima_min NUMERIC(12,3),
    rutas_despachadas INTEGER NOT NULL DEFAULT 0 CHECK (rutas_despachadas >= 0),
    pedidos_entregados_acum INTEGER NOT NULL DEFAULT 0 CHECK (pedidos_entregados_acum >= 0),
    paquetes_entregados_acum INTEGER NOT NULL DEFAULT 0 CHECK (paquetes_entregados_acum >= 0),
    holgura_real_promedio_min NUMERIC(12,3),
    holgura_real_minima_min NUMERIC(12,3),
    pedidos_activos INTEGER NOT NULL DEFAULT 0 CHECK (pedidos_activos >= 0),
    pedidos_entregados_dia INTEGER NOT NULL DEFAULT 0 CHECK (pedidos_entregados_dia >= 0),
    vehiculos_disponibles INTEGER NOT NULL DEFAULT 0 CHECK (vehiculos_disponibles >= 0),
    vehiculos_en_ruta INTEGER NOT NULL DEFAULT 0 CHECK (vehiculos_en_ruta >= 0),
    vehiculos_inoperativos INTEGER NOT NULL DEFAULT 0 CHECK (vehiculos_inoperativos >= 0),
    errores TEXT,
    PRIMARY KEY (id),
    UNIQUE (ejecucion_id, numero)
);

CREATE TABLE ruta (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    ciclo_id BIGINT NOT NULL,
    vehiculo_id BIGINT NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PLANIFICADA' CHECK (estado IN ('PLANIFICADA', 'DESPACHADA', 'EN_CURSO', 'COMPLETADA', 'INTERRUMPIDA', 'REEMPLAZADA')),
    almacen_origen_id VARCHAR(12) NOT NULL,
    almacen_retorno_id VARCHAR(12),
    fecha_salida TIMESTAMP(3) NOT NULL,
    fecha_fin TIMESTAMP(3) NOT NULL CHECK (fecha_fin >= fecha_salida),
    fecha_fin_real TIMESTAMP(3),
    descanso_inicio TIMESTAMP(3),
    descanso_fin TIMESTAMP(3),
    carga INTEGER NOT NULL CHECK (carga BETWEEN 0 AND capacidad),
    capacidad SMALLINT NOT NULL CHECK (capacidad > 0),
    velocidad_kmh NUMERIC(6,2) NOT NULL CHECK (velocidad_kmh > 0),
    distancia_km NUMERIC(10,3) NOT NULL CHECK (distancia_km >= 0),
    duracion_min NUMERIC(10,3) NOT NULL CHECK (duracion_min >= 0),
    costo NUMERIC(12,2) NOT NULL CHECK (costo >= 0),
    ruta_reemplazada_id BIGINT,
    motivo_reemplazo VARCHAR(20) CHECK (motivo_reemplazo IN ('AVERIA', 'BLOQUEO', 'MANTENIMIENTO', 'REPLANIFICACION')),
    PRIMARY KEY (id),
    UNIQUE (ruta_reemplazada_id),
    CHECK ((descanso_inicio IS NULL) = (descanso_fin IS NULL)),
    CHECK (descanso_fin >= descanso_inicio)
);

CREATE TABLE parada (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ruta_id BIGINT NOT NULL,
    orden SMALLINT NOT NULL CHECK (orden >= 1),
    pedido_ejecucion_id BIGINT NOT NULL,
    x SMALLINT NOT NULL CHECK (x BETWEEN 0 AND 70),
    y SMALLINT NOT NULL CHECK (y BETWEEN 0 AND 50),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    fecha_llegada TIMESTAMP(3) NOT NULL,
    fecha_fin_servicio TIMESTAMP(3) NOT NULL CHECK (fecha_fin_servicio >= fecha_llegada),
    holgura_min NUMERIC(12,3) NOT NULL,
    estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'CANCELADA')),
    PRIMARY KEY (id),
    UNIQUE (ruta_id, orden)
);

CREATE TABLE parada_parte (
    parada_id BIGINT NOT NULL,
    parte_pedido_id BIGINT NOT NULL,
    PRIMARY KEY (parada_id, parte_pedido_id)
);

CREATE TABLE ruta_tramo (
    ruta_id BIGINT NOT NULL,
    orden INTEGER NOT NULL CHECK (orden >= 1),
    parada_id BIGINT,
    x_origen SMALLINT NOT NULL CHECK (x_origen BETWEEN 0 AND 70),
    y_origen SMALLINT NOT NULL CHECK (y_origen BETWEEN 0 AND 50),
    x_destino SMALLINT NOT NULL CHECK (x_destino BETWEEN 0 AND 70),
    y_destino SMALLINT NOT NULL CHECK (y_destino BETWEEN 0 AND 50),
    fecha_salida TIMESTAMP(3) NOT NULL,
    fecha_llegada TIMESTAMP(3) NOT NULL CHECK (fecha_llegada > fecha_salida),
    PRIMARY KEY (ruta_id, orden),
    CHECK ((x_origen = x_destino OR y_origen = y_destino) AND (x_origen <> x_destino OR y_origen <> y_destino))
);

CREATE TABLE movimiento_inventario (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    almacen_id VARCHAR(12) NOT NULL,
    fecha TIMESTAMP(3) NOT NULL,
    tipo VARCHAR(15) NOT NULL CHECK (tipo IN ('STOCK_INICIAL', 'DESPACHO', 'RECARGA', 'AJUSTE')),
    cantidad INTEGER NOT NULL,
    stock_anterior INTEGER CHECK (stock_anterior >= 0),
    stock_resultante INTEGER CHECK (stock_resultante >= 0),
    pedido_ejecucion_id BIGINT,
    parte_pedido_id BIGINT,
    ruta_id BIGINT,
    ciclo_id BIGINT,
    observacion VARCHAR(200),
    PRIMARY KEY (id),
    CHECK ((tipo = 'DESPACHO') = (pedido_ejecucion_id IS NOT NULL)),
    CHECK (tipo <> 'DESPACHO' OR cantidad < 0),
    CHECK (tipo NOT IN ('STOCK_INICIAL','RECARGA') OR cantidad >= 0)
);

CREATE TABLE incidencia (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    tipo VARCHAR(15) NOT NULL CHECK (tipo IN ('BLOQUEO', 'AVERIA', 'MANTENIMIENTO')),
    origen VARCHAR(10) NOT NULL CHECK (origen IN ('ARCHIVO', 'MANUAL', 'ALEATORIO')),
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('PROGRAMADA', 'ACTIVA', 'RESUELTA', 'DESCARTADA', 'CONSOLIDADA')),
    bloqueo_id BIGINT,
    mantenimiento_id BIGINT,
    archivo_id BIGINT,
    numero_linea INTEGER CHECK (numero_linea > 0),
    vehiculo_id BIGINT,
    tipo_averia SMALLINT,
    ubicacion_x SMALLINT CHECK (ubicacion_x BETWEEN 0 AND 70),
    ubicacion_y SMALLINT CHECK (ubicacion_y BETWEEN 0 AND 50),
    fecha_programada TIMESTAMP(3),
    fecha_inicio TIMESTAMP(3),
    fecha_fin_prevista TIMESTAMP(3) CHECK (fecha_fin_prevista > fecha_inicio),
    fecha_traslado_central TIMESTAMP(3),
    fecha_fin TIMESTAMP(3) CHECK (fecha_fin >= fecha_inicio),
    duracion_horas NUMERIC(6,2) CHECK (duracion_horas > 0),
    paquetes_a_bordo INTEGER CHECK (paquetes_a_bordo >= 0),
    paquetes_trasvasados INTEGER CHECK (paquetes_trasvasados >= 0),
    vehiculo_trasvase_id BIGINT,
    ruta_afectada_id BIGINT,
    consolidada_en_id BIGINT,
    num_reportes INTEGER NOT NULL DEFAULT 1 CHECK (num_reportes >= 1),
    descripcion VARCHAR(300),
    motivo_descarte VARCHAR(200),
    fecha_real_registro TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    registrado_por BIGINT,
    PRIMARY KEY (id),
    CHECK (tipo <> 'BLOQUEO' OR (bloqueo_id IS NOT NULL AND vehiculo_id IS NULL)),
    CHECK (tipo <> 'AVERIA' OR (vehiculo_id IS NOT NULL AND tipo_averia IS NOT NULL)),
    CHECK (tipo <> 'MANTENIMIENTO' OR vehiculo_id IS NOT NULL),
    CHECK ((estado = 'CONSOLIDADA') = (consolidada_en_id IS NOT NULL)),
    CHECK (estado NOT IN ('ACTIVA','RESUELTA') OR fecha_inicio IS NOT NULL),
    CHECK (estado <> 'RESUELTA' OR fecha_fin IS NOT NULL)
);

CREATE TABLE incidencia_pedido (
    incidencia_id BIGINT NOT NULL,
    pedido_ejecucion_id BIGINT NOT NULL,
    cantidad_afectada INTEGER NOT NULL CHECK (cantidad_afectada > 0),
    efecto VARCHAR(20) NOT NULL CHECK (efecto IN ('REPROGRAMADO', 'RUTA_RECALCULADA', 'RETRASADO')),
    PRIMARY KEY (incidencia_id, pedido_ejecucion_id)
);

CREATE TABLE reasignacion (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    ciclo_id BIGINT,
    fecha TIMESTAMP(3) NOT NULL,
    pedido_ejecucion_id BIGINT NOT NULL,
    parte_pedido_id BIGINT,
    incidencia_id BIGINT,
    motivo VARCHAR(20) NOT NULL CHECK (motivo IN ('AVERIA', 'BLOQUEO', 'MANTENIMIENTO', 'REPLANIFICACION')),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    vehiculo_anterior_id BIGINT,
    vehiculo_nuevo_id BIGINT,
    ruta_anterior_id BIGINT,
    ruta_nueva_id BIGINT,
    almacen_origen_anterior_id VARCHAR(12),
    almacen_origen_nuevo_id VARCHAR(12),
    tiempo_restante_min NUMERIC(12,3) NOT NULL,
    holgura_anterior_min NUMERIC(12,3),
    holgura_nueva_min NUMERIC(12,3),
    fecha_real_registro TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE evento (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    ejecucion_id BIGINT NOT NULL,
    secuencia INTEGER NOT NULL CHECK (secuencia >= 1),
    fecha TIMESTAMP(3) NOT NULL,
    fecha_real TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    tipo_evento VARCHAR(40) NOT NULL,
    nivel VARCHAR(12) NOT NULL CHECK (nivel IN ('EXITO', 'ADVERTENCIA', 'CRITICO', 'INFORMATIVO')),
    mensaje VARCHAR(500) NOT NULL,
    pedido_ejecucion_id BIGINT,
    vehiculo_id BIGINT,
    almacen_id VARCHAR(12),
    incidencia_id BIGINT,
    ruta_id BIGINT,
    ciclo_id BIGINT,
    archivo_id BIGINT,
    detalle JSONB,
    PRIMARY KEY (id),
    UNIQUE (ejecucion_id, secuencia)
);

CREATE TABLE resumen_ejecucion (
    ejecucion_id BIGINT NOT NULL,
    es_parcial BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_real_generacion TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    duracion_simulada_dias NUMERIC(10,4) NOT NULL CHECK (duracion_simulada_dias >= 0),
    duracion_real_ms BIGINT NOT NULL CHECK (duracion_real_ms >= 0),
    ciclos INTEGER NOT NULL CHECK (ciclos >= 0),
    ejecuciones_planificador INTEGER NOT NULL CHECK (ejecuciones_planificador >= 0),
    ta_total_ms NUMERIC(16,3) NOT NULL CHECK (ta_total_ms >= 0),
    ta_promedio_ms NUMERIC(14,3) CHECK (ta_promedio_ms >= 0),
    ta_max_ms NUMERIC(14,3) CHECK (ta_max_ms >= 0),
    pedidos_totales INTEGER NOT NULL CHECK (pedidos_totales >= 0),
    pedidos_entregados INTEGER NOT NULL CHECK (pedidos_entregados >= 0),
    pedidos_entregados_en_plazo INTEGER NOT NULL CHECK (pedidos_entregados_en_plazo >= 0),
    pedidos_no_cumplidos INTEGER NOT NULL CHECK (pedidos_no_cumplidos >= 0),
    pedidos_pendientes_cierre INTEGER NOT NULL CHECK (pedidos_pendientes_cierre >= 0),
    pedidos_reprogramados INTEGER NOT NULL CHECK (pedidos_reprogramados >= 0),
    pedidos_incumplidos_reprogramados INTEGER NOT NULL CHECK (pedidos_incumplidos_reprogramados >= 0),
    cumplimiento_pct NUMERIC(6,2) CHECK (cumplimiento_pct BETWEEN 0 AND 100),
    incumplidos_reprogramacion_pct NUMERIC(6,2) CHECK (incumplidos_reprogramacion_pct BETWEEN 0 AND 100),
    paquetes_entregados INTEGER NOT NULL CHECK (paquetes_entregados >= 0),
    holgura_real_promedio_min NUMERIC(12,3),
    holgura_real_minima_min NUMERIC(12,3),
    tiempo_entrega_promedio_h NUMERIC(10,3) CHECK (tiempo_entrega_promedio_h >= 0),
    tiempo_entrega_minimo_h NUMERIC(10,3) CHECK (tiempo_entrega_minimo_h >= 0),
    tiempo_entrega_maximo_h NUMERIC(10,3) CHECK (tiempo_entrega_maximo_h >= 0),
    vehiculos_disponibles INTEGER NOT NULL CHECK (vehiculos_disponibles >= 0),
    vehiculos_utilizados INTEGER NOT NULL CHECK (vehiculos_utilizados >= 0),
    utilizacion_flota_pct NUMERIC(6,2) CHECK (utilizacion_flota_pct BETWEEN 0 AND 100),
    utilizacion_capacidad_pct NUMERIC(6,2) CHECK (utilizacion_capacidad_pct BETWEEN 0 AND 100),
    rutas_despachadas INTEGER NOT NULL CHECK (rutas_despachadas >= 0),
    distancia_total_km NUMERIC(14,3) NOT NULL CHECK (distancia_total_km >= 0),
    tiempo_rutas_min NUMERIC(16,3) NOT NULL CHECK (tiempo_rutas_min >= 0),
    costo_total NUMERIC(14,2) NOT NULL CHECK (costo_total >= 0),
    incidencias_totales INTEGER NOT NULL CHECK (incidencias_totales >= 0),
    bloqueos_totales INTEGER NOT NULL CHECK (bloqueos_totales >= 0),
    averias_totales INTEGER NOT NULL CHECK (averias_totales >= 0),
    averias_tipo1 INTEGER NOT NULL CHECK (averias_tipo1 >= 0),
    averias_tipo2 INTEGER NOT NULL CHECK (averias_tipo2 >= 0),
    averias_tipo3 INTEGER NOT NULL CHECK (averias_tipo3 >= 0),
    mantenimientos_totales INTEGER NOT NULL CHECK (mantenimientos_totales >= 0),
    incidencias_activas_cierre INTEGER NOT NULL CHECK (incidencias_activas_cierre >= 0),
    resolucion_bloqueo_promedio_min NUMERIC(12,3) CHECK (resolucion_bloqueo_promedio_min >= 0),
    resolucion_averia_promedio_min NUMERIC(12,3) CHECK (resolucion_averia_promedio_min >= 0),
    ultimo_ciclo_completo_id BIGINT,
    PRIMARY KEY (ejecucion_id)
);

CREATE TABLE indicador_plazo (
    ejecucion_id BIGINT NOT NULL,
    plazo_horas SMALLINT NOT NULL,
    pedidos_totales INTEGER NOT NULL CHECK (pedidos_totales >= 0),
    pedidos_entregados INTEGER NOT NULL CHECK (pedidos_entregados >= 0),
    pedidos_en_plazo INTEGER NOT NULL CHECK (pedidos_en_plazo >= 0),
    en_plazo_pct NUMERIC(6,2) CHECK (en_plazo_pct BETWEEN 0 AND 100),
    PRIMARY KEY (ejecucion_id, plazo_horas)
);

CREATE TABLE diagnostico_colapso_pedido (
    ejecucion_id BIGINT NOT NULL,
    pedido_ejecucion_id BIGINT NOT NULL,
    cantidad_total INTEGER NOT NULL CHECK (cantidad_total > 0),
    cantidad_sin_plan INTEGER NOT NULL CHECK (cantidad_sin_plan > 0),
    ya_vencido BOOLEAN NOT NULL,
    fecha_limite TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (ejecucion_id, pedido_ejecucion_id)
);

-- FK al final: resuelve las dependencias circulares sin desactivar integridad.
ALTER TABLE archivo_carga ADD CONSTRAINT fk_archivo_carga_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE archivo_carga_error ADD CONSTRAINT fk_archivo_carga_error_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE pedido ADD CONSTRAINT fk_pedido_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE pedido ADD CONSTRAINT fk_pedido_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE pedido ADD CONSTRAINT fk_pedido_plazo_horas
    FOREIGN KEY (plazo_horas) REFERENCES cat_modalidad_entrega (plazo_horas);
ALTER TABLE bloqueo ADD CONSTRAINT fk_bloqueo_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE bloqueo ADD CONSTRAINT fk_bloqueo_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE bloqueo_vertice ADD CONSTRAINT fk_bloqueo_vertice_bloqueo_id
    FOREIGN KEY (bloqueo_id) REFERENCES bloqueo (id);
ALTER TABLE mantenimiento_programado ADD CONSTRAINT fk_mantenimiento_programado_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE ejecucion ADD CONSTRAINT fk_ejecucion_escenario
    FOREIGN KEY (escenario) REFERENCES cat_escenario (codigo);
ALTER TABLE ejecucion ADD CONSTRAINT fk_ejecucion_estado
    FOREIGN KEY (estado) REFERENCES cat_estado_ejecucion (codigo);
ALTER TABLE ejecucion ADD CONSTRAINT fk_ejecucion_motivo_fin
    FOREIGN KEY (motivo_fin) REFERENCES cat_motivo_fin (codigo);
ALTER TABLE ejecucion ADD CONSTRAINT fk_ejecucion_colapso_pedido_id
    FOREIGN KEY (colapso_pedido_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE configuracion_ejecucion ADD CONSTRAINT fk_configuracion_ejecucion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE configuracion_algoritmo ADD CONSTRAINT fk_configuracion_algoritmo_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE turno_ejecucion ADD CONSTRAINT fk_turno_ejecucion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE flota_ejecucion ADD CONSTRAINT fk_flota_ejecucion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE flota_ejecucion ADD CONSTRAINT fk_flota_ejecucion_tipo_vehiculo
    FOREIGN KEY (tipo_vehiculo) REFERENCES cat_tipo_vehiculo (codigo);
ALTER TABLE velocidad_historial ADD CONSTRAINT fk_velocidad_historial_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE velocidad_historial ADD CONSTRAINT fk_velocidad_historial_tipo_vehiculo
    FOREIGN KEY (tipo_vehiculo) REFERENCES cat_tipo_vehiculo (codigo);
ALTER TABLE velocidad_historial ADD CONSTRAINT fk_velocidad_historial_ciclo_id
    FOREIGN KEY (ciclo_id) REFERENCES ciclo_planificacion (id);
ALTER TABLE ejecucion_archivo ADD CONSTRAINT fk_ejecucion_archivo_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE ejecucion_archivo ADD CONSTRAINT fk_ejecucion_archivo_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE almacen_ejecucion ADD CONSTRAINT fk_almacen_ejecucion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE almacen_ejecucion ADD CONSTRAINT fk_almacen_ejecucion_almacen_id
    FOREIGN KEY (almacen_id) REFERENCES almacen (id);
ALTER TABLE vehiculo ADD CONSTRAINT fk_vehiculo_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE vehiculo ADD CONSTRAINT fk_vehiculo_tipo_vehiculo
    FOREIGN KEY (tipo_vehiculo) REFERENCES cat_tipo_vehiculo (codigo);
ALTER TABLE vehiculo ADD CONSTRAINT fk_vehiculo_estado
    FOREIGN KEY (estado) REFERENCES cat_estado_vehiculo (codigo);
ALTER TABLE vehiculo ADD CONSTRAINT fk_vehiculo_almacen_actual_id
    FOREIGN KEY (almacen_actual_id) REFERENCES almacen (id);
ALTER TABLE pedido_ejecucion ADD CONSTRAINT fk_pedido_ejecucion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE pedido_ejecucion ADD CONSTRAINT fk_pedido_ejecucion_pedido_id
    FOREIGN KEY (pedido_id) REFERENCES pedido (id);
ALTER TABLE pedido_ejecucion ADD CONSTRAINT fk_pedido_ejecucion_estado
    FOREIGN KEY (estado) REFERENCES cat_estado_pedido (codigo);
ALTER TABLE parte_pedido ADD CONSTRAINT fk_parte_pedido_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE parte_pedido ADD CONSTRAINT fk_parte_pedido_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE parte_pedido ADD CONSTRAINT fk_parte_pedido_ruta_actual_id
    FOREIGN KEY (ruta_actual_id) REFERENCES ruta (id);
ALTER TABLE parte_pedido ADD CONSTRAINT fk_parte_pedido_almacen_origen_id
    FOREIGN KEY (almacen_origen_id) REFERENCES almacen (id);
ALTER TABLE ciclo_planificacion ADD CONSTRAINT fk_ciclo_planificacion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE ciclo_planificacion ADD CONSTRAINT fk_ciclo_planificacion_incidencia_id
    FOREIGN KEY (incidencia_id) REFERENCES incidencia (id);
ALTER TABLE ruta ADD CONSTRAINT fk_ruta_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE ruta ADD CONSTRAINT fk_ruta_ciclo_id
    FOREIGN KEY (ciclo_id) REFERENCES ciclo_planificacion (id);
ALTER TABLE ruta ADD CONSTRAINT fk_ruta_vehiculo_id
    FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id);
ALTER TABLE ruta ADD CONSTRAINT fk_ruta_almacen_origen_id
    FOREIGN KEY (almacen_origen_id) REFERENCES almacen (id);
ALTER TABLE ruta ADD CONSTRAINT fk_ruta_almacen_retorno_id
    FOREIGN KEY (almacen_retorno_id) REFERENCES almacen (id);
ALTER TABLE ruta ADD CONSTRAINT fk_ruta_ruta_reemplazada_id
    FOREIGN KEY (ruta_reemplazada_id) REFERENCES ruta (id);
ALTER TABLE parada ADD CONSTRAINT fk_parada_ruta_id
    FOREIGN KEY (ruta_id) REFERENCES ruta (id);
ALTER TABLE parada ADD CONSTRAINT fk_parada_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE parada_parte ADD CONSTRAINT fk_parada_parte_parada_id
    FOREIGN KEY (parada_id) REFERENCES parada (id);
ALTER TABLE parada_parte ADD CONSTRAINT fk_parada_parte_parte_pedido_id
    FOREIGN KEY (parte_pedido_id) REFERENCES parte_pedido (id);
ALTER TABLE ruta_tramo ADD CONSTRAINT fk_ruta_tramo_ruta_id
    FOREIGN KEY (ruta_id) REFERENCES ruta (id);
ALTER TABLE ruta_tramo ADD CONSTRAINT fk_ruta_tramo_parada_id
    FOREIGN KEY (parada_id) REFERENCES parada (id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_inventario_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_inventario_almacen_id
    FOREIGN KEY (almacen_id) REFERENCES almacen (id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_inventario_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_inventario_parte_pedido_id
    FOREIGN KEY (parte_pedido_id) REFERENCES parte_pedido (id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_inventario_ruta_id
    FOREIGN KEY (ruta_id) REFERENCES ruta (id);
ALTER TABLE movimiento_inventario ADD CONSTRAINT fk_movimiento_inventario_ciclo_id
    FOREIGN KEY (ciclo_id) REFERENCES ciclo_planificacion (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_bloqueo_id
    FOREIGN KEY (bloqueo_id) REFERENCES bloqueo (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_mantenimiento_id
    FOREIGN KEY (mantenimiento_id) REFERENCES mantenimiento_programado (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_vehiculo_id
    FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_tipo_averia
    FOREIGN KEY (tipo_averia) REFERENCES cat_tipo_averia (tipo);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_vehiculo_trasvase_id
    FOREIGN KEY (vehiculo_trasvase_id) REFERENCES vehiculo (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_ruta_afectada_id
    FOREIGN KEY (ruta_afectada_id) REFERENCES ruta (id);
ALTER TABLE incidencia ADD CONSTRAINT fk_incidencia_consolidada_en_id
    FOREIGN KEY (consolidada_en_id) REFERENCES incidencia (id);
ALTER TABLE incidencia_pedido ADD CONSTRAINT fk_incidencia_pedido_incidencia_id
    FOREIGN KEY (incidencia_id) REFERENCES incidencia (id);
ALTER TABLE incidencia_pedido ADD CONSTRAINT fk_incidencia_pedido_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_ciclo_id
    FOREIGN KEY (ciclo_id) REFERENCES ciclo_planificacion (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_parte_pedido_id
    FOREIGN KEY (parte_pedido_id) REFERENCES parte_pedido (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_incidencia_id
    FOREIGN KEY (incidencia_id) REFERENCES incidencia (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_vehiculo_anterior_id
    FOREIGN KEY (vehiculo_anterior_id) REFERENCES vehiculo (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_vehiculo_nuevo_id
    FOREIGN KEY (vehiculo_nuevo_id) REFERENCES vehiculo (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_ruta_anterior_id
    FOREIGN KEY (ruta_anterior_id) REFERENCES ruta (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_ruta_nueva_id
    FOREIGN KEY (ruta_nueva_id) REFERENCES ruta (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_almacen_origen_anterior_id
    FOREIGN KEY (almacen_origen_anterior_id) REFERENCES almacen (id);
ALTER TABLE reasignacion ADD CONSTRAINT fk_reasignacion_almacen_origen_nuevo_id
    FOREIGN KEY (almacen_origen_nuevo_id) REFERENCES almacen (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_tipo_evento
    FOREIGN KEY (tipo_evento) REFERENCES cat_tipo_evento (codigo);
ALTER TABLE evento ADD CONSTRAINT fk_evento_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_vehiculo_id
    FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_almacen_id
    FOREIGN KEY (almacen_id) REFERENCES almacen (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_incidencia_id
    FOREIGN KEY (incidencia_id) REFERENCES incidencia (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_ruta_id
    FOREIGN KEY (ruta_id) REFERENCES ruta (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_ciclo_id
    FOREIGN KEY (ciclo_id) REFERENCES ciclo_planificacion (id);
ALTER TABLE evento ADD CONSTRAINT fk_evento_archivo_id
    FOREIGN KEY (archivo_id) REFERENCES archivo_carga (id);
ALTER TABLE resumen_ejecucion ADD CONSTRAINT fk_resumen_ejecucion_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE resumen_ejecucion ADD CONSTRAINT fk_resumen_ejecucion_ultimo_ciclo_completo_id
    FOREIGN KEY (ultimo_ciclo_completo_id) REFERENCES ciclo_planificacion (id);
ALTER TABLE indicador_plazo ADD CONSTRAINT fk_indicador_plazo_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES resumen_ejecucion (ejecucion_id);
ALTER TABLE indicador_plazo ADD CONSTRAINT fk_indicador_plazo_plazo_horas
    FOREIGN KEY (plazo_horas) REFERENCES cat_modalidad_entrega (plazo_horas);
ALTER TABLE diagnostico_colapso_pedido ADD CONSTRAINT fk_diagnostico_colapso_pedido_ejecucion_id
    FOREIGN KEY (ejecucion_id) REFERENCES ejecucion (id);
ALTER TABLE diagnostico_colapso_pedido ADD CONSTRAINT fk_diagnostico_colapso_pedido_pedido_ejecucion_id
    FOREIGN KEY (pedido_ejecucion_id) REFERENCES pedido_ejecucion (id);

-- LE055: una sola ejecucion activa en todo el servidor.
CREATE UNIQUE INDEX uk_ejecucion_activa ON ejecucion ((1))
    WHERE estado IN ('CONFIGURADA', 'ESPERANDO_PEDIDO', 'EN_CURSO', 'PAUSADA');
CREATE UNIQUE INDEX uk_archivo_maestro_vigente ON archivo_carga (tipo_archivo, anio, mes)
    WHERE ejecucion_id IS NULL AND estado <> 'REEMPLAZADO';
CREATE UNIQUE INDEX uk_incidencia_bloqueo ON incidencia (ejecucion_id, bloqueo_id)
    WHERE tipo = 'BLOQUEO' AND estado <> 'CONSOLIDADA';
CREATE UNIQUE INDEX uk_incidencia_archivo_linea ON incidencia (archivo_id, numero_linea)
    WHERE archivo_id IS NOT NULL;
CREATE INDEX ix_archivo_carga_1 ON archivo_carga (hash_sha256);
CREATE INDEX ix_archivo_carga_2 ON archivo_carga (ejecucion_id);
CREATE INDEX ix_pedido_1 ON pedido (fecha_registro);
CREATE INDEX ix_pedido_2 ON pedido (ejecucion_id);
CREATE INDEX ix_pedido_3 ON pedido (cliente_codigo);
CREATE INDEX ix_bloqueo_1 ON bloqueo (fecha_inicio,fecha_fin);
CREATE INDEX ix_bloqueo_2 ON bloqueo (ejecucion_id);
CREATE INDEX ix_mantenimiento_programado_1 ON mantenimiento_programado (fecha);
CREATE INDEX ix_ejecucion_1 ON ejecucion (estado);
CREATE INDEX ix_vehiculo_1 ON vehiculo (ejecucion_id,estado);
CREATE INDEX ix_pedido_ejecucion_1 ON pedido_ejecucion (ejecucion_id,estado);
CREATE INDEX ix_pedido_ejecucion_2 ON pedido_ejecucion (ejecucion_id,fecha_limite);
CREATE INDEX ix_parte_pedido_1 ON parte_pedido (ruta_actual_id);
CREATE INDEX ix_ciclo_planificacion_1 ON ciclo_planificacion (ejecucion_id,fecha_ciclo);
CREATE INDEX ix_ruta_1 ON ruta (ejecucion_id,estado);
CREATE INDEX ix_ruta_2 ON ruta (vehiculo_id,fecha_salida);
CREATE INDEX ix_ruta_3 ON ruta (ciclo_id);
CREATE INDEX ix_parada_1 ON parada (pedido_ejecucion_id);
CREATE INDEX ix_movimiento_inventario_1 ON movimiento_inventario (ejecucion_id,almacen_id,fecha);
CREATE INDEX ix_movimiento_inventario_2 ON movimiento_inventario (pedido_ejecucion_id);
CREATE INDEX ix_incidencia_1 ON incidencia (ejecucion_id,tipo,estado);
CREATE INDEX ix_incidencia_2 ON incidencia (ejecucion_id,fecha_inicio);
CREATE INDEX ix_incidencia_3 ON incidencia (vehiculo_id);
CREATE INDEX ix_reasignacion_1 ON reasignacion (pedido_ejecucion_id);
CREATE INDEX ix_reasignacion_2 ON reasignacion (incidencia_id);
CREATE INDEX ix_evento_1 ON evento (ejecucion_id,fecha,secuencia);
CREATE INDEX ix_evento_2 ON evento (ejecucion_id,tipo_evento);

