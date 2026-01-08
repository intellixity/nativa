## Nativa Examples (Spring Boot, Postgres, tenant-per-database)

This module is the **Integrator Quickstart**. Use it to learn:
- REST endpoints that accept `Query` JSON payloads
- YAML authoring examples (`src/main/resources/authoring/*.yml`)
- Governance headers and tenant-boundary behavior

Root manual: [`../README.md`](../README.md)
- Integrator Manual: `README.md#integrator-manual`
- Developer Manual: `README.md#developer-manual`
- User Manual (end-to-end examples): [`../UserManual.md`](../UserManual.md)

## Table of Contents
- [Prerequisites](#prerequisites)
- [Database setup](#database-setup)
- [Configure tenants](#configure-tenants)
- [Run the app](#run-the-app)
- [SQL logging](#sql-logging)
- [Postman](#postman)
- [API usage](#api-usage)
  - [Required headers](#required-headers)
  - [Create APIs](#create-apis)
  - [Search APIs (Query JSON)](#search-apis-query-json)
  - [Count APIs (Query JSON)](#count-apis-query-json)

## Prerequisites
- Java (as configured by the repo)
- Maven
- Postgres running locally

## Database setup
Create two databases:

```sql
create database nativa_tenant_a;
create database nativa_tenant_b;
```

Apply `src/main/resources/schema.sql` in **each** DB.

## Configure tenants
Edit `src/main/resources/application.yml`:
- `tenants.db.tenantA.jdbcUrl`, `username`, `password`
- `tenants.db.tenantB.jdbcUrl`, `username`, `password`

## Run the app

```bash
mvn -pl nativa-examples -am spring-boot:run
```

## SQL logging
Enable via config:
- SQL + timing: `logging.level.io.intellixity.nativa.persistence.jdbc=DEBUG`
- Bind summaries: `logging.level.io.intellixity.nativa.persistence.jdbc=TRACE`

## Postman
Import: `postman/nativa-examples.postman_collection.json`

## API usage

### Required headers
All requests must include:
- `X-Tenant-Id: tenantA|tenantB`

Optional (governance demo):
- `X-Enterprise: true|false`
- `X-Dealer-Id: <dealerId>` (required when `X-Enterprise=false`)

### Create APIs
Create a customer:

```bash
curl -X POST 'http://localhost:8080/api/customers' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenantA' \
  -H 'X-Enterprise: true' \
  -d '{ "firstName":"Asha","lastName":"K","email":"asha@example.com","phone":"+1-555" }'
```

Create a service:

```bash
curl -X POST 'http://localhost:8080/api/services' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenantA' \
  -H 'X-Enterprise: true' \
  -d '{ "code":"OIL","name":"Oil Change","description":"Basic","price":99.0,"currency":"USD" }'
```

### Search APIs (Query JSON)
Search customers by ID (back-compat shape):

```bash
curl -X POST 'http://localhost:8080/api/customers/search' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenantA' \
  -H 'X-Enterprise: true' \
  -d '{ "filter": { "op":"EQ", "propertyPath":"id", "value":"<uuid>" } }'
```

Search customers by canonical shape (example filter on `email`):

```bash
curl -X POST 'http://localhost:8080/api/customers/search' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenantA' \
  -H 'X-Enterprise: true' \
  -d '{ "filter": { "eq": { "field":"email", "value":"asha@example.com" } } }'
```

### Count APIs (Query JSON)
Count customers (canonical shape):

```bash
curl -X POST 'http://localhost:8080/api/customers/count' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenantA' \
  -H 'X-Enterprise: true' \
  -d '{ "filter": { "eq": { "field":"email", "value":"asha@example.com" } } }'
```

