package com.lomen.tv.ui.screens.settings

import android.util.Log
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lomen.tv.data.guangya.GuangyaApiClient
import com.lomen.tv.domain.model.ResourceLibrary
import com.lomen.tv.ui.screens.settings.QrCodeImage
import com.lomen.tv.ui.DialogDimens
import com.lomen.tv.ui.theme.BackgroundDark
import com.lomen.tv.ui.theme.DialogUiTokens
import com.lomen.tv.ui.theme.PrimaryYellow
import com.lomen.tv.ui.theme.TextMuted
import com.lomen.tv.ui.theme.TextPrimary
import com.lomen.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.UUID

data class GuangyaLoginResult(
    val accessToken: String,
    val refreshToken: String,
    val accessExpireAt: Long,
    val guangyaLoginMode: ResourceLibrary.GuangyaLoginMode,
    val guangyaDeviceId: String?,
    val selectedPaths: List<String>
)

private data class GuangyaTokenPayload(
    val bearerAccessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

private enum class GuangyaLoginTab {
    SMS,
    QR
}

private const val MAX_VISIBLE_DIRS = 300
private val GuangyaLoginDialogWidth = 320.dp
private val GuangyaLoginDialogHeight = 390.dp
private val GuangyaQrSize = 170.dp
private val GuangyaFieldHeight = 48.dp
private val GuangyaFieldCorner = RoundedCornerShape(14.dp)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GuangyaLoginDialog(
    onDismiss: () -> Unit,
    onSuccess: (GuangyaLoginResult) -> Unit,
    initialSmsLogin: Boolean = true
) {
    val tag = "GuangyaLoginDialog"
    val context = LocalContext.current
    val deviceId = remember {
        runCatching {
            val v = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            v?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        }.getOrElse { UUID.randomUUID().toString() }
    }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) } // 1=扫码登录, 2=目录选择
    var accessToken by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf("") }
    var accessExpireAt by remember { mutableStateOf(0L) }
    var verificationUrl by remember { mutableStateOf("") }
    var userCode by remember { mutableStateOf("") }
    var deviceCode by remember { mutableStateOf("") }
    var pollIntervalSec by remember { mutableStateOf(2L) }
    var loginTab by remember { mutableStateOf(if (initialSmsLogin) GuangyaLoginTab.SMS else GuangyaLoginTab.QR) }
    var smsTabFocused by remember { mutableStateOf(false) }
    var qrTabFocused by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var captchaToken by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var smsHint by remember { mutableStateOf<String?>(null) }
    var smsSending by remember { mutableStateOf(false) }
    var smsLogging by remember { mutableStateOf(false) }
    var smsSendButtonFocused by remember { mutableStateOf(false) }
    var smsLoginButtonFocused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var currentPath by remember { mutableStateOf("/") }
    var directories by remember { mutableStateOf<List<com.lomen.tv.data.webdav.WebDavFile>>(emptyList()) }
    var selectedPath by remember { mutableStateOf<String?>(null) } // 单选：允许选父目录或子目录
    var polling by remember { mutableStateOf(false) }
    var listHint by remember { mutableStateOf<String?>(null) }
    val step2CloseFocusRequester = remember { FocusRequester() }
    val firstDirExpandFocusRequester = remember { FocusRequester() }
    val firstDirCheckFocusRequester = remember { FocusRequester() }
    val smsTabFocusRequester = remember { FocusRequester() }
    val qrTabFocusRequester = remember { FocusRequester() }
    val phoneInputFocusRequester = remember { FocusRequester() }
    val smsCodeInputFocusRequester = remember { FocusRequester() }
    val smsSendButtonFocusRequester = remember { FocusRequester() }
    val smsLoginButtonFocusRequester = remember { FocusRequester() }
    val closeButtonFocusRequester = remember { FocusRequester() }
    val directoryListState = rememberLazyListState()

    // 下拉展开缓存（懒加载）
    val expandedMap = remember { mutableStateMapOf<String, Boolean>() }
    val childrenMap = remember { mutableStateMapOf<String, List<com.lomen.tv.data.webdav.WebDavFile>>() }
    val childrenLoading = remember { mutableStateMapOf<String, Boolean>() }
    val hasChildrenMap = remember { mutableStateMapOf<String, Boolean?>() } // null=未知

    val authClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun requestDeviceCode(): Result<Unit> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("client_id", "aMe-8VSlkrbQXpUR")
                    .toString()
                val req = Request.Builder()
                    .url("https://account.guangyapan.com/v1/auth/device/code")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .build()
                authClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Log.e(tag, "device/code http=${resp.code} body=${text.take(400)}")
                        error("HTTP ${resp.code}: ${text.take(200)}")
                    }
                    val json = JSONObject(text)
                    verificationUrl = json.optString("verification_uri_complete")
                    userCode = json.optString("user_code")
                    deviceCode = json.optString("device_code")
                    pollIntervalSec = json.optLong("interval", 2L).coerceAtLeast(1L)
                    if (verificationUrl.isBlank() || deviceCode.isBlank()) {
                        Log.e(tag, "device/code invalid payload: ${text.take(400)}")
                        error("device code response invalid")
                    }
                }
            }
        }
    }

    suspend fun pollTokenOnce(): Result<GuangyaTokenPayload?> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("client_id", "aMe-8VSlkrbQXpUR")
                    .put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .put("device_code", deviceCode)
                    .toString()
                val req = Request.Builder()
                    .url("https://account.guangyapan.com/v1/auth/token")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .build()
                authClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = JSONObject(text)
                    val tokenJson = json.optJSONObject("data") ?: json
                    val access = tokenJson.optString("access_token").ifBlank {
                        tokenJson.optString("accessToken")
                    }
                    if (access.isNotBlank()) {
                        val refresh = tokenJson.optString("refresh_token")
                        val expiresIn = tokenJson.optLong(
                            "expires_in",
                            tokenJson.optLong("expiresIn", 0L)
                        )
                        val expiresAt = if (expiresIn > 0) {
                            System.currentTimeMillis() + (expiresIn * 1000L) - 60_000L
                        } else 0L
                        GuangyaTokenPayload(
                            bearerAccessToken = "Bearer $access",
                            refreshToken = refresh,
                            expiresAt = expiresAt
                        )
                    } else {
                        // 常见：authorization_pending / slow_down / expired_token 等
                        val err = json.optString("error").ifBlank { tokenJson.optString("error") }
                        val errDesc = json.optString("error_description").ifBlank {
                            tokenJson.optString("error_description")
                        }
                        when (err) {
                            "", "authorization_pending" -> null
                            "slow_down" -> {
                                pollIntervalSec = (pollIntervalSec + 2L).coerceAtMost(12L)
                                null
                            }
                            "expired_token" -> {
                                throw IllegalStateException("二维码已过期，请点击重试")
                            }
                            "access_denied" -> {
                                throw IllegalStateException("已取消授权，请重新扫码")
                            }
                            else -> {
                                if (resp.code !in 200..299 || err.isNotBlank()) {
                                    Log.w(tag, "token poll fail http=${resp.code} err=$err desc=$errDesc body=${text.take(300)}")
                                    throw IllegalStateException(
                                        "登录确认失败：${if (err.isNotBlank()) err else "HTTP ${resp.code}"} ${errDesc}".trim()
                                    )
                                }
                                null
                            }
                        }
                    }
                }
            }
        }
    }

    fun normalizePhoneE164(raw: String): String {
        val compact = raw.trim().replace(" ", "")
        if (compact.isBlank()) return ""
        if (compact.startsWith("+")) return compact
        return if (compact.length == 11 && compact.all { it.isDigit() }) "+86$compact" else compact
    }

    fun toShortSmsError(message: String?): String {
        val msg = message.orEmpty()
        return when {
            msg.contains("HTTP 429", ignoreCase = true) ||
                msg.contains("resource_exhausted", ignoreCase = true) ||
                msg.contains("rate limit", ignoreCase = true) -> "短信发送频率过高，请稍后再试"
            msg.contains("HTTP 400", ignoreCase = true) && msg.contains("invalid phone", ignoreCase = true) -> "手机号格式不正确，请检查后重试"
            msg.contains("HTTP 401", ignoreCase = true) || msg.contains("HTTP 403", ignoreCase = true) -> "鉴权失败，请稍后重试"
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) -> "网络超时，请检查网络后重试"
            else -> "发送验证码失败，请稍后重试"
        }
    }

    suspend fun ensureCaptchaToken(): Result<String> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val phone = normalizePhoneE164(phoneNumber)
                val body = JSONObject()
                    .put("client_id", "aMe-8VSlkrbQXpUR")
                    .put("action", "POST:/v1/auth/verification")
                    .put("device_id", deviceId)
                    .put("meta", JSONObject().put("phone_number", phone).put("username", phone))
                    .toString()
                val req = Request.Builder()
                    .url("https://account.guangyapan.com/v1/shield/captcha/init")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .build()
                authClient.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
                    val json = JSONObject(text)
                    val token = json.optString("captcha_token").ifBlank {
                        json.optJSONObject("data")?.optString("captcha_token").orEmpty()
                    }
                    if (token.isBlank()) error("未获取到 captcha token")
                    token
                }
            }
        }
    }

    suspend fun requestSmsCode(captchaTokenOverride: String? = null): Result<String> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val phone = normalizePhoneE164(phoneNumber)
                val body = JSONObject()
                    .put("phone_number", phone)
                    .put("target", "ANY")
                    .put("client_id", "aMe-8VSlkrbQXpUR")
                    .put("device_id", deviceId)
                    .toString()
                val reqBuilder = Request.Builder()
                    .url("https://account.guangyapan.com/v1/auth/verification")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                val tokenToUse = captchaTokenOverride ?: captchaToken
                if (tokenToUse.isNotBlank()) {
                    reqBuilder.header("X-Captcha-Token", tokenToUse)
                }
                authClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
                    val json = JSONObject(text)
                    val id = json.optString("verification_id").ifBlank {
                        json.optJSONObject("data")?.optString("verification_id").orEmpty()
                    }
                    if (id.isBlank()) error("未获取到 verification_id")
                    id
                }
            }
        }
    }

    suspend fun loginBySms(): Result<GuangyaTokenPayload> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val phone = normalizePhoneE164(phoneNumber)
                val verifyBody = JSONObject()
                    .put("verification_id", verificationId)
                    .put("verification_code", smsCode.trim())
                    .put("client_id", "aMe-8VSlkrbQXpUR")
                    .toString()
                val verifyReq = Request.Builder()
                    .url("https://account.guangyapan.com/v1/auth/verification/verify")
                    .post(verifyBody.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .build()
                val verificationToken = authClient.newCall(verifyReq).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("验证码校验失败：HTTP ${resp.code}")
                    val json = JSONObject(text)
                    val token = json.optString("verification_token").ifBlank {
                        json.optJSONObject("data")?.optString("verification_token").orEmpty()
                    }
                    if (token.isBlank()) error("验证码校验失败，请重试")
                    token
                }

                val signinBody = JSONObject()
                    .put("verification_code", smsCode.trim())
                    .put("verification_token", verificationToken)
                    .put("username", phone)
                    .put("client_id", "aMe-8VSlkrbQXpUR")
                    .toString()
                val signinReq = Request.Builder()
                    .url("https://account.guangyapan.com/v1/auth/signin")
                    .post(signinBody.toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .build()
                authClient.newCall(signinReq).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("登录失败：HTTP ${resp.code}")
                    val json = JSONObject(text)
                    val tokenJson = json.optJSONObject("data") ?: json
                    val rawAccess = tokenJson.optString("access_token")
                    val refresh = tokenJson.optString("refresh_token")
                    val expiresIn = tokenJson.optLong(
                        "expires_in",
                        tokenJson.optLong("expiresIn", 0L)
                    )
                    if (rawAccess.isBlank()) error("登录失败，未返回 access_token")
                    val bearer = if (rawAccess.startsWith("Bearer ")) rawAccess else "Bearer $rawAccess"
                    GuangyaTokenPayload(
                        bearerAccessToken = bearer,
                        refreshToken = refresh,
                        expiresAt = if (expiresIn > 0) {
                            System.currentTimeMillis() + (expiresIn * 1000L) - 60_000L
                        } else 0L
                    )
                }
            }
        }
    }

    fun resetQrAndRetry() {
        verificationUrl = ""
        userCode = ""
        deviceCode = ""
        pollIntervalSec = 2L
        error = null
        polling = false
        loading = false
    }

    fun makeClient(token: String): GuangyaApiClient {
        val library = ResourceLibrary(
            id = "tmp",
            name = "tmp",
            type = ResourceLibrary.LibraryType.GUANGYA,
            apiToken = token
        )
        return GuangyaApiClient(library)
    }

    fun loadDirectories(path: String) {
        if (accessToken.isBlank()) return
        loading = true
        error = null
        listHint = null
        scope.launch {
            val client = makeClient(accessToken)
            val result = client.listFiles(path)
            val allDirs = result.getOrNull().orEmpty().filter { it.isDirectory }
            directories = allDirs.take(MAX_VISIBLE_DIRS)
            // 目录切换时重置展开状态，避免旧目录状态污染
            expandedMap.clear()
            if (allDirs.size > MAX_VISIBLE_DIRS) {
                listHint = "目录过多，仅显示前 $MAX_VISIBLE_DIRS 项，请进入子目录继续筛选"
            }
            // 预检测当前层每个目录是否有下级：有下级才显示下拉三角
            val probeTargets = directories.map { it.path }
            probeTargets.forEach { hasChildrenMap.remove(it) } // 先置未知，UI隐藏三角
            val semaphore = Semaphore(4)
            probeTargets.forEach { dirPath ->
                launch {
                    semaphore.withPermit {
                        val hasChildren = runCatching {
                            client.listFiles(dirPath).getOrNull().orEmpty().any { it.isDirectory }
                        }.getOrDefault(false)
                        hasChildrenMap[dirPath] = hasChildren
                    }
                }
            }
            if (result.isFailure) {
                error = "读取目录失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            } else {
                currentPath = path
            }
            loading = false
        }
    }

    fun toggleExpand(dirPath: String) {
        val currently = expandedMap[dirPath] == true
        expandedMap[dirPath] = !currently
        if (!currently) {
            // 展开时懒加载一次
            if (!childrenMap.containsKey(dirPath) && childrenLoading[dirPath] != true) {
                childrenLoading[dirPath] = true
                scope.launch {
                    val client = makeClient(accessToken)
                    val result = client.listFiles(dirPath)
                    val allDirs = result.getOrNull().orEmpty().filter { it.isDirectory }
                    childrenMap[dirPath] = allDirs.take(MAX_VISIBLE_DIRS)
                    hasChildrenMap[dirPath] = allDirs.isNotEmpty()
                    childrenLoading[dirPath] = false
                }
            }
        }
    }

    if (step == 1) {
        // 二维码登录弹窗：样式与 QrCodeDialog 保持一致（尺寸、圆角、右上角黄色关闭按钮）
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(GuangyaLoginDialogWidth)
                        .height(GuangyaLoginDialogHeight)
                        .clip(RoundedCornerShape(DialogUiTokens.CornerRadius))
                        .background(DialogUiTokens.ContainerColor)
                        .border(
                            DialogUiTokens.BorderWidth,
                            DialogUiTokens.BorderColor,
                            RoundedCornerShape(DialogUiTokens.CornerRadius)
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            // 仅拦截返回键，方向键放行用于页签/按钮焦点切换
                            if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                onDismiss()
                                true
                            } else false
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var closeFocused by remember { mutableStateOf(false) }
                            Text(
                                text = "光鸭云盘登录窗口",
                                color = TextPrimary
                            )
                            IconButton(
                                onClick = onDismiss,
                                colors = IconButtonDefaults.colors(
                                    containerColor = Color(0xFF2E2E2E),
                                    contentColor = Color(0xFFD1D5DB),
                                    focusedContainerColor = PrimaryYellow,
                                    focusedContentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(44.dp)
                                    // modifier 里不要写死背景色，否则聚焦时 focusedContainerColor 不生效
                                    .background(if (closeFocused) PrimaryYellow else Color(0xFF2E2E2E), CircleShape)
                                    .focusRequester(closeButtonFocusRequester)
                                    .onFocusChanged { closeFocused = it.isFocused }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyUp &&
                                            (keyEvent.key == Key.DirectionCenter ||
                                                keyEvent.key == Key.Enter ||
                                                keyEvent.key == Key.NumPadEnter)
                                        ) {
                                            onDismiss()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = if (closeFocused) Color.Black else Color(0xFFD1D5DB)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF303030))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    loginTab = GuangyaLoginTab.SMS
                                    error = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(smsTabFocusRequester)
                                    .onFocusChanged { smsTabFocused = it.isFocused }
                                    .focusProperties {
                                        right = qrTabFocusRequester
                                        up = closeButtonFocusRequester
                                        down = phoneInputFocusRequester
                                    },
                                scale = ButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 1.0f
                                ),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (smsTabFocused) PrimaryYellow else Color(0xFF2E2E2E),
                                    contentColor = if (smsTabFocused) Color.Black else {
                                        if (loginTab == GuangyaLoginTab.SMS) Color(0xFFD1D5DB) else Color(0xFF6B7280)
                                    },
                                    focusedContainerColor = if (smsTabFocused) PrimaryYellow else Color(0xFF2E2E2E),
                                    focusedContentColor = if (smsTabFocused) Color.Black else {
                                        if (loginTab == GuangyaLoginTab.SMS) Color(0xFFD1D5DB) else Color(0xFF6B7280)
                                    }
                                )
                            ) {
                                Text(
                                    "手机号登录",
                                    color = if (smsTabFocused) Color.Black else if (loginTab == GuangyaLoginTab.SMS) Color(0xFFD1D5DB) else Color(0xFF6B7280)
                                )
                            }
                            Button(
                                onClick = {
                                    loginTab = GuangyaLoginTab.QR
                                    error = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(qrTabFocusRequester)
                                    .onFocusChanged { qrTabFocused = it.isFocused }
                                    .focusProperties {
                                        left = smsTabFocusRequester
                                        up = closeButtonFocusRequester
                                    },
                                scale = ButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 1.0f
                                ),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (qrTabFocused) PrimaryYellow else Color(0xFF2E2E2E),
                                    contentColor = if (qrTabFocused) Color.Black else {
                                        if (loginTab == GuangyaLoginTab.QR) Color(0xFFD1D5DB) else Color(0xFF6B7280)
                                    },
                                    focusedContainerColor = if (qrTabFocused) PrimaryYellow else Color(0xFF2E2E2E),
                                    focusedContentColor = if (qrTabFocused) Color.Black else {
                                        if (loginTab == GuangyaLoginTab.QR) Color(0xFFD1D5DB) else Color(0xFF6B7280)
                                    }
                                )
                            ) {
                                Text(
                                    "扫码登录",
                                    color = if (qrTabFocused) Color.Black else if (loginTab == GuangyaLoginTab.QR) Color(0xFFD1D5DB) else Color(0xFF6B7280)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (loginTab == GuangyaLoginTab.QR) {
                            Text(
                                text = "请使用手机光鸭 App 扫描二维码登录",
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        if (verificationUrl.isNotBlank()) {
                            QrCodeImage(text = verificationUrl, modifier = Modifier.size(GuangyaQrSize))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(GuangyaQrSize)
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Text(
                                        text = if (loading) "正在生成二维码..." else "二维码生成失败",
                                        color = if (loading) TextMuted else Color(0xFFEF4444)
                                    )
                                    if (!loading && !error.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 64.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = error!!,
                                                    color = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                resetQrAndRetry()
                                                scope.launch {
                                                    loading = true
                                                    val res = requestDeviceCode()
                                                    loading = false
                                                    if (res.isFailure) {
                                                        error = "生成二维码失败：${res.exceptionOrNull()?.message ?: "未知错误"}"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.colors(
                                                containerColor = Color(0xFF2E2E2E),
                                                contentColor = TextPrimary,
                                                focusedContainerColor = PrimaryYellow,
                                                focusedContentColor = BackgroundDark
                                            )
                                        ) {
                                            Text("重试")
                                        }
                                    }
                                }
                            }
                        }

                            Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (userCode.isNotBlank()) "验证码：$userCode" else "",
                            color = TextMuted
                        )

                            Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = if (polling) "等待扫码确认中..." else "提示：扫码后会自动跳转目录选择",
                            color = if (polling) PrimaryYellow else TextMuted
                        )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("手机号：", color = Color(0xFFD1D5DB), modifier = Modifier.width(74.dp))
                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(GuangyaFieldHeight)
                                        .focusRequester(phoneInputFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            // 解决 TV 输入框「焦点锁死」：方向键事件会被 TextField 吞掉，
                                            // 所以需要在这里显式把焦点送到下一个控件。
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                if (keyEvent.key == Key.DirectionUp) {
                                                    // 上移：返回“手机号登录”页头按钮
                                                    smsTabFocusRequester.requestFocus()
                                                    return@onPreviewKeyEvent true
                                                }
                                                if (keyEvent.key == Key.DirectionDown ||
                                                    keyEvent.key == Key.DirectionCenter ||
                                                    keyEvent.key == Key.Enter ||
                                                    keyEvent.key == Key.NumPadEnter
                                                ) {
                                                    // 下键/确认：先把焦点给“获取验证码”
                                                    smsSendButtonFocusRequester.requestFocus()
                                                    return@onPreviewKeyEvent true
                                                }
                                            }
                                            false
                                        }
                                        .focusProperties {
                                            // 在手机号页内：上移先回到“手机号页头按钮”
                                            up = smsTabFocusRequester
                                            down = smsSendButtonFocusRequester
                                            left = qrTabFocusRequester
                                        },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = GuangyaFieldCorner,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryYellow,
                                        unfocusedBorderColor = Color(0xFF6B7280),
                                        focusedContainerColor = PrimaryYellow.copy(alpha = 0.92f),
                                        unfocusedContainerColor = Color(0xFF22242A),
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.White,
                                        focusedPlaceholderColor = Color.Black,
                                        unfocusedPlaceholderColor = TextMuted
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("验证码：", color = Color(0xFFD1D5DB), modifier = Modifier.width(74.dp))
                                OutlinedTextField(
                                    value = smsCode,
                                    onValueChange = { smsCode = it.trim() },
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(GuangyaFieldHeight)
                                        .focusRequester(smsCodeInputFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionUp -> {
                                                        // 上移：返回“手机号输入框”
                                                        phoneInputFocusRequester.requestFocus()
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    Key.DirectionRight -> {
                                                        smsSendButtonFocusRequester.requestFocus()
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    Key.DirectionDown -> {
                                                        smsLoginButtonFocusRequester.requestFocus()
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                            false
                                        }
                                        .focusProperties {
                                            up = phoneInputFocusRequester
                                            // 在验证码输入框内：向左优先回到“获取验证码”按钮
                                            // 避免连续按左键后直接跳到扫码页头（你的反馈问题）
                                            left = smsSendButtonFocusRequester
                                            right = smsSendButtonFocusRequester
                                            down = smsLoginButtonFocusRequester
                                        },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = GuangyaFieldCorner,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryYellow,
                                        unfocusedBorderColor = Color(0xFF6B7280),
                                        focusedContainerColor = PrimaryYellow.copy(alpha = 0.92f),
                                        unfocusedContainerColor = Color(0xFF22242A),
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.White,
                                        focusedPlaceholderColor = Color.Black,
                                        unfocusedPlaceholderColor = TextMuted
                                    )
                                )
                                Button(
                                    onClick = {
                                        if (phoneNumber.isBlank()) {
                                            error = "请先输入手机号"
                                            return@Button
                                        }
                                        error = null
                                        smsHint = null
                                        scope.launch {
                                            smsSending = true
                                            try {
                                                val token1 = if (captchaToken.isBlank()) {
                                                    ensureCaptchaToken().getOrElse { throw it }
                                                } else captchaToken
                                                if (captchaToken.isBlank()) captchaToken = token1

                                                requestSmsCode(captchaTokenOverride = token1)
                                                    .onSuccess {
                                                        verificationId = it
                                                        smsHint = "验证码已发送，请留意短信"
                                                        // 获取成功后自动聚焦到“验证码输入框”
                                                        smsCodeInputFocusRequester.requestFocus()
                                                    }
                                                    .onFailure { ex ->
                                                        val msg = ex.message.orEmpty()
                                                        val needCaptcha = msg.contains("captcha_required", true) ||
                                                            msg.contains("No captcha", true)
                                                        if (needCaptcha) {
                                                            // captcha 过期/无效：重新拉取一次 token 再发码
                                                            val token2 = ensureCaptchaToken().getOrElse { throw it }
                                                            captchaToken = token2
                                                            requestSmsCode(captchaTokenOverride = token2)
                                                                .onSuccess {
                                                                    verificationId = it
                                                                    smsHint = "验证码已发送，请留意短信"
                                                                    // 获取成功后自动聚焦到“验证码输入框”
                                                                    smsCodeInputFocusRequester.requestFocus()
                                                                }
                                                                .onFailure { ex2 ->
                                                                    error = toShortSmsError(ex2.message)
                                                                }
                                                        } else {
                                                            error = toShortSmsError(ex.message)
                                                        }
                                                    }
                                            } catch (ex: Exception) {
                                                error = toShortSmsError(ex.message)
                                            }
                                            smsSending = false
                                        }
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (smsSendButtonFocused) PrimaryYellow else Color(0xFF2E2E2E),
                                        contentColor = if (smsSendButtonFocused) Color.Black else TextPrimary,
                                        focusedContainerColor = PrimaryYellow,
                                        focusedContentColor = Color.Black
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(GuangyaFieldHeight)
                                        .focusRequester(smsSendButtonFocusRequester)
                                        .onFocusChanged { smsSendButtonFocused = it.isFocused }
                                        .focusProperties {
                                            left = smsCodeInputFocusRequester
                                            up = phoneInputFocusRequester
                                            down = smsLoginButtonFocusRequester
                                        },
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                    scale = ButtonDefaults.scale(
                                        scale = 1.0f,
                                        focusedScale = 1.0f,
                                        pressedScale = 1.0f
                                    )
                                ) {
                                    Text(
                                        text = if (smsSending) "发送中..." else "获取验证码",
                                        color = if (smsSendButtonFocused) Color.Black else TextPrimary,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            smsHint?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = TextMuted)
                            }
                            error?.let {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(it, color = Color(0xFFEF4444))
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    if (phoneNumber.isBlank() || smsCode.isBlank() || verificationId.isBlank()) {
                                        error = "请先输入手机号并获取验证码"
                                        return@Button
                                    }
                                    error = null
                                    scope.launch {
                                        smsLogging = true
                                        loginBySms()
                                            .onSuccess { token ->
                                                accessToken = token.bearerAccessToken
                                                refreshToken = token.refreshToken
                                                accessExpireAt = token.expiresAt
                                                step = 2
                                                loadDirectories("/")
                                            }
                                            .onFailure { ex ->
                                                error = "手机号登录失败：${ex.message ?: "未知错误"}"
                                            }
                                        smsLogging = false
                                    }
                                },
                                scale = ButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 1.0f
                                ),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (smsLoginButtonFocused) PrimaryYellow else Color(0xFF2E2E2E),
                                    contentColor = if (smsLoginButtonFocused) Color.Black else Color(0xFFD1D5DB),
                                    focusedContainerColor = PrimaryYellow,
                                    focusedContentColor = Color.Black
                                ),
                                // 下方没有其它可聚焦控件，保证从输入区可以稳定移出
                                modifier = Modifier
                                    .width(240.dp)
                                    .height(GuangyaFieldHeight)
                                    .focusRequester(smsLoginButtonFocusRequester)
                                    .onFocusChanged { smsLoginButtonFocused = it.isFocused }
                                    .focusProperties {
                                        up = smsCodeInputFocusRequester
                                    }
                            ) {
                                Text(
                                    text = if (smsLogging) "登录中..." else "确认",
                                    color = if (smsLoginButtonFocused) Color.Black else Color(0xFFD1D5DB),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            // 居中容器，带半透明背景遮罩（与其它弹窗一致）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(DialogDimens.WebDavBoxPadding)
                    .onPreviewKeyEvent { keyEvent ->
                        // 只处理返回键，方向键留给焦点系统（否则无法移动/操作）
                        if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = DialogDimens.WebDavFormWidthMin, max = DialogDimens.WebDavFormWidthMax)
                        .heightIn(max = DialogDimens.WebDavFormHeightMax)
                        .clip(RoundedCornerShape(DialogUiTokens.CornerRadius))
                        .background(DialogUiTokens.ContainerColor)
                        .border(
                            DialogUiTokens.BorderWidth,
                            DialogUiTokens.BorderColor,
                            RoundedCornerShape(DialogUiTokens.CornerRadius)
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            // 只处理返回键；方向键放行用于焦点移动
                            if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                onDismiss()
                                true
                            } else false
                        }
                ) {
                    Column(modifier = Modifier.padding(DialogDimens.CardPaddingInner)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "选择扫描目录", color = TextPrimary)
                            var closeFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = onDismiss,
                                colors = IconButtonDefaults.colors(
                                    containerColor = Color(0xFF303030),
                                    contentColor = Color.White,
                                    focusedContainerColor = PrimaryYellow,
                                    focusedContentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(44.dp)
                                    .clip(CircleShape)
                                    .onFocusChanged { closeFocused = it.isFocused }
                                    .focusRequester(step2CloseFocusRequester)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyUp &&
                                            (keyEvent.key == Key.DirectionCenter ||
                                                keyEvent.key == Key.Enter ||
                                                keyEvent.key == Key.NumPadEnter)
                                        ) {
                                            onDismiss()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = if (closeFocused) Color.Black else Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("当前目录：$currentPath", color = TextMuted)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("检测到下级会显示 ▾；点击展开；点击右侧 ✓ 选中目录", color = TextMuted)
                        Spacer(modifier = Modifier.height(8.dp))
                        listHint?.let {
                            Text(it, color = TextMuted)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (loading) {
                            Text("正在读取目录...", color = TextMuted)
                        } else {
                            // 列表区：用 LazyColumn（TV 焦点移动更稳定）
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                state = directoryListState,
                                // 预留底部确认按钮+焦点放大空间，确保最末目录项完整可见
                                contentPadding = PaddingValues(bottom = 180.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(directories) { index, dir ->
                                    val checked = selectedPath == dir.path
                                    var checkFocused by remember(dir.path) { mutableStateOf(false) }
                                    var expandFocused by remember(dir.path) { mutableStateOf(false) }
                                    val rowBivr = remember(dir.path) { BringIntoViewRequester() }
                                    val expanded = expandedMap[dir.path] == true
                                    val hasChildren = hasChildrenMap[dir.path] // null=未知
                                    val isLoadingChildren = childrenLoading[dir.path] == true
                                    val children = childrenMap[dir.path].orEmpty()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color(0xFF212121))
                                            .bringIntoViewRequester(rowBivr)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(dir.name, color = TextPrimary, modifier = Modifier.weight(1f))
                                                Spacer(modifier = Modifier.width(12.dp))

                                                // 下拉三角：未知时也展示（展开时再判断有没有子目录；无子目录会自动不再显示）
                                                if (hasChildren == true) {
                                                    IconButton(
                                                        onClick = { toggleExpand(dir.path) },
                                                        colors = IconButtonDefaults.colors(
                                                            containerColor = Color(0xFF303030),
                                                            contentColor = Color.White,
                                                            focusedContainerColor = PrimaryYellow,
                                                            focusedContentColor = Color.Black
                                                        ),
                                                        modifier = Modifier
                                                            .width(36.dp)
                                                            .height(36.dp)
                                                            .clip(CircleShape)
                                                            .onFocusChanged { expandFocused = it.isFocused }
                                                            .then(
                                                                if (index == 0) Modifier.focusRequester(firstDirExpandFocusRequester)
                                                                else Modifier
                                                            )
                                                    ) {
                                                        Icon(
                                                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                            contentDescription = if (expanded) "收起" else "展开",
                                                            tint = if (expandFocused) Color.Black else Color.White
                                                        )
                                                    }
                                                    LaunchedEffect(expandFocused) {
                                                        if (expandFocused) rowBivr.bringIntoView()
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        selectedPath = if (checked) null else dir.path
                                                    },
                                                    colors = IconButtonDefaults.colors(
                                                        containerColor = if (checked) PrimaryYellow else Color(0xFF303030),
                                                        contentColor = if (checked) Color.Black else Color.White,
                                                        focusedContainerColor = PrimaryYellow,
                                                        focusedContentColor = Color.Black,
                                                    ),
                                                    modifier = Modifier
                                                        .width(36.dp)
                                                        .height(36.dp)
                                                        .clip(CircleShape)
                                                        .onFocusChanged { checkFocused = it.isFocused }
                                                        .then(
                                                            if (index == 0) Modifier.focusRequester(firstDirCheckFocusRequester)
                                                            else Modifier
                                                        )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = if (checked) "取消选中目录" else "选中目录",
                                                        tint = if (checkFocused || checked) Color.Black else Color.White
                                                    )
                                                }
                                                LaunchedEffect(checkFocused) {
                                                    if (checkFocused) rowBivr.bringIntoView()
                                                }
                                            }

                                            if (expanded) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color(0xFF1E1E1E))
                                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                                ) {
                                                    if (isLoadingChildren) {
                                                        Text("正在读取下级目录...", color = TextMuted)
                                                    } else if (children.isEmpty()) {
                                                        // 没有下级：标记后续不显示三角
                                                        LaunchedEffect(dir.path) {
                                                            hasChildrenMap[dir.path] = false
                                                        }
                                                        Text("无下级目录", color = TextMuted)
                                                    } else {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                            children.forEach { child ->
                                                                val childChecked = selectedPath == child.path
                                                                var childCheckFocused by remember(child.path) { mutableStateOf(false) }
                                                                var childExpandFocused by remember(child.path) { mutableStateOf(false) }
                                                                val childBivr = remember(child.path) { BringIntoViewRequester() }
                                                                val childExpanded = expandedMap[child.path] == true
                                                                val childHasChildren = hasChildrenMap[child.path]
                                                                val childLoading = childrenLoading[child.path] == true
                                                                val grandChildren = childrenMap[child.path].orEmpty()

                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .bringIntoViewRequester(childBivr),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = child.name,
                                                                        color = TextPrimary,
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .padding(start = 18.dp)
                                                                    )

                                                                    if (childHasChildren == true) {
                                                                        IconButton(
                                                                            onClick = { toggleExpand(child.path) },
                                                                            colors = IconButtonDefaults.colors(
                                                                            containerColor = Color(0xFF303030),
                                                                                contentColor = Color.White,
                                                                                focusedContainerColor = PrimaryYellow,
                                                                                focusedContentColor = Color.Black
                                                                            ),
                                                                            modifier = Modifier
                                                                                .width(34.dp)
                                                                                .height(34.dp)
                                                                                .clip(CircleShape)
                                                                                .onFocusChanged { childExpandFocused = it.isFocused }
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = if (childExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                                                contentDescription = if (childExpanded) "收起" else "展开",
                                                                                tint = if (childExpandFocused) Color.Black else Color.White
                                                                            )
                                                                        }
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                    }

                                                                    IconButton(
                                                                        onClick = {
                                                                            selectedPath = if (childChecked) null else child.path
                                                                        },
                                                                        colors = IconButtonDefaults.colors(
                                                                            containerColor = if (childChecked) PrimaryYellow else Color(0xFF303030),
                                                                            contentColor = if (childChecked) Color.Black else Color.White,
                                                                            focusedContainerColor = PrimaryYellow,
                                                                            focusedContentColor = Color.Black
                                                                        ),
                                                                        modifier = Modifier
                                                                            .width(34.dp)
                                                                            .height(34.dp)
                                                                            .clip(CircleShape)
                                                                            .onFocusChanged { childCheckFocused = it.isFocused }
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Check,
                                                                            contentDescription = if (childChecked) "取消选中目录" else "选中目录",
                                                                            tint = if (childCheckFocused || childChecked) Color.Black else Color.White
                                                                        )
                                                                    }
                                                                    LaunchedEffect(childCheckFocused) {
                                                                        if (childCheckFocused) {
                                                                            val nearBottom = index >= (directories.size - 2).coerceAtLeast(0)
                                                                            if (nearBottom) {
                                                                                runCatching { childBivr.bringIntoView() }
                                                                                runCatching { rowBivr.bringIntoView() }
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                if (childExpanded) {
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(start = 18.dp)
                                                                            .clip(RoundedCornerShape(14.dp))
                                                                            .background(Color(0xFF191919))
                                                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                                                    ) {
                                                                        if (childLoading) {
                                                                            Text("正在读取下级目录...", color = TextMuted)
                                                                        } else if (grandChildren.isEmpty()) {
                                                                            LaunchedEffect(child.path) {
                                                                                hasChildrenMap[child.path] = false
                                                                            }
                                                                            Text("无下级目录", color = TextMuted)
                                                                        } else {
                                                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                                grandChildren.forEach { gc ->
                                                                                    val gcChecked = selectedPath == gc.path
                                                                                    var gcCheckFocused by remember(gc.path) { mutableStateOf(false) }
                                                                                    val gcBivr = remember(gc.path) { BringIntoViewRequester() }
                                                                                    Row(
                                                                                        modifier = Modifier
                                                                                            .fillMaxWidth()
                                                                                            .bringIntoViewRequester(gcBivr),
                                                                                        verticalAlignment = Alignment.CenterVertically
                                                                                    ) {
                                                                                        Text(
                                                                                            text = gc.name,
                                                                                            color = TextPrimary,
                                                                                            modifier = Modifier
                                                                                                .weight(1f)
                                                                                                .padding(start = 18.dp)
                                                                                        )
                                                                                        IconButton(
                                                                                            onClick = {
                                                                                                selectedPath = if (gcChecked) null else gc.path
                                                                                            },
                                                                                            colors = IconButtonDefaults.colors(
                                                                                                containerColor = if (gcChecked) PrimaryYellow else Color(0xFF303030),
                                                                                                contentColor = if (gcChecked) Color.Black else Color.White,
                                                                                                focusedContainerColor = PrimaryYellow,
                                                                                                focusedContentColor = Color.Black
                                                                                            ),
                                                                                            modifier = Modifier
                                                                                                .width(32.dp)
                                                                                                .height(32.dp)
                                                                                                .clip(CircleShape)
                                                                                                .onFocusChanged { gcCheckFocused = it.isFocused }
                                                                                        ) {
                                                                                            Icon(
                                                                                                imageVector = Icons.Default.Check,
                                                                                                contentDescription = if (gcChecked) "取消选中目录" else "选中目录",
                                                                                                tint = if (gcCheckFocused || gcChecked) Color.Black else Color.White
                                                                                            )
                                                                                        }
                                                                                        LaunchedEffect(gcCheckFocused) {
                                                                                            if (gcCheckFocused) {
                                                                                                val nearBottom = index >= (directories.size - 2).coerceAtLeast(0)
                                                                                                if (nearBottom) {
                                                                                                    runCatching { gcBivr.bringIntoView() }
                                                                                                    runCatching { childBivr.bringIntoView() }
                                                                                                    runCatching { rowBivr.bringIntoView() }
                                                                                                }
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        // 预检测二级目录是否还有下级：仅检测为 true 才显示下拉三角
                                                        LaunchedEffect(dir.path, children.size) {
                                                            if (children.isNotEmpty()) {
                                                                val probeSemaphore = Semaphore(3)
                                                                children.forEach { child ->
                                                                    if (hasChildrenMap[child.path] == null && childrenLoading[child.path] != true) {
                                                                        launch {
                                                                            probeSemaphore.withPermit {
                                                                                val hasChild = runCatching {
                                                                                    makeClient(accessToken)
                                                                                        .listFiles(child.path)
                                                                                        .getOrNull()
                                                                                        .orEmpty()
                                                                                        .any { it.isDirectory }
                                                                                }.getOrDefault(false)
                                                                                hasChildrenMap[child.path] = hasChild
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // 展开/加载完成后，尽量把整块内容滚入可视区（修复下拉展开显示不全）
                                    LaunchedEffect(expanded, isLoadingChildren, children.size) {
                                        if (expanded && !isLoadingChildren) {
                                            // 轻微延迟，等 Compose 完成测量再滚动
                                            delay(16)
                                            runCatching { rowBivr.bringIntoView() }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val path = selectedPath
                                if (path == null) {
                                    Toast.makeText(context, "请选择好需要添加的目录", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                onSuccess(
                                    GuangyaLoginResult(
                                        accessToken = accessToken,
                                        refreshToken = refreshToken,
                                        accessExpireAt = accessExpireAt,
                                        guangyaLoginMode = if (loginTab == GuangyaLoginTab.QR) {
                                            ResourceLibrary.GuangyaLoginMode.QR
                                        } else {
                                            ResourceLibrary.GuangyaLoginMode.SMS
                                        },
                                        guangyaDeviceId = deviceId,
                                        selectedPaths = listOf(path)
                                    )
                                )
                            },
                            scale = ButtonDefaults.scale(
                                scale = 1.0f,
                                focusedScale = 1.02f
                            ),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF2E2E2E),
                                contentColor = TextPrimary,
                                focusedContainerColor = PrimaryYellow,
                                focusedContentColor = Color.Black,
                                pressedContainerColor = PrimaryYellow,
                                pressedContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .align(Alignment.End)
                                .width(120.dp)
                        ) {
                            Text(
                                text = "确认",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Unspecified)
                            )
                        }

                        error?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(step) {
        if (step == 2) {
            delay(120)
            if (directories.isNotEmpty()) {
                runCatching { firstDirCheckFocusRequester.requestFocus() }
                    .onFailure { runCatching { step2CloseFocusRequester.requestFocus() } }
            } else {
                step2CloseFocusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(step, directories) {
        if (step == 2 && directories.isNotEmpty()) {
            delay(80)
            runCatching { firstDirCheckFocusRequester.requestFocus() }
                .onFailure { runCatching { step2CloseFocusRequester.requestFocus() } }
        }
    }

    LaunchedEffect(step, loginTab) {
        if (step != 1 || loginTab != GuangyaLoginTab.QR) return@LaunchedEffect
        if (verificationUrl.isBlank() || deviceCode.isBlank()) {
            loading = true
            val codeRes = requestDeviceCode()
            loading = false
            if (codeRes.isFailure) {
                val ex = codeRes.exceptionOrNull()
                val cls = ex?.javaClass?.simpleName ?: "Error"
                error = "生成二维码失败：$cls: ${ex?.message ?: "未知错误"}"
                return@LaunchedEffect
            }
        }

        polling = true
        while (step == 1 && loginTab == GuangyaLoginTab.QR && deviceCode.isNotBlank()) {
            val tokenRes = pollTokenOnce()
            if (tokenRes.isFailure) {
                polling = false
                val ex = tokenRes.exceptionOrNull()
                val cls = ex?.javaClass?.simpleName ?: "Error"
                error = "扫码确认失败：$cls: ${ex?.message ?: "未知错误"}"
                break
            }
            val tokenPayload = tokenRes.getOrNull()
            if (tokenPayload != null) {
                accessToken = tokenPayload.bearerAccessToken
                refreshToken = tokenPayload.refreshToken
                accessExpireAt = tokenPayload.expiresAt
                step = 2
                polling = false
                loadDirectories("/")
                break
            }
            delay(pollIntervalSec * 1000)
        }
    }

    LaunchedEffect(step, loginTab) {
        if (step != 1) return@LaunchedEffect
        delay(80)
        when (loginTab) {
            GuangyaLoginTab.SMS -> runCatching { smsTabFocusRequester.requestFocus() }
            GuangyaLoginTab.QR -> runCatching { qrTabFocusRequester.requestFocus() }
        }
    }
}

