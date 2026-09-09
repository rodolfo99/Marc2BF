# Componentes de terceros

El código propio de Marc2BF usa MIT. Esta licencia no sustituye las licencias de los componentes incorporados.

| Componente | Versión/origen | Licencia y fuente |
|---|---|---|
| Library of Congress `marc2bibframe2` | Commit `ed9abb038214474e8fc8ba4035d01c42fe0246de` | CC0-1.0; [texto íntegro local](vendor/lc/LICENSE), [repositorio](https://github.com/lcnetdev/marc2bibframe2). |
| Reparador MARC | Adaptado de `rodolfo99/ConverrsorMarc21toBibframe`, commit `8afcd1e506eb3aa9e8f970754117929706fc782e` | MIT; [repositorio original](https://github.com/rodolfo99/ConverrsorMarc21toBibframe). Se conserva el texto MIT en `LICENSE`. |
| Marc4j | 2.9.6 | LGPL-2.1; [fuente y licencia](https://github.com/marc4j/marc4j). |
| Saxon-HE | 12.5 | MPL-2.0; [fuente y licencia](https://github.com/Saxonica/Saxon-HE). |
| Apache Jena | 5.4.0 | Apache-2.0; [fuente y licencia](https://github.com/apache/jena). |
| SLF4J | 2.0.17 | MIT; [fuente y licencia](https://github.com/qos-ch/slf4j). |
| JUnit | 5.12.2, solo pruebas | EPL-2.0; [fuente y licencia](https://github.com/junit-team/junit5). |
| lxml | 6.1.1, solo paridad | BSD-3-Clause y licencias de libxml2/libxslt; [fuente](https://github.com/lxml/lxml). |
| RDFLib | 7.6.0, solo paridad | BSD-3-Clause; [fuente](https://github.com/RDFLib/rdflib). |

Las dependencias transitivas conservan sus avisos. El POM permite reconstruir y sustituir dependencias, incluido Marc4j; no se modifica su código. Puedes inspeccionar las dependencias exactas con `mvn dependency:tree`. Las hojas, tablas y fixtures de LC permanecen sin modificaciones.

El código del reparador fue adaptado para rechazar recodificaciones inválidas y para hacer optativa la interpretación de marcadores `^`. Los ejemplos heredados proceden del mismo repositorio del conversor anterior.
