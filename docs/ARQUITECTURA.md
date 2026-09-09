# Arquitectura y uso desde Java

## Flujo

1. `Options` valida parámetros y formatos.
2. `OutputBundle` comprueba rutas y reserva archivos temporales.
3. `ConversionJob` copia la entrada exacta y calcula su SHA-256. Convierte esa copia.
4. Opcionalmente, `MarcRepairService` normaliza UTF-16/BOM, interpreta marcadores de subcampo explícitamente solicitados y reconstruye ISO2709.
5. `MarcInput` lee registros secuencialmente; Marc4j interpreta ISO2709 y un lector StAX seguro interpreta MARCXML.
6. El identificador elegido debe existir y ser único, o se genera un 001 determinista si se solicita.
7. Se conserva el registro normalizado y `LcEngine` ejecuta el preprocesamiento y transformación oficiales.
8. Jena lee el RDF/XML obtenido. Opcionalmente, `SourcePreserver` añade todos los campos originales como RDF local.
9. Se escribe el formato solicitado, se verifica sintaxis si se pidió y se publican los archivos con reportes y manifiesto.

## Clases

| Clase | Responsabilidad |
|---|---|
| `Main` | JAR ejecutable, ayuda y códigos de salida. |
| `Options` | Parámetros y valores predeterminados. |
| `ConversionJob` | Conversión de catálogo, duplicados, reportes y serialización. |
| `LcEngine` | API pública del motor oficial, compilación y ejecución XSLT. |
| `MarcInput` | Entrada MARCXML/ISO2709 y serialización MARCXML. |
| `Xml` | SAX seguro, sin entidades externas ni DTD. |
| `SourcePreserver` | Representación RDF local de todos los campos del registro. |
| `OutputBundle` | Temporales, comprobación de rutas y publicación sin sobrescribir. |
| `MarcRepairService` | Reparación estructural y de codificación; adaptada del proyecto anterior. |

## Reutilizar la biblioteca

Instala el proyecto en tu repositorio Maven local:

```bash
mvn clean install
```

Dependencia:

```xml
<dependency>
  <groupId>mx.ucol</groupId>
  <artifactId>marc2bf</artifactId>
  <version>2.0.0</version>
</dependency>
```

La API de bajo nivel recibe bytes MARCXML y devuelve RDF/XML:

```java
import mx.ucol.marc2bf.LcEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

var engine = new LcEngine();
var parameters = Map.<String, Object>of(
    "baseuri", "https://bibliotecas.ucol.mx/bibframe/",
    "idfield", "001",
    "localfields", false,
    "bcp47inferrence", true,
    "pGenerationDatestamp", "2026-09-09T00:00:00Z"
);
byte[] result = engine.convert(
    Files.readAllBytes(Path.of("catalogo.xml")),
    parameters,
    true // preprocesamiento oficial
);
Files.write(Path.of("catalogo.rdf"), result);
```

Usa valores `Boolean` para parámetros booleanos, no las cadenas `"false"` o `"true"`: en XPath una cadena no vacía puede evaluarse como verdadera. La API ejecuta las hojas; los respaldos, la validación de identificadores y la detección de colisiones pertenecen al comando de catálogo, no a esta API de bajo nivel.

`Templates` se compila una vez por motor; cada conversión crea transformadores nuevos. No compartas modelos Jena mutables entre hilos. El comando procesa registros secuencialmente.

## Memoria y archivos

N-Triples usa un escritor RDF común para todo el catálogo y preserva el ámbito de los nodos en blanco. Los demás formatos acumulan el grafo en RAM para serializarlo. La validación de N-Triples no materializa un segundo grafo. Los conjuntos de identificadores y URI principales crecen con la cantidad de registros. El reparador usa buffers del archivo completo.

Los errores detectados durante lectura o conversión eliminan los temporales y no publican salidas. La publicación de varios archivos no constituye una transacción de filesystem resistente a cortes de energía; el manifiesto y sus hashes permiten verificar los resultados. No se modifican los archivos de entrada.
