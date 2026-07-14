package com.navidabbasian.kibord

import android.app.Application
import com.navidabbasian.kibord.core.crash.CrashReporter

/** نقطه‌ی شروع اپ: نصب نگهبان کرش برای گزارش‌گیری در اجرای بعدی */
class KiBordApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
