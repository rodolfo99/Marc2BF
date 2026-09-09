package mx.ucol.marc2bf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Repairs MARC21/ISO2709 files that were accidentally exported/stored as UTF-16
 * or with textual subfield markers such as ^a, ^b, etc.
 *
 * <p>The repair pipeline:</p>
 * <ol>
 *   <li>Detect UTF-16LE/UTF-16BE (with BOM, or heuristically without BOM).</li>
 *   <li>Convert to UTF-8 and remove UTF-8 BOM.</li>
 *   <li>Convert ^a/^b/... markers to the MARC subfield delimiter 0x1F.</li>
 *   <li>Rebuild every ISO2709 directory entry using real UTF-8 byte lengths.</li>
 *   <li>Recalculate Leader record length and base address.</li>
 *   <li>Set Leader/09 to 'a' (Unicode) and Leader/20-23 to 4500.</li>
 *   <li>Validate the rebuilt ISO2709 structure.</li>
 * </ol>
 */
public final class MarcRepairService {
    private static final byte SUBFIELD_DELIMITER = 0x1F;
    private static final byte FIELD_TERMINATOR = 0x1E;
    private static final byte RECORD_TERMINATOR = 0x1D;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    private MarcRepairService() {
    }

    /** Repairs input and writes a UTF-8 ISO2709 file. */
    public static RepairResult repair(Path input, Path output) throws IOException {
        return repair(input, output, true);
    }

    /** Spanish alias kept for convenience. */
    public static RepairResult reparar(Path entrada, Path salida) throws IOException {
        return repair(entrada, salida, true);
    }

    /**
     * Repairs input and writes a UTF-8 ISO2709 file.
     *
     * @param convertCaretSubfields if true, converts ^a/^b/^0... into 0x1F + code
     */
    public static RepairResult repair(Path input, Path output, boolean convertCaretSubfields) throws IOException {
        if (input == null) throw new IllegalArgumentException("La entrada no puede ser null.");
        if (output == null) throw new IllegalArgumentException("La salida no puede ser null.");

        Path absoluteInput = input.toAbsolutePath().normalize();
        Path absoluteOutput = output.toAbsolutePath().normalize();

        if (!Files.isRegularFile(absoluteInput) || !Files.isReadable(absoluteInput)) {
            throw new IOException("No se puede leer el archivo MARC: " + absoluteInput);
        }
        if (absoluteInput.equals(absoluteOutput)) {
            throw new IllegalArgumentException("Entrada y salida no pueden ser el mismo archivo.");
        }

        byte[] original = Files.readAllBytes(absoluteInput);
        long originalSize = original.length;

        NormalizationResult normalization = normalizeEncoding(original);
        byte[] data = normalization.data();
        decodeStrict(data, StandardCharsets.UTF_8);

        boolean utf8BomRemoved = false;
        if (startsWith(data, UTF8_BOM)) {
            data = Arrays.copyOfRange(data, UTF8_BOM.length, data.length);
            utf8BomRemoved = true;
        }

        int caretConverted = 0;
        if (convertCaretSubfields) {
            CaretResult caretResult = convertCaretSubfields(data);
            data = caretResult.data();
            caretConverted = caretResult.replacements();
        }

        long subfieldDelimiters = countByte(data, SUBFIELD_DELIMITER);
        long fieldTerminators = countByte(data, FIELD_TERMINATOR);
        long recordTerminators = countByte(data, RECORD_TERMINATOR);

        ReconstructionResult reconstruction = rebuildCatalog(data);
        byte[] repaired = reconstruction.data();
        int validated = validateCatalog(repaired);

        if (validated != reconstruction.recordCount()) {
            throw new IOException("Se reconstruyeron " + reconstruction.recordCount()
                    + " registros, pero se validaron " + validated + ".");
        }

        if (absoluteOutput.getParent() != null) Files.createDirectories(absoluteOutput.getParent());
        Files.write(absoluteOutput, repaired);

        return new RepairResult(
                absoluteInput,
                absoluteOutput,
                normalization.description(),
                utf8BomRemoved,
                caretConverted,
                reconstruction.recordCount(),
                validated,
                originalSize,
                repaired.length,
                subfieldDelimiters,
                fieldTerminators,
                recordTerminators
        );
    }

