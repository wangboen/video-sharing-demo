package com.videosharing.videosharingspringboot.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MediaFixtures {
    private MediaFixtures() {
    }

    static byte[] mp4() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeBox(output, "ftyp", concat(
                "isom".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                bytes(0, 0, 0, 0),
                "isom".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "mp42".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        writeBox(output, "moov", new byte[0]);
        writeBox(output, "mdat", bytes(0));
        return output.toByteArray();
    }

    static InputStream largeMp4(long payloadSize) {
        if (payloadSize > Integer.MAX_VALUE - 8L) {
            throw new IllegalArgumentException("fixture payload too large");
        }
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        writeBox(prefix, "ftyp", concat(
                "isom".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                bytes(0, 0, 0, 0),
                "isom".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "mp42".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        writeBox(prefix, "moov", new byte[0]);
        prefix.writeBytes(ByteBuffer.allocate(8).putInt((int) payloadSize + 8)
                .put("mdat".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).array());
        return new SequenceInputStream(new ByteArrayInputStream(prefix.toByteArray()), new InputStream() {
            private long remaining = payloadSize;

            @Override
            public int read() {
                if (remaining == 0) {
                    return -1;
                }
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (remaining == 0) {
                    return -1;
                }
                int count = (int) Math.min(length, remaining);
                java.util.Arrays.fill(buffer, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        });
    }

    static byte[] webm() {
        return bytes(
                0x1A, 0x45, 0xDF, 0xA3, 0x81, 0x00,
                0x18, 0x53, 0x80, 0x67, 0x81, 0x00);
    }

    static byte[] ogg() {
        byte[] page = new byte[29];
        page[0] = 'O';
        page[1] = 'g';
        page[2] = 'g';
        page[3] = 'S';
        page[4] = 0;
        page[5] = 2;
        page[26] = 1;
        page[27] = 1;
        page[28] = 0;
        return page;
    }

    static byte[] jpeg() {
        return image("jpeg");
    }

    static byte[] png() {
        return image("png");
    }

    static byte[] webp() {
        ByteBuffer buffer = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{'R', 'I', 'F', 'F'});
        buffer.putInt(18);
        buffer.put(new byte[]{'W', 'E', 'B', 'P'});
        buffer.put(new byte[]{'V', 'P', '8', 'L'});
        buffer.putInt(5);
        buffer.put(bytes(0x2F, 0, 0, 0, 0));
        buffer.put((byte) 0);
        return buffer.array();
    }

    static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }

    static byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) {
            length += value.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private static byte[] image(String format) {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, Color.BLUE.getRGB());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("ImageIO fixture writer unavailable for " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeBox(ByteArrayOutputStream output, String type, byte[] payload) {
        output.writeBytes(ByteBuffer.allocate(8).putInt(payload.length + 8)
                .put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).array());
        output.writeBytes(payload);
    }
}
