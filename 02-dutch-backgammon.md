# مستند طراحی و قوانین تخته‌نرد هلندی (Dutch Backgammon)

> این سند بر اساس تعریف رایج Dutch Backgammon تنظیم شده است.
>
> منابع: [BKGammon – Dutch Backgammon](https://www.bkgm.com/variants/DutchBackgammon.html) و [Merriam-Webster – Dutch Backgammon](https://www.merriam-webster.com/dictionary/Dutch%20backgammon).

---

## 1. خلاصه بازی

Dutch Backgammon تقریباً همان موتور Backgammon استاندارد را دارد، اما سه تغییر اصلی دارد:

1. همه 15 مهره هر بازیکن در شروع بازی خارج از صفحه هستند.
2. بازیکنی که پرتاب شروع را می‌برد، دوباره دو تاس می‌اندازد و همان پرتاب، شروع واقعی بازی اوست.
3. تا زمانی که حداقل یک مهره خودت وارد Home Board نشده باشد، اجازه Hit کردن مهره حریف را نداری.

همچنین در مرحله ابتدایی باید تمام مهره‌ها را وارد صفحه کنی و قبل از ورود کامل، حرکت معمولی با مهره‌های قبلاً واردشده مجاز نیست.

---

## 2. تجهیزات

- 2 بازیکن
- 15 مهره برای هر بازیکن
- 24 Point
- 2 تاس
- Bar
- Bearing Off
- Doubling Cube: در Rule Set اصلی پیشنهادی این سند استفاده نمی‌شود.

---

## 3. وضعیت اولیه

برخلاف Backgammon استاندارد:

```text
white.onBoard = 0
black.onBoard = 0

white.offBoard = 15
black.offBoard = 15

bar.white = 0
bar.black = 0
```

تمام مهره‌ها در حالت:

```text
NOT_ENTERED
```

هستند.

---

## 4. شروع بازی

هر دو بازیکن یک تاس می‌اندازند.

بالاترین عدد:

```text
starter = winner_of_opening_roll
```

است.

اگر مساوی شد:

```text
repeat opening roll
```

بازیکن برنده سپس دوباره هر دو تاس را می‌اندازد.

این پرتاب، اولین نوبت واقعی بازی است.

---

## 5. ورود اولیه مهره‌ها

هر بازیکن مهره‌های خود را ابتدا وارد Home Board حریف می‌کند.

مثلاً اگر تاس:

```text
6 - 3
```

باشد:

```text
یک مهره روی 6-point حریف
یک مهره روی 3-point حریف
```

قرار می‌گیرد.

اگر یکی از اعداد قابل ورود نباشد، از عدد دیگر استفاده می‌شود.

---

## 6. قانون مهم ورود اولیه

تا زمانی که تمام مهره‌های بازیکن وارد صفحه نشده‌اند:

```text
allCheckersEntered == false
```

بازیکن باید به ورود مهره‌ها ادامه دهد.

حرکت مهره‌ای که قبلاً وارد شده، فقط وقتی مجاز است که Rule Engine تعیین کند ورود دیگری با تاس‌های باقی‌مانده ممکن نیست.

در پیاده‌سازی بهتر است این حالت را به صورت یک Phase جدا نگه داری:

```text
ENTERING_PHASE
```

و بعد:

```text
NORMAL_PHASE
```

---

## 7. چرا وضعیت Dutch با Bar یکی نیست؟

در شروع بازی مهره‌ها:

```text
offBoard
```

هستند، نه:

```text
bar
```

از نظر منطقی بهتر است این دو را در مدل داده جدا نگه داری:

```typescript
offBoard
bar
```

چون رفتار شروع بازی با مهره زده‌شده یکسان نیست، هرچند روش ورود آن‌ها شبیه است.

---

## 8. حرکت

پس از اینکه تمام مهره‌ها وارد شدند، قوانین حرکت مشابه Backgammon استاندارد است.

مثلاً:

```text
5 - 3
```

می‌تواند:

```text
checker A → 5
checker B → 3
```

یا:

```text
checker A → 5 → 3
```

باشد، مشروط به قانونی بودن خانه میانی.

---

## 9. Pointهای بسته

خانه‌ای که حداقل دو مهره حریف دارد:

```text
blocked
```

است.

فرود روی آن غیرمجاز است.

---

## 10. Hit در Dutch Backgammon

یک تفاوت مهم وجود دارد.

در شروع بازی:

```text
canHit = false
```

تا زمانی که بازیکن حداقل یک مهره را به Home Board خودش نرسانده باشد.

پس:

```text
hasCheckerReachedOwnHome = false
```

یعنی:

```text
enemy blot = untouchable
```

در نتیجه اگر روی آن فرود بیایی:

```text
illegal move
```

به محض اینکه حداقل یک مهره وارد Home Board خودت شد:

```text
hasCheckerReachedOwnHome = true
```

و از آن زمان Hit معمولی فعال می‌شود.

---

## 11. Bar

بعد از فعال شدن Hit:

```text
bar > 0
```

قوانین Bar همان قوانین استاندارد Backgammon را دارند.

بازیکن با مهره روی Bar:

```text
must re-enter first
```

است.

---

## 12. Doubles

اگر:

```text
6 - 6
```

بیاید:

```text
6 × 4
```

استفاده می‌شود.

یعنی چهار حرکت 6تایی.

---

## 13. اجبار به مصرف تاس

قانون پیشنهادی:

- اگر هر دو عدد قابل استفاده باشند، هر دو باید استفاده شوند.
- اگر فقط یکی قابل بازی باشد، همان باید بازی شود.
- در حالت Double، بیشترین تعداد چهار حرکت ممکن باید استفاده شود.

این Rule باید در `MoveGenerator` عمومی Backgammon قابل استفاده باشد.

---

## 14. Bearing Off

پس از اینکه هر 15 مهره وارد Home Board خود بازیکن شدند، Bearing Off فعال می‌شود.

قوانین همان Backgammon استاندارد:

- عدد دقیق = خروج از Point متناظر
- عدد بالاتر از بالاترین Point موجود = خروج از بالاترین Point، در صورت نبود Point بالاتر
- حرکت داخلی در صورت الزام قانونی باید اولویت خود را حفظ کند.

---

## 15. شرط برد

اولین بازیکنی که:

```text
borneOff == 15
```

شود، برنده است.

امتیاز معمول:

```text
Single = 1
Gammon = 2
```

در Rule Set منبع اصلی BKGammon، Doubling Cube استفاده نمی‌شود.

برای نسخه دیجیتال بهتر است:

```typescript
scoringMode = "simple"
```

باشد.

---

## 16. نمونه یک دور

وضعیت:

```text
White:
15 checkers offBoard

Black:
15 checkers offBoard
```

White در شروع:

```text
opening die = 5
Black = 2
```

White شروع می‌کند و تاس واقعی:

```text
6 - 3
```

می‌اندازد.

نتیجه:

```text
White checker → opponent 6-point
White checker → opponent 3-point
```

در نوبت بعد:

```text
5 - 2
```

اگر هنوز مهره‌ای برای ورود باقی مانده باشد، باز هم ورود در اولویت است.

وقتی:

```text
white.offBoard == 0
```

آنگاه Phase بازی تبدیل می‌شود به:

```text
NORMAL_PHASE
```

---

## 17. State Machine

```text
OPENING_ROLL
    ↓
FIRST_REAL_ROLL
    ↓
ENTERING_PHASE
    ↓
ALL_CHECKERS_ENTERED?
   ├── NO → ENTER MORE
   └── YES
          ↓
      NORMAL_PHASE
          ↓
        ROLL
          ↓
        MOVE
          ↓
        CHECK_WIN
          ↓
       NEXT_TURN
```

---

## 18. Rule Flags

```typescript
interface DutchBackgammonRules {
  piecesPerPlayer: 15;

  startingOnBoard: 0;
  startingOffBoard: 15;

  mustEnterAllBeforeNormalMovement: true;

  openingWinnerRerolls: true;

  canHitBeforeHomeEntry: false;

  canHitAfterOwnHomeEntry: true;

  doublesMultiplier: 4;

  usesBar: true;

  usesBearingOff: true;

  usesDoublingCube: false;
}
```

---

## 19. تست‌های حیاتی

### Test 1 — Start

```text
onBoard = 0
offBoard = 15
bar = 0
```

### Test 2 — Opening Winner Rerolls

برنده پرتاب اول باید تاس دوم مخصوص اولین نوبت را دریافت کند.

### Test 3 — Forced Entry

اگر مهره خارج از صفحه وجود دارد و ورود قانونی ممکن است:

```text
normal_move = illegal
```

### Test 4 — Early Hit Forbidden

```text
ownHomeReached = false
enemyBlot = true

hit = illegal
```

### Test 5 — Hit Enabled

```text
ownHomeReached = true
enemyBlot = true

hit = legal
```

### Test 6 — All Entered

```text
offBoard = 0
```

بازی از `ENTERING_PHASE` وارد `NORMAL_PHASE` می‌شود.

---

# پایان سند
