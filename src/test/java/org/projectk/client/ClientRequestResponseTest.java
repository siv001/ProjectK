package org.projectk.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientRequest and ClientResponse classes
 * These tests ensure 100% coverage of these data classes
 */
class ClientRequestResponseTest {

    @Test
    void testClientRequest_withAllFields() {
        // Arrange & Act
        String url = "http://example.com/api";
        HttpMethod method = HttpMethod.POST;
        String body = "test payload";
        ClientRequest<String> request = new ClientRequest<>(url, method, body);
        
        // Assert
        assertEquals(url, request.url());
        assertEquals(method, request.method());
        assertEquals(body, request.requestBody());
    }
    
    @Test
    void testClientRequest_withNullBody() {
        // Arrange & Act
        String url = "http://example.com/api";
        HttpMethod method = HttpMethod.GET;
        ClientRequest<String> request = new ClientRequest<>(url, method, null);
        
        // Assert
        assertEquals(url, request.url());
        assertEquals(method, request.method());
        assertNull(request.requestBody());
    }
    
    @Test
    void testClientRequest_equality() {
        // Arrange
        ClientRequest<String> request1 = new ClientRequest<>("http://example.com", HttpMethod.GET, "body");
        ClientRequest<String> request2 = new ClientRequest<>("http://example.com", HttpMethod.GET, "body");
        ClientRequest<String> request3 = new ClientRequest<>("http://other.com", HttpMethod.GET, "body");
        ClientRequest<String> request4 = new ClientRequest<>("http://example.com", HttpMethod.POST, "body");
        ClientRequest<String> request5 = new ClientRequest<>("http://example.com", HttpMethod.GET, "different");
        
        // Assert
        assertEquals(request1, request2); // Same values
        assertNotEquals(request1, request3); // Different URL
        assertNotEquals(request1, request4); // Different method
        assertNotEquals(request1, request5); // Different body
        assertEquals(request1.hashCode(), request2.hashCode()); // Same hashCode
    }
    
    @Test
    void testClientRequest_toString() {
        // Arrange
        ClientRequest<String> request = new ClientRequest<>("http://example.com", HttpMethod.GET, "body");
        
        // Act
        String result = request.toString();
        
        // Assert
        assertTrue(result.contains("http://example.com"));
        assertTrue(result.contains("GET"));
        assertTrue(result.contains("body"));
    }
    
    @Test
    void testClientResponse_withSuccessResponse() {
        // Arrange & Act
        String response = "Success data";
        ClientResponse<String> clientResponse = new ClientResponse<>(response, null, 200);
        
        // Assert
        assertEquals(response, clientResponse.response());
        assertNull(clientResponse.error());
        assertEquals(200, clientResponse.statusCode());
    }
    
    @Test
    void testClientResponse_withErrorResponse() {
        // Arrange & Act
        String errorMessage = "Not found";
        ClientResponse<String> clientResponse = new ClientResponse<>(null, errorMessage, 404);
        
        // Assert
        assertNull(clientResponse.response());
        assertEquals(errorMessage, clientResponse.error());
        assertEquals(404, clientResponse.statusCode());
    }
    
    @Test
    void testClientResponse_equality() {
        // Arrange
        ClientResponse<String> response1 = new ClientResponse<>("data", null, 200);
        ClientResponse<String> response2 = new ClientResponse<>("data", null, 200);
        ClientResponse<String> response3 = new ClientResponse<>("other", null, 200);
        ClientResponse<String> response4 = new ClientResponse<>("data", "error", 200);
        ClientResponse<String> response5 = new ClientResponse<>("data", null, 404);
        
        // Assert
        assertEquals(response1, response2); // Same values
        assertNotEquals(response1, response3); // Different data
        assertNotEquals(response1, response4); // Different error
        assertNotEquals(response1, response5); // Different status code
        assertEquals(response1.hashCode(), response2.hashCode()); // Same hashCode
    }
    
    @Test
    void testClientResponse_toString() {
        // Arrange
        ClientResponse<String> successResponse = new ClientResponse<>("data", null, 200);
        ClientResponse<String> errorResponse = new ClientResponse<>(null, "Error occurred", 500);
        
        // Act
        String successString = successResponse.toString();
        String errorString = errorResponse.toString();
        
        // Assert
        assertTrue(successString.contains("data"));
        assertTrue(successString.contains("200"));
        assertTrue(errorString.contains("Error occurred"));
        assertTrue(errorString.contains("500"));
    }
}
