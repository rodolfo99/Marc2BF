# Cobertura de conversión

## Referencia exacta

- Motor: [Library of Congress marc2bibframe2](https://github.com/lcnetdev/marc2bibframe2/tree/ed9abb038214474e8fc8ba4035d01c42fe0246de).
- Versión declarada en `variables.xsl`: 3.1.0.
- Especificaciones públicas: https://www.loc.gov/bibframe/mtbf/.
- Copia local: `vendor/lc/xsl`, `vendor/lc/spec` y `vendor/lc/test`.

Marc2BF ejecuta el mismo punto de entrada y sus inclusiones. No reemplaza las reglas con un `switch` simplificado de etiquetas. Una plantilla que LC deja comentada, como `ConvSpec-460-468-SeriesTreat.xsl`, permanece comentada: tener el archivo no implica que esté activo.

## Familias incluidas

| Familia | Implementación oficial incorporada |
|---|---|
| Leader | Tipos de recurso y metadatos administrativos. |
| 001–007 | Identificadores, fechas y descripción codificada. |
| 006/008 | Posiciones fijas, distinguiendo tipos de material. |
| 010–048 | Identificadores, catalogación, idiomas, fechas, cartografía y conjuntos musicales. |
| 050–088 | Clasificaciones, componentes y otras reglas específicas. |
| 1XX/7XX/8XX de nombres | Agentes, contribuciones, roles y relaciones nombre/título. |
| 200–247 | Títulos con las diferencias determinadas por etiqueta, indicadores y subcampos. |
| 240/X30 | Títulos uniformes y Hub/relaciones según las reglas actuales. |
| 250–270 | Edición, publicación y otros datos de producción. |
| 3XX | Descripción física, RDA, características técnicas, música y audiencia. |
| 490/510 y procesamiento de series | Menciones de serie, enlaces y recursos relacionados. |
| 5XX | Notas y estructuras especializadas. |
| 600–662 | Materias, subdivisiones, vocabularios y MADS/RDF. |
| 720/740–755 y 758 | Puntos de acceso y relaciones con recursos. |
| 760–788 | Relaciones bibliográficas según las reglas oficiales. |
| 841–887 | Reglas oficiales de localización/acceso y demás campos considerados. |
| 880 y subcampos de control | Escrituras alternativas, identificadores de autoridad, fuentes y enlaces. |
| Preprocess0, Process6 y Process8 | Separación de manifestaciones, series y actividades de publicación. |

Los rangos describen familias documentales, **no prometen que cada número del rango se convierta**. Las especificaciones de LC distinguen conversión, `nac` y `ignore` por elemento. Los datos MARC de autoridades, clasificación, comunidad y existencias independientes no son formatos de entrada de este conversor bibliográfico.

## Qué cambia frente al conversor Java anterior

| Antes | Ahora |
|---|---|
| Reglas propias con etiquetas agrupadas. | Motor oficial íntegro y versionado. |
| 006/007 conservados como cadenas locales. | Interpretación de posiciones que implementa LC. |
| 008 interpretado solo parcialmente. | Reglas oficiales por tipo de material. |
| 880 convertido en nota. | Procesamiento oficial como el campo asociado. |
| Materias reunidas en etiquetas de texto. | Estructuras de materias y componentes MADS/RDF. |
| 336 aplicado a Instance. | Aplicación oficial a Work. |
| 050 mezclaba $a y $b. | Componentes separados según LC. |
| 264 copyright como actividad propia. | Tratamiento oficial de copyrightDate. |
| Respaldo local solo al faltar una regla. | Copia exacta del archivo completo, MARCXML normalizado y extensión RDF opcional para todos los campos. |
| Contador “mapped” podía significar solo conservación. | Inventario sin porcentaje artificial de cobertura semántica. |

## BIBFRAME y preservación

El grafo oficial puede contener Hub, Work, Instance, Item, agentes, contribuciones, títulos, identificadores, clasificación, materias y recursos especializados. Su presencia depende del registro y de las reglas. La salida predeterminada no añade propiedades locales propias.

Con `--preserve-in-rdf` se añaden recursos del vocabulario local `marc:`:

- `Record`: Leader y campos del registro de origen.
- `Field`: etiqueta, posición, indicadores o valor de campo de control.
- `Subfield`: código, valor y posición; conserva repeticiones.

Esta representación está separada por sus URI `_source/SHA256/posición`. Sirve para trazabilidad y recuperación, no convierte semánticamente lo que LC no interpreta. La copia `.source.*` es la garantía de conservación exacta del archivo original, incluida su codificación.

## Límites heredados

El motor oficial no resuelve por sí solo todas las autoridades por Internet ni fusiona globalmente autores, obras o ejemplares entre catálogos. Reutiliza URI y aplica las reglas presentes en los datos y en sus tablas. Las limitaciones de puntuación, asociación de 880 y agrupación de ejemplares documentadas por LC siguen siendo relevantes. La equivalencia se refiere al motor fijado, no a todos los servicios internos de producción de la Biblioteca del Congreso.
