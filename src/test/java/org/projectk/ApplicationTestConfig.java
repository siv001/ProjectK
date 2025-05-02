package org.projectk;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-specific configuration for the application test
 * Excludes cache auto-configuration to avoid Caffeine initialization issues
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    CacheAutoConfiguration.class
})
@ComponentScan(
    basePackages = "org.projectk",
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "org\\.projectk\\.config\\..*Cache.*"
        )
    }
)
public class ApplicationTestConfig {
    // No beans needed - we're just excluding problematic configurations
}