    /** Spanish alias with caret option. */
    public static RepairResult reparar(Path entrada, Path salida, boolean convertirCaret) throws IOException {
        return repair(entrada, salida, convertirCaret);
    }

    private static NormalizationResult normalizeEncoding(byte[] data) throws IOException {
        if (startsWith(data, UTF16LE_BOM) || startsWith(data, UTF16BE_BOM)) {
            String description = startsWith(data, UTF16LE_BOM)
                    ? "UTF-16LE con BOM -> UTF-8"
                    : "UTF-16BE con BOM -> UTF-8";
            String text = decodeStrict(data, StandardCharsets.UTF_16);
            return new NormalizationResult(text.getBytes(StandardCharsets.UTF_8), description);
        }

        if (startsWith(data, UTF8_BOM)) {
            return new NormalizationResult(
                    Arrays.copyOfRange(data, UTF8_BOM.length, data.length),
                    "UTF-8 con BOM -> UTF-8 sin BOM"
            );
        }

        String detected = detectUtf16WithoutBom(data);
        if (detected != null) {
            Charset charset = Charset.forName(detected);
            String text = decodeStrict(data, charset);
            return new NormalizationResult(text.getBytes(StandardCharsets.UTF_8), detected + " sin BOM -> UTF-8");
        }

        decodeStrict(data, StandardCharsets.UTF_8);
        for (byte value : data) {
            if (value == 0x1B) throw new IOException("Se encontraron escapes MARC-8; usa la lectura normal sin --repair.");
        }
        return new NormalizationResult(data, "UTF-8 validado, sin recodificación");
    }

    private static String decodeStrict(byte[] data, Charset charset) throws IOException {
        try {
            return charset.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(data)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IOException("Codificación inválida para reparación " + charset + "; no se sustituyeron caracteres.", e);
        }
    }

    private static String detectUtf16WithoutBom(byte[] data) {
        int sampleLength = Math.min(data.length, 4096);
        if (sampleLength < 20) return null;

        int evenCount = 0, oddCount = 0, evenZeros = 0, oddZeros = 0;
        for (int i = 0; i < sampleLength; i++) {
            if ((i & 1) == 0) {
                evenCount++;
                if (data[i] == 0) evenZeros++;
            } else {
                oddCount++;
                if (data[i] == 0) oddZeros++;
            }
        }

        double evenRatio = evenCount == 0 ? 0 : (double) evenZeros / evenCount;
        double oddRatio = oddCount == 0 ? 0 : (double) oddZeros / oddCount;

        if (oddRatio > 0.25 && evenRatio < 0.10) return "UTF-16LE";
        if (evenRatio > 0.25 && oddRatio < 0.10) return "UTF-16BE";
        return null;
    }

