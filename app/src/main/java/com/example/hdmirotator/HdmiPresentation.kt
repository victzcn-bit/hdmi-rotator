package com.example.hdmirotator

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.view.PreviewView

class HdmiPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    lateinit var previewViewHdmi: PreviewView
        private set

    var currentRotation = 90f
        private set

    var isCropMode = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 强行保持副屏常亮，突破系统休眠限制
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 【核心优化】：纯代码构建 UI，彻底杜绝 R.layout 找不到导致的低级崩溃
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        previewViewHdmi = PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        rootLayout.addView(previewViewHdmi)
        setContentView(rootLayout)

        // 【核心优化】：极其硬核的尺寸变动监听，无论系统怎么魔改多窗口，只要尺寸变了瞬间重算矩阵
        previewViewHdmi.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                applyTransformSafe()
            }
        }
    }

    fun cycleRotation(): Float {
        currentRotation = when (currentRotation) {
            90f -> 180f
            180f -> 270f
            270f -> 0f
            else -> 90f
        }
        applyTransformSafe()
        return currentRotation
    }

    fun toggleScaleMode(): String {
        isCropMode = !isCropMode
        applyTransformSafe()
        return if (isCropMode) "裁剪满屏" else "原始比例"
    }

    /**
     * 工业级图形安全变换矩阵 (绝对无崩溃、无 NaN)
     */
    private fun applyTransformSafe() {
        val parent = previewViewHdmi.parent as? ViewGroup ?: return
        val pW = parent.width.toFloat()
        val pH = parent.height.toFloat()

        // 拦截未完成测量的无效状态
        if (pW <= 0f || pH <= 0f) return

        previewViewHdmi.rotation = currentRotation

        if (currentRotation == 90f || currentRotation == 270f) {
            val aspect = pW / pH
            var scale = if (isCropMode) {
                if (aspect > 1.0f) aspect else 1.0f / aspect
            } else {
                1.0f
            }

            // 拦截极其极端的计算错误
            if (scale.isNaN() || scale.isInfinite() || scale <= 0f) scale = 1.0f

            previewViewHdmi.scaleX = scale
            previewViewHdmi.scaleY = scale
        } else {
            previewViewHdmi.scaleX = 1.0f
            previewViewHdmi.scaleY = 1.0f
        }
    }
}
