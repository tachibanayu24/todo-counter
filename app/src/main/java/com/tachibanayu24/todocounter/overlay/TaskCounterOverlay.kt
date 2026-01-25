package com.tachibanayu24.todocounter.overlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import androidx.core.content.res.ResourcesCompat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
            overlayView?.animateShow()
        }
    }

    fun updateCount(count: TaskCount?) {
        overlayView?.setCount(count)
    }

    // 読み込み成功アニメーション
    fun animateSuccess() {
        overlayView?.animateSuccess()
    }

    fun hide() {
        if (isShowing && overlayView != null) {
            overlayView?.animateHide {
                if (isShowing && overlayView != null) {
                    windowManager.removeView(overlayView)
                    isShowing = false
                }
            }
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
                val counterView = view as CounterView
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongPress = false

                        // タップダウンアニメーション
                        counterView.animateTapDown()

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
                            if (!isDragging) {
                                // ドラッグ開始
                                isDragging = true
                                counterView.animateDragStart()
                            }
                            longPressRunnable?.let { view.removeCallbacks(it) }
                        }
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        wm.updateViewLayout(view, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        if (isDragging) {
                            // ドラッグ終了アニメーション
                            counterView.animateDragEnd()
                        } else if (!isLongPress) {
                            // シングルタップ → 更新
                            counterView.animateTapUp()
                            onTapToRefresh()
                        } else {
                            counterView.animateTapUp()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { view.removeCallbacks(it) }
                        counterView.animateTapUp()
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

        // アニメーション用プロパティ
        private var animScale = 1f
        private var animAlpha = 1f
        private var hokoriRotation = 0f
        private var tadaProgress = 0f  // 🎉の表示進捗 (0 = 非表示, 1 = 完全表示)
        private var isSuccessAnimating = false

        // 色定義
        private val bgColor = 0xDDF5F5F5.toInt()           // 自然な白系背景
        private val successBgColor = 0xDD2d9c4a.toInt()    // 成功時の緑背景
        private var currentBgColor = bgColor
        private val supremeRed = 0xFFFF0000.toInt()        // 赤 #FF0000
        private val safeGreen = 0xFF51cf66.toInt()         // 安全な緑
        private var currentTextColor = Color.WHITE
        private var originalTextColor = Color.WHITE

        // アニメーター
        private var currentAnimator: ValueAnimator? = null
        private var idleAnimator: ValueAnimator? = null
        private var colorAnimator: ValueAnimator? = null
        private var rotationAnimator: ValueAnimator? = null
        private var tadaAnimator: ValueAnimator? = null

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            setShadowLayer(shadowRadius, 0f, 2 * density, 0x40000000)
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = this@CounterView.strokeWidth
            color = 0x15FFFFFF  // より透過
        }

        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)  // 透過なし

        // Ubuntu Bold Italicフォント
        private val ubuntuBoldItalic: Typeface? = try {
            ResourcesCompat.getFont(context, R.font.ubuntu_bold_italic)
        } catch (e: Exception) {
            null
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = ubuntuBoldItalic ?: Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
            isFakeBoldText = true  // さらに太く
        }

        private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = ubuntuBoldItalic ?: Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
            style = Paint.Style.STROKE
            strokeWidth = 2 * density  // 程よい縁取り
            isFakeBoldText = true
        }

        private val hokori: Bitmap? = try {
            val original = BitmapFactory.decodeResource(context.resources, R.drawable.hokori)
            // アスペクト比を維持してリサイズ
            val maxSize = (size * 0.7f)
            val scale = minOf(maxSize / original.width, maxSize / original.height)
            val newWidth = (original.width * scale).toInt()
            val newHeight = (original.height * scale).toInt()
            Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
        } catch (e: Exception) {
            null
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null) // シャドウ用
        }

        fun setCount(count: TaskCount?) {
            val oldTotal = this.count?.total
            val newTotal = count?.total
            this.count = count

            // 数字の色を計算（タスク数で危機感を表現）
            val newTextColor = when {
                (newTotal ?: 0) == 0 -> safeGreen      // 0件: 緑（安全）
                (newTotal ?: 0) <= 3 -> Color.WHITE   // 1-3件: 白
                else -> supremeRed                     // 4件以上: Supreme赤（危険）
            }

            // 元の色を保存（成功アニメーション後に戻る色）
            originalTextColor = newTextColor

            // カウントが変わった場合、パルスアニメーション
            if (oldTotal != null && newTotal != null && oldTotal != newTotal) {
                animateCountChange()
                if (!isSuccessAnimating) {
                    currentTextColor = newTextColor
                }
            } else if (!isSuccessAnimating) {
                currentTextColor = newTextColor
            }

            // 背景色は固定（成功アニメーション中以外）
            if (!isSuccessAnimating) {
                currentBgColor = bgColor
            }

            invalidate()
        }

        // 表示アニメーション
        fun animateShow() {
            stopIdleAnimation()
            currentAnimator?.cancel()

            animScale = 0f
            animAlpha = 0f

            currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    animScale = value
                    animAlpha = value
                    alpha = animAlpha
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        startIdleAnimation()
                    }
                })
                start()
            }
        }

        // 非表示アニメーション
        fun animateHide(onEnd: () -> Unit) {
            stopIdleAnimation()
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    animScale = value
                    animAlpha = value
                    alpha = animAlpha
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onEnd()
                    }
                })
                start()
            }
        }

        // タップダウンアニメーション（大きめの押し込み効果）
        fun animateTapDown() {
            stopIdleAnimation()
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofFloat(animScale, 0.75f).apply {
                duration = 120
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        // タップアップアニメーション（大きめのバウンス戻り）
        fun animateTapUp() {
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofFloat(animScale, 1.25f, 1.0f).apply {
                duration = 200
                interpolator = OvershootInterpolator(2f)
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        startIdleAnimation()
                    }
                })
                start()
            }
        }

        // ドラッグ開始アニメーション
        fun animateDragStart() {
            stopIdleAnimation()
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofFloat(animScale, 1.1f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        // ドラッグ終了アニメーション（バウンス戻り）
        fun animateDragEnd() {
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofFloat(animScale, 1.0f).apply {
                duration = 150
                interpolator = OvershootInterpolator(2f)
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        startIdleAnimation()
                    }
                })
                start()
            }
        }

        // カウント変更パルスアニメーション
        private fun animateCountChange() {
            stopIdleAnimation()
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        startIdleAnimation()
                    }
                })
                start()
            }
        }

        // アイドル時パルスアニメーション（呼吸効果）
        private fun startIdleAnimation() {
            if (idleAnimator?.isRunning == true) return

            idleAnimator = ValueAnimator.ofFloat(1f, 1.05f).apply {
                duration = 2000
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stopIdleAnimation() {
            idleAnimator?.cancel()
            idleAnimator = null
        }

        // タスク減少時の成功アニメーション（hokori一回転 + 緑背景 + 🎉）
        fun animateSuccess() {
            if (isSuccessAnimating) return
            isSuccessAnimating = true

            stopIdleAnimation()
            currentAnimator?.cancel()
            rotationAnimator?.cancel()
            tadaAnimator?.cancel()
            colorAnimator?.cancel()

            // 緑背景に変更
            colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentBgColor, successBgColor).apply {
                duration = 300
                addUpdateListener { animator ->
                    currentBgColor = animator.animatedValue as Int
                    invalidate()
                }
                start()
            }

            // hokori一回転
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    hokoriRotation = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }

            // パルス効果
            currentAnimator = ValueAnimator.ofFloat(1f, 1.3f, 1f).apply {
                duration = 400
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }

            // 🎉表示（遅延開始）
            tadaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                startDelay = 200
                interpolator = OvershootInterpolator(2f)
                addUpdateListener { animator ->
                    tadaProgress = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        postDelayed({ hideTada() }, 800)
                    }
                })
                start()
            }
        }

        // 🎉を消すアニメーション & 元の背景に戻す
        private fun hideTada() {
            tadaAnimator?.cancel()
            colorAnimator?.cancel()

            // 🎉をフェードアウト
            tadaAnimator = ValueAnimator.ofFloat(tadaProgress, 0f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    tadaProgress = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        hokoriRotation = 0f
                    }
                })
                start()
            }

            // 元の背景に戻す
            colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentBgColor, bgColor).apply {
                duration = 300
                addUpdateListener { animator ->
                    currentBgColor = animator.animatedValue as Int
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        isSuccessAnimating = false
                        currentTextColor = originalTextColor
                        startIdleAnimation()
                    }
                })
                start()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val totalSize = size + (shadowRadius * 2).toInt()
            // 固定サイズを強制
            setMeasuredDimension(totalSize, totalSize)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val total = count?.total ?: 0
            val cx = width / 2f
            val cy = height / 2f

            // スケールを適用
            canvas.save()
            canvas.scale(animScale, animScale, cx, cy)

            // 背景色（アニメーション対応）
            bgPaint.color = currentBgColor

            // 円を描画
            canvas.drawCircle(cx, cy, radius - strokeWidth, bgPaint)

            // hokori画像を描画（クリップなし、はみ出しOK）
            hokori?.let {
                canvas.save()
                // 回転を適用
                canvas.rotate(hokoriRotation, cx, cy)

                val left = cx - it.width / 2f
                val top = cy - it.height / 2f
                canvas.drawBitmap(it, left, top, imagePaint)
                canvas.restore()
            }

            canvas.drawCircle(cx, cy, radius - strokeWidth, strokePaint)

            // 🎉表示中は数字を隠す
            if (tadaProgress > 0f) {
                // 🎉を描画
                drawTada(canvas, cx, cy, tadaProgress)
            } else {
                // 数字（色はタスク数に応じて変化）
                val text = if (total > 99) "99+" else total.toString()
                val textSize = when {
                    text.length == 1 -> 32 * density   // 大きく
                    text.length == 2 -> 28 * density   // 大きく
                    else -> 18 * density
                }
                textPaint.textSize = textSize
                textPaint.color = currentTextColor
                textPaint.alpha = 255
                textStrokePaint.textSize = textSize
                textStrokePaint.color = currentTextColor
                textStrokePaint.alpha = 255

                // 斜体の視覚補正（少し左にオフセット）
                val italicOffset = textSize * 0.08f
                val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2

                // レイヤーで一括透過（縁取りと塗りが同じ透過度に）
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), 120)
                canvas.drawText(text, cx - italicOffset, textY, textStrokePaint)  // 太い縁取り
                canvas.drawText(text, cx - italicOffset, textY, textPaint)         // 塗りつぶし
                canvas.restore()
            }

            canvas.restore()
        }

        // 🎉を描画
        private fun drawTada(canvas: Canvas, cx: Float, cy: Float, progress: Float) {
            val emojiSize = 24 * density
            textPaint.textSize = emojiSize
            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * progress).toInt()

            val scale = 0.5f + (progress * 0.5f)  // 0.5 → 1.0 にスケール
            canvas.save()
            canvas.scale(scale, scale, cx, cy)

            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText("🎉", cx, textY, textPaint)

            canvas.restore()
            textPaint.alpha = 255
        }
    }
}
