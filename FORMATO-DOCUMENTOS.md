# FORMATO-DOCUMENTOS.md — Formato de los entregables Word (.docx)

Reglas de formato que deben cumplir **todos los documentos .docx** del Equipo 6F (1INF54-0983, PUCP 2026-2).
Salieron de la revisión del Documento de Estructura de Datos (`24.dis.estructura.datos`) hasta la sección 6.2
y se aplican igual a los documentos futuros. Si el usuario pide algo distinto en un encargo concreto, prevalece
lo que pida el usuario.

> Fuente única: el contenido vive en el `.md` y el `.docx` se genera desde él (ver `.claude/agents/redactor.md`).
> Este archivo define solo **cómo se ve** el `.docx`, no qué dice.

## 1. Reglas obligatorias

| Elemento | Regla |
|---|---|
| Fuente | **Times New Roman** en todo el documento (texto, tablas, títulos, pie de página). Ninguna otra fuente. |
| Título nivel 1 (H1) | 14 pt, negrita, negro. Numerado y en mayúsculas: `1. INTRODUCCIÓN`. |
| Título nivel 2 (H2) | 13 pt, negrita, negro. Numerado: `1.1. Propósito`. |
| Título nivel 3 (H3) | 11 pt, negrita, negro. Numerado: `6.2.1. cat_escenario`. |
| Contenido (párrafos, viñetas, notas) | 11 pt. |
| Tablas | **Todo en 10 pt** (encabezado y cuerpo). |
| Alineación | **Justificado** en todo: títulos, párrafos, viñetas y celdas de tablas. |
| Encabezado de tabla | **Negrita** sobre gris **#D9D9D9**, en la primera fila de cada tabla. |

Excepciones que se mantienen centradas (no se justifican): portada, figuras y su leyenda. El índice queda
alineado a la izquierda con número de página a la derecha.

## 2. Detalle de las tablas

- Encabezado: primera fila, negrita, relleno `D9D9D9` (el mismo gris en todas las tablas; no usar `CCCCCC`),
  fila repetida al cambiar de página (`tblHeader`) y sin partirse entre páginas (`cantSplit`).
- Bordes: exterior e interior sencillos, 0,5 pt; las tablas del cuerpo usan borde de celda gris `808080`.
- Márgenes de celda: 70 twips a los lados y 40 arriba y abajo; sin espacio antes ni después del párrafo
  dentro de la celda.
- Ancho total: 9026 twips (todo el ancho útil de la página). Diseño fijo (`tblLayout fixed`): los anchos salen de
  `tblGrid`, no de cada celda.
- Negrita en el cuerpo de una tabla solo donde el texto lo pida (p. ej. una palabra clave); no negrita en filas completas.
- **Tablas del diccionario de datos** (sección 6.x, 7 columnas: Columna · Tipo · Nulo · Clave · Defecto ·
  Restricción · Descripción): usar siempre la misma grilla, para que todas las tablas se vean iguales y no se
  parta `VARCHAR(20)` ni los códigos de las restricciones:

  `1700 · 1560 · 580 · 660 · 810 · 1860 · 1856` (suma 9026).

  Un identificador largo (`minuto_inicio_turno_retorno`) puede partirse en la columna Columna; es aceptable.
- Cada tabla va después de su texto introductorio y lleva debajo, en este orden y cuando aplique:
  `Restricciones:` y `Trazabilidad:`.

## 3. Texto y estructura

- Párrafo de cuerpo: espacio posterior de 6 pt, interlineado sencillo.
- Etiquetas de párrafo en negrita seguidas de dos puntos y texto normal: **Propósito:**, **Restricciones:**,
  **Trazabilidad:** (LE, RN, CU, Q&A). En la leyenda de figura: **Figura N.** y la descripción.
- Viñetas: lista con sangría de 720 twips y colgante de 360, espacio posterior de 3 pt, mismo tamaño que el cuerpo (11 pt).
  Los niveles siguientes usan ○ y ■.
