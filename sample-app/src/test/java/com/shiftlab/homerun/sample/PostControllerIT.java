package com.shiftlab.homerun.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import com.shiftlab.homerun.pitcher.PitcherClient;

import com.shiftlab.homerun.sample.model.Post;

/**
 * End-to-end integration test for {@code GET /posts/{id}} against a running
 * docker-compose stack ({@code docker-compose up}).
 *
 * <p>
 * The Spring context starts with {@code WebEnvironment.NONE} — no embedded
 * server is started. The app runs in the Docker stack on port 8080.
 *
 * <h3>Sub-projects involved</h3>
 * <ul>
 * <li><b>homerun-batter</b> — servlet filter embedded in the app; intercepts
 * outgoing downstream calls and returns the stored mock expectation when
 * a scenario header is present.</li>
 * <li><b>homerun-pitcher</b> — test-side client; opens/closes mock scenarios,
 * writes expectations to MongoDB, and injects the scenario header into
 * HTTP calls automatically via {@link PitcherClient}.</li>
 * <li><b>homerun-common</b> — shared models and constants (e.g.
 * {@code MockHeaders}, {@code MockExpectation}) used by both batter and
 * pitcher.</li>
 * </ul>
 *
 * <h3>testGetPost_withNoMockHeaders — control</h3>
 * No scenario is opened. The app calls the real JSONPlaceholder API and returns
 * its response unmodified.
 *
 * <h3>testGetPost_withExpectation — mocked</h3>
 * A scenario is opened via pitcher, an expectation is stored in MongoDB, and
 * the app call carries the scenario header so batter intercepts the downstream
 * request and serves the stored mock instead.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class PostControllerIT {

    static final String BASE_URL = "http://localhost:8080";

    /** Plain RestTemplate — no scenario header injected, so batter is bypassed. */
    final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    PitcherClient pitcher;

    // ── Control: no scenario header → batter bypassed → real JSONPlaceholder call
    // ──

    @Test
    void testGetPost_withNoMockHeaders_thenCallsRealDownstreamService() {
        final Post expectedPost = Post.builder()
                .userId(1)
                .id(1)
                .title("sunt aut facere repellat provident occaecati excepturi optio reprehenderit")
                .body("quia et suscipit\nsuscipit recusandae consequuntur expedita et cum\nreprehenderit molestiae ut ut quas totam\nnostrum rerum est autem sunt rem eveniet architecto")
                .build();

        final ResponseEntity<Post> response = restTemplate.getForEntity(BASE_URL + "/posts/1", Post.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .usingRecursiveComparison()
                .isEqualTo(expectedPost);
    }

    // ── Mocked: pitcher opens scenario → batter intercepts → mock response served
    // ──

    @Test
    void testGetPost_withExpectation_thenReturnsMockedResponse() {
        pitcher.open();

        final Post mockedPost = Post.builder()
                .userId(99)
                .id(1)
                .title("Mocked by Homerun Batter")
                .body("This response came from the expectation store, not JSONPlaceholder.")
                .build();

        pitcher.expect("jsonplaceholder", "getPost", 1, mockedPost);

        final ResponseEntity<Post> response = pitcher.get("/posts/1", Post.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .usingRecursiveComparison()
                .isEqualTo(mockedPost);

        pitcher.close();
    }
}