    private static CaretResult convertCaretSubfields(byte[] data) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length);
        int replacements = 0;

        for (int i = 0; i < data.length; i++) {
            byte current = data[i];
            if (current == '^' && i + 1 < data.length && isSubfieldCode(data[i + 1])) {
                output.write(SUBFIELD_DELIMITER);
                output.write(data[i + 1]);
                i++;
                replacements++;
            } else {
                output.write(current);
            }
        }
        return new CaretResult(output.toByteArray(), replacements);
    }

    private static boolean isSubfieldCode(byte value) {
        int c = value & 0xFF;
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static ReconstructionResult rebuildCatalog(byte[] data) throws IOException {
        List<byte[]> records = splitRecords(data);
        if (records.isEmpty()) throw new IOException("No se encontraron registros terminados por 0x1D.");

        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length);
        int count = 0;
        for (byte[] record : records) {
            count++;
            output.writeBytes(rebuildRecord(record, count));
            if (count % 100 == 0) System.out.printf("Reparados: %,d registros%n", count);
        }
        return new ReconstructionResult(output.toByteArray(), count);
    }

    private static List<byte[]> splitRecords(byte[] data) throws IOException {
        List<byte[]> records = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == RECORD_TERMINATOR) {
                byte[] record = Arrays.copyOfRange(data, start, i);
                record = trimInterRecordLineBreaks(record);
                if (record.length > 0) records.add(record);
                start = i + 1;
            }
        }

        if (start < data.length) {
            byte[] tail = Arrays.copyOfRange(data, start, data.length);
            if (!onlyWhitespace(tail)) {
                throw new IOException("Hay " + tail.length + " bytes después del último terminador 0x1D.");
            }
        }
        return records;
    }

    private static byte[] rebuildRecord(byte[] raw, int number) throws IOException {
        if (raw.length < 24) throw new IOException("Registro " + number + ": tiene menos de 24 bytes.");

        byte[] leader = Arrays.copyOfRange(raw, 0, 24);
        if (!isAsciiDigits(leader, 0, 5)) {
            throw new IOException("Registro " + number + ": Leader inválido; longitud inicial no numérica: "
                    + printable(leader, 0, 5));
        }

        int directoryEnd = indexOf(raw, FIELD_TERMINATOR, 24);
        if (directoryEnd < 0) throw new IOException("Registro " + number + ": falta 0x1E al final del Directory.");

        byte[] oldDirectory = Arrays.copyOfRange(raw, 24, directoryEnd);
        if (oldDirectory.length == 0 || oldDirectory.length % 12 != 0) {
            throw new IOException("Registro " + number + ": Directory de " + oldDirectory.length
                    + " bytes; debe ser múltiplo de 12.");
        }

        List<byte[]> tags = new ArrayList<>();
        for (int pos = 0; pos < oldDirectory.length; pos += 12) {
            byte[] tag = Arrays.copyOfRange(oldDirectory, pos, pos + 3);
            if (!validTag(tag)) throw new IOException("Registro " + number + ": tag inválido en Directory: " + Arrays.toString(tag));
            tags.add(tag);
        }

        byte[] dataFields = Arrays.copyOfRange(raw, directoryEnd + 1, raw.length);
        List<byte[]> fields = splitFields(dataFields, number);

        if (tags.size() != fields.size()) {
            throw new IOException("Registro " + number + ": Directory declara " + tags.size()
                    + " campos, pero se localizaron " + fields.size() + " campos físicos terminados por 0x1E.");
        }

        ByteArrayOutputStream newDirectory = new ByteArrayOutputStream();
        ByteArrayOutputStream newData = new ByteArrayOutputStream();
        int offset = 0;

        for (int i = 0; i < tags.size(); i++) {
            byte[] tag = tags.get(i);
            byte[] field = fields.get(i);
            int fieldLength = field.length + 1; // includes field terminator

            if (fieldLength > 9999) {
                throw new IOException("Registro " + number + ", campo " + new String(tag, StandardCharsets.US_ASCII)
                        + ": longitud " + fieldLength + " excede 4 dígitos.");
            }
            if (offset > 99999) throw new IOException("Registro " + number + ": offset " + offset + " excede 5 dígitos.");

            newDirectory.writeBytes(tag);
            newDirectory.writeBytes(String.format("%04d", fieldLength).getBytes(StandardCharsets.US_ASCII));
            newDirectory.writeBytes(String.format("%05d", offset).getBytes(StandardCharsets.US_ASCII));

            newData.writeBytes(field);
            newData.write(FIELD_TERMINATOR);
            offset += fieldLength;
        }

        leader[9] = 'a';          // Unicode/UTF-8
        leader[20] = '4';         // field length digits
        leader[21] = '5';         // field position digits
        leader[22] = '0';
        leader[23] = '0';

        int baseAddress = 24 + newDirectory.size() + 1;
        if (baseAddress > 99999) throw new IOException("Registro " + number + ": Base Address excede 5 dígitos.");
        putAsciiNumber(leader, 12, 5, baseAddress);

        putAsciiNumber(leader, 0, 5, 0);
        byte[] provisional = assembleRecord(leader, newDirectory.toByteArray(), newData.toByteArray());
        int recordLength = provisional.length;
        if (recordLength > 99999) throw new IOException("Registro " + number + ": longitud total excede 5 dígitos.");
        putAsciiNumber(leader, 0, 5, recordLength);

        byte[] result = assembleRecord(leader, newDirectory.toByteArray(), newData.toByteArray());
        if (result.length != recordLength) throw new IOException("Registro " + number + ": longitud recalculada inconsistente.");
        return result;
    }

    private static List<byte[]> splitFields(byte[] data, int recordNumber) throws IOException {
        List<byte[]> fields = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == FIELD_TERMINATOR) {
                fields.add(Arrays.copyOfRange(data, start, i));
                start = i + 1;
            }
        }
        if (start != data.length) {
            throw new IOException("Registro " + recordNumber + ": el último campo no termina en 0x1E; sobran "
                    + (data.length - start) + " bytes.");
        }
        return fields;
    }

    private static byte[] assembleRecord(byte[] leader, byte[] directory, byte[] fields) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(leader.length + directory.length + fields.length + 2);
        output.writeBytes(leader);
        output.writeBytes(directory);
        output.write(FIELD_TERMINATOR);
        output.writeBytes(fields);
        output.write(RECORD_TERMINATOR);
        return output.toByteArray();
    }

    /** Validates record length, base address, directory entries and field terminators. */
    public static int validateCatalog(byte[] data) throws IOException {
        int position = 0;
        int recordNumber = 0;

        while (position < data.length) {
            recordNumber++;
            if (position + 24 > data.length) throw new IOException("Registro " + recordNumber + ": Leader incompleto.");
            if (!isAsciiDigits(data, position, 5)) throw new IOException("Registro " + recordNumber + ": longitud no numérica.");

            int recordLength = parseAsciiNumber(data, position, 5);
            if (recordLength < 25) throw new IOException("Registro " + recordNumber + ": longitud inválida " + recordLength + ".");

            int end = position + recordLength;
            if (end > data.length) throw new IOException("Registro " + recordNumber + ": la longitud declarada rebasa el archivo.");
            if (data[end - 1] != RECORD_TERMINATOR) throw new IOException("Registro " + recordNumber + ": no termina con 0x1D.");
            if (!isAsciiDigits(data, position + 12, 5)) throw new IOException("Registro " + recordNumber + ": Base Address no numérico.");

            int baseAddress = parseAsciiNumber(data, position + 12, 5);
            if (baseAddress <= 24 || baseAddress >= recordLength) {
                throw new IOException("Registro " + recordNumber + ": Base Address " + baseAddress + " inválido.");
            }

            int directoryTerminator = position + baseAddress - 1;
            if (data[directoryTerminator] != FIELD_TERMINATOR) {
                throw new IOException("Registro " + recordNumber + ": Directory no termina en 0x1E.");
            }

            int directoryLength = baseAddress - 25;
            if (directoryLength % 12 != 0) throw new IOException("Registro " + recordNumber + ": Directory no es múltiplo de 12.");
            if (data[position + 9] != 'a') throw new IOException("Registro " + recordNumber + ": Leader/09 no es 'a'.");

            validateDirectory(data, position, recordLength, baseAddress, recordNumber);
            position = end;
        }

        if (position != data.length) throw new IOException("La validación no terminó exactamente al final del archivo.");
        return recordNumber;
    }

    private static void validateDirectory(byte[] data, int recordStart, int recordLength,
                                          int baseAddress, int recordNumber) throws IOException {
        int directoryStart = recordStart + 24;
        int directoryEnd = recordStart + baseAddress - 1;
        int dataStart = recordStart + baseAddress;
        int previousEnd = 0;

        for (int pos = directoryStart; pos < directoryEnd; pos += 12) {
            byte[] tag = Arrays.copyOfRange(data, pos, pos + 3);
            if (!validTag(tag)) throw new IOException("Registro " + recordNumber + ": tag inválido en Directory.");
            if (!isAsciiDigits(data, pos + 3, 4)) throw new IOException("Registro " + recordNumber + ": longitud de campo inválida.");
            if (!isAsciiDigits(data, pos + 7, 5)) throw new IOException("Registro " + recordNumber + ": offset inválido.");

            int fieldLength = parseAsciiNumber(data, pos + 3, 4);
            int offset = parseAsciiNumber(data, pos + 7, 5);
            if (offset != previousEnd) {
                throw new IOException("Registro " + recordNumber + ": offset inconsistente en "
                        + new String(tag, StandardCharsets.US_ASCII) + ".");
            }

            int fieldEnd = dataStart + offset + fieldLength;
            int recordEnd = recordStart + recordLength;
            if (fieldEnd > recordEnd - 1) throw new IOException("Registro " + recordNumber + ": campo rebasa el registro.");
            if (data[fieldEnd - 1] != FIELD_TERMINATOR) {
                throw new IOException("Registro " + recordNumber + ": campo "
                        + new String(tag, StandardCharsets.US_ASCII) + " no termina en 0x1E.");
            }
            previousEnd += fieldLength;
        }
    }

    private static byte[] trimInterRecordLineBreaks(byte[] data) {
        int start = 0;
        while (start < data.length && (data[start] == '\r' || data[start] == '\n')) start++;
        return Arrays.copyOfRange(data, start, data.length);
    }

    private static boolean onlyWhitespace(byte[] data) {
        for (byte b : data) {
            if (b != ' ' && b != '\r' && b != '\n' && b != '\t') return false;
        }
        return true;
    }

    private static boolean validTag(byte[] tag) {
        if (tag.length != 3) return false;
        for (byte b : tag) {
            int c = b & 0xFF;
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }

    private static int indexOf(byte[] data, byte value, int start) {
        for (int i = start; i < data.length; i++) if (data[i] == value) return i;
        return -1;
    }

    private static long countByte(byte[] data, byte value) {
        long count = 0;
        for (byte b : data) if (b == value) count++;
        return count;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static boolean isAsciiDigits(byte[] data, int offset, int length) {
        if (offset < 0 || offset + length > data.length) return false;
        for (int i = offset; i < offset + length; i++) if (data[i] < '0' || data[i] > '9') return false;
        return true;
    }

    private static int parseAsciiNumber(byte[] data, int offset, int length) {
        return Integer.parseInt(new String(data, offset, length, StandardCharsets.US_ASCII));
    }

    private static void putAsciiNumber(byte[] target, int offset, int length, int value) {
        String text = String.format("%0" + length + "d", value);
        if (text.length() != length) throw new IllegalArgumentException("El valor " + value + " no cabe en " + length + " dígitos.");
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, length);
    }

    private static String printable(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.ISO_8859_1);
    }

    private record NormalizationResult(byte[] data, String description) {}
    private record CaretResult(byte[] data, int replacements) {}
    private record ReconstructionResult(byte[] data, int recordCount) {}

    public record RepairResult(
            Path inputFile,
            Path outputFile,
            String encodingConversion,
            boolean utf8BomRemoved,
            int caretSubfieldsConverted,
            int recordsRebuilt,
            int recordsValidated,
            long originalSize,
            long finalSize,
            long subfieldDelimitersBeforeRebuild,
            long fieldTerminatorsBeforeRebuild,
            long recordTerminatorsBeforeRebuild
    ) {}
}
