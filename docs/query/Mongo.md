# Query (Mongo Examples)

This document is “copy/paste” Mongo usage for `Query`.

Prerequisites:
- You have a Mongo `DataEngine` (see `docs/authoring/Mongo.md` for full wiring).
- You have an `EntityViewRef` to target a specific authored view.

Assume:

```java
import io.intellixity.nativa.persistence.exec.EntityViewRef;

EntityViewRef CUSTOMER_VIEW = new EntityViewRef("Customer", "customer_table");
```

Mongo mapping notes:
- Default mapping policy is **camelCase → camelCase**.
- If `view.mapping` defines an explicit `ref`, filters/sorts use the mapped path.

Each example includes:
- **JSON** (canonical keys; same shape works in YAML)
- **Java** (using `Query`, `QueryFilters`, etc.)

## Table of Contents
- [1. Select all (no filter)](#1-select-all-no-filter)
- [2. EQ / NE (including NULL)](#2-eq--ne-including-null)
- [3. Comparisons (GT/GE/LT/LE)](#3-comparisons-gtgeltle)
- [4. LIKE (translated to regex)](#4-like-translated-to-regex)
- [5. IN / NIN](#5-in--nin)
- [6. RANGE](#6-range)
- [7. AND / OR / NOT (nested)](#7-and--or--not-nested)
- [8. Sort + paging](#8-sort--paging)
- [9. Seek paging](#9-seek-paging)
- [10. Projection](#10-projection)
- [11. GroupBy](#11-groupby)
- [12. Params](#12-params)
- [13. ARRAY_* operators](#13-array_-operators)
- [14. JSON_* operators](#14-json_-operators)
- [15. Mapping overrides](#15-mapping-overrides)

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

### 2.3 EQ null

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

### 2.4 NE null

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

## 4. LIKE (translated to regex)

Mongo translates SQL-like patterns:
- `%` → `.*`
- `_` → `.`

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

## 8. Sort + paging

**JSON**

```json
{
  "sort": [{ "field": "createdAt", "dir": "DESC" }],
  "page": { "type": "offset", "offset": 0, "limit": 50 }
}
```

**Java**

```java
import io.intellixity.nativa.persistence.query.*;
import java.util.List;

Query q = new Query()
    .withSort(List.of(new SortField("createdAt", SortField.Direction.DESC)))
    .withPage(new OffsetPage(0, 50));
```

---

## 9. Seek paging

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

## 10. Projection

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

## 11. GroupBy

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

## 12. Params

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

---

## 13. ARRAY_* operators

Mongo mapping:
- `ARRAY_CONTAINS` → `$all`
- `ARRAY_OVERLAPS` → `$in`
- NOT variants are rendered via `$nor`

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

---

## 14. JSON_* operators

Mongo mapping:
- `JSON_PATH_EXISTS`: path is appended to base field as a dot path, then `$exists:true`
- `JSON_VALUE_EQ`: uses `{path,value}` and compares the derived dot-path to the bound value

Assume `profile` is a document field.

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

**JSON (value eq)**

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

---

## 15. Mapping overrides

Default is camelCase → camelCase.

If you override a field path using `view.mapping`, the `field` in Query stays the **entity property path** and the engine resolves it to the mapped path.

Example authoring:

```yaml
views:
  customer_table:
    mapping:
      firstName:
        ref: first_name
      address:
        fields:
          city:
            ref: address.city_name
```

Then these filters are valid:

```json
{ "filter": { "eq": { "field": "firstName", "value": "Asha" } } }
```

```json
{ "filter": { "eq": { "field": "address.city", "value": "Bengaluru" } } }
```


