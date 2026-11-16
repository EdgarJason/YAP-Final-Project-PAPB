-- YAP build gateway — Supabase migration 0001: schema + RLS
-- RANCANGAN-VERCEL-SUPABASE.md §6. Versioned migration; apply in order.
-- The service-role key bypasses RLS; anon/authenticated get no access at all.
-- The API (Vercel) is the only reader/writer and re-checks ownership (§5).

create extension if not exists pgcrypto;

-- SHA-256 helper bound to wherever pgcrypto actually lives. Supabase
-- installs extensions in `extensions`, vanilla Postgres in `public`;
-- SECURITY DEFINER functions below run with search_path public,pg_temp,
-- so digest() must never be called unqualified. The extension schema is
-- baked into this wrapper's body at migration time; the wrapper is
-- immutable and schema-qualified calls need no search_path widening.
do $$
declare
  v_schema text;
begin
  select n.nspname into v_schema
    from pg_extension e
    join pg_namespace n on n.oid = e.extnamespace
   where e.extname = 'pgcrypto';
  if v_schema is null then
    raise exception 'pgcrypto extension not found';
  end if;
  execute format(
    'create or replace function public.yap_sha256_hex(p_text text) '
    'returns text language sql immutable as $f$ '
    'select encode(%I.digest(p_text, ''sha256''), ''hex'') $f$',
    v_schema);
end $$;

-- ---------------------------------------------------------------- build_jobs
create table if not exists public.build_jobs (
  id uuid primary key,
  owner_hash text not null,
  prompt text not null,
  context text not null default '',
  response_mode text not null default 'apk',
  request_hash text,
  status text not null default 'queued',
  queue_revision bigint not null default 0,
  version bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  queued_at timestamptz not null default now(),
  completed_at timestamptz,
  lease_id uuid,
  lease_expires_at timestamptz,
  attempt_count integer not null default 0,
  attempt_started_at timestamptz,
  app_name text,
  message text,
  error_code text,
  sha256 text,
  size bigint,
  result_artifact_id uuid,
  result_expires_at timestamptz,
  preview_html text,
  verification jsonb,
  constraint build_jobs_prompt_len check (char_length(btrim(prompt)) > 0 and char_length(prompt) <= 6000),
  constraint build_jobs_context_len check (char_length(context) <= 12000),
  constraint build_jobs_response_mode check (response_mode in ('apk', 'chat')),
  constraint build_jobs_status check (status in ('queued', 'building', 'awaiting_answer', 'responded', 'ready', 'failed')),
  constraint build_jobs_app_name_len check (app_name is null or (char_length(app_name) <= 80 and app_name !~ '[\x00-\x1f\x7f]')),
  constraint build_jobs_message_len check (message is null or char_length(message) <= 6000)
);

-- ---------------------------------------------------------- build_questions
create table if not exists public.build_questions (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.build_jobs (id) on delete cascade,
  ordinal integer not null,
  question text not null,
  answer text,
  created_at timestamptz not null default now(),
  answered_at timestamptz,
  operation_id uuid,
  constraint build_questions_question_len check (char_length(btrim(question)) > 0 and char_length(question) <= 600),
  constraint build_questions_answer_len check (answer is null or char_length(answer) <= 1200),
  unique (job_id, ordinal),
  unique (job_id, operation_id)
);
-- At most one unanswered question per job (§6).
create unique index if not exists build_questions_one_unanswered_idx
  on public.build_questions (job_id) where answer is null;

-- ---------------------------------------------------------- build_artifacts
create table if not exists public.build_artifacts (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.build_jobs (id) on delete cascade,
  lease_id uuid,
  generation_id uuid not null,
  kind text not null,
  object_path text not null unique,
  expected_size bigint not null,
  expected_sha256 text not null,
  verified_size bigint,
  verified_sha256 text,
  status text not null default 'pending',
  created_at timestamptz not null default now(),
  verified_at timestamptz,
  delete_after timestamptz,
  package_name text,
  version_code integer,
  version_name text,
  certificate_sha256 text,
  verification_report jsonb,
  constraint build_artifacts_kind check (kind in ('apk', 'source', 'verification')),
  constraint build_artifacts_status check (status in ('pending', 'verified', 'rejected', 'deleting', 'deleted')),
  constraint build_artifacts_sha check (expected_sha256 ~ '^[a-f0-9]{64}$')
);

