package com.uth.news.service;

import com.uth.news.constant.AppConstants;
import com.uth.news.dto.response.ImageUploadResponse;
import com.uth.news.exception.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {
    private static final Pattern STORED_FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
    private final Path uploadDirectory;
    private final String baseUrl;

    public ImageService(@Value("${app.upload-dir}") String uploadDirectory,
                        @Value("${app.base-url}") String baseUrl) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public ImageUploadResponse upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) { throw new BadRequestException("Image file must not be empty"); }
        if (file.getSize() > AppConstants.MAX_IMAGE_SIZE_BYTES) { throw tooLarge(); }
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes((int) AppConstants.MAX_IMAGE_SIZE_BYTES + 1);
        }
        if (bytes.length == 0) { throw new BadRequestException("Image file must not be empty"); }
        if (bytes.length > AppConstants.MAX_IMAGE_SIZE_BYTES) { throw tooLarge(); }
        String extension = validateImage(bytes, file.getContentType());
        Files.createDirectories(uploadDirectory);
        // Resolve the configured root once; never incorporate the client filename.
        Path root = uploadDirectory.toRealPath();
        Path destination = root.resolve(UUID.randomUUID() + "." + extension).normalize();
        if (!destination.getParent().equals(root)) { throw new BadRequestException("Invalid image path"); }
        OutputStream created = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try (OutputStream output = created) {
            output.write(bytes);
        } catch (IOException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
        return new ImageUploadResponse(baseUrl + "/uploads/" + destination.getFileName());
    }

    private String validateImage(byte[] bytes, String declaredType) {
        if (!AppConstants.ALLOWED_IMAGE_MIME_TYPES.contains(declaredType == null ? "" : declaredType)) {
            throw unsupported();
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw unsupported(); }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    case "webp" -> "webp";
                    default -> throw unsupported();
                };
                String actualType = extension.equals("jpg") ? "image/jpeg" : "image/" + extension;
                if (!actualType.equals(declaredType)) { throw unsupported(); }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                // Bound decoded allocation as well as compressed upload size.
                if (width <= 0 || height <= 0 || (long) width * height > AppConstants.MAX_IMAGE_PIXELS) {
                    throw new BadRequestException("Image must contain at most 25000000 pixels");
                }
                AtomicBoolean warning = new AtomicBoolean();
                reader.addIIOReadWarningListener((source, message) -> warning.set(true));
                if (reader.read(0) == null || warning.get()) { throw unsupported(); }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw unsupported();
        }
    }

    public Path find(String filename) throws IOException {
        if (!STORED_FILENAME.matcher(filename).matches()
                || !Files.isDirectory(uploadDirectory)) { throw notFound(); }
        Path root = uploadDirectory.toRealPath();
        Path path = root.resolve(filename).normalize();
        if (!path.getParent().equals(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().getParent().equals(root)) { throw notFound(); }
        return path;
    }

    private ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "File must be a valid JPEG, PNG or WebP image matching its Content-Type");
    }

    private ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image file must not exceed 5 MB");
    }

    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Image not found"); }
}
