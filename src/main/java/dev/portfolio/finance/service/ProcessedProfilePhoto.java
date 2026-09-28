package dev.portfolio.finance.service;

/** Only the processor constructs this value. No input metadata is retained. */
public final class ProcessedProfilePhoto {
    private final byte[] bytes;
    private final int width;
    private final int height;

    ProcessedProfilePhoto(byte[] bytes, int width, int height) {
        this.bytes = bytes.clone();
        this.width = width;
        this.height = height;
    }

    public byte[] bytes() { return bytes.clone(); }
    public String contentType() { return "image/jpeg"; }
    public int width() { return width; }
    public int height() { return height; }

    @Override
    public String toString() { return "ProcessedProfilePhoto[image content redacted]"; }
}
