<img src=".resources/logo.png" alt="Homerun Logo" width="200" height="200" />

# Homerun — Java Library for HTTP Service Mocking in Integration Tests

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## The metaphor

In baseball, the **pitcher** throws the ball and the **batter** hits it back.

Homerun borrows this metaphor for HTTP mocking in integration tests:

| Role | Baseball | Homerun |
|---|---|---|
| **Pitcher** | Throws the ball | Test code that _throws_ expectations into the shared store |
| **Batter** | Receives and hits the ball | App-side filter that _intercepts_ outgoing HTTP calls and serves the stored response |


<img src=".resources/homerun-architecture.png" alt="Homerun architecture diagram" width="300" />

<details>
<summary>show dotsource</summary>

```dot
digraph HomerunFlow {
  rankdir=TB;
  node [shape=box, style=rounded, fontname="Arial"];

  subgraph cluster_test {
    label = "Test process";
    PitcherClient [label="PitcherClient\nopen()"];
    Expect [label="expect('svc', 'op', req, resp)\n→ MongoDB"];
    Get [label="get('/endpoint', Response.class)\n(injects X-Mock-Scenario header)"];
    PitcherClient -> Expect;
    PitcherClient -> Get;
    Get -> HTTP;
  }

  HTTP [label="HTTP", shape=ellipse, style=filled, fillcolor=lightgray];

  subgraph cluster_app {
    label = "App under test (Spring Boot + homerun-batter)";
    MockHeaderFilter [label="MockHeaderFilter\nreads X-Mock-Scenario header"];
    RequestMockContext [label="activates RequestMockContext"];
    MockClientSupport [label="MockClientSupport\noutgoing call intercepted?"];
    MongoDB [label="MongoDB\n(fetch expectation)"];
    Resp [label="resp (mocked response)"];
    RealService [label="call real downstream service"];

    MockHeaderFilter -> RequestMockContext;
    RequestMockContext -> MockClientSupport;
    MockClientSupport -> MongoDB [label="YES"];
    MongoDB -> Resp;
    MockClientSupport -> RealService [label="NO"];
  }

  HTTP -> MockHeaderFilter;
}

```
</details>

---

## Sub-projects

| Module | Role |
|---|---|
| [`homerun-common`](homerun-common/) | Shared models and constants (`MockExpectation`, `MockHeaders`, …) used by both libraries |
| [`homerun-batter`](homerun-batter/) | Spring Boot auto-configured library embedded in the **app under test**; intercepts outgoing HTTP calls during mock scenarios |
| [`homerun-pitcher`](homerun-pitcher/) | Spring Boot auto-configured library added to **test scope**; manages scenarios and expectations from the test side |
| [`sample-app`](sample-app/) | Reference application demonstrating end-to-end integration |

---

## How it works end to end

1. The test calls `pitcher.open()` — a fresh UUID scenario is created.
2. The test calls `pitcher.expect(serviceKey, operationKey, request, response)` — the expectation is written to MongoDB with the scenario UUID.
3. The test calls `pitcher.get(path, ResponseType.class)` — the request is sent to the app with the `X-Mock-Scenario` header set to the scenario UUID.
4. Inside the app, `MockHeaderFilter` reads the header and activates `RequestMockContext`.
5. The service bean (wired via `HomeRunBatter`) delegates to `MockClientSupport`, which detects the active context, looks up the expectation in MongoDB, and returns the stored response — the real downstream service is never called.
6. The test calls `pitcher.close()` — all expectations for this scenario are deleted from MongoDB.

---


## Requirements

Homerun requires access to a MongoDB database for storing and retrieving mock expectations. You must:

- Provision a MongoDB instance accessible to both the test runner and the application under test.
- Ensure the application and test code have appropriate read and write permissions to the MongoDB database.
- Provide the correct MongoDB connection details in your configuration files.

---


## Quick start

> **Note:** Homerun requires a running MongoDB instance. You must provision the database and ensure both your application and test code have read/write access. Provide the correct connection details in your configuration.

### 1 — Add dependencies

```gradle
// app module (main scope)
implementation 'com.shiftlab.homerun:homerun-batter:0.1.0'

// test module (test scope only)
testImplementation 'com.shiftlab.homerun:homerun-pitcher:0.1.0'
```

### 2 — Configure batter in the app

```yaml
# application-inttest.yml
homerun:
  batter:
    enabled: true
    auth-token: ${MOCK_AUTH_TOKEN}
    mongo:
      host: localhost
      database: myapp
```

### 3 — Configure pitcher in the test context

```yaml
# application-test.yml
homerun:
  pitcher:
    enabled: true
    auth-token: ${MOCK_AUTH_TOKEN}
    base-url: http://localhost:8080
```

### 4 — Write the test

```java
@Autowired PitcherClient pitcher;

@Test
void testGetOrder_withPaymentMocked_thenReturnsOrder() {
    pitcher.open();

    final PaymentResponse mockedPayment = PaymentResponse.builder()
            .status("APPROVED")
            .build();

    pitcher.expect("payments", "charge", chargeRequest, mockedPayment);

    final ResponseEntity<Order> response = pitcher.get("/orders/42", Order.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
            .usingRecursiveComparison()
            .isEqualTo(expectedOrder);

    pitcher.close();
}
```

See [`homerun-batter/README.md`](homerun-batter/README.md) and [`homerun-pitcher/README.md`](homerun-pitcher/README.md) for detailed integration guides.
