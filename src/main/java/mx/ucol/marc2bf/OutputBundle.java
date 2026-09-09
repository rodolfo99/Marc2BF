package mx.ucol.marc2bf;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Escribe primero en temporales; publica únicamente después de convertir y validar. */
final class OutputBundle implements AutoCloseable {
    private final Map<Path, Path> pending = new LinkedHashMap<>();
    private final Set<Path> destinations = new HashSet<>();
    private final Path input;

    OutputBundle(Path input) throws IOException { this.input = input.toRealPath(); }

    Path stage(Path destination) throws IOException {
        destination = destination.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        Path canonical = destination.getParent().toRealPath().resolve(destination.getFileName());
        if (canonical.equals(input) || !destinations.add(canonical)) throw new IOException("Las rutas de entrada y salida deben ser distintas: " + destination);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("El archivo ya existe; elige otra salida: " + destination);
        Path temporary = Files.createTempFile(destination.getParent(), ".marc2bf-", ".tmp");
        pending.put(destination, temporary);
        return temporary;
    }

    void publish() throws IOException {
        var published = new ArrayList<Path>();
        try {
            for (var entry : pending.entrySet()) {
                // Sin REPLACE_EXISTING: tampoco sobrescribir archivos creados durante la conversión.
                Files.move(entry.getValue(), entry.getKey());
                published.add(entry.getKey());
            }
            pending.clear();
        } catch (IOException e) {
            for (Path path : published) {
                try { Files.deleteIfExists(path); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            throw e;
        }
    }
    @Override public void close() throws IOException {
        IOException failure = null;
        for (Path path : pending.values()) {
            try { Files.deleteIfExists(path); }
            catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        if (failure != null) throw failure;
    }
}
