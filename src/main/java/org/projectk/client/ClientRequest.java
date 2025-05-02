package org.projectk.client;

import org.springframework.http.HttpMethod;

/**
 * A record representing a client request with a URL, HTTP method, and a body.
 *
 * @param <T> The type of the request body
 * @param url The URL to send the request to
 * @param method The HTTP method to use for the request
 * @param requestBody The request body
 */
public record ClientRequest<T>(String url, HttpMethod method, T requestBody) {}