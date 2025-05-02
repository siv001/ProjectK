package org.projectk.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientRequest and ClientResponse record classes.
 * These tests ensure all record methods are properly covered.
 */
public class ClientRecordsTest {

    @Test
    void testClientRequestAllFields() {
        // Create request with all fields populated
        String url = "https://api.example.com/test";
        HttpMethod method = HttpMethod.POST;
        String body = "test-body";
        
        ClientRequest<String> request = new ClientRequest<>(url, method, body);
        
        // Verify accessor methods
        assertEquals(url, request.url());
        assertEquals(method, request.method());
        assertEquals(body, request.requestBody());
    }
    
    @Test
    void testClientRequestWithNullBody() {
        ClientRequest<String> request = new ClientRequest<>("https://api.example.com", HttpMethod.GET, null);
        
        assertNull(request.requestBody());
        assertEquals(HttpMethod.GET, request.method());
        assertEquals("https://api.example.com", request.url());
    }
    
    @Test
    void testClientRequestEquality() {
        ClientRequest<String> request1 = new ClientRequest<>("https://api.example.com", HttpMethod.GET, "body");
        ClientRequest<String> request2 = new ClientRequest<>("https://api.example.com", HttpMethod.GET, "body");
        ClientRequest<String> request3 = new ClientRequest<>("https://api.example.com", HttpMethod.POST, "body");
        
        // Test equals and hashCode
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        
        assertNotEquals(request1, request3);
    }
    
    @Test
    void testClientRequestToString() {
        ClientRequest<String> request = new ClientRequest<>("https://api.example.com", HttpMethod.GET, "body");
        
        String toString = request.toString();
        
        // Verify toString contains all field information
        assertTrue(toString.contains("https://api.example.com"));
        assertTrue(toString.contains("GET"));
        assertTrue(toString.contains("body"));
    }
    
    @Test
    void testClientResponseAllFields() {
        // Create response with all fields populated
        String responseBody = "response-data";
        String error = "no-error";
        int statusCode = 200;
        
        ClientResponse<String> response = new ClientResponse<>(responseBody, error, statusCode);
        
        // Verify accessor methods
        assertEquals(responseBody, response.response());
        assertEquals(error, response.error());
        assertEquals(statusCode, response.statusCode());
    }
    
    @Test
    void testClientResponseWithNullResponse() {
        ClientResponse<String> response = new ClientResponse<>(null, "Error occurred", 500);
        
        assertNull(response.response());
        assertEquals("Error occurred", response.error());
        assertEquals(500, response.statusCode());
    }
    
    @Test
    void testClientResponseWithNullError() {
        ClientResponse<String> response = new ClientResponse<>("Success", null, 200);
        
        assertEquals("Success", response.response());
        assertNull(response.error());
        assertEquals(200, response.statusCode());
    }
    
    @Test
    void testClientResponseEquality() {
        ClientResponse<String> response1 = new ClientResponse<>("data", null, 200);
        ClientResponse<String> response2 = new ClientResponse<>("data", null, 200);
        ClientResponse<String> response3 = new ClientResponse<>("data", "error", 500);
        
        // Test equals and hashCode
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
        
        assertNotEquals(response1, response3);
    }
    
    @Test
    void testClientResponseToString() {
        ClientResponse<String> response = new ClientResponse<>("data", "error", 404);
        
        String toString = response.toString();
        
        // Verify toString contains all field information
        assertTrue(toString.contains("data"));
        assertTrue(toString.contains("error"));
        assertTrue(toString.contains("404"));
    }
}
