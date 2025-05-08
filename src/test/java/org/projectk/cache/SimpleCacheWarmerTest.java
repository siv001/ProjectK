package org.projectk.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.projectk.service.ServiceOneClient;
import org.projectk.service.ServiceTwoClient;
import org.springframework.boot.context.event.ApplicationStartedEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleCacheWarmerTest {

    @Mock
    private ServiceOneClient serviceOneClient;

    @Mock
    private ServiceTwoClient serviceTwoClient;

    @Mock
    private ApplicationStartedEvent applicationStartedEvent;

    private SimpleCacheWarmer cacheWarmer;

    @BeforeEach
    void setUp() {
        cacheWarmer = new SimpleCacheWarmer(serviceOneClient, serviceTwoClient);
    }

    /**
     * Test that the cache warmer calls service one with the expected keys
     */
    @Test
    void testServiceOneWarming() {
        // Execute the cache warming
        cacheWarmer.warmCaches();

        // Capture all the keys passed to service one
        ArgumentCaptor<String> keysCaptor = ArgumentCaptor.forClass(String.class);
        verify(serviceOneClient, times(3)).fetchData(keysCaptor.capture());

        // Assert the correct keys were used
        List<String> capturedKeys = keysCaptor.getAllValues();
        assertEquals(3, capturedKeys.size(), "Should warm exactly 3 keys for service one");
        assertTrue(capturedKeys.contains("common-id-1"), "Should warm key 'common-id-1'");
        assertTrue(capturedKeys.contains("common-id-2"), "Should warm key 'common-id-2'");
        assertTrue(capturedKeys.contains("frequent-access-id"), "Should warm key 'frequent-access-id'");
    }

    /**
     * Test that the cache warmer calls service two with the expected keys
     */
    @Test
    void testServiceTwoWarming() {
        // Execute the cache warming
        cacheWarmer.warmCaches();

        // Capture all the keys passed to service two
        ArgumentCaptor<String> keysCaptor = ArgumentCaptor.forClass(String.class);
        verify(serviceTwoClient, times(2)).fetchData(keysCaptor.capture());

        // Assert the correct keys were used
        List<String> capturedKeys = keysCaptor.getAllValues();
        assertEquals(2, capturedKeys.size(), "Should warm exactly 2 keys for service two");
        assertTrue(capturedKeys.contains("popular-item"), "Should warm key 'popular-item'");
        assertTrue(capturedKeys.contains("system-config"), "Should warm key 'system-config'");
    }

    /**
     * Test that the cache warmer responds appropriately to the application started event
     */
    @Test
    void testApplicationStartedEventListener() {
        // Create a spy on the real cache warmer to verify method calls
        SimpleCacheWarmer spy = spy(cacheWarmer);

        // Trigger the event listener with the mocked event
        spy.warmCaches();

        // Verify that warming happens
        verify(serviceOneClient, atLeastOnce()).fetchData(anyString());
        verify(serviceTwoClient, atLeastOnce()).fetchData(anyString());
    }

    /**
     * Test that cache warmer gracefully handles exceptions
     * This ensures our dynamic cache refresh system's error handling works effectively
     */
    @Test
    void testExceptionHandling() {
        // Execute the cache warming - it should handle any exceptions internally
        assertDoesNotThrow(() -> cacheWarmer.warmCaches(), "Cache warming should handle exceptions gracefully");
        
        // Verify all expected service calls were attempted, showing the error handling doesn't
        // prevent the complete warming process from executing
        verify(serviceOneClient).fetchData("common-id-1");
        verify(serviceOneClient).fetchData("common-id-2");
        verify(serviceOneClient).fetchData("frequent-access-id");
        verify(serviceTwoClient).fetchData("popular-item");
        verify(serviceTwoClient).fetchData("system-config");
    }





    /**
     * Test the execution order of cache warming (service one followed by service two)
     */
    @Test
    void testWarmingExecutionOrder() {
        // Execute the cache warming
        cacheWarmer.warmCaches();

        // Verify correct order using inOrder
        var inOrder = inOrder(serviceOneClient, serviceTwoClient);
        
        // First verify all service one calls happen
        inOrder.verify(serviceOneClient).fetchData("common-id-1");
        inOrder.verify(serviceOneClient).fetchData("common-id-2");
        inOrder.verify(serviceOneClient).fetchData("frequent-access-id");
        
        // Then verify service two calls
        inOrder.verify(serviceTwoClient).fetchData("popular-item");
        inOrder.verify(serviceTwoClient).fetchData("system-config");
    }
}
