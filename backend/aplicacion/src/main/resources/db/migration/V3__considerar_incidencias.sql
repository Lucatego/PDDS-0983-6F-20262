-- La consideración de incidencias históricas/programadas es independiente de generar averías aleatorias.
ALTER TABLE configuracion_ejecucion
    ADD COLUMN considerar_incidencias BOOLEAN NOT NULL DEFAULT FALSE;
