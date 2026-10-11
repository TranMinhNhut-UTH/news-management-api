package com.uth.news;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class ImageFixtures {
    private ImageFixtures() { }

    public static byte[] image(String format) throws IOException {
        if (format.equals("webp")) {
            // A complete 1x1 VP8 image; the test uses the real WebP decoder.
            return Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
}
