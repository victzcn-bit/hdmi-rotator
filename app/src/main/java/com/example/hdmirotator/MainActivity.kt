package com.example.hdmirotator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Display
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutionException

class MainActivity : AppCompatActivity() {

    private lateinit var displayManager: DisplayManager
    private lateinit var previewViewLocal: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnToggleEmulator: Button
    private lateinit var btnRotate: Button
    private lateinit var btnScaleMode: Button
    private lateinit var containerVirtualDisplay: FrameLayout
    private lateinit var surfaceVirtualDisplay: SurfaceView

    private var hdmiPresentation: HdmiPresentation? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var isEmulationActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        bindViews()
        setupListeners()

        if (allPermissionsGranted()) {
            initCameraX()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        displayManager.registerDisplayListener(displayListener, mainHandler)
    }

    private fun bindViews() {
        previewViewLocal = findViewById(R.id.previewViewLocal)
        tvStatus = findViewById(R.id.tvStatus)
        btnToggleEmulator = findViewById(R.id.btnToggleEmulator)
        btnRotate = findViewById(R.id.btnRotate)
        btnScaleMode = findViewById(R.id.btnScaleMode)
        containerVirtualDisplay = findViewById(R.id.containerVirtualDisplay)
        surfaceVirtualDisplay = findViewById(R.id.surfaceVirtualDisplay)
    }

    private fun setupListeners() {
        btnToggleEmulator.setOnClickListener {
            isEmulationActive = !isEmulationActive
            if (isEmulationActive) {
                startEmulation()
            } else {
                stopEmulation()
            }
        }

        btnRotate.setOnClickListener {
            hdmiPresentation?.let {
                val angle = it.cycleRotation()
                btnRotate.text = "画面旋转: ${angle.toInt()}°"
            }
        }

        btnScaleMode.setOnClickListener {
            hdmiPresentation?.let {
                val mode = it.toggleScaleMode()
                btnScaleMode.text = "填充模式: $mode"
            }
        }
    }

    private fun startEmulation() {
        btnToggleEmulator.text = "关闭仿真验证"
        btnToggleEmulator.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        containerVirtualDisplay.visibility = View.VISIBLE
        
        // 核心：创建一个 1920x1080 的虚拟沙盒显示器
        val surface = surfaceVirtualDisplay.holder.surface
        virtualDisplay = displayManager.createVirtualDisplay(
            "Emulated-Director-Monitor",
            1920, 1080, 320,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        )
    }

    private fun stopEmulation() {
        btnToggleEmulator.text = "开启零硬件仿真验证"
        btnToggleEmulator.setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
        containerVirtualDisplay.visibility = View.GONE
        virtualDisplay?.release()
        virtualDisplay = null
        detachExternalDisplay()
    }

    private fun initCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                scanAndAttachExternalDisplay()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 相机管线热切换：安全地在手机屏幕和 HDMI 之间切换视频流
     */
    private fun routeCameraStream() {
        val provider = cameraProvider ?: return
        
        // 必须先全部解绑，防止 Surface 抢占冲突
        provider.unbindAll()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        // 强制 1080P，保证 HDMI 输出的清晰度并兼容绝大多数监视器
        val preview = Preview.Builder()
            .setTargetResolution(Size(1080, 1920))
            .build()

        if (hdmiPresentation != null && hdmiPresentation!!.isShowing) {
            preview.setSurfaceProvider(hdmiPresentation!!.previewViewHdmi.surfaceProvider)
        } else {
            preview.setSurfaceProvider(previewViewLocal.surfaceProvider)
        }

        try {
            provider.bindToLifecycle(this, cameraSelector, preview)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 暴力硬件扫描：兼容所有类原生与深度定制 Android 系统的外接显示器识别
     */
    private fun scanAndAttachExternalDisplay() {
        var targetDisplay: Display? = null
        
        // 1. 优先寻找标准 Presentation 屏
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (presentationDisplays.isNotEmpty()) {
            targetDisplay = presentationDisplays[0]
        } else {
            // 2. 暴力寻找任何非主屏的设备 (防止某些扩展坞未打 Presentation 标签)
            val allDisplays = displayManager.displays
            for (d in allDisplays) {
                if (d.displayId != Display.DEFAULT_DISPLAY) {
                    targetDisplay = d
                    break
                }
            }
        }

        if (targetDisplay != null) {
            attachPresentation(targetDisplay)
        } else {
            detachExternalDisplay()
        }
    }

    private fun attachPresentation(display: Display) {
        if (hdmiPresentation?.display == display && hdmiPresentation?.isShowing == true) return
        detachExternalDisplay()

        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                try {
                    hdmiPresentation = HdmiPresentation(this, display).apply {
                        setOnDismissListener { hdmiPresentation = null }
                        show()
                    }
                    tvStatus.text = if (isEmulationActive) "状态: 监视器 1:1 仿真运行中" else "状态: HDMI 监视器推流中"
                    tvStatus.setTextColor(android.graphics.Color.parseColor("#00FF00"))
                    routeCameraStream()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, 500) // 500ms 握手缓冲，确保扩展坞硬件通道建立
    }

    private fun detachExternalDisplay() {
        hdmiPresentation?.dismiss()
        hdmiPresentation = null
        tvStatus.text = "状态: 硬件监听就绪 (等待投屏线)"
        tvStatus.setTextColor(android.graphics.Color.WHITE)
        routeCameraStream()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) { scanAndAttachExternalDisplay() }
        override fun onDisplayRemoved(displayId: Int) { scanAndAttachExternalDisplay() }
        override fun onDisplayChanged(displayId: Int) { scanAndAttachExternalDisplay() }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        displayManager.unregisterDisplayListener(displayListener)
        detachExternalDisplay()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            initCameraX()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 111
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
