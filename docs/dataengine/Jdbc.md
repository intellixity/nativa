# DataEngine (JDBC Examples)

This document shows “copy/paste” JDBC usage patterns for **every `DataEngine` method**, including transactions and governance.

If you want Query JSON/YAML syntax reference, see `docs/query/README.md`.

## Table of Contents
- [1. Creation (JDBC)](#1-creation-jdbc)
  - [1.1 Minimal wiring (no Spring)](#11-minimal-wiring-no-spring)
  - [1.2 Spring wiring reference (`nativa-examples`)](#12-spring-wiring-reference-nativa-examples)
  - [1.3 Governance-wrapped engine](#13-governance-wrapped-engine)
- [2. All DataEngine methods](#2-all-dataengine-methods)
  - [2.1 `select`](#21-select)
  - [2.2 `count`](#22-count)
  - [2.3 `insert`](#23-insert)
  - [2.4 `bulkInsert`](#24-bulkinsert)
  - [2.5 `upsert`](#25-upsert)
  - [2.6 `bulkUpsert`](#26-bulkupsert)
  - [2.7 `update` (by id)](#27-update-by-id)
  - [2.8 `bulkUpdate`](#28-bulkupdate)
  - [2.9 `updateByCriteria`](#29-updatebycriteria)
  - [2.10 `deleteByCriteria`](#210-deletebycriteria)
- [3. Transactions (JDBC)](#3-transactions-jdbc)
  - [3.1 Explicit `inTx(...)`](#31-explicit-intx)
  - [3.2 Propagation examples](#32-propagation-examples)
- [4. Governance (JDBC)](#4-governance-jdbc)

---

## 1. Creation (JDBC)

### 1.1 Minimal wiring (no Spring)

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

### 1.2 Spring wiring reference (`nativa-examples`)

See:
- `nativa-examples/src/main/java/io/intellixity/nativa/examples/config/NativaExampleConfig.java`

That example uses:
- `EngineHandleResolver` to resolve a `JdbcHandle` from `tenantId` in `GovernanceContext`
- `DataEngineFactory` to build a `JdbcDataEngine` (then wrap it with governance)
- `GovernanceDataEngineResolver` as an LRU+TTL cache for handles/engines

### 1.3 Governance-wrapped engine

```java
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.governance.GovernedDataEngine;
import io.intellixity.nativa.persistence.pojo.PojoMutatorRegistry;

DataEngine<?> governed = new GovernedDataEngine<>(authoringRegistry, engine, pojoMutatorRegistry);
```

---

## 2. All DataEngine methods

Assume:

```java
import io.intellixity.nativa.persistence.exec.EntityViewRef;

EntityViewRef CUSTOMER_VIEW = new EntityViewRef("Customer", "customer_table");
```

Also assume your entity JSON looks like:

```json
{
  "id": "73c98db5-7933-433b-a1c5-af066a546d8a",
  "firstName": "Asha",
  "lastName": "K",
  "email": "asha@example.com",
  "phone": "+91-99999-00000"
}
```

### 2.1 `select`

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("status", "CREATED"));
var rows = engine.select(CUSTOMER_VIEW, q);
```

**JSON (typical REST shape)**

```json
{
  "filter": { "eq": { "field": "status", "value": "CREATED" } },
  "sort": [{ "field": "createdAt", "dir": "DESC" }],
  "page": { "type": "offset", "offset": 0, "limit": 50 }
}
```

### 2.2 `count`

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("status", "CREATED"));
long n = engine.count(CUSTOMER_VIEW, q);
```

**JSON**

```json
{ "filter": { "eq": { "field": "status", "value": "CREATED" } } }
```

### 2.3 `insert`

Writes run inside `inTx(defaultWritePropagation())` automatically (see `AbstractDataEngine`).

**Java**

```java
Customer c = Customer.builder()
    .firstName("Asha")
    .lastName("K")
    .email("asha@example.com")
    .phone("+91-99999-00000")
    .build();

Customer inserted = engine.insert(CUSTOMER_VIEW, c);
```

**JSON (typical REST shape)**

```json
{
  "firstName": "Asha",
  "lastName": "K",
  "email": "asha@example.com",
  "phone": "+91-99999-00000"
}
```

### 2.4 `bulkInsert`

**Java**

```java
engine.bulkInsert(CUSTOMER_VIEW, List.of(c1, c2, c3));
```

**JSON**

```json
[
  { "firstName": "Asha", "lastName": "K" },
  { "firstName": "Mina", "lastName": "R" }
]
```

### 2.5 `upsert`

**Java**

```java
Customer c = customers.getExistingOrNew();
Customer out = engine.upsert(CUSTOMER_VIEW, c);
```

**JSON**

```json
{
  "id": "73c98db5-7933-433b-a1c5-af066a546d8a",
  "firstName": "Asha",
  "lastName": "K"
}
```

### 2.6 `bulkUpsert`

**Java**

```java
engine.bulkUpsert(CUSTOMER_VIEW, List.of(c1, c2));
```

**JSON**

```json
[
  { "id": "73c98db5-7933-433b-a1c5-af066a546d8a", "firstName": "Asha" },
  { "id": "00000000-0000-0000-0000-000000000000", "firstName": "Mina" }
]
```

### 2.7 `update` (by id)

**Java**

```java
Customer patch = Customer.builder()
    .id(existingId)
    .email("new@example.com")
    .build();

long n = engine.update(CUSTOMER_VIEW, patch);
```

**JSON**

```json
{
  "id": "73c98db5-7933-433b-a1c5-af066a546d8a",
  "email": "new@example.com"
}
```

### 2.8 `bulkUpdate`

**Java**

```java
long total = engine.bulkUpdate(CUSTOMER_VIEW, List.of(p1, p2, p3));
```

**JSON**

```json
[
  { "id": "73c98db5-7933-433b-a1c5-af066a546d8a", "email": "a@x.com" },
  { "id": "00000000-0000-0000-0000-000000000000", "email": "b@x.com" }
]
```

### 2.9 `updateByCriteria`

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query where = Query.of(QueryFilters.eq("status", "CREATED"));
Customer patch = Customer.builder().paymentStatus("PAID").build();

long n = engine.updateByCriteria(CUSTOMER_VIEW, where, patch);
```

**JSON (typical REST shape)**

```json
{
  "query": { "filter": { "eq": { "field": "status", "value": "CREATED" } } },
  "set": { "status": "PAID" }
}
```

### 2.10 `deleteByCriteria`

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query where = Query.of(QueryFilters.eq("status", "CANCELLED"));
long n = engine.deleteByCriteria(CUSTOMER_VIEW, where);
```

**JSON (typical REST shape)**

```json
{ "filter": { "eq": { "field": "status", "value": "CANCELLED" } } }
```

---

## 3. Transactions (JDBC)

### 3.1 Explicit `inTx(...)`

Use this when you need multiple operations to be atomic:

```java
engine.inTx(() -> {
  engine.insert(CUSTOMER_VIEW, c1);
  engine.insert(CUSTOMER_VIEW, c2);
  return null;
});
```

### 3.2 Propagation examples

**REQUIRES_NEW**

```java
import io.intellixity.nativa.persistence.exec.Propagation;

engine.inTx(Propagation.REQUIRES_NEW, () -> {
  engine.update(CUSTOMER_VIEW, patch);
  return null;
});
```

**MANDATORY**

```java
engine.inTx(() -> {
  // OK: inside tx
  engine.inTx(Propagation.MANDATORY, () -> {
    engine.deleteByCriteria(CUSTOMER_VIEW, where);
    return null;
  });
  return null;
});
```

---

## 4. Governance (JDBC)

When using `GovernedDataEngine`, always ensure a `GovernanceContext` is bound:

```java
import io.intellixity.nativa.persistence.governance.*;
import java.util.Map;

GovernanceContext ctx = GovernanceContext.of(Map.of(
    "tenantId", "tenantA",
    "dealerId", "d1"
), "tenantA|d1");

List<Customer> rows = Governance.inContext(ctx, () -> {
  Query q = new Query(); // governance will inject tenant filters
  return governed.select(CUSTOMER_VIEW, q);
});
```

Notes:
- For shared stores (`handle.multiTenant()==true`), tenant-boundary keys must be present per request.
- For isolated stores (`handle.multiTenant()==false`), tenant-boundary filters can be skipped (store isolation already applies).


