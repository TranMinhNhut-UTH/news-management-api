package com.uth.news.controller;

import com.uth.news.constant.AppConstants;
import com.uth.news.dto.response.*;
import com.uth.news.service.ImageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.io.IOException;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageController {
    private final ImageService service;

    public ImageController(ImageService service) { this.service = service; }

    @PostMapping(value = AppConstants.API_PREFIX + "/news/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", service.upload(file)));
    }

    @GetMapping("/uploads/{filename}")
    @SecurityRequirements
    public ResponseEntity<Resource> get(@PathVariable String filename) throws IOException {
        var path = service.find(filename);
        MediaType type = filename.endsWith(".jpg") ? MediaType.IMAGE_JPEG
                : filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.valueOf("image/webp");
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new FileSystemResource(path));
    }
}
