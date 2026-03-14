# homerun-batter

**homerun-batter** is the server-side half of the Homerun mock framework. It embeds as a Spring Boot auto-configured library into your application and intercepts outgoing HTTP calls to downstream services whenever a mock scenario is active.

> **Metaphor:** The batter stands at the plate, waits for the ball (the incoming request), and decides whether to swing at the real pitch or return a pre-loaded mock.

---

## How it works

1. `MockHeaderFilter` reads the `X-Mock-Scenario` header on every incoming request.
2. If the header is present and the token is valid, `RequestMockContext` is activated for that request.
3. Service beans wired via `HomeRunBatter` check `batter.isActive()` per request; when active they delegate to `MockClientSupport`, which fetches the matching `MockExpectation` from MongoDB and returns the stored response.
4. If no scenario is active, the real service implementation is called as usual — batter is completely transparent.

---

## Dependency

```gradle
// build.gradle — main scope (not test-only)
implementation 'com.homerun:homerun-batter:0.1.0'
```

---

## Configuration

Add a profile or environment-specific `application.yml` that is **never active in production**:

```yaml
# application-inttest.yml
homerun:
  batter:
    enabled: true                   # must be true to activate any batter bean
    auth-token: ${MOCK_AUTH_TOKEN}  # shared secret; must match pitcher's auth-token
    allowed-ip-ranges:              # optional CIDR allowlist; empty = allow all
      - "10.0.0.0"
      - "172.16."
    strict-request-matching: false  # true = validate incoming request body against stored expectation
    mongo:
      host: ${MONGO_HOST}           # e.g. localhost:27017
      username: ${MONGO_USER}
      password: ${MONGO_PASS}
      database: ${MONGO_DB}         # batter manages the "homerun_<database>" collection
```

> **Security note:** `homerun.batter.enabled` defaults to `false`. Batter registers zero beans when disabled, so there is no mock infrastructure footprint in production.

---

## Wiring service beans

Declare a `HomeRunBatter` bean and use it to switch between real and mock implementations.

### Pattern A — simple builder (recommended)

```java
@Bean
public HomeRunBatter homeRunBatter() {
    return HomeRunBatter.builder()
            .user("user")
            .password("pass")
            .host("mongo-host:27017")
            .build();
}

// Service bean switches at request time
@Bean
public JsonPlaceholderClient jsonPlaceholderClient(HomeRunBatter batter) {
    return batter.isActive()
            ? batter.mockService(JsonPlaceholderClient.class)
            : new JsonPlaceholderClientImpl();
}
```

### Pattern B — `@RequestScope` bean

Evaluated fresh on every request so `isActive()` reflects the live context at the moment the bean is resolved.

```java
@Bean
@RequestScope
public JsonPlaceholderClient jsonPlaceholderClient(HomeRunBatter batter) {
    return batter.isActive()
            ? batter.mockService(JsonPlaceholderClient.class)
            : new JsonPlaceholderClientImpl();
}
```

### Pattern C — singleton proxy via `route()` (zero request-scope)

A single JDK dynamic proxy is created at startup and routes each call at invocation time — no request-scoped beans needed.

```java
@Bean
public JsonPlaceholderClient jsonPlaceholderClient(
        HomeRunBatter batter, JsonPlaceholderClientImpl real) {
    return batter.route(JsonPlaceholderClient.class, real);
}
```

---

## Implementing a mock client

Create a class extending `MockClientSupport` for each downstream service you want to be mockable:

```java
@Component
public class JsonPlaceholderMockClient
        extends MockClientSupport implements JsonPlaceholderClient {

    @Override
    protected String serviceKey() {
        return "jsonplaceholder"; // must match the key used in pitcher.expect(...)
    }

    @Override
    protected Class<JsonPlaceholderClient> targetInterface() {
        return JsonPlaceholderClient.class;
    }

    @Override
    public Post getPost(int id) {
        // MockClientSupport.resolve() looks up the expectation and deserialises
        // the stored response into the declared return type
        return resolve("getPost", id);
    }
}
```

---

## Properties reference

| Property | Default | Description |
|---|---|---|
| `homerun.batter.enabled` | `false` | Activates all batter beans. Must be `true` to use mocking. |
| `homerun.batter.auth-token` | — | Shared secret validated from `X-Mock-Auth-Token` header. |
| `homerun.batter.allowed-ip-ranges` | `[]` (all) | IP prefix allowlist for mock request origins. |
| `homerun.batter.strict-request-matching` | `false` | When `true`, validates the actual request body against the stored expectation. |
| `homerun.batter.mongo.host` | — | MongoDB host (and optional port). |
| `homerun.batter.mongo.username` | — | MongoDB username. |
| `homerun.batter.mongo.password` | — | MongoDB password. |
| `homerun.batter.mongo.database` | — | Database name; batter uses the `homerun_<database>` collection. |
