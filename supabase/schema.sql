-- ═══════════════════════════════════════════════════════════════════
--  «کی برد؟» — طرح پایگاه‌داده‌ی ابری
--  این فایل را در پنل Supabase → SQL Editor یک‌جا اجرا کنید.
--  اجرای دوباره‌اش بی‌خطر است (همه‌چیز if not exists است).
-- ═══════════════════════════════════════════════════════════════════

-- ───────────────────────── پروفایل بازیکن ─────────────────────────
-- هر ردیف به یک حساب auth.users چسبیده است. یوزرنیم یکتاست.
create table if not exists public.profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    username      text not null unique,
    display_name  text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),

    -- یوزرنیم: ۳ تا ۲۴ نویسه، فقط حروف و رقم انگلیسی و زیرخط، همه کوچک
    constraint username_shape check (username ~ '^[a-z0-9_]{3,24}$')
);

-- جست‌وجوی سریع هنگام بررسی آزاد بودن یوزرنیم
create index if not exists profiles_username_idx on public.profiles (username);

-- ───────────────────────── آمار هر بازی ─────────────────────────
-- برای هر (کاربر، بازی) یک ردیف: چند بار بازی کرده و چند بار برده.
create table if not exists public.game_stats (
    user_id     uuid not null references public.profiles (id) on delete cascade,
    game_id     text not null,
    plays       integer not null default 0 check (plays >= 0),
    wins        integer not null default 0 check (wins >= 0),
    updated_at  timestamptz not null default now(),

    primary key (user_id, game_id),
    -- برد بیشتر از تعداد بازی بی‌معناست؛ جلوی داده‌ی بی‌ربط را می‌گیرد
    constraint wins_not_above_plays check (wins <= plays)
);

create index if not exists game_stats_user_idx on public.game_stats (user_id);

-- ───────────────────────── قواعد دسترسی ─────────────────────────
alter table public.profiles   enable row level security;
alter table public.game_stats enable row level security;

-- پروفایل‌ها: همه می‌توانند ببینند (برای جدول رتبه‌بندی و نمایش اسم برنده‌ها)
drop policy if exists "پروفایل‌ها برای همه خواندنی است" on public.profiles;
create policy "پروفایل‌ها برای همه خواندنی است"
    on public.profiles for select
    using (true);

-- ولی هر کس فقط پروفایل خودش را می‌سازد و ویرایش می‌کند
drop policy if exists "هر کس پروفایل خودش را می‌سازد" on public.profiles;
create policy "هر کس پروفایل خودش را می‌سازد"
    on public.profiles for insert
    with check (auth.uid() = id);

drop policy if exists "هر کس پروفایل خودش را ویرایش می‌کند" on public.profiles;
create policy "هر کس پروفایل خودش را ویرایش می‌کند"
    on public.profiles for update
    using (auth.uid() = id)
    with check (auth.uid() = id);

-- آمار: خواندنی برای همه (رتبه‌بندی)، نوشتنی فقط توسط صاحبش
drop policy if exists "آمار برای همه خواندنی است" on public.game_stats;
create policy "آمار برای همه خواندنی است"
    on public.game_stats for select
    using (true);

drop policy if exists "هر کس آمار خودش را می‌نویسد" on public.game_stats;
create policy "هر کس آمار خودش را می‌نویسد"
    on public.game_stats for insert
    with check (auth.uid() = user_id);

drop policy if exists "هر کس آمار خودش را به‌روز می‌کند" on public.game_stats;
create policy "هر کس آمار خودش را به‌روز می‌کند"
    on public.game_stats for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- ───────────────────────── کمکی‌ها ─────────────────────────

-- آزاد بودن یوزرنیم را قبل از ثبت‌نام چک می‌کند.
-- security definer است تا کاربرِ ناشناس هم بتواند صدایش بزند.
create or replace function public.username_available(candidate text)
returns boolean
language sql
security definer
set search_path = public
as $$
    select not exists (
        select 1 from public.profiles where username = lower(candidate)
    );
$$;

grant execute on function public.username_available(text) to anon, authenticated;

-- به‌روزرسانی خودکار ستون updated_at
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
    for each row execute function public.touch_updated_at();

drop trigger if exists game_stats_touch on public.game_stats;
create trigger game_stats_touch before update on public.game_stats
    for each row execute function public.touch_updated_at();

-- ───────────────────────── جدول رتبه‌بندی ─────────────────────────
-- مجموع بازی و برد هر بازیکن، برای بخش «پز دادن»
create or replace view public.leaderboard as
    select
        p.id,
        p.username,
        p.display_name,
        coalesce(sum(s.plays), 0)::int as total_plays,
        coalesce(sum(s.wins), 0)::int  as total_wins,
        count(distinct s.game_id)::int as games_tried
    from public.profiles p
    left join public.game_stats s on s.user_id = p.id
    group by p.id, p.username, p.display_name;

grant select on public.leaderboard to anon, authenticated;
