package com.videosharing.videosharingspringboot.service;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MediaFileValidator {
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final int MAX_MP4_BOXES = 1024;
    private static final int COPY_BUFFER_SIZE = 16 * 1024;

    private MediaFileValidator() {
    }

    static StagedMedia stage(MultipartFile file, Path directory, MediaKind kind) throws IOException {
        if (file == null || file.isEmpty()) {
            throw invalid("Uploaded media file must not be empty.");
        }
        if (file.getSize() > kind.maxBytes()) {
            throw invalid("Uploaded media exceeds the " + kind.limitLabel() + " limit.");
        }
        Files.createDirectories(directory);
        Path staged = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(staged, StandardOpenOption.TRUNCATE_EXISTING)) {
                copyAndScan(input, output, kind);
            }
            MediaFormat format = detectStructure(staged, kind);
            validateMetadata(file, format);
            return new StagedMedia(staged, format);
        } catch (IOException | RuntimeException exception) {
            delete(staged, exception);
            throw exception;
        }
    }

    static MediaFormat inspect(Path file, MediaKind kind) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw invalid("Pending media is missing or not a regular file.");
        }
        try (InputStream input = Files.newInputStream(file)) {
            scan(input, kind);
        }
        return detectStructure(file, kind);
    }

    static void moveAtomically(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void copyAndScan(InputStream input, OutputStream output, MediaKind kind) throws IOException {
        ActiveContentScanner scanner = new ActiveContentScanner();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > kind.maxBytes()) {
                throw invalid("Uploaded media exceeds the " + kind.limitLabel() + " limit.");
            }
            scanner.accept(buffer, count);
            output.write(buffer, 0, count);
        }
        if (total == 0) {
            throw invalid("Uploaded media file must not be empty.");
        }
    }

    private static void scan(InputStream input, MediaKind kind) throws IOException {
        ActiveContentScanner scanner = new ActiveContentScanner();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > kind.maxBytes()) {
                throw invalid("Pending media exceeds the " + kind.limitLabel() + " limit.");
            }
            scanner.accept(buffer, count);
        }
        if (total == 0) {
            throw invalid("Pending media file must not be empty.");
        }
    }

    private static MediaFormat detectStructure(Path file, MediaKind kind) throws IOException {
        long size = Files.size(file);
        byte[] prefix = readPrefix(file, 12);
        if (kind == MediaKind.VIDEO) {
            if (matches(prefix, 4, ascii("ftyp"))) {
                validateMp4(file, size);
                return MediaFormat.MP4;
            }
            if (matches(prefix, 0, bytes(0x1A, 0x45, 0xDF, 0xA3))) {
                validateWebm(file, size);
                return MediaFormat.WEBM;
            }
            if (matches(prefix, 0, ascii("OggS"))) {
                validateOgg(file, size);
                return MediaFormat.OGV;
            }
            throw invalid("Unsupported video file structure.");
        }

        if (matches(prefix, 0, bytes(0xFF, 0xD8, 0xFF))) {
            validateImage(file, "JPEG");
            return MediaFormat.JPEG;
        }
        if (matches(prefix, 0, bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
            validateImage(file, "PNG");
            return MediaFormat.PNG;
        }
        if (matches(prefix, 0, ascii("RIFF")) && matches(prefix, 8, ascii("WEBP"))) {
            validateWebp(file, size);
            validateImage(file, "WEBP");
            return MediaFormat.WEBP;
        }
        throw invalid("Unsupported thumbnail file structure.");
    }

    private static void validateMp4(Path file, long fileSize) throws IOException {
        boolean foundFtyp = false;
        boolean foundMoov = false;
        boolean foundMdat = false;
        long position = 0;
        int boxes = 0;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (position < fileSize) {
                if (++boxes > MAX_MP4_BOXES) {
                    throw invalid("MP4 contains too many top-level boxes.");
                }
                ByteBuffer header = read(channel, position, 8).order(ByteOrder.BIG_ENDIAN);
                long boxSize = Integer.toUnsignedLong(header.getInt());
                String type = fourCc(header);
                int headerSize = 8;
                if (boxSize == 1) {
                    ByteBuffer extended = read(channel, position + 8, 8).order(ByteOrder.BIG_ENDIAN);
                    boxSize = extended.getLong();
                    headerSize = 16;
                } else if (boxSize == 0) {
                    boxSize = fileSize - position;
                }
                if (boxSize < headerSize || boxSize > fileSize - position) {
                    throw invalid("MP4 box length is truncated or invalid.");
                }
                if (position == 0 && (!"ftyp".equals(type) || boxSize < 16)) {
                    throw invalid("MP4 must begin with a complete ftyp box.");
                }
                foundFtyp |= "ftyp".equals(type);
                foundMoov |= "moov".equals(type);
                foundMdat |= "mdat".equals(type);
                position += boxSize;
            }
        }
        if (position != fileSize || !foundFtyp || !foundMoov || !foundMdat) {
            throw invalid("MP4 requires complete ftyp, moov, and mdat boxes.");
        }
    }

    private static void validateWebm(Path file, long fileSize) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            if (!matches(readBytes(channel, 0, 4), 0, bytes(0x1A, 0x45, 0xDF, 0xA3))) {
                throw invalid("WebM EBML header is missing.");
            }
            VariableInteger ebmlSize = readVariableInteger(channel, 4);
            if (ebmlSize.value() <= 0 || ebmlSize.value() > 1024) {
                throw invalid("WebM EBML header size is invalid.");
            }
            long segmentPosition = 4L + ebmlSize.length() + ebmlSize.value();
            if (segmentPosition + 5 > fileSize
                    || !matches(readBytes(channel, segmentPosition, 4), 0, bytes(0x18, 0x53, 0x80, 0x67))) {
                throw invalid("WebM Segment element is missing.");
            }
            VariableInteger segmentSize = readVariableInteger(channel, segmentPosition + 4);
            long payloadStart = segmentPosition + 4 + segmentSize.length();
            if (segmentSize.value() <= 0
                    || segmentSize.value() > fileSize - payloadStart
                    || payloadStart + segmentSize.value() != fileSize) {
                throw invalid("WebM Segment size is truncated or invalid.");
            }
        }
    }

    private static void validateOgg(Path file, long fileSize) throws IOException {
        if (fileSize < 27) {
            throw invalid("Ogg page header is truncated.");
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = 0;
            while (position < fileSize) {
                if (fileSize - position < 27) {
                    throw invalid("Ogg page header is truncated.");
                }
                ByteBuffer header = read(channel, position, 27);
                byte[] capture = new byte[4];
                header.get(capture);
                if (!matches(capture, 0, ascii("OggS")) || header.get() != 0) {
                    throw invalid("Ogg capture pattern or stream version is invalid.");
                }
                header.position(26);
                int segments = Byte.toUnsignedInt(header.get());
                if (fileSize - position - 27 < segments) {
                    throw invalid("Ogg page segment table is truncated.");
                }
                ByteBuffer lacing = read(channel, position + 27, segments);
                long bodyLength = 0;
                while (lacing.hasRemaining()) {
                    bodyLength += Byte.toUnsignedInt(lacing.get());
                }
                long pageLength = 27L + segments + bodyLength;
                if (pageLength > fileSize - position) {
                    throw invalid("Ogg page segment table points beyond the file.");
                }
                position += pageLength;
            }
        }
    }

    private static void validateImage(Path file, String expectedFormat) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) {
                throw invalid(expectedFormat + " image stream could not be opened.");
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid(expectedFormat + " image structure is invalid.");
            }
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().toUpperCase(Locale.ROOT).contains(expectedFormat)) {
                    throw invalid("Thumbnail signature does not match decoded image format.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw invalid("Thumbnail dimensions are invalid or too large.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalid(expectedFormat + " image could not be decoded.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid(expectedFormat + " image structure is invalid.", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw invalid(expectedFormat + " image structure is invalid.", exception);
        }
    }

    private static void validateWebp(Path file, long fileSize) throws IOException {
        if (fileSize < 20 || fileSize > Integer.toUnsignedLong(-1) + 8L) {
            throw invalid("WebP RIFF container is truncated or too large.");
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer header = read(channel, 0, 12).order(ByteOrder.LITTLE_ENDIAN);
            if (!"RIFF".equals(fourCc(header))) {
                throw invalid("WebP RIFF header is missing.");
            }
            long declaredRiffSize = Integer.toUnsignedLong(header.getInt());
            if (!"WEBP".equals(fourCc(header)) || declaredRiffSize + 8 != fileSize) {
                throw invalid("WebP RIFF declared size does not match the file.");
            }

            boolean foundImageBitstream = false;
            long position = 12;
            while (position < fileSize) {
                if (fileSize - position < 8) {
                    throw invalid("WebP chunk header is truncated.");
                }
                ByteBuffer chunkHeader = read(channel, position, 8).order(ByteOrder.LITTLE_ENDIAN);
                String chunkType = fourCc(chunkHeader);
                long chunkSize = Integer.toUnsignedLong(chunkHeader.getInt());
                long paddedChunkSize = chunkSize + (chunkSize & 1);
                long payloadPosition = position + 8;
                if (paddedChunkSize > fileSize - payloadPosition) {
                    throw invalid("WebP chunk length is truncated or invalid.");
                }

                if ("VP8 ".equals(chunkType)) {
                    validateVp8Dimensions(channel, payloadPosition, chunkSize);
                    foundImageBitstream = true;
                } else if ("VP8L".equals(chunkType)) {
                    validateVp8lDimensions(channel, payloadPosition, chunkSize);
                    foundImageBitstream = true;
                } else if ("VP8X".equals(chunkType)) {
                    if (position != 12 || chunkSize != 10) {
                        throw invalid("WebP VP8X chunk is misplaced or has an invalid size.");
                    }
                    validateVp8xDimensions(channel, payloadPosition);
                }
                position = payloadPosition + paddedChunkSize;
            }
            if (!foundImageBitstream) {
                throw invalid("WebP image bitstream chunk is missing.");
            }
        }
    }

    private static void validateVp8Dimensions(FileChannel channel, long position, long chunkSize)
            throws IOException {
        if (chunkSize < 10) {
            throw invalid("WebP VP8 image header is truncated.");
        }
        ByteBuffer payload = read(channel, position, 10).order(ByteOrder.LITTLE_ENDIAN);
        payload.position(3);
        if (payload.get() != (byte) 0x9D || payload.get() != 0x01 || payload.get() != 0x2A) {
            throw invalid("WebP VP8 frame start code is invalid.");
        }
        int width = Short.toUnsignedInt(payload.getShort()) & 0x3FFF;
        int height = Short.toUnsignedInt(payload.getShort()) & 0x3FFF;
        validateWebpDimensions(width, height);
    }

    private static void validateVp8lDimensions(FileChannel channel, long position, long chunkSize)
            throws IOException {
        if (chunkSize < 5) {
            throw invalid("WebP VP8L image header is truncated.");
        }
        ByteBuffer payload = read(channel, position, 5).order(ByteOrder.LITTLE_ENDIAN);
        if (Byte.toUnsignedInt(payload.get()) != 0x2F) {
            throw invalid("WebP VP8L image signature is invalid.");
        }
        int dimensionBits = payload.getInt();
        int width = (dimensionBits & 0x3FFF) + 1;
        int height = ((dimensionBits >>> 14) & 0x3FFF) + 1;
        validateWebpDimensions(width, height);
    }

    private static void validateVp8xDimensions(FileChannel channel, long position) throws IOException {
        ByteBuffer payload = read(channel, position, 10);
        payload.position(4);
        int width = littleEndian24(payload) + 1;
        int height = littleEndian24(payload) + 1;
        validateWebpDimensions(width, height);
    }

    private static int littleEndian24(ByteBuffer buffer) {
        return Byte.toUnsignedInt(buffer.get())
                | (Byte.toUnsignedInt(buffer.get()) << 8)
                | (Byte.toUnsignedInt(buffer.get()) << 16);
    }

    private static void validateWebpDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
            throw invalid("WebP dimensions are invalid or too large.");
        }
    }

    private static VariableInteger readVariableInteger(FileChannel channel, long position) throws IOException {
        int first = Byte.toUnsignedInt(read(channel, position, 1).get());
        int mask = 0x80;
        int length = 1;
        while (length <= 8 && (first & mask) == 0) {
            mask >>>= 1;
            length++;
        }
        if (length > 8) {
            throw invalid("EBML variable integer is invalid.");
        }
        long value = first & (mask - 1);
        if (length > 1) {
            ByteBuffer remainder = read(channel, position + 1, length - 1);
            while (remainder.hasRemaining()) {
                value = (value << 8) | Byte.toUnsignedInt(remainder.get());
            }
        }
        long unknownValue = (1L << (7 * length)) - 1;
        if (value == unknownValue) {
            throw invalid("Unknown-sized EBML elements are not accepted.");
        }
        return new VariableInteger(length, value);
    }

    private static void validateMetadata(MultipartFile file, MediaFormat format) {
        String contentType = file.getContentType();
        String normalizedType = contentType == null
                ? ""
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!format.mimeTypes().contains(normalizedType)) {
            throw invalid("Uploaded media MIME type does not match its content.");
        }

        String original = file.getOriginalFilename();
        String normalizedName = original == null ? "" : original.replace('\\', '/').toLowerCase(Locale.ROOT);
        String baseName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        if (format.sourceExtensions().stream().noneMatch(baseName::endsWith)) {
            throw invalid("Uploaded media filename extension does not match its content.");
        }
    }

    private static byte[] readPrefix(Path file, int maximum) throws IOException {
        int length = (int) Math.min(Files.size(file), maximum);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return readBytes(channel, 0, length);
        }
    }

    private static byte[] readBytes(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = read(channel, position, length);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }

    private static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        int read = 0;
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position + read);
            if (count < 0) {
                throw invalid("Media structure is truncated.");
            }
            read += count;
        }
        return buffer.flip();
    }

    private static String fourCc(ByteBuffer buffer) {
        byte[] type = new byte[4];
        buffer.get(type);
        return new String(type, StandardCharsets.US_ASCII);
    }

    private static boolean matches(byte[] content, int offset, byte[] expected) {
        if (offset < 0 || content.length - offset < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static void delete(Path path, Exception original) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    enum MediaKind {
        VIDEO(512L * 1024 * 1024, "512 MB"),
        THUMBNAIL(32L * 1024 * 1024, "32 MB");

        private final long maxBytes;
        private final String limitLabel;

        MediaKind(long maxBytes, String limitLabel) {
            this.maxBytes = maxBytes;
            this.limitLabel = limitLabel;
        }

        long maxBytes() {
            return maxBytes;
        }

        String limitLabel() {
            return limitLabel;
        }
    }

    enum MediaFormat {
        MP4(".mp4", "video/mp4", Set.of(".mp4")),
        WEBM(".webm", "video/webm", Set.of(".webm")),
        OGV(".ogv", "video/ogg", Set.of(".ogv", ".ogg")),
        JPEG(".jpg", "image/jpeg", Set.of(".jpg", ".jpeg")),
        PNG(".png", "image/png", Set.of(".png")),
        WEBP(".webp", "image/webp", Set.of(".webp"));

        private final String serverExtension;
        private final String mediaType;
        private final Set<String> mimeTypes;
        private final Set<String> sourceExtensions;

        MediaFormat(String serverExtension, String mediaType, Set<String> sourceExtensions) {
            this.serverExtension = serverExtension;
            this.mediaType = mediaType;
            this.mimeTypes = Set.of(mediaType);
            this.sourceExtensions = sourceExtensions;
        }

        String serverExtension() {
            return serverExtension;
        }

        String mediaType() {
            return mediaType;
        }

        Set<String> mimeTypes() {
            return mimeTypes;
        }

        Set<String> sourceExtensions() {
            return sourceExtensions;
        }
    }

    record StagedMedia(Path path, MediaFormat format) {
    }

    private record VariableInteger(int length, long value) {
    }

    private static final class ActiveContentScanner {
        private static final List<byte[]> MARKERS = List.of(
                ascii("<html"),
                ascii("<script"),
                ascii("<svg"),
                ascii("<!doctype"),
                ascii("<?xml"),
                ascii("javascript:"));
        private static final int WINDOW_SIZE = MARKERS.stream().mapToInt(marker -> marker.length).max().orElse(1);

        private final byte[] window = new byte[WINDOW_SIZE];
        private final byte[] prefix = new byte[4];
        private long position;
        private int cursor;
        private int windowCount;

        void accept(byte[] bytes, int length) {
            for (int index = 0; index < length; index++) {
                accept(bytes[index]);
            }
        }

        private void accept(byte value) {
            if (position < prefix.length) {
                prefix[(int) position] = value;
            }
            byte normalized = value;
            if (normalized >= 'A' && normalized <= 'Z') {
                normalized = (byte) (normalized + ('a' - 'A'));
            }
            window[cursor] = normalized;
            cursor = (cursor + 1) % WINDOW_SIZE;
            windowCount = Math.min(WINDOW_SIZE, windowCount + 1);
            position++;

            if (position == 2 && ((prefix[0] == 'M' && prefix[1] == 'Z')
                    || (prefix[0] == '#' && prefix[1] == '!'))) {
                throw invalid("Executable media content is not allowed.");
            }
            if (position == 4 && prefix[0] == 0x7F && prefix[1] == 'E'
                    && prefix[2] == 'L' && prefix[3] == 'F') {
                throw invalid("Executable media content is not allowed.");
            }
            for (byte[] marker : MARKERS) {
                if (endsWith(marker)) {
                    throw invalid("Active or polyglot media content is not allowed.");
                }
            }
        }

        private boolean endsWith(byte[] marker) {
            if (windowCount < marker.length) {
                return false;
            }
            int start = Math.floorMod(cursor - marker.length, WINDOW_SIZE);
            for (int index = 0; index < marker.length; index++) {
                if (window[(start + index) % WINDOW_SIZE] != marker[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
