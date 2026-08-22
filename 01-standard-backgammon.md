# مستند طراحی و قوانین تخته‌نرد استاندارد (Standard Backgammon)

> نسخه پیشنهادی برای پیاده‌سازی دیجیتال بر مبنای قوانین بین‌المللی رایج.
>
> منابع اصلی برای تطبیق قوانین: [WBGF Tournament Rules](https://wbgf.info/tournament-rules/) و [GNU Backgammon Manual](https://www.gnu.org/software/gnubg/manual/).

---

## 1. خلاصه بازی

- بازیکنان: 2 نفر
- مهره: 15 مهره برای هر بازیکن
- صفحه: 24 خانه (Point)
- تاس: 2 تاس شش‌وجهی
- Bar (بار): محل مهره‌های زده‌شده
- هدف: انتقال تمام 15 مهره به Home Board (خانه داخلی) و سپس خارج کردن همه آن‌ها
- جهت حرکت دو بازیکن: مخالف یکدیگر
- حالت‌های امتیازی: Single، Gammon، Backgammon
- Doubling Cube (مکعب دوبرابرکننده): در بازی مسابقه‌ای استاندارد قابل پشتیبانی است، ولی برای نسخه اول محصول می‌توان آن را اختیاری کرد.

---

## 2. شماره‌گذاری صفحه

برای جلوگیری از وابستگی موتور به ظاهر رابط کاربری، صفحه را همیشه با 24 Point شماره‌گذاری کن.

برای هر بازیکن:

- Point 1 تا 6: Home Board
- Point 7 تا 12: Outer Board
- Point 13: Midpoint
- Point 24: دورترین خانه از خانه خودی

هر بازیکن در جهت کاهش شماره حرکت می‌کند؛ از 24 به 1.

در رابط کاربری می‌توانی صفحه را بچرخانی، اما در موتور بازی شماره‌گذاری باید ثابت بماند.

---

## 3. چیدمان اولیه

هر بازیکن:

```text
2 مهره روی Point 24
5 مهره روی Point 13
3 مهره روی Point 8
5 مهره روی Point 6
```

برای حریف، این آرایش قرینه است.

```text
Player A: 24×2, 13×5, 8×3, 6×5
Player B: 1×2, 12×5, 17×3, 19×5
```

### تست اولیه

پس از `createGame()` باید:

```text
board.checkerCount(A) = 15
board.checkerCount(B) = 15
bar(A) = 0
bar(B) = 0
borneOff(A) = 0
borneOff(B) = 0
```

---

## 4. شروع بازی

هر بازیکن یک تاس می‌اندازد.

- عدد بالاتر شروع می‌کند.
- اگر مساوی شود، دوباره هر دو بازیکن تاس می‌اندازند.
- عددهای همان پرتاب برنده، حرکت اولین نوبت او هستند.
- پس از آن نوبت‌ها به شکل متناوب ادامه پیدا می‌کنند.

مثال:

```text
Player A = 5
Player B = 3

A شروع می‌کند و اولین نوبت او با 5 و 3 است.
```

> برای پیاده‌سازی، `openingRoll` را از `normalRoll` جدا نگه دار.

---

## 5. حرکت مهره

هر تاس یک Move (حرکت) مستقل ایجاد می‌کند.

مثال:

```text
Dice = 3, 5
```

حرکت‌های ممکن:

```text
مهره A → 3 خانه
مهره B → 5 خانه
```

یا در صورت قانونی بودن:

```text
مهره A → 3 خانه → 5 خانه
```

### ترتیب دو تاس

باید هر دو ترتیب ممکن را بررسی کنی:

```text
3 → 5
5 → 3
```

چون یک ترتیب ممکن است قانونی باشد و دیگری نباشد.

---

## 6. خانه قابل ورود

یک Point در این شرایط قابل ورود است:

1. خالی باشد.
2. مهره خودی داشته باشد.
3. فقط یک مهره حریف داشته باشد؛ در این صورت آن مهره زده می‌شود.

ورود به خانه‌ای که حداقل دو مهره حریف دارد ممنوع است.

---

## 7. Hit یا زدن مهره

اگر حریف فقط یک مهره روی یک Point داشته باشد، آن مهره `Blot` است.

اگر مهره بازیکن روی Blot فرود بیاید:

```text
opponent.checker
→ Bar
```

مثال:

```text
Point 8:
Black ×1
```

White روی Point 8 فرود می‌آید:

```text
Point 8:
White ×1

Bar:
Black +1
```

---

## 8. قانون Bar

بازیکنی که یک یا چند مهره روی Bar دارد، اولویت مطلق ورود مجدد دارد.

تا زمانی که مهره‌های Bar وارد نشده‌اند:

```text
هیچ مهره دیگری مجاز به حرکت نیست.
```

### ورود

ورود از Home Board حریف انجام می‌شود.

مثلاً اگر:

```text
Dice = 4, 6
```

بازیکن می‌تواند از Bar وارد:

```text
Opponent Point 4
```

یا:

```text
Opponent Point 6
```

شود؛ مشروط به اینکه آن Point بسته نباشد.

### اگر هیچ ورود قانونی نباشد

کل نوبت از دست می‌رود.

### اگر فقط بخشی از مهره‌های Bar وارد شوند

هر تعداد که از نظر قوانین ممکن است وارد کن و باقی حرکت نوبت را مطابق قوانین تاس بررسی کن.

---

## 9. تاس جفت

اگر دو تاس برابر باشند:

```text
4, 4
```

به جای دو حرکت، چهار حرکت داری:

```text
4
4
4
4
```

بنابراین:

```text
Total dice uses = 4
```

موتور نباید آن را مثل دو حرکت مستقل 4تایی در نظر بگیرد؛ باید چهار مصرف جداگانه تاس بسازد.

---

## 10. اجبار به استفاده از تاس‌ها

قاعده اصلی:

- اگر هر دو تاس قابل استفاده باشند، هر دو باید استفاده شوند.
- اگر فقط یکی قابل استفاده باشد، باید همان را استفاده کرد.
- اگر هر دو قابل استفاده باشند، بازیکن نمی‌تواند عمداً یکی را کنار بگذارد.
- در شرایطی که هر دو عدد به صورت جداگانه قابل بازی نیستند ولی فقط یکی از آن‌ها امکان استفاده دارد، همان حرکت مجاز باید انتخاب شود.
- در حالت‌هایی که تنها یک عدد از دو عدد قابل استفاده است و انتخاب اهمیت دارد، قواعد بازی باید به‌صورت دقیق در `MoveGenerator` اعمال شود.

### نکته توسعه‌ای مهم

`MoveGenerator` باید تمام توالی‌های قانونی را تولید کند و فقط توالی‌هایی را نگه دارد که بیشترین تعداد تاس ممکن را مصرف می‌کنند.

---

## 11. ورود به Home Board و Bearing Off

وقتی همه مهره‌های فعال بازیکن در Pointهای 1 تا 6 هستند:

```text
borneOff = 0..14
allActiveCheckersInsideHome = true
```

Bearing Off (خارج کردن) آغاز می‌شود.

### Exact Roll

اگر تاس 5 باشد و مهره‌ای روی Point 5 داشته باشی:

```text
Point 5 → Off
```

### تاس بزرگ‌تر از دورترین مهره

اگر تاس 6 باشد ولی هیچ مهره‌ای روی Point 6 تا 5 وجود نداشته باشد و بالاترین Point موجود 4 باشد:

```text
6 → Bear Off از Point 4
```

مشروط به اینکه هیچ مهره‌ای روی Point بالاتر از 4 وجود نداشته باشد.

### اجبار به حرکت

اگر برای تاس فعلی امکان Bearing Off مستقیم وجود نداشته باشد اما حرکت قانونی دیگری در Home Board وجود داشته باشد، باید همان حرکت اجرا شود.

---

## 12. برخورد در زمان Bearing Off

اگر هنگام Bearing Off مهره بازیکن زده شود:

```text
Home Board state
→ Bar
```

و از آن لحظه:

```text
Bearing Off = disabled
```

تا زمانی که مهره دوباره وارد شده و به Home Board برگردد.

---

## 13. پایان بازی

وقتی:

```text
borneOff(player) == 15
```

بازی تمام می‌شود.

### Single Game

حریف حداقل یک مهره خارج کرده:

```text
score = 1
```

### Gammon

برنده همه 15 مهره را خارج کرده و حریف:

```text
borneOff(opponent) == 0
```

است:

```text
score = 2
```

### Backgammon

حریف:

```text
borneOff == 0
```

و حداقل یکی از این دو را دارد:

```text
bar > 0
OR
checker on winner's home board
```

```text
score = 3
```

در بازی با Doubling Cube، این امتیاز پایه در مقدار Cube ضرب می‌شود.

---

## 14. Doubling Cube

این بخش برای نسخه مسابقه‌ای مهم است.

Stateهای پایه:

```text
cubeValue = 1
cubeOwner = null
```

بازیکن در نوبت خود می‌تواند قبل از انداختن تاس:

```text
Offer Double
```

کند.

حریف:

```text
Take
OR
Pass
```

اگر:

```text
Pass
```

کند، بازی فوراً تمام می‌شود و پیشنهاددهنده برنده ارزش فعلی مکعب را می‌گیرد.

اگر:

```text
Take
```

کند:

```text
cubeValue *= 2
cubeOwner = opponent_of_previous_cube_owner
```

---

## 15. State Machine پیشنهادی

```text
WAITING_FOR_PLAYERS
        ↓
OPENING_ROLL
        ↓
ROLLING
        ↓
MOVE_SELECTION
        ↓
APPLY_MOVE
        ↓
HAS_REMAINING_DICE?
   ┌────┴────┐
  YES       NO
   ↓         ↓
MOVE_SELECTION
             ↓
CHECK_WIN
             ↓
NEXT_TURN
```

در حالت Bar:

```text
ROLL
 ↓
CHECK_BAR
 ↓
REENTRY_ONLY
```

---

## 16. ساختار وضعیت بازی

```typescript
interface GameState {
  gameId: string;
  turnPlayer: "white" | "black";

  board: PointState[];

  bar: {
    white: number;
    black: number;
  };

  borneOff: {
    white: number;
    black: number;
  };

  dice: number[];

  remainingDice: number[];

  phase:
    | "opening_roll"
    | "rolling"
    | "moving"
    | "finished";

  cube?: {
    value: number;
    owner: "white" | "black" | null;
  };

  winner?: "white" | "black";
}
```

---

## 17. تست‌های حیاتی

### Test 1 — شروع

```text
هر بازیکن = 15 مهره
Bar = 0
Off = 0
```

### Test 2 — Hit

```text
Enemy point count = 1
Landing = legal
Result = enemy → Bar
```

### Test 3 — Block

```text
Enemy point count >= 2
Landing = illegal
```

### Test 4 — Bar Priority

```text
Bar > 0
Move from normal point = illegal
```

### Test 5 — Doubles

```text
4-4
remainingDice = [4,4,4,4]
```

### Test 6 — Bear Off

```text
All active checkers in home
roll exact point
checker leaves board
```

### Test 7 — Hit During Bear Off

```text
Home phase
→ hit
→ bar > 0
→ bearing off disabled
```

### Test 8 — Gammon

```text
winner.off = 15
loser.off = 0
loser.bar = 0
loser.hasCheckerOnWinnerHome = false

result = Gammon
```

---

# پایان سند
