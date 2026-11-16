-- YAP build gateway — Supabase migration 0002: transactional RPCs
-- RANCANGAN-VERCEL-SUPABASE.md §7-§9. All mutations go through these
-- SECURITY DEFINER functions. They check leases against database time
-- (now()), hold the single-worker slot in the same transaction, and
-- enforce idempotency via build_operations. Business failures are returned
-- as {ok:false, status, code, message} envelopes (not raised), so the
-- Node client can map them deterministically; unexpected errors raise.
-- Every function: SECURITY DEFINER, fixed search_path, schema-qualified.

-- ------------------------------------------------------------ helpers
create or replace function public.yap_op_hash(p_text text)
returns text language sql immutable as $$
  select public.yap_sha256_hex(p_text);
$$;

create or replace function public.yap_job_json(p_job_id uuid)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job jsonb;
begin
  select to_jsonb(j) - 'verification' into v_job
  from (
    select j.*,
      coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', q.id, 'question', q.question, 'answer', q.answer
        ) order by q.ordinal)
        from public.build_questions q where q.job_id = j.id
      ), '[]'::jsonb) as clarifications
    from public.build_jobs j where j.id = p_job_id
  ) j;
  return v_job;
end;
$$;

create or replace function public.yap_release_slot(p_job_id uuid, p_lease_id uuid)
returns void
language plpgsql security definer set search_path = public, pg_temp as $$
begin
  update public.worker_slots
     set job_id = null, lease_id = null, updated_at = now()
   where id = 'muse-primary' and job_id = p_job_id
     and (p_lease_id is null or lease_id = p_lease_id);
end;
$$;

create or replace function public.yap_fail_envelope(p_status integer, p_code text, p_message text)
returns jsonb language sql immutable as $$
  select jsonb_build_object('ok', false, 'status', p_status, 'code', p_code, 'message', p_message);
$$;

-- ------------------------------------------------------------ create
create or replace function public.yap_create_job(
  p_id uuid, p_owner_hash text, p_prompt text, p_context text, p_response_mode text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_hash text;
  v_existing public.build_jobs;
  v_queued integer;
  v_today integer;
begin
  if p_id is null or char_length(btrim(p_prompt)) = 0 or char_length(p_prompt) > 6000
     or char_length(p_context) > 12000 or p_response_mode not in ('apk', 'chat') then
    return public.yap_fail_envelope(400, 'invalid_input', 'Payload tidak valid.');
  end if;
  -- Serialize creates so the queue/daily limits below cannot race:
  -- COUNT then INSERT is not atomic on its own. One fixed advisory key
  -- for all creates; released at transaction end (each RPC = 1 txn).
  perform pg_advisory_xact_lock(20261116);
  v_hash := public.yap_sha256_hex(
    p_id::text || '|' || coalesce(p_owner_hash, '') || '|' || p_prompt || '|' ||
    coalesce(p_context, '') || '|' || p_response_mode);

  select * into v_existing from public.build_jobs where id = p_id;
  if found then
    if v_existing.owner_hash <> p_owner_hash then
      return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
    end if;
    if v_existing.request_hash is not null and v_existing.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'id_reused', 'ID sudah dipakai.');
    end if;
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_id), 'created', false);
  end if;

  select count(*) into v_queued from public.build_jobs
   where status in ('queued', 'building', 'awaiting_answer');
  if v_queued >= 20 then
    return public.yap_fail_envelope(409, 'queue_full', 'Antrean sedang penuh. Coba lagi nanti.');
  end if;
  select count(*) into v_today from public.build_jobs
   where owner_hash = p_owner_hash and created_at >= date_trunc('day', now());
  if v_today >= 5 then
    return public.yap_fail_envelope(429, 'daily_quota_exceeded', 'Batas 5 build per hari tercapai.');
  end if;

  insert into public.build_jobs
    (id, owner_hash, prompt, context, response_mode, request_hash, status)
  values (p_id, p_owner_hash, p_prompt, coalesce(p_context, ''), p_response_mode, v_hash, 'queued');
  insert into public.build_events (job_id, event_type) values (p_id, 'created');
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_id), 'created', true);
end;
$$;

