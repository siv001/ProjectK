package org.projectk.client;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class CommonWebClient {
    private final WebClient webClient;
//    private final Map<String, ClientResponse<?>> cache = new ConcurrentHashMap<>();

    public CommonWebClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public <T, R> CompletableFuture<ClientResponse<R>> callService(ClientRequest<T> call, Class<R> responseType) {
        String cacheKey = call.getUrl() + call.getRequestBody().hashCode();
        return webClient.post()
                .uri(call.getUrl())
                .bodyValue(call.getRequestBody())
                .retrieve()
                .bodyToMono(responseType)
                .map(response -> {
                    ClientResponse<R> clientResponse = new ClientResponse<>(response, null, 200);
//                    cache.put(cacheKey, clientResponse);
                    return clientResponse;
                })
                .onErrorResume(e -> {
//                    ClientResponse<R> cachedResponse = (ClientResponse<R>) cache.get(cacheKey);
//                    if (cachedResponse != null) {
//                        return Mono.just(cachedResponse);
//                    } else {
                        return Mono.just(new ClientResponse<>(null, e.getMessage(), 500));
//                    }
                })
                .toFuture();
    }
}