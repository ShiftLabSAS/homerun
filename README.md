# Homerun — HTTP Mock Framework for Integration Tests

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## The metaphor

In baseball, the **pitcher** throws the ball and the **batter** hits it back.

Homerun borrows this metaphor for HTTP mocking in integration tests:

| Role | Baseball | Homerun |
|---|---|---|
| **Pitcher** | Throws the ball | Test code that _throws_ expectations into the shared store |
| **Batter** | Receives and hits the ball | App-side filter that _intercepts_ outgoing HTTP calls and serves the stored response |

```
┌──────────────────────────────────────────────────────────┐
│  Test process                                            │
│                                                          │
│  PitcherClient ──── open() ─────────────────────────┐   │
│       │                                             │   │
│       ├── expect("svc", "op", req, resp) ──► MongoDB│   │
│       │                                             │   │
│       └── get("/endpoint", Response.class)          │   │
│             │  (injects X-Mock-Scenario header)     │   │
└─────────────┼───────────────────────────────────────┘   │
              │  HTTP
              ▼
┌──────────────────────────────────────────────────────────┐
│  App under test  (Spring Boot + homerun-batter)          │
│                                                          │
│  MockHeaderFilter ── reads X-Mock-Scenario header        │
│       │                                                  │
│       └── activates RequestMockContext                   │
│                                                          │
│  MockClientSupport ── outgoing call intercepted?         │
│       ├── YES → fetch expectation from MongoDB ──► resp  │
│       └── NO  → call real downstream service             │
└──────────────────────────────────────────────────────────┘
```

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

## Quick start

### 1 — Add dependencies

```gradle
// app module (main scope)
implementation 'com.homerun:homerun-batter:0.1.0'

// test module (test scope only)
testImplementation 'com.homerun:homerun-pitcher:0.1.0'
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
