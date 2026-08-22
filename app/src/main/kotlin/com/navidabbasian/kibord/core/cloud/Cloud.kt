package com.navidabbasian.kibord.core.cloud

import com.navidabbasian.kibord.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * تنها نقطه‌ی اتصال اپ به Supabase.
 *
 * کلیدها از local.properties به BuildConfig تزریق می‌شوند. اگر خالی باشند —
 * مثلاً روی سیستمی که کلید ندارد — اپ کاملاً سالم بالا می‌آید و فقط بخش
 * آنلاین خاموش می‌ماند. هیچ‌جای بازی‌های آفلاین نباید به این وابسته باشد.
 */
object Cloud {

    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) return@lazy null
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) {
                // نشست روی دیسک می‌ماند و توکن خودکار تمدید می‌شود، پس
                // کاربر تا وقتی خودش خارج نشود لاگین می‌ماند
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
