package com.uth.news.service;

import com.uth.news.exception.*;
import com.uth.news.repository.NewsRepository;
import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageDeletionService {
    private static final Pattern FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");
    private final Path directory;
    private final NewsRepository newsRepository;

    public ImageDeletionService(@Value("${app.upload-dir}") String directory, NewsRepository newsRepository) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.newsRepository = newsRepository;
    }

    public void delete(String filename) throws IOException {
        if (filename == null || !FILENAME.matcher(filename).matches()) {
            throw new BadRequestException("Invalid managed image filename");
        }
        // Reject symlinks in the configured root and its ancestors, not just the file.
        if (Files.isSymbolicLink(directory)) { throw unsafePath(); }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { throw notFound(); }
        Path root = directory.toRealPath();
        if (!root.equals(directory)) { throw unsafePath(); }
        Path image = root.resolve(filename).normalize();
        validatePath(root, image);

        // Match the public path independently of APP_BASE_URL (including old hosts
        // and URLs with query strings/fragments), so changing the base URL is safe.
        String suffix = "%/uploads/" + filename;
        if (newsRepository.isImageReferenced(suffix, suffix + "?%", suffix + "#%")) {
            throw new ConflictException("Image is referenced by a news article");
        }
        validatePath(root, image);
        try {
            Files.delete(image);
        } catch (NoSuchFileException exception) {
            throw notFound();
        }
    }

    private void validatePath(Path root, Path image) throws IOException {
        if (!image.getParent().equals(root) || Files.isSymbolicLink(image)) { throw unsafePath(); }
        if (!Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS)) { throw notFound(); }
        if (!directory.toRealPath().equals(root) || !image.toRealPath().getParent().equals(root)) {
            throw unsafePath();
        }
    }

    private BadRequestException unsafePath() { return new BadRequestException("Unsafe image path"); }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("Image not found"); }
}
