package com.uth.news.controller;

import com.uth.news.constant.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import com.uth.news.dto.request.*;
import com.uth.news.dto.response.*;
import com.uth.news.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping(AppConstants.API_PREFIX + "/auth")
@SecurityRequirements
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    @Operation(summary = "Register a user")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Registered successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Registered successfully\",\"data\":{\"id\":1,\"username\":\"writer\"}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registered successfully", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and obtain JWT")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged in successfully", useReturnTypeSchema = true,
                content = @Content(mediaType = "application/json", examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Logged in successfully\",\"data\":{\"accessToken\":\"<JWT>\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Logged in successfully", authService.login(request));
    }
}
