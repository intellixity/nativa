# DataEngine (Mongo Examples)

This document shows “copy/paste” Mongo usage patterns for **every `DataEngine` method**, including transactions and governance.

If you want Query JSON/YAML syntax reference, see `docs/query/README.md`.

## Table of Contents
- [1. Creation (Mongo)](#1-creation-mongo)
  - [1.1 Minimal wiring (no Spring)](#11-minimal-wiring-no-spring)
  - [1.2 Governance-wrapped engine](#12-governance-wrapped-engine)
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
- [3. Transactions (Mongo)](#3-transactions-mongo)
- [4. Governance (Mongo)](#4-governance-mongo)

---

## 1. Creation (Mongo)

### 1.1 Minimal wiring (no Spring)

```java
import com.mongodb.client.MongoClient;
import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.mongo.*;

MongoDialect dialect = new MongoDialect();
DmlPlanner planner = new MongoDmlPlanner(authoringRegistry);

MongoHandle handle = new MongoHandle("mongo:tenantA|rw", mongoClient, "tenantA_db", true);
DataEngine<MongoHandle> engine = new MongoDataEngine(handle, authoringRegistry, dialect, planner, Propagation.SUPPORTS);
```

Mongo note:
- `MongoDataEngine` uses Mongo sessions for tx (`begin/commit/rollback` create/commit/abort a driver transaction).

### 1.2 Governance-wrapped engine

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

### 2.1 `select`

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("status", "CREATED"));
var rows = engine.select(CUSTOMER_VIEW, q);
```

**JSON**

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

**Java**

```java
Customer c = Customer.builder()
    .firstName("Asha")
    .lastName("K")
    .build();

Customer inserted = engine.insert(CUSTOMER_VIEW, c);
```

**JSON**

```json
{ "firstName": "Asha", "lastName": "K" }
```

Mongo note:
- if the authored key maps to a non-`_id` field, Mongo engine can mirror that value into `_id` when present (see `MongoDataEngine`).

### 2.4 `bulkInsert`

**Java**

```java
engine.bulkInsert(CUSTOMER_VIEW, List.of(c1, c2));
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
{ "id": "73c98db5-7933-433b-a1c5-af066a546d8a", "firstName": "Asha" }
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
{ "id": "73c98db5-7933-433b-a1c5-af066a546d8a", "email": "new@example.com" }
```

### 2.8 `bulkUpdate`

**Java**

```java
long total = engine.bulkUpdate(CUSTOMER_VIEW, List.of(p1, p2));
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
Customer patch = Customer.builder().status("PAID").build();

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

**JSON**

```json
{ "filter": { "eq": { "field": "status", "value": "CANCELLED" } } }
```

---

## 3. Transactions (Mongo)

Transactions are driven by `inTx(...)` and use Mongo sessions internally:

```java
engine.inTx(() -> {
  engine.insert(CUSTOMER_VIEW, c1);
  engine.insert(CUSTOMER_VIEW, c2);
  return null;
});
```

Propagation works the same as JDBC (`REQUIRED`, `SUPPORTS`, `REQUIRES_NEW`, etc.).\n

---

## 4. Governance (Mongo)

Governance is backend-agnostic; bind `GovernanceContext` and use a `GovernedDataEngine`:

```java
import io.intellixity.nativa.persistence.governance.*;
import java.util.Map;

GovernanceContext ctx = GovernanceContext.of(Map.of(
    "tenantId", "tenantA"
), "tenantA");

List<Customer> rows = Governance.inContext(ctx, () -> {
  Query q = new Query();
  return governed.select(CUSTOMER_VIEW, q);
});
```


