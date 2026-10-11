package com.uth.news.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.examples.Example;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenApiCustomizer jsonResponseSchemas() {
        // Controllers infer */* from their return types. Keep that typed schema
        // alongside the explicitly documented JSON examples, without changing runtime negotiation.
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().values().forEach(response -> {
                    Content content = response.getContent();
                    if (content != null && content.containsKey("application/json") && content.containsKey("*/*")) {
                        MediaType json = content.get("application/json");
                        if (json.getSchema() == null) { json.setSchema(content.get("*/*").getSchema()); }
                        content.remove("*/*");
                    }
                })));
    }

    @Bean
    public OpenAPI openAPI() {
        Components components = new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Login to obtain accessToken. Paste the token only; Swagger adds Bearer. Public routes need no token, but an invalid Authorization header returns 401."));
        Schema<?> failure = new ObjectSchema()
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("message", new StringSchema().example("News not found"))
                .addProperty("data", new ObjectSchema().nullable(true).description("Always null for errors"))
                .additionalProperties(false)
                .required(List.of("success", "message", "data"));
        components.addSchemas("ApiFailure", failure);
        components.addSchemas("ValidationFailure", new ObjectSchema()
                .addProperty("success", new BooleanSchema().example(false))
                .addProperty("message", new StringSchema().example("Validation failed"))
                .addProperty("data", new ObjectSchema().nullable(true))
                .addProperty("errors", new ObjectSchema().additionalProperties(new ArraySchema().items(new StringSchema())))
                .additionalProperties(false)
                .required(List.of("success", "message", "data", "errors")));
        addError(components, "BadRequest", "Invalid body, query, path or multipart input", "Bad Request", true);
        addError(components, "Unauthorized", "Missing, invalid or expired JWT; invalid credentials. Also possible on public routes when an invalid Authorization header is sent.", "Authentication required", false);
        components.getResponses().get("Unauthorized").addHeaderObject("WWW-Authenticate",
                new io.swagger.v3.oas.models.headers.Header().schema(new StringSchema().example("Bearer")));
        addError(components, "Forbidden", "Only the author may update or delete this news", "Only the author can modify this news", false);
        addError(components, "NotFound", "News or image does not exist", "News not found", false);
        addError(components, "Conflict", "Duplicate username, database constraint conflict or image referenced by News", "Image is referenced by a news article", false);
        addError(components, "PayloadTooLarge", "Image exceeds 5 MB or multipart request exceeds 6 MB", "Image file must not exceed 5 MB", false);
        addError(components, "UnsupportedMediaType", "Unsupported request Content-Type or invalid/mismatched image content", "Unsupported Media Type", false);
        addError(components, "InternalServerError", "Unexpected server, database or filesystem failure", "Internal server error", false);
        return new OpenAPI().info(new Info().title("News Management API").version("v1"))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    private void addError(Components components, String name, String description, String message, boolean validation) {
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ApiFailure");
        if (validation) {
            schema = new ComposedSchema().oneOf(List.of(schema,
                    new Schema<>().$ref("#/components/schemas/ValidationFailure")));
        }
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", false);
        example.put("message", message);
        example.put("data", null);
        var media = new MediaType().schema(schema).addExamples("error", new Example().value(example));
        if (validation) {
            Map<String, Object> validationExample = new LinkedHashMap<>(example);
            validationExample.put("message", "Validation failed");
            validationExample.put("errors", Map.of("title", List.of("must not be blank")));
            media.addExamples("validation", new Example().value(validationExample));
        }
        components.addResponses(name, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", media)));
    }
}
