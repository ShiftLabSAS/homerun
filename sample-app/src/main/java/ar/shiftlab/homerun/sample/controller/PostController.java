package ar.shiftlab.homerun.sample.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ar.shiftlab.homerun.sample.client.PostClient;
import ar.shiftlab.homerun.sample.model.Post;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    /**
     * Scoped proxy — resolves to mock or real client depending on the live request
     * context.
     */
    private final PostClient postClient;

    /**
     * Proxies {@code GET /posts/{id}} to the configured downstream client.
     *
     * <p>
     * Normal request → calls JSONPlaceholder (real HTTP).
     * <p>
     * Mock request → batter intercepts, returns the pitched expectation.
     */
    @GetMapping("/{id}")
    public Post getPost(@PathVariable int id) {
        return postClient.getPost(id);
    }
}
