package com.navidabbasian.kibord.core.cloud

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * حساب بازیکن روی Supabase.
 *
 * ثبت‌نام با یوزرنیم و پسورد است؛ ایمیل اختیاری است و فقط برای بازیابی
 * پسورد به کار می‌رود. چون سیستم احراز هویت Supabase ایمیل‌محور است، برای
 * کسی که ایمیل نمی‌دهد یک نشانیِ داخلی از روی یوزرنیم ساخته می‌شود که
 * هیچ‌وقت به آن نامه‌ای فرستاده نمی‌شود.
 */
object AccountRepository {

    /**
     * دامنه‌ی داخلی برای حساب‌های بی‌ایمیل — نامه‌ای به آن نمی‌رود.
     * پسوندهای غیرواقعی مثل .local را سرور رد می‌کند، پس یک زیردامنه‌ی
     * ثبت‌نشده از دامنه‌ی برند به کار می‌رود که از اعتبارسنجی رد می‌شود.
     */
    private const val INTERNAL_DOMAIN = "id.kibord.ir"

    private val client get() = Cloud.client

    /** یوزرنیم را به شکل استانداردِ جدول درمی‌آورد */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    /** آیا این یوزرنیم شکل درستی دارد؟ (پیام خطا، یا null اگر درست است) */
    fun validateUsername(raw: String): String? {
        val u = normalizeUsername(raw)
        return when {
            u.length < 3 -> "یوزرنیم باید حداقل ۳ نویسه باشد"
            u.length > 24 -> "یوزرنیم نباید بیشتر از ۲۴ نویسه باشد"
            !u.matches(Regex("^[a-z0-9_]+$")) ->
                "فقط حروف انگلیسی، عدد و زیرخط مجاز است"
            else -> null
        }
    }

    fun validatePassword(password: String): String? =
        if (password.length < 6) "پسورد باید حداقل ۶ نویسه باشد" else null

    /** وضعیت لاگین به‌صورت جریان — رابط کاربری به این گوش می‌دهد */
    val isSignedIn: Flow<Boolean>?
        get() = client?.auth?.sessionStatus?.map { it is SessionStatus.Authenticated }

    /** شناسه‌ی کاربر لاگین‌شده، یا null */
    fun currentUserId(): String? = client?.auth?.currentUserOrNull()?.id

    /** آیا این یوزرنیم آزاد است؟ */
    suspend fun isUsernameAvailable(raw: String): CloudResult<Boolean> {
        val c = client ?: return CloudResult.Failed(OFFLINE)
        return try {
            val free = c.postgrest.rpc(
                "username_available",
                buildJsonObject { put("candidate", normalizeUsername(raw)) },
            ).decodeAs<Boolean>()
            CloudResult.Ok(free)
        } catch (e: Exception) {
            CloudResult.Failed(humanize(e))
        }
    }

    /**
     * ثبت‌نام: حساب ساخته می‌شود و بلافاصله پروفایل با یوزرنیم ثبت می‌شود.
     * ایمیل خالی یعنی حسابِ بی‌بازیابی.
     */
    suspend fun register(
        username: String,
        password: String,
        email: String? = null,
    ): CloudResult<Unit> {
        val c = client ?: return CloudResult.Failed(OFFLINE)
        val user = normalizeUsername(username)

        validateUsername(user)?.let { return CloudResult.Failed(it) }
        validatePassword(password)?.let { return CloudResult.Failed(it) }

        when (val free = isUsernameAvailable(user)) {
            is CloudResult.Failed -> return free
            is CloudResult.Ok -> if (!free.value) {
                return CloudResult.Failed("این یوزرنیم قبلاً گرفته شده")
            }
        }

        return try {
            c.auth.signUpWith(Email) {
                this.email = email?.trim()?.takeIf { it.isNotBlank() } ?: "$user@$INTERNAL_DOMAIN"
                this.password = password
            }
            // اگر پروژه تایید ایمیل خواسته باشد، نشست هنوز ساخته نشده؛
            // در آن حالت یک بار ورود می‌کنیم تا شناسه‌ی کاربر را داشته باشیم
            if (c.auth.currentUserOrNull() == null) {
                signIn(user, password, email)
            }
            val id = c.auth.currentUserOrNull()?.id
                ?: return CloudResult.Failed("حساب ساخته شد ولی ورود انجام نشد")

            c.from("profiles").insert(CloudProfile(id = id, username = user))
            CloudResult.Ok(Unit)
        } catch (e: Exception) {
            CloudResult.Failed(humanize(e))
        }
    }

    /** ورود با یوزرنیم (یا ایمیل، اگر موقع ثبت‌نام داده باشد) */
    suspend fun signIn(
        username: String,
        password: String,
        email: String? = null,
    ): CloudResult<Unit> {
        val c = client ?: return CloudResult.Failed(OFFLINE)
        val user = normalizeUsername(username)
        return try {
            c.auth.signInWith(Email) {
                this.email = email?.trim()?.takeIf { it.isNotBlank() } ?: "$user@$INTERNAL_DOMAIN"
                this.password = password
            }
            CloudResult.Ok(Unit)
        } catch (e: Exception) {
            CloudResult.Failed(humanize(e))
        }
    }

    suspend fun signOut(): CloudResult<Unit> {
        val c = client ?: return CloudResult.Failed(OFFLINE)
        return try {
            c.auth.signOut()
            CloudResult.Ok(Unit)
        } catch (e: Exception) {
            CloudResult.Failed(humanize(e))
        }
    }

    /** پروفایل کاربر لاگین‌شده */
    suspend fun myProfile(): CloudResult<CloudProfile?> {
        val c = client ?: return CloudResult.Failed(OFFLINE)
        val id = currentUserId() ?: return CloudResult.Ok(null)
        return try {
            val row = c.from("profiles")
                .select { filter { eq("id", id) } }
                .decodeSingleOrNull<CloudProfile>()
            CloudResult.Ok(row)
        } catch (e: Exception) {
            CloudResult.Failed(humanize(e))
        }
    }

    private const val OFFLINE = "بخش آنلاین روی این نسخه فعال نیست"

    /** پیام خام سرور را به جمله‌ی فارسیِ قابل فهم تبدیل می‌کند */
    private fun humanize(e: Exception): String {
        val raw = e.message.orEmpty().lowercase()
        return when {
            "invalid login" in raw || "credential" in raw -> "یوزرنیم یا پسورد درست نیست"
            "already registered" in raw || "duplicate" in raw -> "این حساب از قبل وجود دارد"
            "network" in raw || "unable to resolve" in raw || "timeout" in raw ->
                "به اینترنت وصل نیستی"
            "weak password" in raw -> "پسورد خیلی ساده است"
            else -> "مشکلی پیش اومد — دوباره امتحان کن"
        }
    }
}
