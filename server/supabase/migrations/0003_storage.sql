-- YAP build gateway — Supabase migration 0003: private Storage bucket
-- RANCANGAN-VERCEL-SUPABASE.md §5, §9-§10. Bucket is private; only the
-- service role (via the Vercel backend) issues signed upload/download URLs.
-- anon/authenticated have no access to objects in this bucket.
-- Apply after 0001/0002. Requires the storage schema (present in Supabase
-- projects). Safe to re-run: bucket insert is ON CONFLICT DO NOTHING.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('yap-artifacts', 'yap-artifacts', false, 33554432, array['application/vnd.android.package-archive', 'application/zip', 'application/json'])
on conflict (id) do nothing;

-- Deny direct access for anon/authenticated on objects in this bucket.
-- The service role bypasses RLS; signed URLs are issued by the backend.
do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'storage' and tablename = 'objects' and policyname = 'yap_artifacts_no_anon') then
    create policy yap_artifacts_no_anon on storage.objects
      for all to anon, authenticated
      using (bucket_id = 'yap-artifacts' and false)
      with check (bucket_id = 'yap-artifacts' and false);
  end if;
end $$;
