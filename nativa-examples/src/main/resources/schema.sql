-- Apply this schema in EACH tenant database (tenant-per-database model).
-- Example DBs: nativa_tenant_a, nativa_tenant_b

create table if not exists customers (
  id uuid primary key,
  tenant_id text not null,
  first_name text null,
  last_name text null,
  email text null,
  phone text null,
  created_at timestamptz null
);

create table if not exists services (
  id uuid primary key,
  tenant_id text not null,
  code text null,
  name text null,
  description text null,
  price double precision null,
  currency text null,
  active boolean null,
  created_at timestamptz null
);

create table if not exists orders (
  id uuid primary key,
  tenant_id text not null,

  customer_id uuid not null,
  customer_tenant_id text not null,
  customer_first_name text null,
  customer_last_name text null,
  customer_email text null,
  customer_phone text null,
  customer_created_at timestamptz null,

  service_id uuid not null,
  service_tenant_id text not null,
  service_code text null,
  service_name text null,
  service_description text null,
  service_price double precision null,
  service_currency text null,
  service_active boolean null,
  service_created_at timestamptz null,

  total_amount double precision null,
  currency text null,
  payment_method text null,
  payment_status text null,
  paid_at timestamptz null,
  created_at timestamptz null
);

create table if not exists appointments (
  id uuid primary key,
  tenant_id text not null,
  order_id uuid not null,
  customer_id uuid not null,
  service_id uuid not null,
  scheduled_at timestamptz null,
  status text null,
  notes text null,
  created_at timestamptz null
);