- Códigos, nombres de tabla/columna y valores literales van en el mismo texto (Times New Roman); no cambiar de fuente ni usar monoespaciada.
- Estilo de redacción: español formal, como los demás documentos del curso.

## 4. Estructura de un documento

1. **Portada** (centrada, 14 pt): PONTIFICIA UNIVERSIDAD CATÓLICA DEL PERÚ (negrita) · FACULTAD DE CIENCIAS E INGENIERÍA ·
   ESPECIALIDAD DE INGENIERÍA INFORMÁTICA · escudo · nombre del curso `[1INF54-0983]` · nombre del documento · versión ·
   Equipo, Horario e Integrantes (justificado, 11 pt) · ciudad y fecha **con año** (p. ej. `Lima, 30 de setiembre de 2026`).
2. **Historial de versiones** (título 14 pt negrita, sin numerar, en página aparte): tabla Fecha · Versión · Descripción · Autor(es).
3. **Índice** (título 14 pt negrita, sin numerar): entradas de 10 pt, nivel 1 en negrita y nivel 2 con sangría de 360 twips,
   tabulador derecho en 9026 con el número de página.
4. **Cuerpo**: secciones numeradas desde `1. INTRODUCCIÓN` (Propósito, Alcance, Definiciones, Referencias documentales).
5. **Referencias** al final.

Página: A4 vertical, márgenes de 2,54 cm. Pie de página a la derecha: `Página X de Y` (9 pt).
Nombre del archivo: `NN.fase.tema.vNN.docx` (p. ej. `24.dis.estructura.datos.v01.docx`), igual que los documentos de `context/`.

## 5. Cómo aplicarlo técnicamente (docx)

Cargar la skill `anthropic-skills:docx`. Para que el formato quede consistente y no se rompa al editar en Word:

1. Fijar los valores **en los estilos** (`styles.xml`): `docDefaults` en Times New Roman 11 pt; `Heading1/2/3` en 14/13/11 pt,
   negrita, color `000000`, justificados. Reemplazar cualquier `Arial` o `Georgia` heredado.
2. Fijar además los valores **en cada run y en la marca de párrafo** (`pPr/rPr`): fuente y tamaño explícitos. Los documentos
   importados desde Google Docs traen runs con tamaños sueltos (9,5 / 10,5 pt) y fuentes ajenas (Cardo, Gungsuh) que hay que normalizar.
3. Respetar el orden de los hijos de `rPr` y `pPr` que exige el esquema (si no, `validate.py` falla o Word repara el archivo).
4. Al cambiar anchos de columna, actualizar `tblGrid` y también la copia dentro de `tblGridChange`.
5. Validar con `scripts/office/validate.py` (opción `--original` al editar), convertir a PDF con LibreOffice en una carpeta
   temporal **fuera del repo** y revisar las páginas con tablas, listas y la figura.
6. Comprobar por código: ningún run con fuente distinta de Times New Roman; tamaño 14/13/11 en títulos, 11 en cuerpo y 10 en tablas;
   todos los encabezados de tabla en negrita con `D9D9D9`; todo justificado salvo las excepciones de la sección 1.
7. No editar un `.docx` que esté abierto en Word (aparece un `~$…docx` en la carpeta): pedir al usuario que lo cierre o guardar la copia con otro nombre.

## 6. Pendientes conocidos

- El **índice es texto fijo**, no un campo de Word: sus números de página hay que revisarlos en Word al cerrar cada versión
  (o convertirlo a tabla de contenido automática si el equipo lo aprueba).
- LibreOffice y Word paginan con una página de diferencia en algunas secciones; la verificación final de páginas se hace en Word.
- Las secciones **6.3 en adelante** de `24.dis.estructura.datos` aún no se reformatearon: tienen los anchos de columna antiguos
  (se parte `VARCHAR(12)` en la columna Tipo) y tamaños sueltos en el cuerpo.
- La justificación en columnas estrechas deja huecos entre palabras; es consecuencia de la regla «todo justificado» y se aceptó.
