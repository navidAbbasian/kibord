# مستند طراحی و قوانین هایپرگامون (Hypergammon)

> Hypergammon یک Variant (نسخه متفاوت) از Backgammon است که تقریباً تمام قوانین اصلی را نگه می‌دارد اما هر بازیکن فقط 3 مهره دارد.
>
> منابع: [GamesGrid – Hypergammon](https://gamesgrid.com/hypergammon/) و [GammonSite – Backgammon Variations](https://www.gammonsite.com/bgrules.aspx/default.aspx).

---

## 1. خلاصه

- بازیکنان: 2
- مهره هر بازیکن: 3
- صفحه: 24 Point
- تاس: 2
- Hit: بله
- Bar: بله
- Re-entry: بله
- Bearing Off: بله
- Doubling Cube: بسته به Rule Set؛ برای نسخه اول این سند غیرفعال پیشنهاد می‌شود.
- مدت بازی: بسیار کوتاه‌تر از Backgammon استاندارد

---

## 2. چیدمان اولیه

هر بازیکن سه مهره دارد.

برای بازیکن White:

```text
Point 24 = 1
Point 23 = 1
Point 22 = 1
```

برای Black چیدمان قرینه است:

```text
Point 1 = 1
Point 2 = 1
Point 3 = 1
```

پس:

```text
White = 3
Black = 3
```

و هر مهره جدا از دو مهره دیگر شروع می‌کند.

---

## 3. تفاوت اصلی با Backgammon استاندارد

در Hypergammon فقط این موارد تغییر کرده‌اند:

```text
15 checkers → 3 checkers
starting layout → 22,23,24
```

هسته قوانین همچنان Backgammon است.

یعنی:

- Hit وجود دارد.
- Bar وجود دارد.
- Re-entry وجود دارد.
- Block وجود دارد.
- Doubles چهار حرکت می‌دهد.
- Bearing Off وجود دارد.

این موضوع از نظر برنامه‌نویسی فوق‌العاده مهم است:

> Hypergammon باید بیشتر یک `BackgammonRulesVariant` باشد تا یک بازی کاملاً مستقل.

---

## 4. شروع بازی

شروع بازی مانند Backgammon استاندارد:

1. هر بازیکن یک تاس می‌اندازد.
2. عدد بالاتر شروع می‌کند.
3. در صورت مساوی، پرتاب تکرار می‌شود.
4. پرتاب برنده، دو عدد همان نوبت اول را تعیین می‌کند.

---

## 5. حرکت

مثلاً:

```text
Dice = 5 - 2
```

بازیکن می‌تواند:

```text
checker A → 5
checker B → 2
```

یا در صورت قانونی بودن:

```text
checker A → 5 → 2
```

باشد.

---

## 6. Hit

اگر روی یک Point فقط یک مهره حریف باشد:

```text
enemy count = 1
```

و مهره بازیکن روی آن فرود بیاید:

```text
enemy → Bar
```

می‌رود.

چون فقط سه مهره وجود دارد، Hit در Hypergammon بسیار تأثیرگذار است.

---

## 7. Bar

اگر:

```text
bar > 0
```

بازیکن باید ابتدا مهره‌های Bar را وارد کند.

هیچ مهره دیگری اجازه حرکت ندارد تا ورود قانونی مهره Bar بررسی شود.

### نمونه

```text
Bar = 1
Dice = 3 - 5
```

بازیکن باید از:

```text
Opponent Home Board
```

با 3 یا 5 وارد شود.

اگر یکی از نقاط بسته باشد و دیگری باز:

```text
open point = mandatory candidate
```

اگر هیچ‌کدام ممکن نباشند:

```text
turn ends
```

---

## 8. Doubles

مثلاً:

```text
4 - 4
```

به:

```text
4, 4, 4, 4
```

تبدیل می‌شود.

---

## 9. Bearing Off

وقتی هر سه مهره در Home Board قرار گرفتند:

```text
allActiveCheckersInsideHome = true
```

بازیکن می‌تواند Bearing Off را آغاز کند.

### مثال

```text
Checker A = Point 6
Checker B = Point 4
Checker C = Point 2

Dice = 6 - 2
```

می‌توان:

```text
6 → Off
2 → Off
```

کرد.

---

## 10. حمله دوباره در Bear Off

اگر یکی از سه مهره قبل از پایان بازی زده شود:

```text
checker → Bar
```

باید طبق قوانین عادی برگردد.

تا وقتی که به Home Board برنگشته:

```text
bearingOff = disabled
```

است.

---

## 11. شرط برد

وقتی:

```text
borneOff == 3
```

بازیکن برنده است.

---

## 12. Gammon و Backgammon

اگر محصول تو از امتیازدهی استاندارد Backgammon استفاده می‌کند:

### Single

```text
winner.off = 3
loser.off >= 1
```

برد عادی.

### Gammon

```text
winner.off = 3
loser.off = 0
```

حریف هیچ مهره‌ای خارج نکرده است.

### Backgammon

اگر:

```text
loser.off = 0
```

و یکی از شرایط زیر برقرار باشد:

```text
loser.bar > 0
OR
loser checker is in winner's home board
```

برد Backgammon محسوب می‌شود.

> چون Hypergammon سه مهره دارد، این حالت‌ها می‌توانند خیلی سریع رخ دهند. موتور امتیازدهی باید تعداد مهره‌ها را از `piecesPerPlayer` بخواند و عدد 15 را Hard-code نکند.

---

## 13. نمونه یک دور

وضعیت:

```text
White:
24×1
23×1
22×1

Black:
1×1
2×1
3×1
```

White:

```text
Dice = 5 - 3
```

یک حرکت ممکن:

```text
24 → 19
23 → 20
```

یا در صورت مناسب بودن وضعیت:

```text
24 → 21 → 16
```

بعد از حرکت، موتور باید:

```text
remainingDice
```

را به‌روز کند.

اگر یکی از مهره‌ها روی Blot حریف فرود بیاید:

```text
opponent → Bar
```

---

## 14. State Machine

```text
OPENING_ROLL
      ↓
ROLL
      ↓
CHECK_BAR
  ┌───┴────┐
 YES      NO
  ↓        ↓
REENTRY   NORMAL_MOVEMENT
  └───┬────┘
      ↓
MOVE_SEQUENCE
      ↓
CHECK_BEAR_OFF
      ↓
CHECK_WIN
      ↓
NEXT_TURN
```

---

## 15. Rule Object

```typescript
interface HypergammonRules {
  piecesPerPlayer: 3;

  startingPositions: {
    white: [24, 23, 22];
    black: [1, 2, 3];
  };

  canHit: true;
  hasBar: true;

  doublesMultiplier: 4;

  bearingOff: true;

  usesDoublingCube: false;
}
```

---

## 16. نکات مهم برای موتور مشترک

### هیچ‌جا این را ننویس:

```typescript
15 - borneOff
```

به جای آن:

```typescript
rules.piecesPerPlayer - borneOff
```

استفاده کن.

همین موضوع برای:

```text
remaining checkers
win condition
gammon
backgammon
pip count
```

هم باید رعایت شود.

---

## 17. تست‌های حیاتی

### Test 1 — Setup

```text
white = [22, 23, 24]
black = [1, 2, 3]
```

### Test 2 — Three Checkers

```text
white.total = 3
black.total = 3
```

### Test 3 — Hit

```text
enemy blot
→ enemy.bar += 1
```

### Test 4 — Bar Priority

```text
bar > 0
→ normal movement disabled
```

### Test 5 — Bear Off Unlock

```text
all active checkers in home
→ bearingOff = true
```

### Test 6 — Win

```text
borneOff = 3
→ winner
```

### Test 7 — Gammon

```text
winner.off = 3
loser.off = 0
```

باید نتیجه Gammon شود.

---

## 18. مزیت Hypergammon برای محصول موبایل

Hypergammon برای MVP (Minimum Viable Product، نسخه اولیه قابل عرضه) مزیت بزرگی دارد:

- قوانین آن تقریباً تماماً از Backgammon به ارث می‌رسد.
- بازی بسیار سریع‌تر تمام می‌شود.
- برای Quick Match مناسب است.
- تست آن ساده‌تر است.
- برای بازی مقابل Bot (ربات) فضای حالت کوچک‌تری دارد.
- می‌توان آن را بدون ساخت موتور جدید به محصول اضافه کرد.

---

# پایان سند
