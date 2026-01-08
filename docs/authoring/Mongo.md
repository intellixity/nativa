# Mongo Examples

This document shows “copy/paste” Mongo usage patterns across **all common combinations**.\n
Mongo supports:\n
- `find` queries (base filter + merged Query filter)\n
- `aggregate` pipeline (base pipeline + appended `$match` + sort/skip/limit)\n
\n
Default mapping policy: **camelCase → camelCase**, unless explicitly overridden in `view.mapping`.

## Table of Contents
- [1. Minimal wiring](#1-minimal-wiring)
- [2. Query examples](#2-query-examples)
  - [2.1 Simple filter](#21-simple-filter)
  - [2.2 AND/OR/NOT](#22-andornot)
  - [2.3 Sort + paging](#23-sort--paging)
- [3. Native view examples](#3-native-view-examples)
  - [3.1 Base filter object](#31-base-filter-object)
  - [3.2 Base pipeline](#32-base-pipeline)
- [4. Mapping overrides](#4-mapping-overrides)
- [5. Governance (when used)](#5-governance-when-used)

---

## 1. Minimal wiring
Required pieces:\n
- `AuthoringRegistry`\n
- `MongoHandle` (client + database + multiTenant)\n
- `MongoDialect`\n
- DML planner (Mongo planner)\n
- `MongoDataEngine`\n

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

---

## 2. Query examples
Assume:

```java
import io.intellixity.nativa.persistence.exec.EntityViewRef;
EntityViewRef CUSTOMER_VIEW = new EntityViewRef("Customer", "customer_table");
```

### 2.1 Simple filter

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("firstName", "Asha"));
var rows = engine.select(CUSTOMER_VIEW, q);
```

### 2.2 AND/OR/NOT

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.and(
    QueryFilters.eq("firstName", "Asha"),
    QueryFilters.or(
        QueryFilters.eq("status", "CREATED"),
        QueryFilters.not(QueryFilters.eq("status", "FAILED"))
    )
));
```

### 2.3 Sort + paging

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = new Query()
    .withSort(List.of(new SortField("createdAt", SortField.Direction.DESC)))
    .withPage(new OffsetPage(0, 50));
```

---

## 3. Native view examples
Mongo views can store native definitions in `views.<id>.sqlView.sql`:\n
- a filter document (map)\n
- or a pipeline list\n

### 3.1 Base filter object

```yaml
views:
  active_customers:
    mapping:
      id: _id
      firstName: firstName
    sqlView:
      sql:
        active: true
```

### 3.2 Base pipeline

```yaml
views:
  customer_pipeline:
    mapping:
      firstName: firstName
    sqlView:
      sql:
        - { $match: { active: true } }
        - { $project: { firstName: 1, lastName: 1 } }
```

---

## 4. Mapping overrides
Default mapping is camelCase → camelCase.\n
To override:\n

```yaml
views:
  customer_table:
    mapping:
      firstName: first_name
```

With this override, filtering by `firstName` will target `first_name` in Mongo.\n

---

## 5. Governance (when used)
Governance is backend-agnostic: you can wrap a Mongo engine with `GovernedDataEngine` and provide a `GovernanceContext`.\n


