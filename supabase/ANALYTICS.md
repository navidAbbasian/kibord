# گزارش‌گیری «کی برد؟» — دفترچه‌ی کوئری‌ها

همه‌ی این‌ها را می‌شود مستقیم در **SQL Editor** داشبورد Supabase اجرا کرد.
پیش‌نیاز: یک بار `analytics.sql` و `online_stats.sql` اجرا شده باشند.

## چه چیزی جمع می‌شود؟

| جدول | محتوا | چه‌کسی می‌نویسد |
|---|---|---|
| `sessions` | هر بار بازشدنِ اپ (با ضربان هر ۶۰ ثانیه، پس «آخرین لحظه‌ی زنده‌بودن» را داریم) | اپ، با RPC `analytics_heartbeat` |
| `events` | رویدادهای ناشناس با `props` جی‌سان | اپ، درج مستقیم (RLS فقط اجازه‌ی insert می‌دهد) |
| `game_stats` | آمار هر بازیکن به تفکیک بازی — **فقط بازی‌های اینترنتی** | اپ، با RPC `record_online_result` |

هویت: `device_id` یک UUID تصادفی روی گوشی است (نه شناسه‌ی سخت‌افزاری)؛
`user_id` فقط وقتی پر می‌شود که کاربر وارد حساب باشد. کاربر می‌تواند از
تنظیمات («ارسال آمار ناشناس استفاده») خاموشش کند.

### رویدادها

| نام | props | معنی |
|---|---|---|
| `app_open` | — | یک نشست تازه شروع شد |
| `game_start` | `game_id`, `mode` | وارد صفحه‌ی یک بازی شد |
| `game_mode` | `game_id`, `mode` (`lan`/`online`) | بازی چندگوشی شد |
| `game_finish` | `game_id`, `mode`, `elapsed_s` | به صفحه‌ی برنده رسید |
| `game_abandon` | `game_id`, `mode`, `elapsed_s`, `elapsed_bucket` | بدون پایان از بازی بیرون رفت |
| `online_enter` | `role` (`host`/`guest`), `game_id` | وارد اتاق اینترنتی شد |
| `online_leave` | `game_id` | اتاق اینترنتی را ترک کرد |
| `online_join_failed` | `game_id`, `reason` | پیوستن با کد شکست خورد |
| `lan_host` | `game_id` | روی وای‌فای محلی میزبان شد |
| `rate_prompt` | `result` (`shown`/`rated`/`later`/`never`/`dismissed`) | پاپ‌آپ امتیاز مایکت |
| `account_register` | `with_email` | حساب ساخت |
| `account_sign_in` | — | وارد حساب شد |
| `onboarding_done` | `skipped` | خوش‌آمدگویی تمام/رد شد |
| `guide_open` | `game_id` | راهنمای یک بازی را باز کرد |

## ویوهای آماده

| ویو | سؤالی که جواب می‌دهد |
|---|---|
| `v_daily_active` | روزانه چند دستگاه/کاربر/نشست فعال داشتیم؟ |
| `v_game_funnel` | هر بازی چند بار شروع، تمام و رها شد؟ (+ درصد رهاشدن و تعداد دستگاه) |
| `v_game_mode_split` | پایان/رهاشدن هر بازی به تفکیک راهِ بازی: `local` / `lan` / `online` |
| `v_abandon_by_phase` | رهاشدن‌ها در چه بازه‌ای از بازی اتفاق می‌افتد؟ |
| `v_game_duration` | میانه/میانگین طول یک دستِ کامل هر بازی |
| `v_peak_concurrency_daily` | پیکِ کاربرِ هم‌زمانِ داخل اپ در هر روز (و ساعتش) |
| `v_peak_online_daily` | پیکِ بازیکنِ هم‌زمان داخل اتاق‌های اینترنتی در هر روز |
| `v_online_rooms` | اتاق‌های اینترنتی: چند بار میزبانی/پیوستن/شکست |
| `v_onboarding_funnel` | چند نفر خوش‌آمدگویی را دیدند/رد کردند |
| `v_rate_prompt` | پاپ‌آپ امتیاز چقدر جواب داده |
| `game_leaderboard` | رتبه‌بندی هر بازی (بیشترین برد) — همان که اپ نشان می‌دهد |

## کوئری‌های پرکاربرد

### محبوب‌ترین بازی‌ها در ۳۰ روز گذشته
```sql
select game_id, starts, finishes, abandons, abandon_pct, devices
from v_game_funnel
order by starts desc;
```

### کدام بازی‌ها بیشتر نصفه رها می‌شوند؟
```sql
select game_id, abandon_pct, starts
from v_game_funnel
where starts >= 20
order by abandon_pct desc;
```

### بازی اینترنتی چقدر بازی می‌شود؟
```sql
select * from v_game_mode_split where mode in ('lan', 'online');
```

### رهاشدن‌ها کِی اتفاق می‌افتد؟ (همان اول، یا وسط بازی)
```sql
select * from v_abandon_by_phase order by game_id, elapsed_bucket;
```

### پیک کاربر هم‌زمان — کی زدیم؟
```sql
-- داخل اپ
select day, peak_concurrent, peak_at from v_peak_concurrency_daily order by day desc limit 30;
-- داخل اتاق‌های اینترنتی
select day, peak_online, peak_at from v_peak_online_daily order by day desc limit 30;
```

### فعال‌های روزانه (DAU)
```sql
select * from v_daily_active order by day desc limit 30;
```

### چند درصد کاربرها حساب می‌سازند؟
```sql
select
  count(distinct device_id) filter (where name = 'app_open')           as devices,
  count(distinct device_id) filter (where name = 'account_register')   as registered,
  round(100.0 * count(distinct device_id) filter (where name = 'account_register')
        / nullif(count(distinct device_id) filter (where name = 'app_open'), 0), 1) as pct
from events
where client_ts > now() - interval '30 days';
```

### ماندگاری: چند دستگاه بعد از هفته‌ی اول برگشتند؟
```sql
with first_seen as (
  select device_id, min(started_at) as first_at from sessions group by device_id
)
select
  date_trunc('week', f.first_at)::date as cohort_week,
  count(*) as devices,
  count(*) filter (where exists (
    select 1 from sessions s
    where s.device_id = f.device_id
      and s.started_at >= f.first_at + interval '7 days'
      and s.started_at <  f.first_at + interval '14 days'
  )) as back_in_week_2
from first_seen f
group by 1 order by 1 desc;
```

### نسخه‌های در حال استفاده
```sql
select app_version, count(distinct device_id) as devices
from sessions
where started_at > now() - interval '14 days'
group by 1 order by 2 desc;
```

### پرافتخارترین بازیکن‌های یک بازی
```sql
select rank, username, wins, plays
from game_leaderboard
where game_id = 'backgammon'
order by rank
limit 20;
```

## نگهداری

- `events` فقط درج می‌شود؛ برای پاک‌سازی قدیمی‌ها:
  `delete from events where client_ts < now() - interval '1 year';`
- اگر روزی خواستید داده‌ی یک دستگاه را پاک کنید:
  `delete from events where device_id = '...'; delete from sessions where device_id = '...';`
