package com.lemon.yingshi.tv.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lemon.yingshi.tv.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.lemon.yingshi.tv.data.preferences.BootPreferences

/**
 * 开机启动接收器
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_TEST_BOOT = "com.lemon.yingshi.tv.TEST_BOOT"
    }

    @Inject
    lateinit var bootPreferences: BootPreferences

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到广播: ${intent.action}")
        
        // 支持多种开机广播 + 测试广播
        val isBootAction = intent.action in listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT",
            ACTION_TEST_BOOT  // 测试用
        )
        
        if (isBootAction) {
            Log.d(TAG, "是开机广播，检查设置...")
            
            // 使用 goAsync() 延长 BroadcastReceiver 的生命周期
            val pendingResult = goAsync()
            
            // 在协程中检查设置
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bootStartup = bootPreferences.bootStartup.first()
                    Log.d(TAG, "开机启动设置: $bootStartup")
                    
                    if (bootStartup) {
                        Log.d(TAG, "启动 MainActivity...")
                        // 启动APP
                        val startIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            // 添加启动动画标志
                            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        context.startActivity(startIntent)
                        Log.d(TAG, "MainActivity 启动成功")
                    } else {
                        Log.d(TAG, "开机启动未开启，跳过")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "开机启动失败", e)
                } finally {
                    // 必须调用 finish() 来释放 BroadcastReceiver
                    pendingResult.finish()
                }
            }
        }
    }
}
