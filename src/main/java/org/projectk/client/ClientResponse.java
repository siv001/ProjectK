package org.projectk.client;

/**
 * ClientResponse is a generic class that represents the response from a web service call.
 * It contains the payload, an error message (if any), and the HTTP status code.
 *
 * @param <T> The type of the payload.
 */
public record ClientResponse<T>(T response, String error, int statusCode) {}