-- ------------------------------------------------------------ signal
create or replace function public.yap_signal()
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
begin
  select * into v_job from public.build_jobs
   where status = 'queued'
      or (status = 'building' and lease_expires_at <= now())
   order by queued_at, id limit 1;
  if not found then
    return jsonb_build_object('ok', true, 'pending', false);
  end if;
  return jsonb_build_object(
    'ok', true, 'pending', true, 'job_id', v_job.id,
    'queue_revision', v_job.queue_revision, 'lease_id', v_job.lease_id);
end;
$$;

-- ------------------------------------------------------------ claim
create or replace function public.yap_claim_job(p_claim_request_id uuid, p_worker_id text)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_op public.build_operations;
  v_hash text;
  v_job public.build_jobs;
  v_slot_job uuid;
  v_slot_lease uuid;
  v_slot_active boolean;
  v_candidate_id uuid;
  v_candidate public.build_jobs;
  v_lease uuid;
begin
  if p_claim_request_id is null then
    return public.yap_fail_envelope(400, 'invalid_input', 'claimRequestId wajib diisi.');
  end if;
  v_hash := public.yap_op_hash('claim|' || coalesce(p_worker_id, 'muse-primary'));
  select * into v_op from public.build_operations where operation_id = p_claim_request_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    if v_op.worker_id is distinct from p_worker_id then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    -- Replay must never hand out a replacement lease: only return the
    -- job when the lease recorded for this operation is still the
    -- active lease of a building job. Otherwise the old attempt is
    -- dead (superseded, expired, or terminal) and gets job null.
    select * into v_job from public.build_jobs where id = (v_op.result ->> 'job_id')::uuid;
    if found and v_job.status = 'building'
       and v_job.lease_id = (v_op.result ->> 'lease')::uuid
       and v_job.lease_expires_at > now() then
      return jsonb_build_object(
        'ok', true, 'job', public.yap_job_json(v_job.id), 'replayed', true);
    end if;
    return jsonb_build_object('ok', true, 'job', null, 'replayed', true);
  end if;

  -- Single-worker slot, locked in the same transaction as the claim.
  select job_id, lease_id into v_slot_job, v_slot_lease
    from public.worker_slots where id = 'muse-primary' for update;
  if v_slot_job is not null then
    select exists(
      select 1 from public.build_jobs
       where id = v_slot_job and status = 'building'
         and lease_id = v_slot_lease and lease_expires_at > now()
    ) into v_slot_active;
    if v_slot_active then
      return jsonb_build_object('ok', true, 'job', null, 'slot_busy', true);
    end if;
  end if;

  select id into v_candidate_id from public.build_jobs
   where status = 'queued'
      or (status = 'building' and lease_expires_at <= now())
   order by queued_at, id limit 1 for update skip locked;
  if v_candidate_id is null then
    return jsonb_build_object('ok', true, 'job', null);
  end if;
  select * into v_candidate from public.build_jobs where id = v_candidate_id;
  if v_candidate.status = 'building' and v_candidate.attempt_count >= 3 then
    update public.build_jobs
       set status = 'failed', error_code = 'attempts_exhausted',
           completed_at = now(), lease_id = null, lease_expires_at = null, version = version + 1
     where id = v_candidate_id;
    perform public.yap_release_slot(v_candidate_id, null);
    return jsonb_build_object('ok', true, 'job', null);
  end if;

  v_lease := gen_random_uuid();
  update public.build_jobs
     set status = 'building', lease_id = v_lease,
         lease_expires_at = now() + interval '10 minutes',
         attempt_count = attempt_count + 1, attempt_started_at = now(),
         version = version + 1
   where id = v_candidate_id;
  update public.worker_slots
     set job_id = v_candidate_id, lease_id = v_lease, acquired_at = now(), updated_at = now()
   where id = 'muse-primary';
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_claim_request_id, p_worker_id, 'claim', v_candidate_id, v_hash,
          jsonb_build_object('job_id', v_candidate_id, 'lease', v_lease));
  insert into public.build_events (job_id, event_type, attempt)
  values (v_candidate_id, 'claimed', v_candidate.attempt_count + 1);
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(v_candidate_id), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ answer
create or replace function public.yap_answer_question(
  p_job_id uuid, p_owner_hash text, p_question_id uuid, p_answer text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_q public.build_questions;
begin
  select * into v_job from public.build_jobs where id = p_job_id for update;
  -- Admin ('admin' owner namespace) may act on any job, as in the v1
  -- handler; every other caller must match the job owner exactly.
  if not found or (v_job.owner_hash <> p_owner_hash and p_owner_hash <> 'admin') then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  end if;
  select * into v_q from public.build_questions where id = p_question_id and job_id = p_job_id;
  if not found then
    return public.yap_fail_envelope(409, 'question_inactive', 'Pertanyaan ini sudah tidak aktif.');
  end if;
  if v_q.answer = p_answer then
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'already_answered', true);
  end if;
  if v_q.answer is not null or v_job.status <> 'awaiting_answer' then
    return public.yap_fail_envelope(409, 'question_inactive', 'Pertanyaan ini sudah tidak aktif.');
  end if;
  update public.build_questions
     set answer = p_answer, answered_at = now() where id = p_question_id;
  update public.build_jobs
     set status = 'queued', queue_revision = queue_revision + 1, queued_at = now(),
         lease_id = null, lease_expires_at = null, version = version + 1
   where id = p_job_id;
  insert into public.build_events (job_id, event_type) values (p_job_id, 'answered');
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'already_answered', false);
end;
$$;

