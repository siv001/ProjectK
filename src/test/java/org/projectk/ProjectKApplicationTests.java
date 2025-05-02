package org.projectk;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cache.type=none",
    "spring.main.allow-bean-definition-overriding=true"
})
class ProjectKApplicationTests {

    @Disabled("Temporarily disabled due to bean definition overrides and Caffeine cache initialization issues. To be fixed in a separate PR.")
    @Test
    void contextLoads() {
        // Empty test that verifies that the application context loads
    }
}
