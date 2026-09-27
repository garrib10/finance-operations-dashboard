package dev.portfolio.finance.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import dev.portfolio.finance.config.ProfilePhotoProperties;
import dev.portfolio.finance.exception.account.InvalidProfilePhotoException;
import static dev.portfolio.finance.exception.account.InvalidProfilePhotoException.Reason.*;
import org.springframework.stereotype.Component;

@Component
public class ProfilePhotoProcessor {
    private final ProfilePhotoProperties policy;

    public ProfilePhotoProcessor(ProfilePhotoProperties policy) { this.policy = policy; }

    public ProcessedProfilePhoto process(byte[] input) {
        if (input == null || input.length == 0) throw new InvalidProfilePhotoException(EMPTY);
        if (input.length > policy.maxInputBytes()) throw new InvalidProfilePhotoException(TOO_LARGE);
        try {
            byte[] content = ProfilePhotoContainerInspector.inspect(input);
            boolean png = ProfilePhotoContainerInspector.isPng(content);
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
                ImageReader reader = reader(stream, png ? "png" : "jpeg");
                try {
                    reader.setInput(stream, false, true);
                    reader.addIIOReadWarningListener((source, warning) -> { throw new InvalidProfilePhotoException(INVALID); });
                    checkDimensions(reader.getWidth(0), reader.getHeight(0));
                    if (reader.getNumImages(true) != 1) throw new InvalidProfilePhotoException(ANIMATION);
                    int orientation = png ? 1 : orientation(content);
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw new InvalidProfilePhotoException(INVALID);
                    try { return encode(resizeAndOrient(decoded, orientation)); }
                    finally { decoded.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (InvalidProfilePhotoException ex) {
            throw ex;
        } catch (Exception ex) {
            // Decoder/metadata messages can contain input. Do not retain the cause.
            throw new InvalidProfilePhotoException(INVALID);
        }
    }

    private ImageReader reader(MemoryCacheImageInputStream stream, String expected) throws IOException {
        var readers = ImageIO.getImageReaders(stream);
        while (readers.hasNext()) {
            var reader = readers.next();
            boolean approved = false;
            try {
                approved = reader.getFormatName().equalsIgnoreCase(expected);
                if (approved) return reader;
            } finally {
                if (!approved) reader.dispose();
            }
        }
        throw new InvalidProfilePhotoException(UNSUPPORTED);
    }

    private void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > policy.maxWidth() || height > policy.maxHeight()
                || (long) width * height > policy.maxDecodedPixels()) throw new InvalidProfilePhotoException(DIMENSIONS);
    }

    private int orientation(byte[] content) throws Exception {
        var metadata = JpegMetadataReader.readMetadata(new ByteArrayInputStream(content), List.of(new ExifReader()));
        if (metadata.hasErrors()) throw new InvalidProfilePhotoException(INVALID);
        int orientation = 1;
        for (var directory : metadata.getDirectoriesOfType(ExifIFD0Directory.class)) {
            if (directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                if (orientation < 1 || orientation > 8) throw new InvalidProfilePhotoException(INVALID);
            }
        }
        return orientation;
    }

    private BufferedImage resizeAndOrient(BufferedImage source, int orientation) {
        int w = source.getWidth(), h = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage oriented = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        try {
            // Integer pixel mapping supports all eight EXIF rotations/reflections.
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int dx = switch (orientation) { case 2, 3 -> w - 1 - x; case 5, 8 -> y; case 6, 7 -> h - 1 - y; default -> x; };
                int dy = switch (orientation) { case 3, 4 -> h - 1 - y; case 5, 6 -> x; case 7, 8 -> w - 1 - x; default -> y; };
                oriented.setRGB(dx, dy, source.getRGB(x, y));
            }
            double scale = Math.min(1d, Math.min((double)policy.outputMaxWidth() / oriented.getWidth(),
                    (double)policy.outputMaxHeight() / oriented.getHeight()));
            int width = Math.max(1, (int)Math.floor(oriented.getWidth() * scale));
            int height = Math.max(1, (int)Math.floor(oriented.getHeight() * scale));
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = output.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(oriented, 0, 0, width, height, null);
            } finally { graphics.dispose(); }
            return output;
        } finally { oriented.flush(); }
    }

    private ProcessedProfilePhoto encode(BufferedImage output) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) { output.flush(); throw new InvalidProfilePhotoException(INVALID); }
        var writer = writers.next();
        try (var bytes = new ByteArrayOutputStream(); var stream = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(stream);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality((float)policy.jpegQuality());
            writer.write(null, new IIOImage(output, null, null), parameters);
            stream.flush();
            if (bytes.size() == 0) throw new InvalidProfilePhotoException(INVALID);
            return new ProcessedProfilePhoto(bytes.toByteArray(), output.getWidth(), output.getHeight());
        } finally { writer.dispose(); output.flush(); }
    }
}
