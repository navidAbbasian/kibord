package com.navidabbasian.kibord.core.net

/**
 * واسط مشترک میزبان و مهمان برای بازی‌های چندگوشی.
 *
 * دو پیاده‌سازی دارد: سوکت روی شبکه‌ی محلی (وای‌فای/هات‌اسپات) و اتاق
 * اینترنتی روی کانال بلادرنگ. وی‌مدلِ بازی فقط با همین واسط حرف می‌زند و
 * نمی‌داند پیام از کدام راه می‌رود — همان بازی، دو جاده.
 */
interface HostLink<T> {
    fun broadcast(msg: T)
    fun stop()
}

/**
 * میزبانی که می‌تواند به یک مهمانِ مشخص هم پیام بدهد — برای بازی‌های
 * کارتی که هر بازیکن فقط باید دستِ خودش را ببیند، عکسِ وضعیت برای هر
 * مهمان جداگانه سانسور و فرستاده می‌شود.
 */
interface TargetedHostLink<T> : HostLink<T> {
    fun sendTo(playerName: String, msg: T)
}

interface ClientLink<T> {
    fun send(msg: T)
    fun close()
}
