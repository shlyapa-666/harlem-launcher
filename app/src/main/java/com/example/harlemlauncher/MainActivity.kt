
package com.example.harlemlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Random
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {

    private lateinit var root: FrameLayout
    private lateinit var grid: GridView
    private lateinit var btn: Button
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private var apps: List<ResolveInfo> = emptyList()
    private var player: MediaPlayer? = null
    private var running = false
    private var startTime = 0L
    private var soloIndex = 0
    private var lastShake = 0L

    // Timings (seconds) like in the original meme: 0-15.5 one dancer, then everybody
    private val dropAt = 15.5f
    private val endAt = 30f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadApps()

        root = FrameLayout(this)
        grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(12)
            setPadding(dp(8), dp(48), dp(8), dp(80))
            clipToPadding = false
            clipChildren = false
            adapter = AppAdapter()
            setOnItemClickListener { _, _, pos, _ ->
                if (!running) {
                    val ai = apps[pos].activityInfo
                    packageManager.getLaunchIntentForPackage(ai.packageName)?.let { startActivity(it) }
                }
            }
        }
        btn = Button(this).apply {
            text = "HARLEM SHAKE"
            setOnClickListener { if (running) stopShake() else startShake() }
        }
        root.addView(grid, FrameLayout.LayoutParams(-1, -1))
        root.addView(btn, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(24)
        })
        setContentView(root)
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Harlem Shake ----------

    private fun startShake() {
        running = true
        btn.text = "STOP"
        // put your own track into app/src/main/res/raw/harlem_shake.mp3
        val id = resources.getIdentifier("harlem_shake", "raw", packageName)
        if (id != 0) {
            player = MediaPlayer.create(this, id)?.also { it.start() }
        }
        startTime = SystemClock.uptimeMillis()
        soloIndex = rnd.nextInt(maxOf(1, grid.childCount))
        handler.post(tick)
    }

    private fun stopShake() {
        running = false
        handler.removeCallbacks(tick)
        player?.release(); player = null
        btn.text = "HARLEM SHAKE"
        root.setBackgroundColor(Color.TRANSPARENT)
        for (i in 0 until grid.childCount) resetView(grid.getChildAt(i))
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val t = player?.let { it.currentPosition / 1000f }
                ?: ((SystemClock.uptimeMillis() - startTime) / 1000f)
            val n = grid.childCount
            if (n > 0) {
                if (t < dropAt) {
                    for (i in 0 until n) if (i != soloIndex) resetView(grid.getChildAt(i))
                    shake(grid.getChildAt(soloIndex.coerceIn(0, n - 1)), 1f)
                } else {
                    for (i in 0 until n) shake(grid.getChildAt(i), 1.6f)
                    root.setBackgroundColor(
                        Color.HSVToColor(120, floatArrayOf(rnd.nextInt(360).toFloat(), 1f, 1f))
                    )
                }
            }
            if (t > endAt) stopShake() else handler.postDelayed(this, 140)
        }
    }

    private fun shake(v: View, k: Float) {
        val s = 1f + rnd.nextFloat() * 0.35f * k
        v.animate()
            .rotation((rnd.nextFloat() - 0.5f) * 50f * k)
            .translationX((rnd.nextFloat() - 0.5f) * dp(24) * k)
            .translationY((rnd.nextFloat() - 0.5f) * dp(24) * k)
            .scaleX(s).scaleY(s)
            .setDuration(120).start()
    }

    private fun resetView(v: View) {
        v.animate().cancel()
        v.rotation = 0f; v.translationX = 0f; v.translationY = 0f
        v.scaleX = 1f; v.scaleY = 1f
    }

    // ---------- Shake the phone to start ----------

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
        val now = SystemClock.uptimeMillis()
        if (g > 2.6f && now - lastShake > 1500 && !running) {
            lastShake = now
            startShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStop() {
        super.onStop()
        if (running) stopShake()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* launcher: do nothing */ }

    // ---------- Adapter ----------

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val ri = apps[p]
            val cell = (convert as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(TextView(context).apply {
                    setTextColor(Color.WHITE); textSize = 11f
                    maxLines = 1; gravity = Gravity.CENTER
                    setShadowLayer(3f, 0f, 0f, Color.BLACK)
                })
            }
            resetView(cell)
            (cell.getChildAt(0) as ImageView).setImageDrawable(ri.loadIcon(packageManager))
            (cell.getChildAt(1) as TextView).text = ri.loadLabel(packageManager)
            return cell
        }
    }
}
}
