package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.*;
import static dev.portfolio.finance.support.ProfilePhotoImages.*;
import static dev.portfolio.finance.exception.account.InvalidProfilePhotoException.Reason.*;

import dev.portfolio.finance.exception.account.InvalidProfilePhotoException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfilePhotoContainerInspectorTest {
    // These fixtures exercise container structure only; raster validation belongs to the processor.
    private byte[] png(byte[]... chunks) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        for (byte[] chunk : chunks) out.writeBytes(chunk);
        return out.toByteArray();
    }

    private byte[] chunk(String type) { return pngChunk(type, new byte[0]); }
    private byte[] header() { return pngChunk("IHDR", new byte[13]); }

    @Test
    void rejectsMissingOrWrongSizedHeaderAndInvalidEnd() {
        invalid(png(chunk("IDAT"), chunk("IEND")));
        invalid(png(chunk("IHDR"), chunk("IDAT"), chunk("IEND")));
        invalid(png(header(), chunk("IEND")));
        invalid(png(header(), chunk("IDAT"), pngChunk("IEND", new byte[]{1})));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLTE", "tRNS"})
    void rejectsDuplicateOrLatePaletteAndTransparency(String type) {
        invalid(png(header(), chunk(type), chunk(type), chunk("IDAT"), chunk("IEND")));
        invalid(png(header(), chunk("IDAT"), chunk(type), chunk("IEND")));
    }

    @Test
    void retainsPaletteTransparencyAndConsecutiveDataButStripsAncillaryAndTail() {
        byte[] expected = png(header(), pngChunk("PLTE", new byte[]{0, 0, 0}),
                pngChunk("tRNS", new byte[]{(byte)255}), chunk("IDAT"), chunk("IDAT"), chunk("IEND"));
        assertThat(ProfilePhotoContainerInspector.inspect(append(expected, new byte[8]))).isEqualTo(expected);
        byte[] withText = png(header(), chunk("IDAT"), chunk("tEXt"), chunk("IEND"));
        assertThat(ProfilePhotoContainerInspector.inspect(withText))
                .isEqualTo(png(header(), chunk("IDAT"), chunk("IEND")));
        invalid(png(header(), chunk("IDAT"), chunk("tEXt"), chunk("IDAT"), chunk("IEND")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@BCD", "[BCD", "{BCD"})
    void rejectsNonAlphabeticChunkNames(String type) {
        invalid(png(header(), chunk(type), chunk("IDAT"), chunk("IEND")));
    }

    @Test
    void rejectsFalseJpegSignaturesAndUnexpectedBytesBetweenSegments() {
        for (byte[] bytes : new byte[][]{{(byte)255}, {(byte)255, 0, (byte)255},
                {(byte)255, (byte)216, 0}}) {
            assertThatThrownBy(() -> ProfilePhotoContainerInspector.inspect(bytes))
                    .isInstanceOfSatisfying(InvalidProfilePhotoException.class,
                            ex -> assertThat(ex.getReason()).isEqualTo(UNSUPPORTED));
        }
        invalid(new byte[]{(byte)255, (byte)216, (byte)255, (byte)224, 0, 2, 1});
        assertThatThrownBy(() -> ProfilePhotoContainerInspector.inspect(
                new byte[]{(byte)255, (byte)216, (byte)255, (byte)216}))
                .isInstanceOfSatisfying(InvalidProfilePhotoException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ANIMATION));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 208, 215})
    void acceptsStuffedBytesAndRestartMarkersInsideScan(int marker) {
        byte[] jpeg = {(byte)255, (byte)216, (byte)255, (byte)218, 0, 2,
                (byte)255, (byte)marker, 42, (byte)255, (byte)255, (byte)217};
        assertThat(ProfilePhotoContainerInspector.inspect(jpeg)).isEqualTo(jpeg);
    }

    @Test
    void distinguishesOtherApp2MetadataFromMultiPictureSignature() throws Exception {
        byte[] jpeg = image("jpeg", 4, 4);
        for (byte[] payload : new byte[][]{new byte[0], {'X','P','F',0}, {'M','X','F',0},
                {'M','P','X',0}, {'M','P','F',1}}) {
            byte[] withMetadata = segment(jpeg, 226, payload);
            assertThat(ProfilePhotoContainerInspector.inspect(withMetadata)).isEqualTo(withMetadata);
        }
        assertThat(ProfilePhotoContainerInspector.inspect(append(jpeg, new byte[]{(byte)255, 0})))
                .isEqualTo(jpeg);
        invalid(Arrays.copyOf(jpeg, jpeg.length - 1));
    }

    private void invalid(byte[] bytes) {
        assertThatThrownBy(() -> ProfilePhotoContainerInspector.inspect(bytes))
                .isInstanceOfSatisfying(InvalidProfilePhotoException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(INVALID));
    }
}
