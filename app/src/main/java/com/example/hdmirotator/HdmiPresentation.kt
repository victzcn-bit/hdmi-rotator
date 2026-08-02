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
    private val mediaProjection: MediaProjection,
    private var currentAngle: Float = 90f
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
            setBackgroundColor(0xFF000000.toInt())
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

        applyTransform(currentAngle)
    }

    fun setRotation(angle: Float) {
        currentAngle = angle
        if (::textureView.isInitialized && textureView.isAvailable) {
            applyTransform(currentAngle)
        }
    }

    private fun applyTransform(degrees: Float) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth == 0 || viewHeight == 0) return

        val metrics = context.resources.displayMetrics
        val phoneWidth = metrics.widthPixels
        val phoneHeight = metrics.heightPixels

        val matrix = Matrix()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        if (degrees == 90f || degrees == 270f) {
            matrix.postRotate(degrees, centerX, centerY)

            val rotatedWidth = phoneHeight.toFloat()
            val rotatedHeight = phoneWidth.toFloat()

            val scaleX = viewWidth.toFloat() / rotatedWidth
            val scaleY = viewHeight.toFloat() / rotatedHeight
            val scale = minOf(scaleX, scaleY)

            matrix.postScale(
                scale * (rotatedWidth / viewWidth),
                scale * (rotatedHeight / viewHeight),
                centerX,
                centerY
            )
        } else {
            // 0度（原始方向）：居中适应缩放
            val scaleX = viewWidth.toFloat() / phoneWidth
            val scaleY = viewHeight.toFloat() / phoneHeight
            val scale = minOf(scaleX, scaleY)

            matrix.postScale(
                scale,
                scale,
                centerX,
                centerY
            )
        }

        textureView.setTransform(matrix)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        applyTransform(currentAngle)
    }

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
