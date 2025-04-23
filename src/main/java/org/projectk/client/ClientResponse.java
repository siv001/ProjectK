package org.projectk.client;

import lombok.Data;

/**
 * ClientResponse is a generic class that represents the response from a web service call.
 * It contains the payload, an error message (if any), and the HTTP status code.
 *
 * @param <T> The type of the payload.
 */
@Data
public class ClientResponse<T> {
    private T payload;
    private String errorMessage;
    private int statusCode;

    public ClientResponse(T payload, String errorMessage, int statusCode) {
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
    }

    // Getters and Setters
    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }


}