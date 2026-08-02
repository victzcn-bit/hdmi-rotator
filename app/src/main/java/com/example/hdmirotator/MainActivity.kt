package com.example.hdmirotator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class MainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var hdmiPresentation: HdmiPresentation? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                // 收到授权时前台服务已在运行，此时获取 MediaProjection 100% 安全
                mediaProjection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
                startPresentationIfReady()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "屏幕抓取初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "需要屏幕录制权限才能将画面输出至外屏", Toast.LENGTH_SHORT).show()
            stopService(Intent(this, MediaProjectionService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val switchRotate = findViewById<SwitchCompat>(R.id.switchRotate)
        switchRotate.setOnCheckedChangeListener { _, isChecked ->
            hdmiPresentation?.setRotation(if (isChecked) 90f else 0f)
        }

        displayManager.registerDisplayListener(displayListener, null)

        // 检查并请求“悬浮窗/显示在其他应用上层”权限，防止后台弹出外屏时崩溃
        checkOverlayPermissionAndStart()
    }

    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予'显示在其他应用上层'权限，确保外屏能正常显示", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            requestCapture()
        }
    }

    private fun requestCapture() {
        // 【关键点】在弹窗请求录屏权限前，先启动 MediaProjection 前台服务！
        val serviceIntent = Intent(this, MediaProjectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 启动录屏申请弹窗
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            startPresentationIfReady()
        }

        override fun onDisplayRemoved(displayId: Int) {
            hdmiPresentation?.dismiss()
            hdmiPresentation = null
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    private fun startPresentationIfReady() {
        val proj = mediaProjection ?: return

        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val externalDisplay = displays[0]
            if (hdmiPresentation == null) {
                val switchRotate = findViewById<SwitchCompat>(R.id.switchRotate)
                val initialAngle = if (switchRotate.isChecked) 90f else 0f

                hdmiPresentation = HdmiPresentation(this, externalDisplay, proj, initialAngle)
                hdmiPresentation?.show()
            }
        } else {
            Toast.makeText(this, "未检测到外接 HDMI 监视器，请连接外屏", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        hdmiPresentation?.dismiss()
        mediaProjection?.stop()
        stopService(Intent(this, MediaProjectionService::class.java))
    }
}
