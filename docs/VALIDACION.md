# Validación de Marc2BF 2.0.0

Fecha de verificación: 9 de septiembre de 2026.

## Integración Java

`mvn clean verify` compila el código Java 21, ejecuta las pruebas JUnit y crea el JAR con dependencias y recursos oficiales.

Las pruebas cubren:

- 336 aplicado a Work, componentes de clasificación 050 y copyright 264.
- Materias complejas, género/forma y títulos 880.
- Conservación de campos convertidos y no convertidos, indicadores, subcampos repetidos y posiciones.
- Equivalencia entre lectura ISO2709 y MARCXML.
- Equivalencia de RDF/XML, Turtle, N-Triples y JSON-LD con varios registros.
- Rechazo de identificadores duplicados y generación explícita de identificadores únicos.
- Protección de entrada y salidas existentes y limpieza tras un error.
- Rechazo de DTD/entidades externas.
- Reparación del ejemplo UTF-16 con marcadores `^`, conservando el original byte por byte.
- Rechazo de codificaciones inválidas en reparación.
- Integridad SHA-256 de los recursos oficiales.
- Separación de manifestaciones y consulta de tablas dentro de las propias hojas XSLT.

## Paridad con la distribución oficial

Se usan los **63 archivos XML** bajo `vendor/lc/test/data`, sin editar sus campos ni su contenido. Cada archivo se convierte con y sin el preprocesamiento oficial: **126 casos**.

| Ruta | Ejecución |
|---|---|
| Referencia | Saxon-HE 12.5, API s9api, hojas originales en disco y resolución normal de rutas. |
| Marc2BF | Saxon-HE 12.5, API JAXP, `LcEngine`, hojas y tablas empaquetadas dentro del JAR. |

La referencia no usa `LcEngine` ni su resolvedor. La comparación comprueba el grafo completo por isomorfismo: conserva todas las propiedades y literales; solo permite las diferencias irrelevantes de orden y nombres de nodos en blanco. La fecha de generación y los parámetros son idénticos.

Resultado: **126/126 grafos isomorfos**. El detalle de la ejecución inicial está en [parity-report.json](parity-report.json).

Esto verifica la integración y empaquetado del motor completo sobre ese corpus. **No demuestra que todo MARC21 esté convertido ni que todos los registros posibles estén libres de problemas**. Tampoco equivale a ejecutar las aserciones de todas las suites XSpec de LC: los fixtures oficiales se usan como corpus de paridad.

## Diferencias entre procesadores XSLT

Se exploró además libxslt como referencia. Las hojas oficiales actuales muestran diferencias entre procesadores en ciertos fixtures y una rama de 261 intenta navegar un result-tree-fragment directamente, lo cual libxslt rechaza. Por eso la paridad de esta distribución se define con el procesador fijado **Saxon-HE 12.5**, que también pertenece a la familia de procesadores usada por las pruebas XSpec oficiales.

El diagnóstico opcional se ejecuta con:

```bash
python3 scripts/verify-parity.py --reference libxslt
```

Este diagnóstico puede terminar con discrepancias o errores de la referencia; no se declara equivalencia universal con libxslt. No se modificaron las hojas oficiales para ocultar estas diferencias.

## Qué valida `--validate`

La opción relee el archivo de salida con Jena y verifica su sintaxis RDF. No ejecuta un perfil SHACL completo ni certifica corrección catalográfica, exhaustividad de subcampos, existencia remota de URI o coherencia de todos los valores tipados. Algunos fixtures oficiales contienen valores deliberadamente incompletos y pueden producir avisos del parser.

La copia exacta de la entrada, el MARCXML normalizado y el manifiesto permiten auditar y repetir una conversión. No se utilizó un catálogo privado real de SIABUC en esta verificación; se usaron ejemplos y fixtures incluidos en el proyecto.

## Docker y CI

Se proporciona Dockerfile y flujo de GitHub Actions. La verificación local se realizó con Java/Maven y el JAR; no se presenta como una prueba de ejecución del contenedor. El resultado remoto de Actions debe consultarse en la pestaña Actions del repositorio.
