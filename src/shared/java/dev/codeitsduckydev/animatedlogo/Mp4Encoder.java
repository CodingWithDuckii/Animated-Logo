package dev.codeitsduckydev.animatedlogo;

import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Thin wrapper around JCodec's {@link AWTSequenceEncoder} (pure Java H.264
 * muxing, no native libraries) so the recording code only deals with paths
 * and images. The video is written in the caller's background thread.
 */
final class Mp4Encoder {
    private final AWTSequenceEncoder delegate;

    private Mp4Encoder(AWTSequenceEncoder delegate) {
        this.delegate = delegate;
    }

    /** Opens {@code output} for writing at {@code fps} frames per second. */
    static Mp4Encoder open(Path output, int fps) throws IOException {
        return new Mp4Encoder(AWTSequenceEncoder.createSequenceEncoder(output.toFile(), fps));
    }

    void encode(BufferedImage image) throws IOException {
        this.delegate.encodeImage(image);
    }

    void finish() throws IOException {
        this.delegate.finish();
    }

    /**
     * Best-effort resource release after a failed/cancelled encode. The
     * caller deletes the partial output file itself.
     */
    void abort() {
        try {
            this.delegate.finish();
        } catch (IOException e) {
            AnimatedLogo.LOGGER.debug("Failed to finalize aborted recording", e);
        }
    }
}
