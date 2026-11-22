package com.florin.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class TestImages {

    private TestImages() {}

    public static byte[] image(String format, int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static byte[] pngHeader(int width, int height) {
        byte[] type = "IHDR".getBytes(StandardCharsets.US_ASCII);
        byte[] fields = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 6).array();
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(fields);
        return ByteBuffer.allocate(33)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(fields.length)
                .put(type)
                .put(fields)
                .putInt((int) crc.getValue())
                .array();
    }
}
