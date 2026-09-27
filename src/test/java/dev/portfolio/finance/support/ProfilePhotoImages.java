package dev.portfolio.finance.support;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Generated in memory only; no real photographs or filesystem fixtures. */
public final class ProfilePhotoImages {
    private ProfilePhotoImages() {}

    public static byte[] image(String format, int width, int height) throws Exception {
        return image(format, width, height, false, false);
    }

    public static byte[] image(String format, int width, int height, boolean transparent, boolean progressive) throws Exception {
        var raster = new BufferedImage(width, height, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = raster.createGraphics();
        try {
            if (!transparent) {
                g.setColor(Color.RED); g.fillRect(0, 0, width / 2, height / 2);
                g.setColor(Color.GREEN); g.fillRect(width / 2, 0, width - width / 2, height / 2);
                g.setColor(Color.BLUE); g.fillRect(0, height / 2, width / 2, height - height / 2);
                g.setColor(Color.YELLOW); g.fillRect(width / 2, height / 2, width - width / 2, height - height / 2);
            }
        } finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName(format).next();
        try (var stream = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(stream);
            var params = writer.getDefaultWriteParam();
            if (progressive) params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.write(null, new IIOImage(raster, null, null), params);
            stream.flush();
        } finally { writer.dispose(); raster.flush(); }
        return bytes.toByteArray();
    }

    public static byte[] segment(byte[] jpeg, int marker, byte[] payload) {
        var out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); out.write(255); out.write(marker);
        out.write((payload.length + 2) >> 8); out.write(payload.length + 2);
        out.writeBytes(payload); out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    public static byte[] exif(byte[] jpeg, int orientation) {
        var data = ByteBuffer.allocate(62).order(ByteOrder.LITTLE_ENDIAN);
        data.put("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        data.put((byte)'I').put((byte)'I').putShort((short)42).putInt(8);
        data.putShort((short)2);
        data.putShort((short)0x0112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0);
        data.putShort((short)0x8825).putShort((short)4).putInt(1).putInt(38);
        data.putInt(0);
        data.putShort((short)1).putShort((short)1).putShort((short)2).putInt(2).put((byte)'N').put((byte)0).putShort((short)0).putInt(0);
        return segment(jpeg, 225, data.array());
    }

    public static byte[] pngChunk(String type, byte[] payload) {
        var b = ByteBuffer.allocate(payload.length + 12);
        b.putInt(payload.length).put(type.getBytes(StandardCharsets.US_ASCII)).put(payload);
        var crc = new CRC32(); crc.update(b.array(), 4, payload.length + 4);
        b.putInt((int)crc.getValue()); return b.array();
    }

    public static byte[] insertPngChunk(byte[] png, String type, byte[] payload) {
        var out = new ByteArrayOutputStream();
        out.write(png, 0, 33); out.writeBytes(pngChunk(type, payload)); out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }

    public static byte[] dimensions(byte[] png, int width, int height) {
        var copy = png.clone(); var b = ByteBuffer.wrap(copy);
        b.putInt(16, width); b.putInt(20, height);
        var crc = new CRC32(); crc.update(copy, 12, 17); b.putInt(29, (int)crc.getValue());
        return copy;
    }

    public static byte[] append(byte[] bytes, byte[] tail) {
        var result = Arrays.copyOf(bytes, bytes.length + tail.length);
        System.arraycopy(tail, 0, result, bytes.length, tail.length); return result;
    }
}
