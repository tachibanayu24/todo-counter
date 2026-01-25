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
        private var currentBgColor = 0xA02d9c4a.toInt()
        private var originalBgColor = 0xA02d9c4a.toInt()  // 成功アニメーション後に戻る色
        private var hokoriRotation = 0f
        private var checkmarkProgress = 0f  // 0 = 非表示, 1 = 完全表示
        private var isSuccessAnimating = false

        // アニメーター
        private var currentAnimator: ValueAnimator? = null
        private var idleAnimator: ValueAnimator? = null
        private var colorAnimator: ValueAnimator? = null
        private var rotationAnimator: ValueAnimator? = null
        private var checkmarkAnimator: ValueAnimator? = null

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

        private val checkmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
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
            val oldTotal = this.count?.total
            val newTotal = count?.total
            this.count = count

            // 色の計算
            val newColor = when {
                (newTotal ?: 0) == 0 -> 0xA02d9c4a.toInt()  // 緑
                (newTotal ?: 0) <= 3 -> 0xA0f59f00.toInt()  // オレンジ
                else -> 0xA0e03131.toInt()                   // 赤
            }

            // 元の色を保存（成功アニメーション後に戻る色）
            originalBgColor = newColor

            // カウントが変わった場合、パルスアニメーション & 色遷移
            // ただし成功アニメーション中は色を変えない（後で戻すため）
            if (oldTotal != null && newTotal != null && oldTotal != newTotal) {
                animateCountChange()
                if (!isSuccessAnimating) {
                    animateColorChange(currentBgColor, newColor)
                }
            } else if (!isSuccessAnimating) {
                currentBgColor = newColor
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

        // 色遷移アニメーション
        private fun animateColorChange(fromColor: Int, toColor: Int) {
            colorAnimator?.cancel()

            colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
                duration = 200
                addUpdateListener { animator ->
                    currentBgColor = animator.animatedValue as Int
                    invalidate()
                }
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

        // 読み込み成功アニメーション（hokori一回転 + 緑 + チェックマーク）
        fun animateSuccess() {
            if (isSuccessAnimating) return
            isSuccessAnimating = true

            stopIdleAnimation()
            currentAnimator?.cancel()
            rotationAnimator?.cancel()
            checkmarkAnimator?.cancel()
            colorAnimator?.cancel()

            // 緑色に変更
            val successColor = 0xE02d9c4a.toInt()
            colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentBgColor, successColor).apply {
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

            // パルス効果（少し大きくなって戻る）
            currentAnimator = ValueAnimator.ofFloat(1f, 1.3f, 1f).apply {
                duration = 400
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { animator ->
                    animScale = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }

            // チェックマーク表示（遅延開始）
            checkmarkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                startDelay = 200
                interpolator = OvershootInterpolator(2f)
                addUpdateListener { animator ->
                    checkmarkProgress = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // チェックマークを一定時間後に消す
                        postDelayed({
                            hideCheckmark()
                        }, 800)
                    }
                })
                start()
            }
        }

        // チェックマークを消すアニメーション & 元の色に戻す
        private fun hideCheckmark() {
            checkmarkAnimator?.cancel()
            colorAnimator?.cancel()

            // チェックマークをフェードアウト
            checkmarkAnimator = ValueAnimator.ofFloat(checkmarkProgress, 0f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    checkmarkProgress = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        hokoriRotation = 0f
                    }
                })
                start()
            }

            // 元の色に戻す
            colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentBgColor, originalBgColor).apply {
                duration = 300
                addUpdateListener { animator ->
                    currentBgColor = animator.animatedValue as Int
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        isSuccessAnimating = false
                        startIdleAnimation()
                    }
                })
                start()
            }
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

            // スケールを適用
            canvas.save()
            canvas.scale(animScale, animScale, cx, cy)

            // 背景色（アニメーション対応）
            bgPaint.color = currentBgColor

            // 円を描画
            canvas.drawCircle(cx, cy, radius - strokeWidth, bgPaint)

            // hokori画像をうっすら描画（円形にクリップ + 回転）
            hokori?.let {
                canvas.save()
                val clipPath = Path().apply {
                    addCircle(cx, cy, radius - strokeWidth, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)

                // 回転を適用
                canvas.rotate(hokoriRotation, cx, cy)

                val left = cx - it.width / 2f
                val top = cy - it.height / 2f
                canvas.drawBitmap(it, left, top, imagePaint)
                canvas.restore()
            }

            canvas.drawCircle(cx, cy, radius - strokeWidth, strokePaint)

            // チェックマーク表示中は数字を隠す
            if (checkmarkProgress > 0f) {
                // チェックマークを描画
                drawCheckmark(canvas, cx, cy, checkmarkProgress)
            } else {
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

            canvas.restore()
        }

        // チェックマークを描画
        private fun drawCheckmark(canvas: Canvas, cx: Float, cy: Float, progress: Float) {
            val checkSize = radius * 0.5f

            // チェックマークのパス（左下から中央下、中央下から右上）
            val startX = cx - checkSize * 0.5f
            val startY = cy
            val midX = cx - checkSize * 0.1f
            val midY = cy + checkSize * 0.35f
            val endX = cx + checkSize * 0.5f
            val endY = cy - checkSize * 0.35f

            // プログレスに応じて描画
            checkmarkPaint.alpha = (255 * progress).toInt()

            val path = Path()
            if (progress <= 0.5f) {
                // 最初の半分：左下から中央下
                val t = progress * 2
                val currentX = startX + (midX - startX) * t
                val currentY = startY + (midY - startY) * t
                path.moveTo(startX, startY)
                path.lineTo(currentX, currentY)
            } else {
                // 後半：中央下から右上
                val t = (progress - 0.5f) * 2
                val currentX = midX + (endX - midX) * t
                val currentY = midY + (endY - midY) * t
                path.moveTo(startX, startY)
                path.lineTo(midX, midY)
                path.lineTo(currentX, currentY)
            }

            canvas.drawPath(path, checkmarkPaint)
        }
    }
}
