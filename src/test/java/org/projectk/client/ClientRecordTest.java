package org.projectk.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class focusing on the ClientRequest and ClientResponse record classes
 */
public class ClientRecordTest {

    @Test
    void testClientRequestRecord() {
        // Test the ClientRequest record
        String url = "https://example.com";
        HttpMethod method = HttpMethod.PUT;
        String body = "test body";
        
        ClientRequest<String> request = new ClientRequest<>(url, method, body);
        
        assertEquals(url, request.url());
        assertEquals(method, request.method());
        assertEquals(body, request.requestBody());
        
        // Test toString contains all fields
        String toString = request.toString();
        assertTrue(toString.contains(url));
        assertTrue(toString.contains(method.toString()));
        assertTrue(toString.contains(body));
        
        // Test equals with same values
        ClientRequest<String> request2 = new ClientRequest<>(url, method, body);
        assertEquals(request, request2);
        assertEquals(request.hashCode(), request2.hashCode());
        
        // Test not equals
        ClientRequest<String> request3 = new ClientRequest<>(url, HttpMethod.POST, body);
        assertNotEquals(request, request3);
        
        // Test with null body
        ClientRequest<String> requestNullBody = new ClientRequest<>(url, method, null);
        assertNull(requestNullBody.requestBody());
        
        // Test different URLs
        ClientRequest<String> requestDiffUrl = new ClientRequest<>("https://different.com", method, body);
        assertNotEquals(request, requestDiffUrl);
    }
    
    @Test
    void testClientResponseRecord() {
        // Test the ClientResponse record
        String responseBody = "response data";
        String error = "test error";
        int statusCode = 418;
        
        ClientResponse<String> response = new ClientResponse<>(responseBody, error, statusCode);
        
        assertEquals(responseBody, response.response());
        assertEquals(error, response.error());
        assertEquals(statusCode, response.statusCode());
        
        // Test toString contains all fields
        String toString = response.toString();
        assertTrue(toString.contains(responseBody));
        assertTrue(toString.contains(error));
        assertTrue(toString.contains(Integer.toString(statusCode)));
        
        // Test equals with same values
        ClientResponse<String> response2 = new ClientResponse<>(responseBody, error, statusCode);
        assertEquals(response, response2);
        assertEquals(response.hashCode(), response2.hashCode());
        
        // Test not equals with different values
        ClientResponse<String> response3 = new ClientResponse<>(responseBody, "different error", statusCode);
        assertNotEquals(response, response3);
        
        ClientResponse<String> response4 = new ClientResponse<>("different response", error, statusCode);
        assertNotEquals(response, response4);
        
        ClientResponse<String> response5 = new ClientResponse<>(responseBody, error, 200);
        assertNotEquals(response, response5);
        
        // Test with null values
        ClientResponse<String> responseNullBody = new ClientResponse<>(null, error, statusCode);
        assertNull(responseNullBody.response());
        
        ClientResponse<String> responseNullError = new ClientResponse<>(responseBody, null, statusCode);
        assertNull(responseNullError.error());
    }
}
