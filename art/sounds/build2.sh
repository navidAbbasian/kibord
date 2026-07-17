#!/bin/bash
# نسخه‌ی ۳: صداهای مسئله‌دار، این‌بار از فایل‌های خام ارباب (mixkit)
set -e
cd "$(dirname "$0")"
RAW="/Users/navan/Documents/Zzz/Asset/raw"
OUT=out
mkdir -p "$OUT" tmp

FF="ffmpeg -y -v error"

norm() { # $1=in wav  $2=out name
  local peak gain
  peak=$(ffmpeg -i "$1" -af volumedetect -f null - 2>&1 | grep max_volume | grep -oE '\-?[0-9.]+ dB' | grep -oE '\-?[0-9.]+')
  gain=$(python3 -c "print(round(-1.5 - ($peak), 2))")
  $FF -i "$1" -af "volume=${gain}dB" -ac 1 -ar 44100 tmp/_norm.wav
  oggenc -Q -q 5 -o "$OUT/$2.ogg" tmp/_norm.wav
}

TT="$RAW/mixkit-tick-tock-clock-close-up-1059.wav"

# ---------- ۱) تیک عادی: جفت تیک-تاک واقعی ساعت، برش ۱٫۰۰۰ ثانیه‌ای با فید لبه ----------
$FF -i "$TT" -af "atrim=4.091:5.091,asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,aresample=44100,afade=t=in:st=0:d=0.008,afade=t=out:st=0.992:d=0.008,apad,atrim=end_sample=44100" tmp/tick_normal.wav
norm tmp/tick_normal.wav timer_tick_normal

# ---------- ۲) تیک تند: همان ضربه‌های واقعی، زیرتر و ۴ بار در ثانیه ----------
$FF -i "$TT" -af "atrim=4.091:4.35,asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,afade=t=out:st=0.2:d=0.05,asetrate=44100*1.15,aresample=44100" tmp/hit_tick.wav
$FF -i "$TT" -af "atrim=4.580:4.84,asetpts=PTS-STARTPTS,aformat=channel_layouts=mono,afade=t=out:st=0.2:d=0.05,asetrate=44100*1.15,aresample=44100" tmp/hit_tock.wav
$FF -i tmp/hit_tick.wav -i tmp/hit_tock.wav -filter_complex "
[0:a]asplit=2[a][c];
[1:a]asplit=2[b][d];
[b]adelay=250,volume=0.9[b1];
[c]adelay=500[c1];
[d]adelay=750,volume=0.9[d1];
[a][b1][c1][d1]amix=inputs=4:normalize=0,apad,atrim=end_sample=44100" tmp/tick_fast.wav
norm tmp/tick_fast.wav timer_tick_fast

# ---------- ۳) کلمه‌ی غلط / جریمه ----------
$FF -i "$RAW/mixkit-click-error-1110.wav" -af "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-55dB,areverse" tmp/error.wav
norm tmp/error.wav click_error

# ---------- ۴) بوق شمارش معکوس ----------
$FF -i "$RAW/mixkit-cool-interface-click-tone-2568.wav" -af "silenceremove=start_periods=1:start_threshold=-55dB" tmp/beep.wav
norm tmp/beep.wav countdown_beep

# ---------- ۵) دکمه‌ی «کی برد؟» ----------
$FF -i "$RAW/mixkit-quick-win-video-game-notification-269.wav" -af "silenceremove=start_periods=1:start_threshold=-55dB,areverse,silenceremove=start_periods=1:start_threshold=-55dB,areverse" tmp/kibord.wav
norm tmp/kibord.wav kibord_drum

# ---------- ۶) انفجار بمب: کرش دیجیتال ارباب + بوم بم سنتزی زیرش ----------
$FF -i "$RAW/mixkit-tech-break-fail-2947.wav" -filter_complex "
[0:a]aformat=channel_layouts=mono,aresample=44100,silenceremove=start_periods=1:start_threshold=-55dB[crack];
aevalsrc='0.9*sin(2*PI*(110-90*min(t/0.5,1))*t)*exp(-t*5)*(1-exp(-t*400))':sample_rate=44100:duration=1.0[boom];
[crack][boom]amix=inputs=2:normalize=0,atrim=0:1.0,afade=t=out:st=0.85:d=0.15" tmp/explosion.wav
norm tmp/explosion.wav dor_explosion

# ---------- ۷) شکست دراماتیک: درام و زیلوفون خود ارباب ----------
$FF -i "$RAW/mixkit-fail-drum-and-xylophone-568.wav" -af "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-55dB,areverse" tmp/fail.wav
norm tmp/fail.wav fail_sting

rm -rf tmp
echo "=== DONE ==="
for f in timer_tick_normal timer_tick_fast click_error countdown_beep kibord_drum dor_explosion fail_sting; do
  ffprobe -v quiet -show_entries format=duration -of csv=p=0 "$OUT/$f.ogg" | xargs printf "%-20s %ss\n" "$f"
done
