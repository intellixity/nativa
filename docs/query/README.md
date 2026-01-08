# Query (Reference)

This document is the **reference** for Nativa’s `Query` model.

## Purpose of `Query`

`Query` is a **portable query AST** that can be rendered into:
- **SQL** for JDBC engines (via the active `JdbcDialect`)
- **BSON** for Mongo engines

`Query` is designed so integrators can:
- send queries over HTTP as JSON (or YAML, same shape)
- build queries in Java without writing SQL/Mongo queries directly
- keep performance high by rendering to **native** backend queries

At a high level, `Query` models:
- **filter**: a boolean expression tree (`AND` / `OR` / `NOT` / conditions)
- **sort**: ordered fields
- **page**: offset paging or keyset/seek paging
- **projection**: optional list of fields
- **groupBy**: optional grouping
- **params**: named parameters used by filters and/or view SQL

## Canonical JSON/YAML shape (top level)

```json
{
  "filter": { "...": "..." },
  "sort": [{ "field": "createdAt", "dir": "DESC" }],
  "page": { "type": "offset", "offset": 0, "limit": 50 },
  "projection": ["id", "firstName"],
  "groupBy": { "fields": ["status"] },
  "params": { "tenantId": "tenantA" }
}
```

Notes:
- **JSON and YAML shortnames are identical** (the keys shown below).
- Any missing section is treated as “not specified” (e.g. no filter).

## Canonical filter element forms (shortnames)

`Query.filter` is a `QueryElement`, which can be:
- a **group**: `{ "and": [ ... ] }` or `{ "or": [ ... ] }`
- a **not**: `{ "not": <element> }`
- a **condition**: `{ "<op>": { ... } }`

### Group

```json
{ "and": [ { "eq": { "field": "status", "value": "CREATED" } } ] }
```

```json
{ "or": [ { "eq": { "field": "status", "value": "CREATED" } } ] }
```

### NOT

```json
{ "not": { "eq": { "field": "status", "value": "FAILED" } } }
```

### Condition (generic)

```json
{ "<op>": { "field": "<propertyPath>", "value": "<scalar>", "not": false } }
```

`not` is optional and defaults to `false`.

## Operator shortnames (canonical)

The canonical operator key is `operator.name().toLowerCase()` (see `QueryJsonSerializer`), so:

| Operator enum | JSON/YAML shortname | Condition body |
|---|---|---|
| `EQ` | `eq` | `{field, value}` |
| `NE` | `ne` | `{field, value}` |
| `GT` | `gt` | `{field, value}` (non-null) |
| `GE` | `ge` | `{field, value}` (non-null) |
| `LT` | `lt` | `{field, value}` (non-null) |
| `LE` | `le` | `{field, value}` (non-null) |
| `IN` | `in` | `{field, values:[...]}` |
| `NIN` | `nin` | `{field, values:[...]}` |
| `RANGE` | `range` | `{field, lower, upper}` (both non-null) |
| `LIKE` | `like` | `{field, value}` (non-null) |
| `ARRAY_CONTAINS` | `array_contains` | `{field, value\|values}` (dialect-specific) |
| `ARRAY_NOT_CONTAINS` | `array_not_contains` | `{field, value\|values}` (dialect-specific) |
| `ARRAY_OVERLAPS` | `array_overlaps` | `{field, value\|values}` (dialect-specific) |
| `ARRAY_NOT_OVERLAPS` | `array_not_overlaps` | `{field, value\|values}` (dialect-specific) |
| `JSON_PATH_EXISTS` | `json_path_exists` | `{field, value:\"$.a.b\"}` (dialect-specific) |
| `JSON_VALUE_EQ` | `json_value_eq` | `{field, value:{path:\"$.a.b\", value:<any>}}` (dialect-specific) |

Dialect notes:
- JDBC: array/json operators are implemented by dialects that support them (e.g. Postgres). Other dialects may throw.
- Mongo: these operators are supported in `MongoQueryRenderer`.

## NULL semantics (EQ/NE)

If `value` is `null`:
- **JDBC** renders `EQ null` as `IS NULL`, and `NE null` as `IS NOT NULL` (see `AbstractJdbcSqlDialect`)
- **Mongo** renders `EQ null` as `{field: null}` and `NE null` as `{field: {$ne:null}}` (see `MongoQueryRenderer`)

## Params (`params` + `{param: ...}`)

You can reference named parameters from filter values using `QueryValues.Param`.