-- Result artifact FK added after both tables exist (circular reference).
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'build_jobs_result_artifact_fk'
  ) then
    alter table public.build_jobs
      add constraint build_jobs_result_artifact_fk
      foreign key (result_artifact_id) references public.build_artifacts (id);
  end if;
end $$;

-- ------------------------------------------------------------ build_events
create table if not exists public.build_events (
  id bigint generated always as identity primary key,
  job_id uuid not null references public.build_jobs (id) on delete cascade,
  event_type text not null,
  attempt integer,
  correlation_id uuid,
  created_at timestamptz not null default now()
);

-- ------------------------------------------------------------ worker_slots
create table if not exists public.worker_slots (
  id text primary key,
  job_id uuid references public.build_jobs (id) on delete set null,
  lease_id uuid,
  acquired_at timestamptz,
  updated_at timestamptz not null default now()
);
-- One active build for the demo phase (§6-§7).
insert into public.worker_slots (id) values ('muse-primary')
on conflict (id) do nothing;

-- ----------------------------------------------------------- usage_buckets
create table if not exists public.usage_buckets (
  owner_hash text not null,
  window_start timestamptz not null,
  window_minutes integer not null,
  builds integer not null default 0,
  submits integer not null default 0,
  primary key (owner_hash, window_start, window_minutes)
);

-- ---------------------------------------------------------- build_operations
create table if not exists public.build_operations (
  operation_id uuid primary key,
  worker_id text,
  action text not null,
  job_id uuid references public.build_jobs (id) on delete cascade,
  request_hash text not null,
  result jsonb,
  created_at timestamptz not null default now()
);

-- ------------------------------------------------------------------ indexes
create index if not exists build_jobs_status_queued_idx on public.build_jobs (status, queued_at, id);
create index if not exists build_jobs_lease_idx on public.build_jobs (lease_expires_at) where status = 'building';
create index if not exists build_jobs_owner_idx on public.build_jobs (owner_hash, created_at desc);
create index if not exists build_questions_job_idx on public.build_questions (job_id, ordinal);
create index if not exists build_artifacts_job_idx on public.build_artifacts (job_id, status);
create index if not exists build_events_job_idx on public.build_events (job_id, created_at);
create index if not exists build_events_created_idx on public.build_events (created_at);
create index if not exists build_operations_created_idx on public.build_operations (created_at);

-- ------------------------------------------------------- updated_at trigger
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists build_jobs_updated_at on public.build_jobs;
create trigger build_jobs_updated_at
  before update on public.build_jobs
  for each row execute function public.set_updated_at();

drop trigger if exists worker_slots_updated_at on public.worker_slots;
create trigger worker_slots_updated_at
  before update on public.worker_slots
  for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------- RLS
-- RLS on, and anon/authenticated explicitly denied on every table. The
-- service role bypasses RLS; the API re-checks ownership on every call (§5).
alter table public.build_jobs enable row level security;
alter table public.build_questions enable row level security;
alter table public.build_artifacts enable row level security;
alter table public.build_events enable row level security;
alter table public.worker_slots enable row level security;
alter table public.usage_buckets enable row level security;
alter table public.build_operations enable row level security;

do $$
declare t text;
begin
  foreach t in array array[
    'build_jobs', 'build_questions', 'build_artifacts', 'build_events',
    'worker_slots', 'usage_buckets', 'build_operations'
  ] loop
    execute format('drop policy if exists no_anon_access on public.%I', t);
    execute format(
      'create policy no_anon_access on public.%I for all to anon, authenticated using (false) with check (false)', t);
    execute format('revoke all on public.%I from anon, authenticated', t);
    execute format('grant all on public.%I to service_role', t);
  end loop;
end $$;
