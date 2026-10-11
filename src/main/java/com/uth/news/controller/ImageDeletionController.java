package com.uth.news.controller;

import com.uth.news.constant.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import com.uth.news.dto.response.ApiResponse;
import com.uth.news.service.ImageDeletionService;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Images")
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/news/images")
public class ImageDeletionController {
    private final ImageDeletionService service;

    public ImageDeletionController(ImageDeletionService service) { this.service = service; }

    @DeleteMapping("/{filename}")
    @Operation(summary = "Delete an image not referenced by News", description = "Managed UUID filename only. Rejects traversal/symlinks; returns 409 while News references the image.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Image deleted successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Image deleted successfully\",\"data\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<Void> delete(@PathVariable String filename) throws IOException {
        service.delete(filename);
        return ApiResponse.success("Image deleted successfully", null);
    }
}
