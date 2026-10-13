package com.firesphere.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesphere.backend.common.exception.GlobalExceptionHandler;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest(classes = {FiresphereBackendApplication.class, FoundationTests.TestController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
public class FoundationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testOpenApiEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void testValidationError() throws Exception {
        String invalidRequest = "{\"name\":\"\"}"; // name is blank

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void testUnsupportedOperationException() throws Exception {
        mockMvc.perform(get("/test/unsupported"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_OPERATION"))
                .andExpect(jsonPath("$.message").value("This operation is not supported"));
    }

    @Test
    void testGeneralException() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    public static class TestController {

        public record DummyRequest(@NotBlank String name) {}

        @PostMapping("/test/validation")
        public String testValidation(@Valid @RequestBody DummyRequest request) {
            return "ok";
        }

        @GetMapping("/test/unsupported")
        public String testUnsupported() {
            throw new UnsupportedOperationException("This operation is not supported");
        }

        @GetMapping("/test/error")
        public String testError() {
            throw new RuntimeException("Something went wrong internally");
        }
    }
}
