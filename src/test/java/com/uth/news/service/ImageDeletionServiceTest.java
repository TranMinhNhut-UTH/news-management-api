package com.uth.news.service;

import com.uth.news.ImageFixtures;
import com.uth.news.exception.*;
import com.uth.news.repository.NewsRepository;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageDeletionServiceTest {
    @TempDir Path directory;
    private final NewsRepository repository = mock(NewsRepository.class);
    private static final String NAME = "00000000-0000-0000-0000-000000000001.png";

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png", "webp"})
    void deletesRealUploadedImages(String format) throws Exception {
        var upload = new ImageService(directory.toString(), "http://old-host:8080");
        String url = upload.upload(new MockMultipartFile("file", "image", "image/" + format,
                ImageFixtures.image(format))).imageUrl();
        String filename = java.net.URI.create(url).getPath().substring("/uploads/".length());
        new ImageDeletionService(directory.toString(), repository).delete(filename);
        assertThat(directory.resolve(filename)).doesNotExist();
        String pattern = "%/uploads/" + filename;
        verify(repository).isImageReferenced(pattern, pattern + "?%", pattern + "#%");
    }

    @Test
    void referencedImageIsRetainedAndConflictIsReturned() throws Exception {
        Path image = Files.write(directory.resolve(NAME), ImageFixtures.image("png"));
        when(repository.isImageReferenced("%/uploads/" + NAME, "%/uploads/" + NAME + "?%",
                "%/uploads/" + NAME + "#%")).thenReturn(true);
        assertThatThrownBy(() -> new ImageDeletionService(directory.toString(), repository).delete(NAME))
                .isInstanceOf(ConflictException.class);
        assertThat(image).exists();
    }

    @Test
    void missingFileDirectoryOrNonRegularFileReturns404() throws Exception {
        var service = new ImageDeletionService(directory.toString(), repository);
        assertThatThrownBy(() -> service.delete(NAME)).isInstanceOf(ResourceNotFoundException.class);
        Files.createDirectory(directory.resolve(NAME));
        assertThatThrownBy(() -> service.delete(NAME)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> new ImageDeletionService(directory.resolve("missing").toString(), repository)
                .delete(NAME)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.png", "..\\outside.png", "/tmp/outside.png", "C:\\outside.png",
            "private.txt", "00000000-0000-0000-0000-000000000001.exe",
            "00000000-0000-0000-0000-000000000001.png:stream", "%2e%2e%2foutside.png", "image.png", ""})
    void rejectsInvalidNamesWithoutTouchingFilesOrDatabase(String filename) throws Exception {
        Path sentinel = Files.writeString(directory.resolve("private.txt"), "keep");
        assertThatThrownBy(() -> new ImageDeletionService(directory.toString(), repository).delete(filename))
                .isInstanceOf(BadRequestException.class);
        assertThat(Files.readString(sentinel)).isEqualTo("keep");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFileAndRootSymlinksWithoutDeletingAnything() throws Exception {
        Path root = directory.toRealPath();
        Path image = Files.write(root.resolve(NAME), ImageFixtures.image("png"));
        // Windows may not grant symlink creation privileges; simulate the filesystem
        // symlink flag while keeping all other filesystem operations real.
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.isSymbolicLink(image)).thenReturn(true);
            assertThatThrownBy(() -> new ImageDeletionService(root.toString(), repository).delete(NAME))
                    .isInstanceOf(BadRequestException.class);
            filesystem.verify(() -> Files.delete(image), never());
        }
        try (var filesystem = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            filesystem.when(() -> Files.isSymbolicLink(root)).thenReturn(true);
            assertThatThrownBy(() -> new ImageDeletionService(root.toString(), repository).delete(NAME))
                    .isInstanceOf(BadRequestException.class);
            filesystem.verify(() -> Files.delete(image), never());
        }
        assertThat(image).exists();
        verifyNoInteractions(repository);
    }

    @Test
    void filesystemFailureDoesNotReportSuccessOrDeleteImage() throws Exception {
        Path root = directory.toRealPath();
        Path image = Files.write(root.resolve(NAME), ImageFixtures.image("png"));
        try (var filesystem = mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("delete") && image.equals(invocation.getArgument(0))) {
                throw new AccessDeniedException("private details");
            }
            return invocation.callRealMethod();
        })) {
            assertThatThrownBy(() -> new ImageDeletionService(root.toString(), repository).delete(NAME))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(image).exists();
    }
}
