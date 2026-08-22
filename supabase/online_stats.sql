-- ============================================================
-- کی برد؟ — آمار آنلاین بازیکن‌ها و لیدربورد هر بازی
-- بعد از schema.sql در SQL Editor اجرا شود.
--
-- قاعده: آمارِ هر بازیکن فقط از بازی‌های اینترنتی ساخته می‌شود.
-- بازی آفلاین/محلی هیچ ردی در این جدول‌ها نمی‌گذارد.
-- ============================================================

-- ثبت نتیجه‌ی یک بازی اینترنتی برای کاربرِ لاگین‌شده (اتمیک)
create or replace function public.record_online_result(p_game_id text, p_won boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not signed in';
    end if;
    if p_game_id is null or length(p_game_id) = 0 or length(p_game_id) > 40 then
        raise exception 'bad game id';
    end if;

    insert into public.game_stats (user_id, game_id, plays, wins, updated_at)
    values (auth.uid(), p_game_id, 1, case when p_won then 1 else 0 end, now())
    on conflict (user_id, game_id) do update
        set plays      = public.game_stats.plays + 1,
            wins       = public.game_stats.wins + (case when p_won then 1 else 0 end),
            updated_at = now();
end;
$$;

grant execute on function public.record_online_result(text, boolean) to authenticated;

-- لیدربورد هر بازی: بیشترین برد، بعد کمترین بازی (نرخ برد بهتر)
create or replace view public.game_leaderboard as
select
    s.game_id,
    s.user_id,
    p.username,
    p.display_name,
    s.wins,
    s.plays,
    rank() over (partition by s.game_id order by s.wins desc, s.plays asc, p.username asc) as rank
from public.game_stats s
join public.profiles p on p.id = s.user_id
where s.plays > 0;

grant select on public.game_leaderboard to anon, authenticated;
