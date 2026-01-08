# Query (JDBC Examples)

This document is “copy/paste” JDBC usage for `Query`.

Prerequisites:
- You have a JDBC `DataEngine` (see `docs/authoring/Jdbc.md` for full wiring).
- You have an `EntityViewRef` to target a specific authored view.

Assume:

```java
import io.intellixity.nativa.persistence.exec.EntityViewRef;

EntityViewRef CUSTOMER_VIEW = new EntityViewRef("Customer", "customer_table");
```

Each example includes:
- **JSON** (canonical keys; same shape works in YAML)
- **Java** (using `Query`, `QueryFilters`, etc.)

## Table of Contents
- [1. Select all (no filter)](#1-select-all-no-filter)
- [2. EQ / NE (including NULL)](#2-eq--ne-including-null)
- [3. Comparisons (GT/GE/LT/LE)](#3-comparisons-gtgeltle)
- [4. LIKE](#4-like)
- [5. IN / NIN](#5-in--nin)
- [6. RANGE](#6-range)
- [7. AND / OR / NOT (nested)](#7-and--or--not-nested)
- [8. Sort](#8-sort)
- [9. Offset paging](#9-offset-paging)
- [10. Seek paging](#10-seek-paging)
- [11. Projection](#11-projection)
- [12. GroupBy](#12-groupby)
- [13. Params (filter placeholders + view SQL params)](#13-params-filter-placeholders--view-sql-params)
- [14. Postgres-only operators (ARRAY_*, JSON_*)](#14-postgres-only-operators-array_-json_)

---

## 1. Select all (no filter)

**JSON**

```json
{}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.Query;

var rows = engine.select(CUSTOMER_VIEW, new Query());
```

---

## 2. EQ / NE (including NULL)

### 2.1 EQ scalar

**JSON**

```json
{
  "filter": { "eq": { "field": "status", "value": "CREATED" } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("status", "CREATED"));
var rows = engine.select(CUSTOMER_VIEW, q);
```

### 2.2 NE scalar

**JSON**

```json
{
  "filter": { "ne": { "field": "status", "value": "FAILED" } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.ne("status", "FAILED"));
```

### 2.3 EQ null (IS NULL in SQL)

**JSON**

```json
{
  "filter": { "eq": { "field": "email", "value": null } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("email", null));
```

### 2.4 NE null (IS NOT NULL in SQL)

**JSON**

```json
{
  "filter": { "ne": { "field": "email", "value": null } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.ne("email", null));
```

---

## 3. Comparisons (GT/GE/LT/LE)

**JSON**

```json
{
  "filter": {
    "and": [
      { "gt": { "field": "createdAt", "value": "2026-01-01T00:00:00Z" } },
      { "le": { "field": "createdAt", "value": "2026-02-01T00:00:00Z" } }
    ]
  }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.and(
    QueryFilters.gt("createdAt", "2026-01-01T00:00:00Z"),
    QueryFilters.le("createdAt", "2026-02-01T00:00:00Z")
));
```

---

## 4. LIKE

**JSON**

```json
{
  "filter": { "like": { "field": "email", "value": "%@example.com" } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.like("email", "%@example.com"));
```

---

## 5. IN / NIN

### 5.1 IN

**JSON**

```json
{
  "filter": { "in": { "field": "status", "values": ["CREATED", "PAID"] } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = Query.of(QueryFilters.in("status", List.of("CREATED", "PAID")));
```

### 5.2 NIN

**JSON**

```json
{
  "filter": { "nin": { "field": "status", "values": ["FAILED", "CANCELLED"] } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = Query.of(QueryFilters.nin("status", List.of("FAILED", "CANCELLED")));
```

---

## 6. RANGE

**JSON**

```json
{
  "filter": {
    "range": {
      "field": "createdAt",
      "lower": "2026-01-01T00:00:00Z",
      "upper": "2026-02-01T00:00:00Z"
    }
  }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.range(
    "createdAt",
    "2026-01-01T00:00:00Z",
    "2026-02-01T00:00:00Z"
));
```

---

## 7. AND / OR / NOT (nested)

**JSON**

```json
{
  "filter": {
    "and": [
      { "eq": { "field": "firstName", "value": "Asha" } },
      {
        "or": [
          { "eq": { "field": "status", "value": "CREATED" } },
          { "not": { "eq": { "field": "status", "value": "FAILED" } } }
        ]
      }
    ]
  }
}
```

**Java**

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

---

## 8. Sort

### 8.1 Single sort

**JSON**

```json
{
  "sort": [{ "field": "createdAt", "dir": "DESC" }]
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = new Query().withSort(List.of(new SortField("createdAt", SortField.Direction.DESC)));
```

### 8.2 Multi sort (stable order)

**JSON**

```json
{
  "sort": [
    { "field": "createdAt", "dir": "DESC" },
    { "field": "id", "dir": "DESC" }
  ]
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = new Query().withSort(List.of(
    new SortField("createdAt", SortField.Direction.DESC),
    new SortField("id", SortField.Direction.DESC)
));
```

---

## 9. Offset paging

**JSON**

```json
{
  "page": { "type": "offset", "offset": 0, "limit": 50 }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = new Query().withPage(new OffsetPage(0, 50));
```

---

## 10. Seek paging

Seek paging requires:
- a stable `sort`, and
- `page.type=seek` plus an `after` cursor map.

**JSON**

```json
{
  "sort": [
    { "field": "createdAt", "dir": "DESC" },
    { "field": "id", "dir": "DESC" }
  ],
  "page": {
    "type": "seek",
    "limit": 50,
    "after": {
      "createdAt": "2026-01-01T00:00:00Z",
      "id": "00000000-0000-0000-0000-000000000000"
    }
  }
}
```

**Java**

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

---

## 11. Projection

**JSON**

```json
{
  "projection": ["id", "firstName", "lastName"]
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.Query;
import java.util.List;

Query q = new Query().withProjection(List.of("id", "firstName", "lastName"));
```

---

## 12. GroupBy

**JSON**

```json
{
  "groupBy": { "fields": ["status"] }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.Query;
import io.intellixity.nativa.persistence.query.aggregation.GroupBy;

Query q = new Query().withGroupBy(GroupBy.of("status"));
```

---

## 13. Params (filter placeholders + view SQL params)

### 13.1 Filter placeholder values (`{param:...}`)

**JSON**

```json
{
  "filter": { "eq": { "field": "tenantId", "value": { "param": "tenantId" } } },
  "params": { "tenantId": "tenantA" }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.eq("tenantId", QueryValues.param("tenantId")))
    .withParam("tenantId", "tenantA");
```

### 13.2 Params for authored view SQL (`:param`)

If your view SQL contains named params (example `:tenantId`), you can supply them via `Query.params`.

**JSON**

```json
{
  "params": { "tenantId": "tenantA" }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.Query;

Query q = new Query().withParam("tenantId", "tenantA");
```

---

## 14. Postgres-only operators (ARRAY_*, JSON_*)

These operators are supported by:
- `PostgresDialect` (JDBC) for `ARRAY_*` and `JSON_*`

Other JDBC dialects may throw “not supported” at runtime.

### 14.1 ARRAY_CONTAINS / ARRAY_OVERLAPS

**JSON (contains all)**

```json
{
  "filter": { "array_contains": { "field": "tags", "value": ["vip", "active"] } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = Query.of(QueryFilters.arrayContains("tags", List.of("vip", "active")));
```

**JSON (overlaps any)**

```json
{
  "filter": { "array_overlaps": { "field": "tags", "value": ["vip", "trial"] } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = Query.of(QueryFilters.arrayOverlaps("tags", List.of("vip", "trial")));
```

### 14.2 JSON_PATH_EXISTS / JSON_VALUE_EQ

Assume `profile` is a JSON column.

**JSON (path exists)**

```json
{
  "filter": { "json_path_exists": { "field": "profile", "value": "$.address.city" } }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.jsonPathExists("profile", "$.address.city"));
```

**JSON (fragment match, Postgres `@>`)**

```json
{
  "filter": {
    "json_value_eq": {
      "field": "profile",
      "value": { "path": "$.address.city", "value": "Bengaluru" }
    }
  }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;

Query q = Query.of(QueryFilters.jsonValueEq("profile", "$.address.city", "Bengaluru"));
```