-- ------------------------------------------------------------ lease guard (shared by worker mutations)
create or replace function public.yap_require_lease(p_job_id uuid, p_lease_id uuid)
returns public.build_jobs
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
begin
  select * into v_job from public.build_jobs where id = p_job_id for update;
  if not found then
    raise exception 'job not found' using errcode = 'P0002';
  end if;
  if v_job.status <> 'building' or v_job.lease_id is distinct from p_lease_id
     or v_job.lease_expires_at is null or v_job.lease_expires_at <= now() then
    raise exception 'lease expired' using errcode = 'P0003';
  end if;
  return v_job;
end;
$$;

-- ------------------------------------------------------------ question
create or replace function public.yap_ask_question(
  p_job_id uuid, p_lease_id uuid, p_operation_id uuid, p_question text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_hash text;
  v_op public.build_operations;
  v_count integer;
  v_qid uuid;
begin
  v_hash := public.yap_op_hash('question|' || p_job_id::text || '|' || p_question);
  select * into v_op from public.build_operations where operation_id = p_operation_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', true);
  end if;
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  select count(*) into v_count from public.build_questions where job_id = p_job_id;
  if v_count >= 5 then
    return public.yap_fail_envelope(409, 'clarifications_exhausted', 'Batas klarifikasi tercapai. Lanjutkan build.');
  end if;
  insert into public.build_questions (job_id, ordinal, question, operation_id)
  values (p_job_id, v_count, p_question, p_operation_id) returning id into v_qid;
  update public.build_jobs
     set status = 'awaiting_answer', lease_id = null, lease_expires_at = null, version = version + 1
   where id = p_job_id;
  perform public.yap_release_slot(p_job_id, p_lease_id);
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_operation_id, 'muse-primary', 'question', p_job_id, v_hash,
          jsonb_build_object('job_id', p_job_id, 'question_id', v_qid));
  insert into public.build_events (job_id, event_type) values (p_job_id, 'question_asked');
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ message
create or replace function public.yap_send_message(
  p_job_id uuid, p_lease_id uuid, p_operation_id uuid, p_message text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_hash text;
  v_op public.build_operations;
begin
  v_hash := public.yap_op_hash('message|' || p_job_id::text || '|' || p_message);
  select * into v_op from public.build_operations where operation_id = p_operation_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', true);
  end if;
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  if v_job.response_mode <> 'chat' then
    return public.yap_fail_envelope(409, 'chat_unsupported', 'Klien APK lama membutuhkan hasil APK atau klarifikasi.');
  end if;
  update public.build_jobs
     set status = 'responded', message = p_message, completed_at = now(),
         lease_id = null, lease_expires_at = null, version = version + 1
   where id = p_job_id;
  perform public.yap_release_slot(p_job_id, p_lease_id);
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_operation_id, 'muse-primary', 'message', p_job_id, v_hash, jsonb_build_object('job_id', p_job_id));
  insert into public.build_events (job_id, event_type) values (p_job_id, 'message_sent');
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ metadata
create or replace function public.yap_set_metadata(
  p_job_id uuid, p_lease_id uuid, p_operation_id uuid, p_app_name text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_hash text;
  v_op public.build_operations;
begin
  v_hash := public.yap_op_hash('metadata|' || p_job_id::text || '|' || p_app_name);
  select * into v_op from public.build_operations where operation_id = p_operation_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', true);
  end if;
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  update public.build_jobs set app_name = p_app_name, version = version + 1 where id = p_job_id;
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_operation_id, 'muse-primary', 'metadata', p_job_id, v_hash, jsonb_build_object('job_id', p_job_id));
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ fail
create or replace function public.yap_fail_job(
  p_job_id uuid, p_lease_id uuid, p_operation_id uuid, p_error_code text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_hash text;
  v_op public.build_operations;
begin
  v_hash := public.yap_op_hash('fail|' || p_job_id::text || '|' || coalesce(p_error_code, 'build_failed'));
  select * into v_op from public.build_operations where operation_id = p_operation_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', true);
  end if;
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  update public.build_jobs
     set status = 'failed', error_code = coalesce(p_error_code, 'build_failed'),
         completed_at = now(), lease_id = null, lease_expires_at = null, version = version + 1
   where id = p_job_id;
  perform public.yap_release_slot(p_job_id, p_lease_id);
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_operation_id, 'muse-primary', 'fail', p_job_id, v_hash, jsonb_build_object('job_id', p_job_id));
  insert into public.build_events (job_id, event_type) values (p_job_id, 'failed');
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ renew
create or replace function public.yap_renew_lease(p_job_id uuid, p_lease_id uuid)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_deadline timestamptz;
begin
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  -- Attempt deadline: attempt_started_at + 30 minutes. A renew may
  -- never push the lease past it, and renewing after it is rejected.
  v_deadline := coalesce(v_job.attempt_started_at, now()) + interval '30 minutes';
  if now() >= v_deadline then
    return public.yap_fail_envelope(409, 'attempt_expired', 'Batas attempt 30 menit tercapai.');
  end if;
  update public.build_jobs
     set lease_expires_at = least(now() + interval '10 minutes', v_deadline), version = version + 1
   where id = p_job_id
  returning lease_expires_at into v_job.lease_expires_at;
  return jsonb_build_object(
    'ok', true, 'job', public.yap_job_json(p_job_id),
    'lease_expires_at', v_job.lease_expires_at);
