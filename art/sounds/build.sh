#!/bin/bash
# ساخت ۱۳ صدای مشترک «کی برد؟» از بسته‌های CC0 کنی + سنتز
set -e
cd "$(dirname "$0")"
P=packs
OUT=out
mkdir -p "$OUT" tmp

FF="ffmpeg -y -v error"

# ---------- کمکی: نرمال‌سازی پیک به -1.5dB و خروجی ogg مونو 44.1k ----------
norm() { # $1=in wav  $2=out name
  local peak gain
  peak=$(ffmpeg -i "$1" -af volumedetect -f null - 2>&1 | grep max_volume | grep -oE '\-?[0-9.]+ dB' | grep -oE '\-?[0-9.]+')
  gain=$(python3 -c "print(round(-1.5 - ($peak), 2))")
  $FF -i "$1" -af "volume=${gain}dB" -ac 1 -ar 44100 tmp/_norm.wav
  oggenc -Q -q 5 -o "$OUT/$2.ogg" tmp/_norm.wav
}

# ---------- ۱) تیک تایمر عادی: تیک در ۰٫۰ + تاک بم‌تر در ۰٫۵ — دقیقاً ۱ ثانیه ----------
$FF -i "$P/interface-sounds/Audio/tick_001.ogg" -i "$P/interface-sounds/Audio/tick_002.ogg" -filter_complex "
[0:a]aformat=channel_layouts=mono,aresample=44100,volume=1.0[t1];
[1:a]aformat=channel_layouts=mono,asetrate=44100*0.82,aresample=44100,volume=0.8,adelay=500[t2];
[t1][t2]amix=inputs=2:normalize=0,apad,atrim=end_sample=44100" tmp/tick_normal.wav
norm tmp/tick_normal.wav timer_tick_normal

# ---------- ۲) تیک تایمر تند: ۴ تیک زیرتر در ثانیه — دقیقاً ۱ ثانیه ----------
$FF -i "$P/interface-sounds/Audio/tick_001.ogg" -filter_complex "
[0:a]aformat=channel_layouts=mono,asetrate=44100*1.22,aresample=44100,asplit=4[a][b][c][d];
[a]volume=1.0[a1];
[b]adelay=250,volume=0.85[b1];
[c]adelay=500,volume=1.0[c1];
[d]adelay=750,volume=0.85[d1];
[a1][b1][c1][d1]amix=inputs=4:normalize=0,apad,atrim=end_sample=44100" tmp/tick_fast.wav
norm tmp/tick_fast.wav timer_tick_fast

# ---------- ۳) اتمام تایمر: سوت پایین‌رونده ----------
$FF -i "$P/digital-audio/Audio/lowDown.ogg" -af "silenceremove=start_periods=1:start_threshold=-45dB,afade=t=out:st=0.55:d=0.2,atrim=0:0.8" tmp/timer_end.wav
norm tmp/timer_end.wav timer_end

# ---------- ۴) کلیک عادی ----------
$FF -i "$P/interface-sounds/Audio/click_001.ogg" -af "silenceremove=start_periods=1:start_threshold=-50dB,atrim=0:0.15" tmp/click.wav
norm tmp/click.wav click_normal

# ---------- ۵) کلیک خطا/جریمه: وزوز دوتایی بم ----------
$FF -i "$P/interface-sounds/Audio/error_006.ogg" -af "silenceremove=start_periods=1:start_threshold=-50dB,atrim=0:0.45,afade=t=out:st=0.35:d=0.1" tmp/error.wav
norm tmp/error.wav click_error

# ---------- ۶) کلیک درست: دینگ بالارونده ----------
$FF -i "$P/interface-sounds/Audio/confirmation_001.ogg" -af "silenceremove=start_periods=1:start_threshold=-50dB" tmp/success.wav
norm tmp/success.wav click_success

# ---------- ۷) بوق شمارش معکوس ۳-۲-۱ ----------
$FF -i "$P/digital-audio/Audio/tone1.ogg" -af "silenceremove=start_periods=1:start_threshold=-50dB,atrim=0:0.35,afade=t=out:st=0.25:d=0.1" tmp/beep.wav
norm tmp/beep.wav countdown_beep

# ---------- ۸) جابه‌جایی نوبت: ووش کارت ----------
$FF -i "$P/casino-audio/Audio/card-slide-5.ogg" -af "silenceremove=start_periods=1:start_threshold=-45dB,asetrate=44100*1.12,aresample=44100,atrim=0:0.4,afade=t=out:st=0.3:d=0.1" tmp/turn.wav
norm tmp/turn.wav turn_change

# ---------- ۹) پیوستن بازیکن به لابی: پاپ دوستانه ----------
$FF -i "$P/digital-audio/Audio/pepSound3.ogg" -af "silenceremove=start_periods=1:start_threshold=-50dB,atrim=0:0.5" tmp/join.wav
norm tmp/join.wav player_join

