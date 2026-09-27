package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.*;
import static dev.portfolio.finance.support.ProfilePhotoImages.*;
import static dev.portfolio.finance.exception.account.InvalidProfilePhotoException.Reason.*;
import dev.portfolio.finance.exception.account.InvalidProfilePhotoException;
import dev.portfolio.finance.support.ProfilePhotoTestSupport;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProfilePhotoProcessorTest {
    private final ProfilePhotoProcessor processor = new ProfilePhotoProcessor(ProfilePhotoTestSupport.properties(true));

    @ParameterizedTest
    @CsvSource({"jpeg,1024,512,512,256", "png,512,1024,256,512", "jpeg,700,700,512,512", "png,40,20,40,20",
            "png,4096,2,512,1", "png,2,4096,1,512", "png,4000,3000,512,384"})
    void decodesResizesAndWritesFreshJpeg(String format, int width, int height, int outWidth, int outHeight) throws Exception {
        var photo = processor.process(image(format, width, height));
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.width()).isEqualTo(outWidth); assertThat(photo.height()).isEqualTo(outHeight);
        byte[] bytes = photo.bytes(); assertThat(bytes[0]).isEqualTo((byte)255); assertThat(bytes[1]).isEqualTo((byte)216);
        var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded.getWidth()).isEqualTo(outWidth); assertThat(decoded.getHeight()).isEqualTo(outHeight);
        bytes[0] = 0; assertThat(photo.bytes()[0]).isEqualTo((byte)255);
        assertThat(photo.toString()).isEqualTo("ProcessedProfilePhoto[image content redacted]");
    }

    @Test
    void acceptsProgressiveJpegAndFlattensTransparentPngToWhite() throws Exception {
        assertThat(processor.process(image("jpeg", 60, 30, false, true)).width()).isEqualTo(60);
        var out = processor.process(image("png", 16, 16, true, false));
        var raster = ImageIO.read(new ByteArrayInputStream(out.bytes()));
        assertThat(raster.getRGB(8, 8) & 0xffffff).isEqualTo(0xffffff);
    }

    @Test
    void enforcesByteBoundaryAndRemovesAppendedPayload() throws Exception {
        byte[] jpeg = image("jpeg", 40, 20);
        byte[] exact = Arrays.copyOf(jpeg, 2097152);
        byte[] marker = "EXECUTABLE_PAYLOAD_PROBE".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, exact, jpeg.length, marker.length);
        var output = processor.process(exact);
        assertThat(new String(output.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("EXECUTABLE_PAYLOAD_PROBE");
        rejects(Arrays.copyOf(exact, exact.length + 1), TOO_LARGE);
        rejects(null, EMPTY); rejects(new byte[0], EMPTY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<svg><script>evil</script></svg>", "GIF89a", "RIFFxxxxWEBP", "%PDF-1.7", "PK1234", "MZexecutable", "arbitrary"})
    void rejectsUnsupportedContentRegardlessOfAnyFilenameOrMimeClaim(String input) {
        rejects(input.getBytes(StandardCharsets.US_ASCII), UNSUPPORTED);
    }

    @ParameterizedTest
    @CsvSource({"4097,1", "1,4097", "4000,3001", "0,20", "20,0", "-1,20", "2147483647,2147483647"})
    void rejectsDimensionsBeforeDecode(int width, int height) throws Exception {
        assertThatThrownBy(() -> processor.process(dimensions(image("png", 16, 16), width, height)))
                .isInstanceOf(InvalidProfilePhotoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"acTL", "fcTL", "fdAT"})
    void rejectsApngChunksWithoutTrustingImageCount(String type) throws Exception {
        rejects(insertPngChunk(image("png", 20, 20), type, new byte[8]), ANIMATION);
    }

    @Test
    void rejectsCorruptTruncatedAndMultipleImages() throws Exception {
        byte[] png = image("png", 20, 20), jpeg = image("jpeg", 20, 20);
        rejects(Arrays.copyOf(png, png.length - 1), INVALID);
        rejects(Arrays.copyOf(jpeg, jpeg.length - 2), INVALID);
        byte[] damaged = png.clone(); damaged[29] ^= 1; rejects(damaged, INVALID);
        rejects(new byte[]{(byte)255,(byte)216,(byte)255,(byte)217}, INVALID);
        rejects(append(jpeg, jpeg), ANIMATION); rejects(append(png, png), ANIMATION);
        rejects(segment(jpeg, 226, new byte[]{'M','P','F',0}), ANIMATION);
        rejects(insertPngChunk(png, "ABCD", new byte[0]), INVALID);
        rejects(Arrays.copyOf(png, 10), INVALID);
    }

    @ParameterizedTest
    @CsvSource({"1,80,40,red", "2,80,40,green", "3,80,40,yellow", "4,80,40,blue",
            "5,40,80,red", "6,40,80,blue", "7,40,80,yellow", "8,40,80,green"})
    void appliesAllExifOrientationsBeforeResizingAndRemovesMetadata(int orientation, int width, int height, String corner) throws Exception {
        byte[] source = exif(image("jpeg", 80, 40), orientation);
        assertThat(ImageMetadataReader.readMetadata(new ByteArrayInputStream(source)).getFirstDirectoryOfType(GpsDirectory.class)).isNotNull();
        var out = processor.process(segment(source, 254, "CAMERA_COMMENT_PROBE".getBytes(StandardCharsets.US_ASCII)));
        assertThat(out.width()).isEqualTo(width); assertThat(out.height()).isEqualTo(height);
        var rgb = new java.awt.Color(ImageIO.read(new ByteArrayInputStream(out.bytes())).getRGB(5, 5));
        var expected = switch(corner) { case "red" -> java.awt.Color.RED; case "green" -> java.awt.Color.GREEN;
            case "blue" -> java.awt.Color.BLUE; default -> java.awt.Color.YELLOW; };
        assertThat(rgb.getRed()).isCloseTo(expected.getRed(), within(20));
        assertThat(rgb.getGreen()).isCloseTo(expected.getGreen(), within(20));
        assertThat(rgb.getBlue()).isCloseTo(expected.getBlue(), within(20));
        var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(out.bytes()));
        assertThat(metadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
        assertThat(new String(out.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("CAMERA_COMMENT_PROBE", "Exif");
    }

    @Test
    void rejectsMalformedExifAndStripsPngAncillaryMetadata() throws Exception {
        byte[] jpeg = image("jpeg", 20, 20);
        rejects(exif(jpeg, 9), INVALID);
        rejects(segment(jpeg, 225, "Exif\0\0malformed".getBytes(StandardCharsets.US_ASCII)), INVALID);
        byte[] png = insertPngChunk(image("png", 20, 20), "tEXt", "Comment\0PRIVATE_METADATA_PROBE".getBytes(StandardCharsets.US_ASCII));
        assertThat(new String(processor.process(png).bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("PRIVATE_METADATA_PROBE");
    }

    @Test
    void disposesFailedAndUnapprovedReadersWithoutExposingTheirErrors() throws Exception {
        byte[] input = image("png", 20, 20);
        var rejected = org.mockito.Mockito.mock(javax.imageio.ImageReader.class);
        var accepted = org.mockito.Mockito.mock(javax.imageio.ImageReader.class);
        org.mockito.Mockito.when(rejected.getFormatName()).thenReturn("gif");
        org.mockito.Mockito.when(accepted.getFormatName()).thenReturn("png");
        org.mockito.Mockito.when(accepted.getWidth(0)).thenThrow(new java.io.IOException("PRIVATE_DECODER_PROBE"));
        try (var io = org.mockito.Mockito.mockStatic(ImageIO.class)) {
            io.when(() -> ImageIO.getImageReaders(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.List.of(rejected, accepted).iterator());
            rejects(input, INVALID);
        }
        org.mockito.Mockito.verify(rejected).dispose();
        org.mockito.Mockito.verify(accepted).dispose();
    }

    @Test
    void disposesReaderWhenFormatInspectionFails() throws Exception {
        byte[] input = image("png", 20, 20);
        var reader = org.mockito.Mockito.mock(javax.imageio.ImageReader.class);
        org.mockito.Mockito.when(reader.getFormatName()).thenThrow(new java.io.IOException("PRIVATE_FORMAT_PROBE"));
        try (var io = org.mockito.Mockito.mockStatic(ImageIO.class)) {
            io.when(() -> ImageIO.getImageReaders(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.List.of(reader).iterator());
            rejects(input, INVALID);
        }
        org.mockito.Mockito.verify(reader).dispose();
    }

    @Test
    void rejectsWhenNoApprovedReaderIsAvailable() throws Exception {
        byte[] input = image("png", 20, 20);
        try (var io = org.mockito.Mockito.mockStatic(ImageIO.class)) {
            io.when(() -> ImageIO.getImageReaders(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.Collections.emptyIterator());
            rejects(input, UNSUPPORTED);
        }
    }

    @Test
    void rejectsMalformedChunkLengthsTypesAndIncompleteJpegSegments() throws Exception {
        byte[] png = image("png", 20, 20);
        byte[] excessive = png.clone(); java.nio.ByteBuffer.wrap(excessive).putInt(8, Integer.MAX_VALUE);
        rejects(excessive, INVALID);
        rejects(insertPngChunk(png, "t1Xt", new byte[0]), INVALID);
        rejects(insertPngChunk(png, "texT", new byte[0]), INVALID);
        rejects(insertPngChunk(png, "IHDR", new byte[13]), INVALID);
        rejects(new byte[]{(byte)255,(byte)216,(byte)255}, INVALID);
        rejects(new byte[]{(byte)255,(byte)216,(byte)255,(byte)224,0,1}, INVALID);
        rejects(new byte[]{(byte)255,(byte)216,(byte)255,(byte)224,0,20}, INVALID);
        rejects(new byte[]{(byte)255,(byte)216,(byte)255,(byte)224,0}, INVALID);
    }

    @ParameterizedTest
    @CsvSource({"0,20,1,DIMENSIONS", "20,0,1,DIMENSIONS", "20,20,2,ANIMATION", "20,20,1,INVALID"})
    void rejectsInvalidDecoderDimensionsCountsAndNullRaster(int width, int height, int count,
            InvalidProfilePhotoException.Reason reason) throws Exception {
        byte[] input = image("png", 20, 20);
        var reader = org.mockito.Mockito.mock(javax.imageio.ImageReader.class);
        org.mockito.Mockito.when(reader.getFormatName()).thenReturn("png");
        org.mockito.Mockito.when(reader.getWidth(0)).thenReturn(width);
        org.mockito.Mockito.when(reader.getHeight(0)).thenReturn(height);
        org.mockito.Mockito.when(reader.getNumImages(true)).thenReturn(count);
        try (var io = org.mockito.Mockito.mockStatic(ImageIO.class)) {
            io.when(() -> ImageIO.getImageReaders(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.List.of(reader).iterator());
            rejects(input, reason);
        }
        org.mockito.Mockito.verify(reader).dispose();
        if (reason != INVALID) org.mockito.Mockito.verify(reader, org.mockito.Mockito.never()).read(0);
    }

    @Test
    void rejectsZeroExifOrientation() throws Exception {
        rejects(exif(image("jpeg", 20, 20), 0), INVALID);
    }

    private void rejects(byte[] input, InvalidProfilePhotoException.Reason reason) {
        assertThatThrownBy(() -> processor.process(input)).isInstanceOfSatisfying(InvalidProfilePhotoException.class,
                ex -> { assertThat(ex.getReason()).isEqualTo(reason); assertThat(ex.getCause()).isNull(); });
    }
}