end;
$$;

-- ------------------------------------------------------------ prepare artifact
create or replace function public.yap_prepare_artifact(
  p_job_id uuid, p_lease_id uuid, p_operation_id uuid,
  p_kind text, p_size bigint, p_sha256 text
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_hash text;
  v_op public.build_operations;
  v_generation uuid;
  v_path text;
  v_artifact public.build_artifacts;
begin
  if p_kind not in ('apk', 'source', 'verification') then
    return public.yap_fail_envelope(400, 'invalid_input', 'Jenis artifact tidak valid.');
  end if;
  if p_kind = 'apk' and p_size > 33554432 then
    return public.yap_fail_envelope(413, 'artifact_too_large', 'APK terlalu besar.');
  end if;
  if p_kind = 'source' and p_size > 8388608 then
    return public.yap_fail_envelope(413, 'artifact_too_large', 'Snapshot sumber terlalu besar.');
  end if;
  v_hash := public.yap_op_hash('prepare|' || p_job_id::text || '|' || p_kind || '|' || p_size::text || '|' || p_sha256);
  select * into v_op from public.build_operations where operation_id = p_operation_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    select * into v_artifact from public.build_artifacts where id = (v_op.result ->> 'artifact_id')::uuid;
    return jsonb_build_object('ok', true, 'artifact', to_jsonb(v_artifact), 'replayed', true);
  end if;
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  v_generation := gen_random_uuid();
  v_path := 'jobs/' || p_job_id::text || '/' || p_lease_id::text || '/' || v_generation::text || '/' ||
    case p_kind when 'apk' then 'app.apk' when 'source' then 'source.zip' else 'verification.json' end;
  insert into public.build_artifacts
    (job_id, lease_id, generation_id, kind, object_path, expected_size, expected_sha256, status)
  values (p_job_id, p_lease_id, v_generation, p_kind, v_path, p_size, p_sha256, 'pending')
  returning * into v_artifact;
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_operation_id, 'muse-primary', 'prepare', p_job_id, v_hash,
          jsonb_build_object('artifact_id', v_artifact.id));
  return jsonb_build_object('ok', true, 'artifact', to_jsonb(v_artifact), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ finalize
create or replace function public.yap_finalize_job(
  p_job_id uuid, p_lease_id uuid, p_operation_id uuid, p_artifact_id uuid,
  p_preview_html text, p_verified_size bigint, p_verified_sha256 text, p_verification jsonb
)
returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_job public.build_jobs;
  v_hash text;
  v_op public.build_operations;
  v_artifact public.build_artifacts;
begin
  v_hash := public.yap_op_hash('finalize|' || p_job_id::text || '|' || p_artifact_id::text);
  select * into v_op from public.build_operations where operation_id = p_operation_id;
  if found then
    if v_op.request_hash <> v_hash then
      return public.yap_fail_envelope(409, 'operation_conflict', 'ID operasi sudah dipakai dengan payload berbeda.');
    end if;
    return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', true);
  end if;
  -- Lease is re-checked AFTER the server verified the bytes (§9).
  begin
    v_job := public.yap_require_lease(p_job_id, p_lease_id);
  exception when sqlstate 'P0002' then
    return public.yap_fail_envelope(404, 'not_found', 'Tugas tidak ditemukan.');
  when sqlstate 'P0003' then
    return public.yap_fail_envelope(409, 'lease_expired', 'Klaim tugas tidak aktif.');
  end;
  select * into v_artifact from public.build_artifacts where id = p_artifact_id for update;
  if not found or v_artifact.job_id <> p_job_id or v_artifact.lease_id is distinct from p_lease_id then
    return public.yap_fail_envelope(409, 'artifact_mismatch', 'Artifact tidak terdaftar untuk klaim ini.');
  end if;
  if v_artifact.status <> 'pending' then
    return public.yap_fail_envelope(409, 'artifact_unverified', 'Artifact belum terverifikasi.');
  end if;
  if v_artifact.expected_size <> p_verified_size or v_artifact.expected_sha256 <> p_verified_sha256 then
    return public.yap_fail_envelope(400, 'artifact_mismatch', 'Ukuran atau SHA-256 artifact tidak cocok.');
  end if;
  if p_preview_html is not null and octet_length(p_preview_html) > 2097152 then
    return public.yap_fail_envelope(400, 'preview_too_large', 'Preview HTML melebihi 2 MiB.');
  end if;
  update public.build_artifacts
     set status = 'verified', verified_size = p_verified_size,
         verified_sha256 = p_verified_sha256, verified_at = now()
   where id = p_artifact_id;
  update public.build_jobs
     set status = 'ready', sha256 = p_verified_sha256, size = p_verified_size,
         result_artifact_id = p_artifact_id,
         result_expires_at = now() + interval '72 hours',
         preview_html = p_preview_html, verification = p_verification,
         completed_at = now(), lease_id = null, lease_expires_at = null, version = version + 1
   where id = p_job_id;
  perform public.yap_release_slot(p_job_id, p_lease_id);
  insert into public.build_operations (operation_id, worker_id, action, job_id, request_hash, result)
  values (p_operation_id, 'muse-primary', 'finalize', p_job_id, v_hash, jsonb_build_object('job_id', p_job_id));
  insert into public.build_events (job_id, event_type) values (p_job_id, 'ready');
  return jsonb_build_object('ok', true, 'job', public.yap_job_json(p_job_id), 'replayed', false);
end;
$$;

-- ------------------------------------------------------------ access
-- RPCs are for the service role only (called by the Vercel backend).
do $$
declare
  r record;
begin
  for r in select p.oid, p.proname
             from pg_proc p join pg_namespace n on n.oid = p.pronamespace
            where n.nspname = 'public' and p.proname like 'yap\_%' loop
    execute format('revoke all on function %s from public, anon, authenticated', r.oid::regprocedure);
    execute format('grant execute on function %s to service_role', r.oid::regprocedure);
  end loop;
end $$;
