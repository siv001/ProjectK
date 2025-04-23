package org.projectk.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommonWebClientTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @InjectMocks
    private CommonWebClient commonWebClient;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Test
    void testSuccessfulServiceCall() {
        // Arrange
        String responseData = "Success Response";
        ClientRequest<String> request = new ClientRequest<>("http://test.com", "testBody");

        // Mock the WebClient chain
        WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class, RETURNS_SELF);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseData));

        // Act
        CompletableFuture<ClientResponse<String>> future =
            commonWebClient.callService(request, String.class);

        // Assert
        ClientResponse<String> response = future.join();
        assertNotNull(response);
        assertEquals(responseData, response.getPayload());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getErrorMessage());

        // Verify interactions
        verify(webClient).post();
        verify(requestSpec).uri(request.getUrl());
        verify(requestSpec).bodyValue(request.getRequestBody());
        verify(requestSpec).retrieve();
    }

    @Test
    void testServiceCallWithError() {
        // Arrange
        ClientRequest<String> request = new ClientRequest<>("http://test.com", "testBody");
        String errorMessage = "Service Unavailable";

        // Mock the WebClient chain
        WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class, RETURNS_SELF);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
            .thenReturn(Mono.error(new RuntimeException(errorMessage)));

        // Act
        CompletableFuture<ClientResponse<String>> future =
            commonWebClient.callService(request, String.class);

        // Assert
        ClientResponse<String> response = future.join();
        assertNotNull(response);
        assertNull(response.getPayload());
        assertEquals(500, response.getStatusCode());
        assertEquals(errorMessage, response.getErrorMessage());
    }

    @Test
    void testServiceCallWithCustomResponseType() {
        // Arrange
        TestResponse expectedResponse = new TestResponse("test data");
        ClientRequest<String> request = new ClientRequest<>("http://test.com", "testBody");

        // Mock the WebClient chain
        WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class, RETURNS_SELF);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TestResponse.class))
            .thenReturn(Mono.just(expectedResponse));

        // Act
        CompletableFuture<ClientResponse<TestResponse>> future =
            commonWebClient.callService(request, TestResponse.class);

        // Assert
        ClientResponse<TestResponse> response = future.join();
        assertNotNull(response);
        assertEquals(expectedResponse, response.getPayload());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getErrorMessage());
    }

    @Test
    void testServiceCallWithNullRequest() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            commonWebClient.callService(null, String.class));
    }

    @Test
    void testServiceCallWithNullResponseType() {
        // Arrange
        ClientRequest<String> request = new ClientRequest<>("http://test.com", "testBody");

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
            commonWebClient.callService(request, null));
    }

    @Test
    void testServiceCallWithEmptyUrl() {
        // Arrange
        ClientRequest<String> request = new ClientRequest<>("", "testBody");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            commonWebClient.callService(request, String.class));
    }

    // Helper class for testing custom response types
    private static class TestResponse {
        private String data;

        public TestResponse(String data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TestResponse) {
                return data.equals(((TestResponse) obj).data);
            }
            return false;
        }
    }
}