### Canonical param reference (value placeholder)

```json
{
  "filter": {
    "eq": { "field": "tenantId", "value": { "param": "tenantId" } }
  },
  "params": { "tenantId": "tenantA" }
}
```

Also accepted:

```json
{ "param": "x" }
{ "$param": "x" }
```

Resolution:
- params are resolved by `QueryNormalizer` using `Query#param(name)`
- missing params fail fast (`Query#param` throws)

## Paging shortnames

`page` is a sealed interface implemented by:

### Offset page (`OffsetPage`)

```json
{ "page": { "type": "offset", "offset": 0, "limit": 50 } }
```

`type` is optional; if omitted the deserializer treats it as offset paging.

### Seek page (`SeekPage`)

```json
{
  "sort": [{ "field": "createdAt", "dir": "DESC" }, { "field": "id", "dir": "DESC" }],
  "page": { "type": "seek", "limit": 50, "after": { "createdAt": "2026-01-01T00:00:00Z", "id": "..." } }
}
```

Seek paging requires:
- stable sort fields (often `(createdAt DESC, id DESC)`), and
- an `after` map carrying the last-seen values for those fields.

## Sort shortnames

```json
{ "sort": [{ "field": "createdAt", "dir": "DESC" }] }
```

`dir` is `ASC` or `DESC`. If omitted, Java defaults to `ASC` (`SortField`).

## Projection shortname

```json
{ "projection": ["id", "firstName", "lastName"] }
```

Projection support is backend/dialect dependent. (If a backend/view cannot project, it may ignore or enforce it.)

## GroupBy shortname

```json
{ "groupBy": { "fields": ["status"] } }
```

## Full class catalog (package → class → keywords)

### `io.intellixity.nativa.persistence.query`

| Class | Role | Canonical JSON/YAML keys (shortnames) |
|---|---|---|
| `Query` | Top-level query container | `filter`, `page`, `sort`, `projection`, `groupBy`, `params` |
| `QueryElement` | Filter AST node | (polymorphic; see below) |
| `Condition` | Leaf condition (`property` + `operator` + values) | `{ "<op>": { field, value|values|lower|upper, not? } }` |
| `Operator` | Condition operator enum | `<op>` shortname is `operator.name().toLowerCase()` |
| `LogicalGroup` | AND/OR group node | `{and:[...]}` / `{or:[...]}` |
| `Clause` | AND/OR enum | group shortnames: `and`, `or` |
| `NotElement` | Unary NOT node | `{not:<element>}` |
| `SortField` | Sort field descriptor | `sort[].field`, `sort[].dir` |
| `Page` | Paging marker | `page` object |
| `OffsetPage` | Offset paging | `page.type=offset` (optional), `page.offset`, `page.limit` |
| `SeekPage` | Keyset/seek paging | `page.type=seek`, `page.limit`, `page.after` |
| `QueryValues` / `QueryValues.Param` | Param placeholder value | `{param:\"x\"}` (or `{\"$param\":\"x\"}`) |
| `QueryVisitor` | Visitor API for traversals | (internal API; no JSON keys) |
| `QueryValidationException` | Query validation failure | (exception; no JSON keys) |
| `QueryJsonSerializer` | Canonical JSON writer | (codec; no JSON keys) |
| `QueryJsonDeserializer` | Canonical JSON reader | (codec; no JSON keys) |

### `io.intellixity.nativa.persistence.query.aggregation`

| Class | Role | Canonical JSON/YAML keys |
|---|---|---|
| `GroupBy` | Grouping descriptor | `groupBy.fields` |

## Back-compat input formats (accepted by `QueryJsonDeserializer`)

These are accepted for compatibility but are not the canonical form.

### Back-compat condition

Aliases supported:
- `operator` or `op`
- `property` or `propertyPath`

```json
{
  "filter": {
    "op": "EQ",
    "propertyPath": "tenantId",
    "value": "tenantA"
  }
}
```

### Back-compat group (map form)

```json
{
  "filter": {
    "clause": "AND",
    "elements": [
      { "operator": "EQ", "property": "status", "value": "CREATED" }
    ]
  }
}
```

### Back-compat group (list form)

`LogicalGroup.fromList` supports a nested list style that starts with `{clause:AND|OR}` followed by elements.

```json
{
  "filter": [
    { "clause": "AND" },
    { "operator": "EQ", "property": "status", "value": "CREATED" }
  ]
}
```


