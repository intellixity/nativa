# JDBC Examples (Postgres-focused)

This document shows “copy/paste” JDBC usage patterns across **all common combinations**.\n
Reference implementation: `nativa-examples` (tenant-per-database, Postgres).

## Table of Contents
- [1. Minimal wiring (no Spring)](#1-minimal-wiring-no-spring)
- [2. Query examples](#2-query-examples)
  - [2.1 Simple select](#21-simple-select)
  - [2.2 Filter AND/OR/NOT](#22-filter-andornot)
  - [2.3 Sort + offset paging](#23-sort--offset-paging)
  - [2.4 Seek paging](#24-seek-paging)
  - [2.5 Count](#25-count)
- [3. DML examples](#3-dml-examples)
  - [3.1 Insert](#31-insert)
  - [3.2 Bulk insert](#32-bulk-insert)
  - [3.3 Upsert](#33-upsert)
  - [3.4 Update by id](#34-update-by-id)
  - [3.5 Update by criteria](#35-update-by-criteria)
  - [3.6 Delete by criteria](#36-delete-by-criteria)
- [4. Transactions](#4-transactions)
- [5. Governance example (headers → context → governed engine)](#5-governance-example-headers--context--governed-engine)
- [6. Authoring mapping patterns](#6-authoring-mapping-patterns)
  - [6.1 Table view (flat columns)](#61-table-view-flat-columns)
  - [6.2 Joined view (ref + label)](#62-joined-view-ref--label)

---

## 1. Minimal wiring (no Spring)
The required pieces:
- `AuthoringRegistry` (YAML)
- `JdbcHandle` (DataSource + schema + multiTenant)
- `JdbcDialect` (`PostgresDialect`)
- `DmlPlanner` (`JdbcDmlPlanner`)
- `JdbcDataEngine`

```java
import io.intellixity.nativa.persistence.authoring.AuthoringRegistry;
import io.intellixity.nativa.persistence.dmlast.DmlPlanner;
import io.intellixity.nativa.persistence.exec.DataEngine;
import io.intellixity.nativa.persistence.exec.Propagation;
import io.intellixity.nativa.persistence.jdbc.JdbcDataEngine;
import io.intellixity.nativa.persistence.jdbc.JdbcHandle;
import io.intellixity.nativa.persistence.jdbc.dialect.JdbcDialect;
import io.intellixity.nativa.persistence.jdbc.dml.JdbcDmlPlanner;
import io.intellixity.nativa.persistence.jdbc.postgres.PostgresDialect;

import javax.sql.DataSource;
import java.util.Set;

JdbcDialect dialect = new PostgresDialect();
Set<String> tenantBoundaryKeys = Set.of("tenantId");
DmlPlanner planner = new JdbcDmlPlanner(authoringRegistry, pojoAccessorRegistry, tenantBoundaryKeys);

JdbcHandle handle = new JdbcHandle("jdbc:tenantA|rw", dataSource, "public", true);
DataEngine<JdbcHandle> engine = new JdbcDataEngine(handle, authoringRegistry, dialect, planner, Propagation.REQUIRED);
```

---

## 2. Query examples
Assume:

```java
import io.intellixity.nativa.persistence.exec.EntityViewRef;
EntityViewRef CUSTOMER_TABLE = new EntityViewRef("Customer", "customer_table");
```

### 2.1 Simple select

```java
var rows = engine.select(CUSTOMER_TABLE, new io.intellixity.nativa.persistence.query.Query());
```

### 2.2 Filter AND/OR/NOT

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.and(
    QueryFilters.eq("firstName", "Asha"),
    QueryFilters.not(QueryFilters.eq("email", null))
));

var rows = engine.select(CUSTOMER_TABLE, q);
```

### 2.3 Sort + offset paging

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = new Query()
    .withSort(List.of(new SortField("createdAt", SortField.Direction.DESC)))
    .withPage(new OffsetPage(0, 50));

var rows = engine.select(CUSTOMER_TABLE, q);
```

### 2.4 Seek paging
Seek requires sort fields and an `after` map.\n

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;
import java.util.Map;

Query q = new Query()
    .withSort(List.of(
        new SortField("createdAt", SortField.Direction.DESC),
        new SortField("id", SortField.Direction.DESC)
    ))
    .withPage(new SeekPage(50, Map.of(
        "createdAt", "2026-01-01T00:00:00Z",
        "id", "00000000-0000-0000-0000-000000000000"
    )));
```

### 2.5 Count

```java
long n = engine.count(CUSTOMER_TABLE, Query.of(QueryFilters.eq("email", "asha@example.com")));
```

---

## 3. DML examples

### 3.1 Insert
```java
engine.insert(CUSTOMER_TABLE, customerPojo);
```

### 3.2 Bulk insert
```java
engine.bulkInsert(CUSTOMER_TABLE, java.util.List.of(c1, c2));
```

### 3.3 Upsert
```java
engine.upsert(CUSTOMER_TABLE, customerPojo);
```

### 3.4 Update by id
```java
engine.update(CUSTOMER_TABLE, customerPojo);
```

### 3.5 Update by criteria
```java
engine.updateByCriteria(CUSTOMER_TABLE, Query.of(QueryFilters.eq("email", "asha@example.com")), patchPojo);
```

### 3.6 Delete by criteria
```java
engine.deleteByCriteria(CUSTOMER_TABLE, Query.of(QueryFilters.eq("email", "asha@example.com")));
```

---

## 4. Transactions
Explicit transaction boundary:

```java
import io.intellixity.nativa.persistence.exec.Propagation;

engine.inTx(Propagation.REQUIRED, () -> {
  engine.insert(CUSTOMER_TABLE, c1);
  engine.insert(CUSTOMER_TABLE, c2);
  engine.update(CUSTOMER_TABLE, c2);
  return null;
});
```

---

## 5. Governance example (headers → context → governed engine)
In the examples app, a servlet filter builds a `GovernanceContext` from headers and binds it using `Governance.inContext`.\n
See: `io.intellixity.nativa.examples.web.TenantGovernanceFilter`.

---

## 6. Authoring mapping patterns

### 6.1 Table view (flat columns)
See: `nativa-examples/src/main/resources/authoring/Customer.yml` → `customer_table`.

### 6.2 Joined view (ref + label)
See: `nativa-examples/src/main/resources/authoring/Order.yml` → `order_view`.


