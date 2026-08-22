package com.navidabbasian.kibord.core.cloud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** حالت صفحه‌ی حساب: ثبت‌نام یا ورود */
enum class AccountMode { REGISTER, SIGN_IN }

data class AccountUiState(
    val mode: AccountMode = AccountMode.REGISTER,
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** پروفایلِ کاربر لاگین‌شده — null یعنی وارد نشده */
    val profile: CloudProfile? = null,
    /** آمار آنلاین کاربر به تفکیک بازی — فقط بازی‌های اینترنتی */
    val stats: List<CloudGameStat> = emptyList(),
)

/**
 * وضعیت حساب بازیکن برای رابط کاربری.
 * هیچ‌کدام از بازی‌ها به این وابسته نیستند — نبودنش فقط یعنی بخش آنلاین خاموش است.
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    val cloudAvailable: Boolean get() = Cloud.isConfigured

    init {
        refreshProfile()
    }

    fun setMode(mode: AccountMode) = _uiState.update { it.copy(mode = mode, error = null) }
    fun setUsername(v: String) = _uiState.update { it.copy(username = v.take(24), error = null) }
    fun setPassword(v: String) = _uiState.update { it.copy(password = v.take(64), error = null) }
    fun setEmail(v: String) = _uiState.update { it.copy(email = v.take(120), error = null) }

    /** پروفایل فعلی را از سرور می‌خواند (اگر نشستی باشد) */
    fun refreshProfile() {
        if (!cloudAvailable) return
        viewModelScope.launch {
            when (val r = AccountRepository.myProfile()) {
                is CloudResult.Ok -> _uiState.update { it.copy(profile = r.value) }
                is CloudResult.Failed -> Unit // بی‌صدا: نبودِ نت نباید خطا نشان دهد
            }
            refreshStats()
        }
    }

    /** آمار آنلاین خودِ کاربر */
    fun refreshStats() {
        if (!cloudAvailable) return
        viewModelScope.launch {
            when (val r = StatsSync.myStats()) {
                is CloudResult.Ok -> _uiState.update { it.copy(stats = r.value) }
                is CloudResult.Failed -> Unit
            }
        }
    }

    fun submit() {
        val s = _uiState.value
        if (s.busy) return
        _uiState.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val email = s.email.trim().takeIf { it.isNotBlank() }
            val result = when (s.mode) {
                AccountMode.REGISTER -> AccountRepository.register(s.username, s.password, email)
                AccountMode.SIGN_IN -> AccountRepository.signIn(s.username, s.password, email)
            }
            when (result) {
                is CloudResult.Failed ->
                    _uiState.update { it.copy(busy = false, error = result.message) }

                is CloudResult.Ok -> {
                    when (val p = AccountRepository.myProfile()) {
                        is CloudResult.Ok -> _uiState.update {
                            it.copy(busy = false, profile = p.value, password = "")
                        }

                        is CloudResult.Failed -> _uiState.update {
                            it.copy(busy = false, error = p.message)
                        }
                    }
                    refreshStats()
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            AccountRepository.signOut()
            _uiState.update {
                AccountUiState(mode = AccountMode.SIGN_IN)
            }
        }
    }

}
