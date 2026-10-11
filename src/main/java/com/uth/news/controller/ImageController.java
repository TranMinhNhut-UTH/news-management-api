package com.uth.news.controller;

import com.uth.news.constant.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import com.uth.news.dto.response.*;
import com.uth.news.service.ImageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.io.IOException;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Images")
@RestController
public class ImageController {
    private final ImageService service;

    public ImageController(ImageService service) { this.service = service; }

    @PostMapping(value = AppConstants.API_PREFIX + "/news/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload JPEG, PNG or WebP", description = "Multipart field file; max 5 MB, request max 6 MB, max 25000000 decoded pixels. Actual image content must match Content-Type. Returns an independent URL to use as News.imageUrl.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Image uploaded successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Image uploaded successfully\",\"data\":{\"imageUrl\":\"http://localhost:8080/uploads/00000000-0000-0000-0000-000000000001.png\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", ref = "#/components/responses/PayloadTooLarge"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded successfully", service.upload(file)));
    }

    @GetMapping("/uploads/{filename}")
    @SecurityRequirements
    @Operation(summary = "Read a public uploaded image")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image bytes", content = {@Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")), @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")), @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))}),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Resource> get(@PathVariable String filename) throws IOException {
        var path = service.find(filename);
        MediaType type = filename.endsWith(".jpg") ? MediaType.IMAGE_JPEG
                : filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.valueOf("image/webp");
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new FileSystemResource(path));
    }
}
