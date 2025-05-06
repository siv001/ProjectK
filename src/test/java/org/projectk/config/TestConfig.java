package org.projectk.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.BeforeEach;

@ContextConfiguration(classes = {CacheConfig.class})
@SpringBootTest
public class TestConfig {
    
    @Autowired
    private CacheConfig cacheConfig;
    
    @BeforeEach
    public void beforeEach() {
        // Reset any state before each test
        cacheConfig.getActiveCacheKeys().clear();
    }
}
