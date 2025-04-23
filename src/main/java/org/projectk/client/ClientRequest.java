package org.projectk.client;

public class ClientRequest<T> {
    private String url;
    private T requestBody;

    public ClientRequest(String url, T requestBody) {
        this.url = url;
        this.requestBody = requestBody;
    }

    // Getters
    public String getUrl() { return url; }
    public T getRequestBody() { return requestBody; }
}