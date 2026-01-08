# DataEngine (Reference)

This document is the **reference** for Nativa’s `DataEngine` API: creation, all methods, transactions, and governance.

## 1. What is a `DataEngine`?

`DataEngine` is the core runtime API that:
- reads your **authoring** (`AuthoringRegistry` / YAML)
- normalizes + validates queries (`QueryNormalizer` + `QueryValidationStrategy`)
- renders backend-native statements (SQL or BSON) using the backend `Dialect`
- executes against the backend via a backend-specific `EngineHandle`

In code, orchestration is implemented by `AbstractDataEngine`, while concrete engines implement backend I/O:
- JDBC: `JdbcDataEngine`
- Mongo: `MongoDataEngine`

## 2. Creation of a DataEngine

Every engine is created from the same conceptual building blocks:

- **Authoring**: `AuthoringRegistry`
- **Handle**: `EngineHandle` (backend client + namespace)
  - JDBC: `JdbcHandle` (client = `DataSource`, namespace = `schema`)
  - Mongo: `MongoHandle` (client = `MongoClient`, namespace = `database`)
- **Dialect**: backend renderer
  - JDBC: `PostgresDialect` (or another `JdbcDialect`)
  - Mongo: `MongoDialect`
- **DML Planner**: converts POJOs and query criteria into DML AST
  - JDBC: `JdbcDmlPlanner`
  - Mongo: `MongoDmlPlanner`
- **DataEngine**: orchestrator + executor
  - JDBC: `JdbcDataEngine`
  - Mongo: `MongoDataEngine`

### 2.1 Minimal wiring (no Spring): JDBC

```java
import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.jdbc.JdbcDataEngine;
import io.intellixity.nativa.persistence.jdbc.JdbcHandle;
import io.intellixity.nativa.persistence.jdbc.dml.JdbcDmlPlanner;
import io.intellixity.nativa.persistence.jdbc.postgres.PostgresDialect;
import io.intellixity.nativa.persistence.pojo.PojoAccessorRegistry;

import javax.sql.DataSource;
import java.util.Set;

PostgresDialect dialect = new PostgresDialect();
Set<String> tenantBoundaryKeys = Set.of("tenantId");
DmlPlanner planner = new JdbcDmlPlanner(authoringRegistry, pojoAccessorRegistry, tenantBoundaryKeys);

JdbcHandle handle = new JdbcHandle("jdbc:tenantA|rw", dataSource, "public", true);
DataEngine<JdbcHandle> engine = new JdbcDataEngine(handle, authoringRegistry, dialect, planner, Propagation.REQUIRED);
```

### 2.2 Minimal wiring (no Spring): Mongo

```java
import com.mongodb.client.MongoClient;
import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.mongo.*;

MongoDialect dialect = new MongoDialect();
DmlPlanner planner = new MongoDmlPlanner(authoringRegistry);

MongoHandle handle = new MongoHandle("mongo:tenantA", mongoClient, "tenantA_db", true);
DataEngine<MongoHandle> engine = new MongoDataEngine(handle, authoringRegistry, dialect, planner, Propagation.SUPPORTS);
```

### 2.3 Governance-first wiring (recommended)

In most applications, **you don’t create DataEngines directly per request**.

Instead, you:
- bind a `GovernanceContext` per request
- resolve the right `EngineHandle` + cached `DataEngine` using `GovernanceDataEngineResolver`

Reference wiring exists in `nativa-examples` (`NativaExampleConfig`).

## 3. DataEngine methods (all operations)

The `DataEngine` API is backend-agnostic:

### 3.1 Read operations

#### `select(EntityViewRef, Query) -> List<T>`

- Renders a **native select/find** based on the authored view + `Query`.
- **Does not auto-create a transaction** (it will use a current tx if you already started one via `inTx(...)`).

#### `count(EntityViewRef, Query) -> long`

- Renders a native count.
- Same transaction behavior as `select`.

### 3.2 Write operations

All write operations in `AbstractDataEngine` are run through:
- `inTx(defaultWritePropagation(), ...)`

So writes **auto-create a transaction** when default write propagation is `REQUIRED` (common for JDBC).

#### `insert(EntityViewRef, T) -> T`

