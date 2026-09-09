# Marc2BF

Conversor **MARC21 bibliográfico → BIBFRAME** en Java 21, con el **motor completo de la Biblioteca del Congreso** incorporado. Lee ISO2709 (`.mrc`, `.iso`, `.marc`) y MARCXML; genera RDF/XML, Turtle, N-Triples o JSON-LD.

El proyecto ejecuta las hojas y tablas originales de [`lcnetdev/marc2bibframe2`](https://github.com/lcnetdev/marc2bibframe2), versión declarada **3.1.0**, commit **`ed9abb038214474e8fc8ba4035d01c42fe0246de`**. Las reglas están incluidas en el repositorio y en el JAR: no se descargan durante la conversión.

**“Completo” significa que incorpora todo el motor oficial de esa revisión**, con sus subcampos, indicadores, vocabularios, preprocesamiento y limitaciones. No significa que todos los elementos MARC21 tengan una conversión semántica: LC marca algunos como `nac` o `ignore`. Marc2BF conserva siempre una copia exacta de la entrada y permite conservar todos los campos también como RDF local.

## Requisitos y compilación

- JDK 21 o posterior, con `java` y `javac`.
- Maven 3.9 o posterior.
- Internet para descargar las dependencias durante la primera compilación.

```bash
git clone https://github.com/rodolfo99/Marc2BF.git
cd Marc2BF
java -version
javac -version
mvn clean verify
```

El archivo ejecutable, con sus dependencias y reglas, queda en **`target/marc2bf.jar`**. Para ejecutarlo solo necesitas Java 21 o posterior; no necesitas instalar Saxon, Python, Jena ni `xsltproc` por separado.

## Convertir un catálogo

```bash
java -jar target/marc2bf.jar \
  examples/catalogo-ejemplo.mrc \
  salida/catalogo.rdf \
  --validate
```

También acepta MARCXML:

```bash
java -jar target/marc2bf.jar \
  examples/catalogo.xml \
  salida/catalogo.ttl \
  --base https://bibliotecas.ucol.mx/bibframe/ \
  --validate
```

Para un archivo SIABUC con UTF-16 o un directorio de longitudes incorrectas:

```bash
java -jar target/marc2bf.jar \
  fichas_siabuc.iso \
  salida/siabuc.rdf \
  --repair \
  --validate
```

Si utiliza literalmente `^a`, `^b`, etc. como separadores, usa **`--repair-carets`** en lugar de `--repair`. Esta opción interpreta esas secuencias como subcampos; no debe usarse si son texto literal de la ficha. La reparación acepta UTF-8/UTF-16 y reconstruye el directorio; para MARC-8 normal utiliza la lectura habitual sin reparación.

## Archivos producidos

Para `salida/catalogo.rdf`, se producen:

| Archivo | Contenido |
|---|---|
| `catalogo.rdf` | Grafo convertido mediante LC. |
| `catalogo.rdf.source.mrc` o `.source.xml` | Copia exacta, byte por byte, del archivo recibido. |
| `catalogo.rdf.marcxml` | Registros procesados, normalizados a MARCXML, antes del preprocesamiento LC. |
| `catalogo.rdf.report.csv` | Identificador, campos y triples por registro; cantidades de Work, Instance e Item. |
| `catalogo.rdf.fields.csv` | Inventario de etiquetas encontradas; no presume que todo su contenido se haya convertido. |
| `catalogo.rdf.manifest.properties` | Commit LC, parámetros, hashes SHA-256 y resultado de validación. |
| `catalogo.rdf.repaired.mrc` | Solo con reparación: ISO2709 reconstruido. |

Los archivos se preparan en temporales y se publican tras terminar la conversión. Un error impide publicar resultados parciales. **No se sobrescriben archivos existentes**: utiliza otra ruta de salida para una nueva ejecución.

## Identificadores y preservación

Por defecto, `001` debe existir y ser único dentro de la entrada. También se detectan colisiones de las URI principales producidas por LC. Si necesitas otro identificador:

```bash
java -jar target/marc2bf.jar catalogo.xml salida/catalogo.rdf --id-field 035a
```

Si faltan identificadores o están repetidos, puedes sustituir `001` por un identificador determinista basado en SHA-256 del archivo y posición del registro:

```bash
java -jar target/marc2bf.jar catalogo.mrc salida/catalogo.rdf --generate-ids
```

Con esta opción los identificadores cambian si cambia el archivo de entrada o el orden de los registros. El original se conserva intacto. Para un catálogo que se actualiza periódicamente conviene tener identificadores estables propios.

La salida predeterminada contiene el grafo oficial. Para añadir a ese grafo una representación local de **todos** los campos, indicadores, subcampos y posiciones —incluidos los ya convertidos—:

```bash
java -jar target/marc2bf.jar catalogo.mrc salida/catalogo.ttl --preserve-in-rdf
```

La extensión utiliza `https://bibliotecas.ucol.mx/vocab/marc/`; no presenta campos MARC sin interpretar como si fueran propiedades BIBFRAME oficiales. El respaldo del original se crea con o sin esta opción.

## Formatos y tamaño del catálogo

| Formato | Extensión | Ejecución |
|---|---|---|
| RDF/XML | `.rdf` | Acumula el grafo en memoria. |
| Turtle | `.ttl` | Acumula el grafo en memoria. |
| JSON-LD | `.jsonld` | Acumula el grafo en memoria. |
| N-Triples | `.nt` | Escribe por registro sin acumular el catálogo RDF completo. |

Para catálogos grandes:

```bash
java -Xmx2g -jar target/marc2bf.jar catalogo.mrc salida/catalogo.nt --validate
```

N-Triples mantiene en memoria un registro/grafo a la vez, más los conjuntos de identificadores usados para detectar duplicados. Puede repetir triples compartidos entre registros, lo cual no cambia el grafo RDF. La reparación sí carga el archivo original completo en memoria.

## Opciones principales

```bash
java -jar target/marc2bf.jar --help
java -jar target/marc2bf.jar --version
```

El preprocesamiento oficial de manifestaciones está **activado por defecto**. `--no-preprocess` permite comparar con una ejecución directa del XSLT principal. `--lc-localfields` activa los campos locales **de LC**, como `859`; no define automáticamente campos locales de SIABUC.

`--generation-date 2026-09-09T00:00:00Z` fija la fecha de generación para comparar ejecuciones. `--validate` verifica la sintaxis RDF por relectura: no es validación catalográfica ni certificación de conformidad con toda la ontología.

## Pruebas

```bash
mvn clean verify
```

Incluye pruebas de mapeos, formatos, ISO2709/MARCXML, preservación, duplicados, reparación, XML seguro y hashes del motor.

La comparación ejecuta **las hojas oficiales originales en disco mediante Saxon/s9api** y las contrasta con **el motor empaquetado en el JAR mediante JAXP**, sobre los fixtures originales de LC, con y sin preprocesamiento. Ambas rutas utilizan Saxon-HE 12.5:

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/parity-requirements.txt
.venv/bin/python scripts/verify-parity.py
```

El informe queda en `target/parity/report.json`. **La revisión inicial pasó 126 de 126 comparaciones**, correspondientes a 63 archivos y dos modos. Compara grafos por isomorfismo, no por orden de serialización ni por nombres arbitrarios de nodos en blanco. No elimina propiedades para forzar coincidencias. Véase [validación y alcance](docs/VALIDACION.md).

## Docker

Docker permite compilar y ejecutar el conversor sin instalar Java ni Maven directamente en tu computadora. Necesitas Docker con Docker Compose instalado y en funcionamiento. Los siguientes comandos están preparados para la terminal de Ubuntu/Linux.

### Uso con Docker Compose

1. Descarga el proyecto y entra en su carpeta:

   ```bash
   git clone https://github.com/rodolfo99/Marc2BF.git
   cd Marc2BF
   ```

   Si ya lo descargaste, entra en la carpeta donde están `Dockerfile` y `compose.yaml`.

2. Construye la imagen:

   ```bash
   docker compose build
   ```

   Docker descarga las imágenes y dependencias necesarias, compila el conversor y ejecuta las pruebas Java. La primera construcción necesita Internet y puede tardar varios minutos.

3. Convierte el archivo de ejemplo incluido:

   ```bash
   docker compose run --rm --user "$(id -u):$(id -g)" marc2bf
   ```

   El resultado queda en `salida/compose.rdf`, dentro de la carpeta del proyecto en tu computadora. También se generan los reportes y el respaldo del MARC original descritos en [Archivos producidos](#archivos-producidos).

4. Para convertir tus fichas de SIABUC, coloca `fichas_siabuc.iso` dentro de la carpeta `Marc2BF` y ejecuta:

   ```bash
   docker compose run --rm --user "$(id -u):$(id -g)" marc2bf \
     fichas_siabuc.iso \
     salida/siabuc.rdf \
     --repair \
     --validate
   ```

   Usa `--repair` cuando el archivo necesite reparación, por ejemplo, por UTF-16 o un directorio incorrecto. Para un MARC válido puedes omitirlo. `--validate` comprueba que el RDF generado pueda volver a leerse.

### Qué significan `id -u` e `id -g`

**Son comandos de Ubuntu/Linux que obtienen los identificadores de tu usuario y de tu grupo.** No necesitas crear ningún archivo con esos nombres. Copia esta parte tal como está:

```bash
--user "$(id -u):$(id -g)"
```

La terminal sustituye `$(id -u)` y `$(id -g)` por sus valores numéricos. Docker ejecuta el conversor con esos identificadores para que los archivos generados pertenezcan a tu usuario.

**Tu archivo MARC puede llamarse como quieras.** En el comando anterior, cambia `fichas_siabuc.iso` por el nombre real de tu archivo y `salida/siabuc.rdf` por la ruta de salida que prefieras. Si el nombre contiene espacios, escríbelo entre comillas, por ejemplo, `"mis fichas.iso"`.

El archivo `compose.yaml` comparte la carpeta del proyecto con `/data` dentro del contenedor. Por eso las rutas de entrada y salida del ejemplo corresponden a archivos de tu computadora. La opción `--rm` elimina el contenedor al terminar; los resultados permanecen en la carpeta compartida.

Para convertir otro archivo, repite el comando cambiando los nombres. **El conversor no sobrescribe resultados existentes:** utiliza una ruta de salida nueva en cada ejecución. Solo necesitas reconstruir la imagen si cambias el código o la configuración de construcción.

### Uso directo con Docker

También puedes construir y ejecutar la imagen sin Docker Compose, desde la carpeta del proyecto:

```bash
docker build -t marc2bf .
docker run --rm --network none \
  --user "$(id -u):$(id -g)" \
  -v "$PWD:/data" \
  marc2bf examples/catalogo-ejemplo.mrc salida/docker.rdf --validate
```

La opción `-v "$PWD:/data"` comparte la carpeta actual con el contenedor. La conversión se ejecuta sin acceso a la red, tanto en este comando como con el `compose.yaml` incluido.

## Documentación

- [Cobertura y diferencias con el conversor anterior](docs/COBERTURA.md).
- [Arquitectura y uso desde Java](docs/ARQUITECTURA.md).
- [Validación y límites](docs/VALIDACION.md).
- [Actualización del motor oficial](docs/ACTUALIZAR_LC.md).
- [Licencias de terceros](THIRD_PARTY_NOTICES.md).

## Licencias

El código propio se distribuye bajo [MIT](LICENSE). El motor oficial conserva su [CC0-1.0](vendor/lc/LICENSE). Las dependencias mantienen sus respectivas licencias. Marc2BF no es un producto oficial ni certificado por la Biblioteca del Congreso.
