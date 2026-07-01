package ngo.xnet.zeeksworld

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import de.fabmax.kool.createDefaultKoolContext
import de.fabmax.kool.platform.KoolContextAndroid

class MainActivity : Activity() {
    lateinit var koolCtx: KoolContextAndroid
    val game = ZekesGame()
    private val slotViews = mutableListOf<Button>()

    private val slotColors = intArrayOf(
        Color.rgb(77, 204, 51),   // grass
        Color.rgb(102, 64, 26),   // dirt
        Color.rgb(128, 128, 128), // stone
        Color.rgb(140, 89, 38),   // wood
        Color.rgb(230, 217, 153), // sand
        Color.rgb(153, 51, 204)   // amethyst
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        koolCtx = createDefaultKoolContext()
        game.createScenes(koolCtx).forEach { koolCtx.scenes += it }

        val root = FrameLayout(this)
        root.addView(koolCtx.surfaceView)

        val btnSize = 120
        val margin = 20

        // D-pad (bottom-left)
        addButton(root, "▲", margin + btnSize, margin + btnSize * 2, btnSize) { game.btnForward = it }
        addButton(root, "▼", margin + btnSize, margin, btnSize) { game.btnBack = it }
        addButton(root, "◀", margin, margin + btnSize, btnSize) { game.btnLeft = it }
        addButton(root, "▶", margin + btnSize * 2, margin + btnSize, btnSize) { game.btnRight = it }

        // Action buttons (bottom-right)
        addButton(root, "⬆", margin, margin + btnSize * 2, btnSize, rightAlign = true) { game.btnJump = it }
        addButton(root, "✚", margin + btnSize, margin + btnSize, btnSize, rightAlign = true) { if (it) game.btnPlace = true }
        addButton(root, "✖", margin, margin + btnSize, btnSize, rightAlign = true) { if (it) game.btnDestroy = true }

        // Inventory bar (bottom-center)
        val invBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in slotColors.indices) {
            val slot = Button(this).apply {
                val bg = GradientDrawable().apply {
                    setColor(slotColors[i])
                    cornerRadius = 8f
                    setStroke(if (i == 0) 6 else 2, Color.WHITE)
                }
                background = bg
                alpha = 0.8f
                setOnClickListener {
                    game.selectedSlot = i
                    updateSlotSelection()
                }
            }
            val lp = LinearLayout.LayoutParams(100, 100).apply { setMargins(6, 0, 6, 0) }
            invBar.addView(slot, lp)
            slotViews.add(slot)
        }
        val invLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = margin + btnSize * 3 + 20
        }
        root.addView(invBar, invLp)

        // Speech bubble
        val speechBubble = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            val bg = GradientDrawable().apply {
                setColor(Color.argb(180, 0, 0, 0))
                cornerRadius = 16f
            }
            background = bg
            setPadding(32, 16, 32, 16)
            visibility = android.view.View.GONE
        }
        val speechLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 100
        }
        root.addView(speechBubble, speechLp)

        val handler = Handler(Looper.getMainLooper())
        game.onSpeech = { text ->
            handler.post {
                speechBubble.text = text
                speechBubble.visibility = android.view.View.VISIBLE
                handler.removeCallbacksAndMessages("speech")
                handler.postDelayed({ speechBubble.visibility = android.view.View.GONE }, 5000)
            }
        }

        setContentView(root)
        koolCtx.run()
    }

    private fun updateSlotSelection() {
        for (i in slotViews.indices) {
            val bg = GradientDrawable().apply {
                setColor(slotColors[i])
                cornerRadius = 8f
                setStroke(if (i == game.selectedSlot) 6 else 2, Color.WHITE)
            }
            slotViews[i].background = bg
        }
    }

    private fun addButton(parent: FrameLayout, text: String, x: Int, y: Int, size: Int, rightAlign: Boolean = false, onState: (Boolean) -> Unit) {
        val btn = Button(this).apply {
            this.text = text
            textSize = 24f
            alpha = 0.6f
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { onState(true); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { onState(false); true }
                    else -> true
                }
            }
        }
        val lp = FrameLayout.LayoutParams(size, size)
        lp.gravity = Gravity.BOTTOM or (if (rightAlign) Gravity.END else Gravity.START)
        lp.leftMargin = if (!rightAlign) x else 0
        lp.rightMargin = if (rightAlign) x else 0
        lp.bottomMargin = y
        parent.addView(btn, lp)
    }

    override fun onResume() { super.onResume(); koolCtx.onResume() }
    override fun onPause() { super.onPause(); koolCtx.onPause() }
    override fun onDestroy() { koolCtx.onDestroy(); super.onDestroy() }
}