- Plans insert DML and executes it.
- If the entity has an **auto-generated key** and the caller didn’t supply it, the engine may **write the generated ID back** into the POJO.

#### `bulkInsert(EntityViewRef, List<T>)`

- Runs inserts in a loop within one transaction boundary.

#### `upsert(EntityViewRef, T) -> T`

- Plans upsert DML and executes it.
- May populate generated ID similarly to insert.

#### `bulkUpsert(EntityViewRef, List<T>)`

- Runs upserts in a loop within one transaction boundary.

#### `update(EntityViewRef, T) -> long`

- Update-by-id (based on authored key fields).
- Returns affected row/document count.

#### `bulkUpdate(EntityViewRef, List<T>) -> long`

- Runs updates in a loop within one transaction boundary.

#### `updateByCriteria(EntityViewRef, Query, T) -> long`

- Plans update-by-criteria using `Query.filter` (after normalization + validation).
- Returns affected row/document count.

#### `deleteByCriteria(EntityViewRef, Query) -> long`

- Deletes by criteria using `Query.filter` (after normalization + validation).
- Returns affected row/document count.

## 4. Transactions

Transactions are controlled through `DataEngine.inTx(...)`.

### 4.1 `inTx(...)` basics

```java
engine.inTx(() -> {
  engine.insert(ref, entity);
  engine.updateByCriteria(ref, query, patch);
  return null;
});
```

Implementation notes (important behavior):
- Transaction scope is managed by `AbstractDataEngine` using `ScopedValue`.
- A transaction is **engine-instance scoped** (it won’t accidentally reuse another engine instance’s tx).
- **Reads do not auto-create** a tx; **writes do** (via `defaultWritePropagation()`).

### 4.2 Propagation behavior

`inTx(Propagation, work)` supports:
- `REQUIRED`: reuse existing tx, else create one
- `SUPPORTS`: run with tx if present, else run non-transactionally
- `MANDATORY`: require an existing tx
- `REQUIRES_NEW`: always create a new tx for the work
- `NEVER`: fail if a tx exists
- `NESTED`: best-effort; currently treated like `REQUIRED`

## 5. Governance

Governance is an opt-in layer that enforces:
- **filter injection** based on `FieldDef.attrs.governanceKey`
- **payload population** of governed fields on write operations

### 5.1 Binding a `GovernanceContext`

Governance uses `ScopedValue` scoping (Java 25):

```java
import io.intellixity.nativa.persistence.governance.Governance;
import io.intellixity.nativa.persistence.governance.GovernanceContext;

GovernanceContext ctx = GovernanceContext.of(java.util.Map.of(
    "tenantId", "tenantA",
    "userId", "u1"
), "tenantA|u1");

return Governance.inContext(ctx, () -> {
  // ... call governed engine here ...
  return null;
});
```

### 5.2 Using `GovernedDataEngine`

Wrap a base engine:

```java
import io.intellixity.nativa.persistence.governance.GovernedDataEngine;
import io.intellixity.nativa.persistence.pojo.PojoMutatorRegistry;

DataEngine<?> governed = new GovernedDataEngine<>(authoringRegistry, baseEngine, pojoMutatorRegistry);
```

Behavior:
- `select` / `count`: injects extra `AND` filters for each governance key that is present in `GovernanceContext`.
- `insert` / `upsert` / `update*`: populates governed fields if the corresponding context key is present.

### 5.3 Tenant boundary + shared store vs isolated store

`EngineHandle.multiTenant()` means:
- `true`: shared physical store contains multiple tenants → tenant boundary keys must be present and mappable
- `false`: isolated physical store per tenant → tenant filters may be skipped (tenant isolation is already guaranteed by the store selection)

Tenant keys come from:
- `GovernanceContext.tenantKeys()` (per request), or
- the engine’s default `tenantBoundaryKeys` (constructor argument)

### 5.4 Cached resolve: `GovernanceDataEngineResolver`

Use `GovernanceDataEngineResolver` to turn `(engineFamily, ctx, readOnly)` into a cached `DataEngine`:
- caches `EngineHandle` by `(engineFamily, readOnly, ctx.cacheKey())`
- caches `DataEngine` by `(engineFamily, readOnly, handle.id())`

This pattern is used by `nativa-examples` via `EngineHandleResolver` + `DataEngineFactory`.


