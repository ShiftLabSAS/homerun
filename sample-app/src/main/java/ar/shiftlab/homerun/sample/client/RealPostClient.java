package ar.shiftlab.homerun.sample.client;

import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import ar.shiftlab.homerun.sample.model.Post;

/**
 * Real HTTP implementation of {@link PostClient}.
 * Calls {@code https://jsonplaceholder.typicode.com} via Spring's
 * {@link RestClient}.
 */
@Slf4j
public class RealPostClient implements PostClient {

    private final RestClient restClient;

    public RealPostClient(String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Post getPost(int id) {
        log.debug("Calling real downstream service for post id={}", id);
        return restClient.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .body(Post.class);
    }
}
