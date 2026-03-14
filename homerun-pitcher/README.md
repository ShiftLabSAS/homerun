# homerun-pitcher

**homerun-pitcher** is the test-side half of the Homerun mock framework. It is added to your test classpath and gives tests a simple API to open mock scenarios, register expectations, and make HTTP calls that automatically carry the scenario header so the embedded batter can serve the right response.

> **Metaphor:** The pitcher winds up and throws the ball — delivering a pre-defined expectation into the store so the batter knows exactly what to return.

---

## How it works

1. `pitcher.open()` generates a fresh UUID and stores it as the active scenario for the current test.
2. `pitcher.expect(serviceKey, operationKey, request, response)` serialises the expectation and writes it to the shared MongoDB under that scenario UUID.
3. `pitcher.get(path, ResponseType.class)` (or `post`, `put`, `delete`) sends the request to the app under test with `X-Mock-Scenario` and `X-Mock-Auth-Token` headers automatically injected.
4. `pitcher.close()` deletes all expectations for the current scenario, leaving the store clean for the next test.

---

## Dependency

```gradle
// build.gradle — test scope only, never shipped in production
testImplementation 'com.shiftlab.homerun:homerun-pitcher:0.1.0'
```

---

## Configuration

```yaml
# application-test.yml  (active via @ActiveProfiles("test"))
homerun:
  pitcher:
    enabled: true                            # must be true to activate PitcherClient
    auth-token: ${MOCK_AUTH_TOKEN}           # must match homerun.batter.auth-token
    base-url: http://localhost:8080          # base URL of the app under test
    default-ttl: 1h                          # TTL applied to every expectation
    default-created-by: ci-integration-tests # tag stored with every expectation
```

The pitcher needs a MongoDB connection to write expectations. It reuses the same database as batter — configure the Spring Data MongoDB datasource in your test application context:

```yaml
spring:
  data:
    mongodb:
      host: ${MONGO_HOST}
      port: 27017
      username: ${MONGO_USER}
      password: ${MONGO_PASS}
      database: ${MONGO_DB}
```

---

## Basic usage

```java
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class OrderServiceIT {

    @Autowired
    PitcherClient pitcher;

    @Test
    void testCreateOrder_withPaymentMocked_thenOrderIsCreated() {
        pitcher.open(); // start scenario

        final PaymentResponse mockedPayment = PaymentResponse.builder()
                .status("APPROVED")
                .transactionId("txn-001")
                .build();

        // register expectation: when "payments" service is asked to "charge",
        // return mockedPayment regardless of the actual request body
        pitcher.expect("payments", "charge", chargeRequest, mockedPayment);

        // make the call — X-Mock-Scenario header is injected automatically
        final ResponseEntity<Order> response = pitcher.post("/orders", orderRequest, Order.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .usingRecursiveComparison()
                .isEqualTo(expectedOrder);

        pitcher.close(); // remove all expectations for this scenario
    }
}
```

---

## Advanced: independent scenario handles

Use `pitcher.newScenario()` when you need multiple concurrent scenarios or want try-with-resources cleanup:

```java
@Test
void testWithTwoIndependentScenarios() {
    try (ScenarioHandle payments = pitcher.newScenario();
         ScenarioHandle notifications = pitcher.newScenario()) {

        payments
            .expect("payments", "charge", chargeReq, chargeResp);
        notifications
            .expect("notifications", "send", notifReq, notifResp);

        // pass scenario IDs manually in headers when not using pitcher.get()
        // both scenarios are cleaned up automatically on close()
    }
}
```

---

## `PitcherClient` API summary

| Method | Description |
|---|---|
| `open()` | Creates a new scenario UUID for the current test. |
| `close()` | Deletes all expectations registered in the current open session. |
| `expect(serviceKey, operationKey, request, response)` | Stores an expectation in MongoDB. |
| `get(path, responseType)` | `GET` with mock headers injected. |
| `post(path, body, responseType)` | `POST` with mock headers injected. |
| `put(path, body, responseType)` | `PUT` with mock headers injected. |
| `delete(path)` | `DELETE` with mock headers injected. |
| `newScenario()` | Returns an independent `ScenarioHandle` (auto-closeable). |

---

## Properties reference

| Property | Default | Description |
|---|---|---|
| `homerun.pitcher.enabled` | `false` | Activates `PitcherClient` and related beans. |
| `homerun.pitcher.auth-token` | — | Sent as `X-Mock-Auth-Token`; must match batter's token. |
| `homerun.pitcher.base-url` | `http://localhost:8080` | Base URL of the app under test (used with `WebEnvironment.NONE`). |
| `homerun.pitcher.default-ttl` | `2h` | TTL applied to expectations when none is set explicitly. |
| `homerun.pitcher.default-created-by` | `homerun-pitcher` | Tag stored with every expectation for traceability. |