# ---------- ۱۰) بازی دوباره: پاورآپ بالارونده ----------
$FF -i "$P/digital-audio/Audio/powerUp5.ogg" -af "silenceremove=start_periods=1:start_threshold=-50dB" tmp/again.wav
norm tmp/again.wav play_again

# ---------- ۱۱) دکمه‌ی «کی برد؟»: چرخش تاس + ضربه‌ی ارکستر ----------
$FF -i "$P/casino-audio/Audio/dice-shake-1.ogg" -i "$P/music-jingles/Audio/Hit jingles/jingles_HIT03.ogg" -filter_complex "
[0:a]aformat=channel_layouts=mono,asetrate=44100*1.15,aresample=44100,afade=t=in:st=0:d=0.25,volume=1.6[shake];
[1:a]aformat=channel_layouts=mono,adelay=1150,volume=1.0[hit];
[shake][hit]amix=inputs=2:normalize=0,atrim=0:2.1,afade=t=out:st=1.9:d=0.2" tmp/kibord.wav
norm tmp/kibord.wav kibord_drum

# ---------- ۱۲) فانفار برد (سنتز): آرپژ ماژور + شیمر ----------
$FF -filter_complex "
aevalsrc='
  0.5*(sin(2*PI*523.25*t)+0.4*sin(2*PI*1046.5*t)+0.2*sin(2*PI*1569.75*t))*exp(-(t-0.00)*9)*(1-exp(-(t-0.00)*300))*between(t,0.00,0.60)
+ 0.5*(sin(2*PI*659.25*t)+0.4*sin(2*PI*1318.5*t)+0.2*sin(2*PI*1977.75*t))*exp(-(t-0.15)*9)*(1-exp(-(t-0.15)*300))*between(t,0.15,0.75)
+ 0.5*(sin(2*PI*783.99*t)+0.4*sin(2*PI*1567.98*t)+0.2*sin(2*PI*2351.97*t))*exp(-(t-0.30)*9)*(1-exp(-(t-0.30)*300))*between(t,0.30,0.90)
+ 0.62*(sin(2*PI*1046.5*t+1.1*sin(2*PI*5.5*(t-0.45)))+0.45*sin(2*PI*2093*t+2.2*sin(2*PI*5.5*(t-0.45)))+0.18*sin(2*PI*3139.5*t))*exp(-(t-0.45)*2.2)*(1-exp(-(t-0.45)*300))*between(t,0.45,2.30)
+ 0.30*(sin(2*PI*523.25*t)+0.3*sin(2*PI*659.25*t))*exp(-(t-0.45)*2.0)*(1-exp(-(t-0.45)*200))*between(t,0.45,2.30)
':sample_rate=44100:duration=2.4[notes];
anoisesrc=colour=white:sample_rate=44100:duration=2.4:amplitude=0.28,highpass=f=7000,afade=t=in:st=0.40:d=0.1,afade=t=out:st=0.6:d=1.6[shimmer];
[notes][shimmer]amix=inputs=2:normalize=0,afade=t=out:st=2.1:d=0.3" tmp/fanfare.wav
norm tmp/fanfare.wav game_over

# ---------- ۱۳) استینگ باخت (سنتز): ترومبون غمگین کروماتیک ----------
$FF -filter_complex "
aevalsrc='
  0.5*(sin(2*PI*233.08*t)+0.55*sin(2*PI*466.16*t)+0.35*sin(2*PI*699.24*t)+0.2*sin(2*PI*932.32*t))*exp(-(t-0.00)*4)*(1-exp(-(t-0.00)*150))*between(t,0.00,0.42)
+ 0.5*(sin(2*PI*220.00*t)+0.55*sin(2*PI*440.00*t)+0.35*sin(2*PI*660.00*t)+0.2*sin(2*PI*880.00*t))*exp(-(t-0.42)*4)*(1-exp(-(t-0.42)*150))*between(t,0.42,0.84)
+ 0.5*(sin(2*PI*207.65*t)+0.55*sin(2*PI*415.30*t)+0.35*sin(2*PI*622.95*t)+0.2*sin(2*PI*830.60*t))*exp(-(t-0.84)*4)*(1-exp(-(t-0.84)*150))*between(t,0.84,1.26)
+ 0.6*(sin(2*PI*196.00*t+0.8*sin(2*PI*6.5*(t-1.26)))+0.55*sin(2*PI*392.0*t+1.6*sin(2*PI*6.5*(t-1.26)))+0.3*sin(2*PI*588.0*t))*exp(-(t-1.26)*1.8)*(1-exp(-(t-1.26)*150))*between(t,1.26,2.60)
':sample_rate=44100:duration=2.6[sad];
[sad]lowpass=f=2200,afade=t=out:st=2.3:d=0.3" tmp/fail.wav
norm tmp/fail.wav fail_sting

rm -rf tmp
echo "=== DONE ==="; ls -la "$OUT"
