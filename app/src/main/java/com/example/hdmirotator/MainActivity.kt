package com.example.hdmirotator

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Display
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var hdmiPresentation: HdmiPresentation? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            mediaProjection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
            startPresentationIfReady()
        } else {
            Toast.makeText(this, "需要屏幕录制权限才能将画面输出至外屏", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // 监听 HDMI / 外接显示屏连接状态
        displayManager.registerDisplayListener(displayListener, null)

        // 启动时请求录屏抓取权限
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

        // 查找外接 HDMI 显示屏
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val externalDisplay = displays[0]
            if (hdmiPresentation == null) {
                hdmiPresentation = HdmiPresentation(this, externalDisplay, proj)
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
    }
}
