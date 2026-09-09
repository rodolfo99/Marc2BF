# Actualizar el motor de la Biblioteca del Congreso

Las reglas están fijadas por commit. No se actualizan durante una conversión ni dependen de una rama remota mutable. `vendor/lc/SHA256SUMS` permite comprobar el contenido de cada hoja y tabla usada.

Para preparar una actualización:

1. Revisa el historial y las notas de versión de https://github.com/lcnetdev/marc2bibframe2.
2. Obtén un checkout del commit concreto que deseas incorporar.
3. Sustituye `vendor/lc/xsl`, `vendor/lc/test` y `vendor/lc/spec` por las carpetas de ese checkout completo; no mezcles tablas de distintas versiones.
4. Actualiza también `vendor/lc/LICENSE`, `README.md`, `release-notes.md` y `upstream.properties`.
5. Regenera los checksums:

```bash
python3 scripts/check-lc.py --write
```

6. Actualiza la versión/commit citados en documentación y prueba de paridad.
7. Ejecuta `mvn clean verify` y `python3 scripts/verify-parity.py` con las dependencias Python indicadas en README.
8. Compara un conjunto representativo de tu catálogo, revisa las diferencias y conserva tanto el commit anterior como los informes.

Para comprobar la copia actual sin actualizar nada:

```bash
python3 scripts/check-lc.py
```

Actualizar los hashes confirma la integridad de la copia elegida; no demuestra por sí solo que sea una versión oficial. Registra siempre el commit verificado. Las extensiones locales deben vivir fuera de `vendor/lc/xsl` para poder comparar el motor original sin modificaciones.
