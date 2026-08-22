-- ═══════════════════════════════════════════════════════════════════
--  «کی برد؟» — گزارش‌گیری ناشناس (تحلیل محصول)
--  در پنل Supabase → SQL Editor یک‌جا اجرا کنید. اجرای دوباره بی‌خطر است.
--
--  اصل طراحی: اپ فقط «می‌نویسد» (با کلید anon)، هیچ‌وقت نمی‌خواند.
--  خواندن و گزارش فقط از همین پنل — با نماهای آماده‌ی پایین همین فایل.
--  هیچ داده‌ی شخصی ثبت نمی‌شود: شناسه‌ی دستگاه یک UUID تصادفیِ تولیدشده
--  داخل خود اپ است و با هیچ چیز واقعی گره نمی‌خورد.
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────── نشست‌ها ─────────────────────────
-- هر بار که اپ به جلو می‌آید یک نشست است؛ هر ۶۰ ثانیه ضربان می‌زند.
create table if not exists public.sessions (
    id            uuid primary key,
    device_id     uuid not null,
    user_id       uuid references public.profiles (id) on delete set null,
    app_version   text,
    sdk_int       integer,
    started_at    timestamptz not null default now(),
    last_seen_at  timestamptz not null default now()
);
create index if not exists sessions_started_idx on public.sessions (started_at);
create index if not exists sessions_device_idx  on public.sessions (device_id);

-- ───────────────────────── رویدادها ─────────────────────────
create table if not exists public.events (
    id          bigint generated always as identity primary key,
    session_id  uuid,
    device_id   uuid not null,
    user_id     uuid,
    name        text not null,
    props       jsonb not null default '{}'::jsonb,
    client_ts   timestamptz not null,
    created_at  timestamptz not null default now()
);
create index if not exists events_name_ts_idx   on public.events (name, client_ts);
create index if not exists events_session_idx   on public.events (session_id);
create index if not exists events_game_idx      on public.events ((props->>'game_id'));

-- ───────────────────────── قواعد دسترسی ─────────────────────────
alter table public.sessions enable row level security;
alter table public.events   enable row level security;

-- اپ فقط رویداد اضافه می‌کند؛ نه خواندن، نه ویرایش، نه حذف
drop policy if exists "اپ فقط رویداد می‌نویسد" on public.events;
create policy "اپ فقط رویداد می‌نویسد"
    on public.events for insert
    to anon, authenticated
    with check (true);

