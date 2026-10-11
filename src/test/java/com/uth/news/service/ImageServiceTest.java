package com.uth.news.service;

import com.uth.news.ImageFixtures;
import com.uth.news.constant.AppConstants;
import com.uth.news.exception.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class ImageServiceTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png", "webp"})
    void validImagesAreDecodedAndStoredWithUniqueSafeNames(String format) throws Exception {
        ImageService service = new ImageService(directory.toString(), "http://localhost:8080/");
        byte[] bytes = ImageFixtures.image(format);
        var file = new MockMultipartFile("file", "../../outside.exe", "image/" + format, bytes);
        String first = service.upload(file).imageUrl(), second = service.upload(file).imageUrl();
        String extension = format.equals("jpeg") ? "jpg" : format;
        assertThat(first).startsWith("http://localhost:8080/uploads/").endsWith("." + extension);
        assertThat(first).isNotEqualTo(second);
        String filename = first.substring(first.lastIndexOf('/') + 1);
        assertThat(UUID.fromString(filename.substring(0, filename.lastIndexOf('.')))).isNotNull();
        assertThat(service.find(filename)).hasParent(directory.toRealPath());
        assertThat(Files.readAllBytes(service.find(filename))).isEqualTo(bytes);
        assertThat(directory.resolve("outside.exe")).doesNotExist();
    }

    @Test
    void rejectsEmptyUnsupportedSpoofedAndTruncatedFilesWithoutSaving() throws Exception {
        ImageService service = new ImageService(directory.toString(), "http://localhost");
        assertStatus(service, new MockMultipartFile("file", "empty.png", "image/png", new byte[0]), HttpStatus.BAD_REQUEST);
        assertStatus(service, new MockMultipartFile("file", "text.jpg", "image/jpeg", "plain text".getBytes()), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(service, new MockMultipartFile("file", "fake.jpg", "image/jpeg", ImageFixtures.image("png")), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(service, new MockMultipartFile("file", "image.png", "text/plain", ImageFixtures.image("png")), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(service, new MockMultipartFile("file", "image.png", null, ImageFixtures.image("png")), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(service, new MockMultipartFile("file", "fake.png", "image/png", ImageFixtures.image("gif")), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        for (String format : List.of("jpeg", "png", "webp")) {
            byte[] valid = ImageFixtures.image(format);
            assertStatus(service, new MockMultipartFile("file", "image." + format, "image/" + format,
                    Arrays.copyOf(valid, valid.length / 2)), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test
    void rejectsSizeAboveLimitIncludingMisreportedSizeAndAcceptsExactLimit() throws Exception {
        ImageService service = new ImageService(directory.toString(), "http://localhost");
        byte[] large = new byte[(int) AppConstants.MAX_IMAGE_SIZE_BYTES + 1];
        assertStatus(service, new MockMultipartFile("file", "large.png", "image/png", large), HttpStatus.PAYLOAD_TOO_LARGE);
        var misreported = new MockMultipartFile("file", "large.png", "image/png", large) {
            @Override public long getSize() { return 1; }
        };
        assertStatus(service, misreported, HttpStatus.PAYLOAD_TOO_LARGE);
        byte[] exact = Arrays.copyOf(ImageFixtures.image("png"), (int) AppConstants.MAX_IMAGE_SIZE_BYTES);
        assertThat(service.upload(new MockMultipartFile("file", "exact.png", "image/png", exact)).imageUrl()).endsWith(".png");
    }

    @Test
    void rejectsExcessiveDecodedDimensionsBeforeAllocatingPixels() throws Exception {
        ImageService service = new ImageService(directory.toString(), "http://localhost");
        byte[] bytes = ImageFixtures.image("png");
        // PNG IHDR: adjust width/height and its CRC while preserving a valid header.
        java.nio.ByteBuffer.wrap(bytes).putInt(16, 26_000_000).putInt(20, 1);
        var crc = new java.util.zip.CRC32();
        crc.update(bytes, 12, 17);
        java.nio.ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        assertStatus(service, new MockMultipartFile("file", "wide.png", "image/png", bytes), HttpStatus.BAD_REQUEST);
        try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
    }

    @Test
    void onlyGeneratedImageNamesCanBeRead() throws Exception {
        ImageService service = new ImageService(directory.toString(), "http://localhost");
        Files.writeString(directory.resolve("private.txt"), "private");
        for (String filename : List.of("../private.txt", "..\\private.txt", "private.txt",
                "00000000-0000-0000-0000-000000000000.png")) {
            assertThatThrownBy(() -> service.find(filename)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    private void assertStatus(ImageService service, MockMultipartFile file, HttpStatus status) {
        assertThatThrownBy(() -> service.upload(file)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(status));
    }
}
