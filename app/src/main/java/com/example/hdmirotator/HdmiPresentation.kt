package com.example.hdmirotator

import android.app.Presentation
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout

class HdmiPresentation(
    outerContext: Context,
    display: Display,
    private val mediaProjection: MediaProjection
) : Presentation(outerContext, display), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private var virtualDisplay: VirtualDisplay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt()) // 纯黑背景
        }

        textureView = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            surfaceTextureListener = this@HdmiPresentation
        }

        root.addView(textureView)
        setContentView(root)
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val metrics = context.resources.displayMetrics
        val phoneWidth = metrics.widthPixels
        val phoneHeight = metrics.heightPixels
        val density = metrics.densityDpi

        // 1. 创建虚拟显示源，抓取手机屏幕
        val surface = Surface(surfaceTexture)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "HdmiRotatorDisplay",
            phoneWidth,
            phoneHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        // 2. 旋转 90 度并适应外接监视器比例
        apply90DegreeRotation(width, height, phoneWidth, phoneHeight)
    }

    private fun apply90DegreeRotation(viewWidth: Int, viewHeight: Int, phoneWidth: Int, phoneHeight: Int) {
        val matrix = Matrix()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // 围绕中心旋转 90 度
        matrix.postRotate(90f, centerX, centerY)

        // 旋转后宽高互换
        val rotatedWidth = phoneHeight.toFloat()
        val rotatedHeight = phoneWidth.toFloat()

        // 自动计算缩放比，保持比例并适应外屏
        val scaleX = viewWidth.toFloat() / rotatedWidth
        val scaleY = viewHeight.toFloat() / rotatedHeight
        val scale = minOf(scaleX, scaleY)

        matrix.postScale(
            scale * (rotatedWidth / viewWidth),
            scale * (rotatedHeight / viewHeight),
            centerX,
            centerY
        )

        textureView.setTransform(matrix)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        virtualDisplay?.release()
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    override fun onStop() {
        super.onStop()
        virtualDisplay?.release()
    }
}
