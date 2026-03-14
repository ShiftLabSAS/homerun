// MongoDB initialisation script — runs once when the container is first created.
// Creates the 'homerun' database, the 'mock_expectations' collection, and its indexes.
// Mirrors the schema declared in MockExpectation.java.

db = db.getSiblingDB("homerun");

db.createCollection("mock_expectations");

// Single-field index on scenarioId for fast scenario-scoped queries.
db.mock_expectations.createIndex({ scenarioId: 1 }, { name: "scenarioId_idx" });

// Unique compound index — one expectation per (scenario, service, operation).
db.mock_expectations.createIndex(
  { scenarioId: 1, targetService: 1, operationName: 1 },
  { name: "scenario_service_op_uidx", unique: true },
);