-- نشست با RPC زیر ساخته/به‌روز می‌شود تا کسی نتواند نشست دیگران را دست بزند
create or replace function public.analytics_heartbeat(
    p_session   uuid,
    p_device    uuid,
    p_user      uuid,
    p_version   text,
    p_sdk       integer
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.sessions (id, device_id, user_id, app_version, sdk_int)
    values (p_session, p_device, p_user, p_version, p_sdk)
    on conflict (id) do update
        set last_seen_at = now(),
            user_id      = coalesce(excluded.user_id, public.sessions.user_id)
        where public.sessions.device_id = excluded.device_id;
end;
$$;
grant execute on function public.analytics_heartbeat(uuid, uuid, uuid, text, integer) to anon, authenticated;

-- ═══════════════════════ نماهای گزارش (فقط برای پنل) ═══════════════════════
-- این نماها عمداً به anon/authenticated داده نمی‌شوند.

-- کاربر فعال روزانه (دستگاه یکتا)
create or replace view public.v_daily_active as
    select date_trunc('day', started_at)::date as day,
           count(distinct device_id)            as active_devices,
           count(*)                             as sessions
    from public.sessions
    group by 1 order by 1 desc;

-- محبوبیت بازی‌ها: شروع، پایان، رهاشده و نرخ رها کردن
create or replace view public.v_game_funnel as
    with s as (
        select props->>'game_id' as game_id,
               count(*) filter (where name = 'game_start')   as starts,
               count(*) filter (where name = 'game_finish')  as finishes,
               count(*) filter (where name = 'game_abandon') as abandons,
               count(distinct device_id) filter (where name = 'game_start') as devices
        from public.events
        where name in ('game_start', 'game_finish', 'game_abandon')
        group by 1
    )
    select *,
           case when starts > 0 then round(100.0 * abandons / starts, 1) else 0 end as abandon_pct
    from s order by starts desc;

-- پایان/رهاشدن به تفکیک راهِ بازی (local / lan / online) — شروع همیشه local ثبت می‌شود
create or replace view public.v_game_mode_split as
    select props->>'game_id' as game_id,
           coalesce(props->>'mode', 'local') as mode,
           count(*) filter (where name = 'game_finish')  as finishes,
           count(*) filter (where name = 'game_abandon') as abandons
    from public.events
    where name in ('game_finish', 'game_abandon')
    group by 1, 2 order by 1, 2;

-- در کدام فاز رها می‌کنند (برای پیدا کردن صفحه‌های خسته‌کننده)
create or replace view public.v_abandon_by_phase as
    select props->>'game_id' as game_id,
           props->>'elapsed_bucket' as elapsed,
           count(*) as abandons
    from public.events where name = 'game_abandon'
    group by 1, 2 order by 1, 3 desc;

-- پیک کاربر هم‌زمان (همه‌ی اپ) در هر روز — جاروی زمانی روی نشست‌ها
create or replace view public.v_peak_concurrency_daily as
    with pts as (
        select started_at as t, 1 as d from public.sessions
        union all
        select last_seen_at + interval '90 seconds' as t, -1 as d from public.sessions
    ),
    running as (
        select t, sum(d) over (order by t, d rows unbounded preceding) as concurrent
        from pts
    )
    -- برای هر روز: بیشترین هم‌زمانی و دقیقاً کِی اتفاق افتاد
    select distinct on (t::date)
           t::date    as day,
           concurrent as peak_concurrent,
           t          as peak_at
    from running
    order by t::date desc, concurrent desc, t asc;

-- پیک کاربرِ «داخل اتاق اینترنتی» در هر روز
create or replace view public.v_peak_online_daily as
    with pts as (
        select client_ts as t, 1 as d from public.events where name = 'online_enter'
        union all
        select client_ts as t, -1 as d from public.events where name = 'online_leave'
    ),
    running as (
        select t, sum(d) over (order by t, d rows unbounded preceding) as concurrent
        from pts
    )
    select distinct on (t::date)
           t::date                  as day,
           greatest(concurrent, 0)  as peak_online,
           t                        as peak_at
    from running
    order by t::date desc, concurrent desc, t asc;

-- اتاق‌های اینترنتی: چند تا ساخته شد، چند تا پیوستن موفق/ناموفق
create or replace view public.v_online_rooms as
    select date_trunc('day', client_ts)::date as day,
           count(*) filter (where name = 'online_enter' and props->>'role' = 'host')  as rooms_created,
           count(*) filter (where name = 'online_enter' and props->>'role' = 'guest') as guests_joined,
           count(*) filter (where name = 'online_join_failed')                        as join_failures,
           count(*) filter (where name = 'lan_host')                                   as lan_hosts
    from public.events group by 1 order by 1 desc;

-- قیف ورود: خوش‌آمدگویی، راهنما، ثبت‌نام
create or replace view public.v_onboarding_funnel as
    select date_trunc('day', client_ts)::date as day,
           count(*) filter (where name = 'onboarding_done' and (props->>'skipped')::boolean = false) as completed,
           count(*) filter (where name = 'onboarding_done' and (props->>'skipped')::boolean = true)  as skipped,
           count(*) filter (where name = 'guide_open')                                               as guides_opened,
           count(*) filter (where name = 'account_register')                                         as registrations
    from public.events group by 1 order by 1 desc;

-- واکنش به درخواست امتیاز مایکت
create or replace view public.v_rate_prompt as
    select props->>'result' as result, count(*) as n
    from public.events where name = 'rate_prompt'
    group by 1 order by 2 desc;

-- مدت میانگین بازی‌های تمام‌شده (ثانیه) به تفکیک بازی
create or replace view public.v_game_duration as
    select props->>'game_id' as game_id,
           round(avg((props->>'elapsed_s')::numeric))    as avg_seconds,
           round(percentile_cont(0.5) within group (order by (props->>'elapsed_s')::numeric)) as median_seconds,
           count(*) as finished_games
    from public.events
    where name = 'game_finish' and props ? 'elapsed_s'
    group by 1 order by finished_games desc;
