package com.firesphere.backend;

import com.firesphere.backend.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "cors.allowed-origins=https://example.com",
    "AI_SERVICE_BASE_URL=http://dummy",
    "AI_SERVICE_API_KEY=dummy",
    "JWT_SECRET=dummySecretLengthGreaterThan32Bytes!"
})
@ActiveProfiles({"test", "prod-simulation"})
public class CorsProductionSafetyTests {

    @Autowired
    private SecurityConfig securityConfig;

    @Test
    @SuppressWarnings("unchecked")
    void corsConfigurationDoesNotSilentlyFallbackToUnrestricted() {
        // Retrieve the injected allowedOrigins field using Reflection
        List<String> allowedOrigins = (List<String>) ReflectionTestUtils.getField(securityConfig, "allowedOrigins");
        
        // Assert that the origins are exactly what is explicitly configured
        // and importantly, it does not silently become "*" 
        assertThat(allowedOrigins).isNotNull();
        assertThat(allowedOrigins).containsExactly("https://example.com");
        assertThat(allowedOrigins).doesNotContain("*");
    }
}
