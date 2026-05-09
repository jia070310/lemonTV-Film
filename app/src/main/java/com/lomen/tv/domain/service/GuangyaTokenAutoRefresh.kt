package com.lomen.tv.domain.service

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lomen.tv.data.guangya.GuangyaApiClient
import com.lomen.tv.data.repository.ResourceLibraryRepository
import com.lomen.tv.domain.model.ResourceLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在应用回到前台时静默刷新光鸭 access_token（与网页端「每次打开页面会带着会话 / 静默续期」接近）。
 *
 * 网页可长期在线常见原因包括：HttpOnly 会话 Cookie、每次导航触发的静默 refresh、以及标签页常驻触发心跳。
 * TV 客户端若长时间不切回应用，则不会有请求；本类至少在每次进程视为「前台」时补一轮续期。
 */
@Singleton
class GuangyaTokenAutoRefresh @Inject constructor(
    private val repository: ResourceLibraryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastAttemptMs = ConcurrentHashMap<String, Long>()
    private var registered = false

    fun start() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch {
                    delay(DEBOUNCE_MS)
                    runRefreshPass()
                }
            }
        })
    }

    private suspend fun runRefreshPass() {
        val libs = try {
            repository.libraries.first()
        } catch (e: Exception) {
            Log.w(TAG, "read libraries failed", e)
            return
        }
        val now = System.currentTimeMillis()
        for (lib in libs) {
            if (lib.type != ResourceLibrary.LibraryType.GUANGYA) continue
            if (lib.apiRefreshToken.isBlank()) continue
            if (!needsRotation(lib, now)) continue
            val last = lastAttemptMs[lib.id] ?: 0L
            if (!cooldownAllows(lib, now, last)) continue

            lastAttemptMs[lib.id] = now
            val result = withContext(Dispatchers.IO) {
                GuangyaApiClient(lib).refreshTokens(forceRefresh = true)
            }
            result.onSuccess { u ->
                repository.updateLibrary(
                    lib.copy(
                        apiToken = u.accessToken,
                        apiRefreshToken = u.refreshToken,
                        apiTokenExpireAt = u.accessExpireAt
                    )
                )
                Log.d(TAG, "auto refresh ok libraryId=${lib.id}")
            }.onFailure { e ->
                Log.w(TAG, "auto refresh failed libraryId=${lib.id}: ${e.message}")
            }
        }
    }

    private fun needsRotation(lib: ResourceLibrary, now: Long): Boolean {
        val exp = lib.apiTokenExpireAt
        if (exp <= 0L) return true
        return now >= exp - REFRESH_BEFORE_EXPIRY_MS
    }

    private fun cooldownAllows(lib: ResourceLibrary, now: Long, lastAttempt: Long): Boolean {
        val exp = lib.apiTokenExpireAt
        val urgent = exp > 0L && now >= exp - REFRESH_BEFORE_EXPIRY_MS
        val minGap = when {
            urgent -> URGENT_COOLDOWN_MS
            exp <= 0L -> UNKNOWN_EXPIRY_COOLDOWN_MS
            else -> ROUTINE_COOLDOWN_MS
        }
        return now - lastAttempt >= minGap
    }

    companion object {
        private const val TAG = "GuangyaTokenAutoRefresh"
        private const val DEBOUNCE_MS = 1_500L
        private const val REFRESH_BEFORE_EXPIRY_MS = 15 * 60 * 1000L
        private const val URGENT_COOLDOWN_MS = 60_000L
        private const val UNKNOWN_EXPIRY_COOLDOWN_MS = 45 * 60 * 1000L
        private const val ROUTINE_COOLDOWN_MS = 10 * 60 * 1000L
    }
}
