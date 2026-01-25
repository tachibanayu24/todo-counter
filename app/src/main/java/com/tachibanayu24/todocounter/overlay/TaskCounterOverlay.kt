package com.tachibanayu24.todocounter.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.tachibanayu24.todocounter.R
import com.tachibanayu24.todocounter.api.TaskCount
import kotlin.math.abs

class TaskCounterOverlay(
    private val context: Context,
    private val onTapToRefresh: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: CounterView? = null
    private var isShowing = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 50
        y = 200
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(count: TaskCount?) {
        if (overlayView == null) {
            overlayView = createOverlayView()
        }

        updateCount(count)

        if (!isShowing) {
            windowManager.addView(overlayView, layoutParams)
            isShowing = true
        }
    }

    fun updateCount(count: TaskCount?) {
        overlayView?.setCount(count)
    }

    fun hide() {
        if (isShowing && overlayView != null) {
            windowManager.removeView(overlayView)
            isShowing = false
        }
    }

    fun isShowing(): Boolean = isShowing

    fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun openGoogleTasks() {
        try {
            // Google Tasksアプリを開く
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.tasks")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            } else {
                // アプリがない場合はPlay Storeを開く
                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=com.google.android.apps.tasks")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playStoreIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView(): CounterView {
        val params = layoutParams
        val wm = windowManager

        return CounterView(context).apply {
            // ドラッグ移動 & シングルタップで更新 & 長押しでGoogle Tasks起動
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false
            var isLongPress = false
            val longPressTimeout = 500L
            var longPressRunnable: Runnable? = null

            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongPress = false

                        // 長押し検出
                        longPressRunnable = Runnable {
                            if (!isDragging) {
                                isLongPress = true
                                openGoogleTasks()
                            }
                        }
                        view.postDelayed(longPressRunnable, longPressTimeout)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)
                        if (dx > 10 || dy > 10) {
                            isDragging = true
                            longPressRunnable?.let { view.removeCallbacks(it) }
                        }
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        wm.updateViewLayout(view, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        if (!isDragging && !isLongPress) {
                            // シングルタップ → 更新
                            onTapToRefresh()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private class CounterView(context: Context) : View(context) {
        private var count: TaskCount? = null
        private val density = context.resources.displayMetrics.density

        private val size = (56 * density).toInt()
        private val radius = size / 2f
        private val shadowRadius = 8 * density
        private val strokeWidth = 3 * density

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            setShadowLayer(shadowRadius, 0f, 2 * density, 0x40000000)
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = this@CounterView.strokeWidth
            color = 0x30FFFFFF
        }

        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 120 // うっすら表示（0-255）
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isFakeBoldText = true
        }

        private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val hokori: Bitmap? = try {
            val original = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
            Bitmap.createScaledBitmap(original, size, size, true)
        } catch (e: Exception) {
            null
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null) // シャドウ用
        }

        fun setCount(count: TaskCount?) {
            this.count = count
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val totalSize = size + (shadowRadius * 2).toInt()
            setMeasuredDimension(totalSize, totalSize)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val total = count?.total ?: 0
            val cx = width / 2f
            val cy = height / 2f

            // 背景色（薄め）
            bgPaint.color = when {
                total == 0 -> 0xA02d9c4a.toInt()  // 緑
                total <= 3 -> 0xA0f59f00.toInt()  // オレンジ
                else -> 0xA0e03131.toInt()         // 赤
            }

            // 円を描画
            canvas.drawCircle(cx, cy, radius - strokeWidth, bgPaint)

            // hokori画像をうっすら描画（円形にクリップ）
            hokori?.let {
                canvas.save()
                val clipPath = Path().apply {
                    addCircle(cx, cy, radius - strokeWidth, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
                val left = cx - it.width / 2f
                val top = cy - it.height / 2f
                canvas.drawBitmap(it, left, top, imagePaint)
                canvas.restore()
            }

            canvas.drawCircle(cx, cy, radius - strokeWidth, strokePaint)

            // 数字
            val text = if (total > 99) "99+" else total.toString()
            val textSize = when {
                text.length == 1 -> 24 * density
                text.length == 2 -> 20 * density
                else -> 14 * density
            }
            textPaint.textSize = textSize
            textStrokePaint.textSize = textSize

            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(text, cx, textY, textStrokePaint) // 太い輪郭
            canvas.drawText(text, cx, textY, textPaint)        // 塗りつぶし
        }
    }
}
