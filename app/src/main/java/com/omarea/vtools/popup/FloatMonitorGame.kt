package com.omarea.vtools.popup

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.TextView
import android.widget.Toast
import com.omarea.library.shell.*
import com.omarea.vtools.R
import java.util.*

class FloatMonitorGame(private val mContext: Context) {
    private var startMonitorTime = 0L
    private var cpuLoadUtils = CpuLoadUtils()
    private var CpuFrequencyUtil = CpuFrequencyUtils()

    /**
     * 显示弹出框
     * @param context
     */
    fun showPopupWindow() {
        if (show!!) {
            return
        }
        startMonitorTime = System.currentTimeMillis()
        if (batteryManager == null) {
            batteryManager = mContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(mContext)) {
            Toast.makeText(mContext, mContext.getString(R.string.permission_float), Toast.LENGTH_LONG).show()
            return
        }

        show = true
        mWindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        mView = setUpView(mContext)

        val params = LayoutParams()
        val monitorStorage = mContext.getSharedPreferences("float_monitor2_storage", Context.MODE_PRIVATE)

        // 类型
        params.type = LayoutParams.TYPE_SYSTEM_ALERT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//6.0+
            params.type = LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            params.type = LayoutParams.TYPE_SYSTEM_ALERT
        }
        params.format = PixelFormat.TRANSLUCENT

        params.width = LayoutParams.WRAP_CONTENT
        params.height = LayoutParams.WRAP_CONTENT

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = monitorStorage.getInt("x", 0)
        params.y = monitorStorage.getInt("y", 0)

        params.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_FULLSCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val navHeight = 0
        if (navHeight > 0) {
            val display = mWindowManager!!.getDefaultDisplay()
            val p = Point()
            display.getRealSize(p)
            params.y = -navHeight
            params.x = 0
        }
        mWindowManager!!.addView(mView, params)

        startTimer()
    }

    private fun stopTimer() {
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    private var view: View? = null
    private var cpuLoadTextView: TextView? = null
    private var gpuLoadTextView: TextView? = null
    private var gpuPanel: View? = null
    private var temperaturePanel: View? = null
    private var temperatureText: TextView? = null
    private var fpsText: TextView? = null

    private var activityManager: ActivityManager? = null
    private var myHandler = Handler(Looper.getMainLooper())
    private var batteryUnit = BatteryUtils()
    private val info = ActivityManager.MemoryInfo()
    private var coreCount = -1
    private var clusters = ArrayList<Array<String>>()

    private val fpsUtils = FpsUtils()
    private var batteryManager: BatteryManager? = null

    private fun updateInfo() {
        if (coreCount < 1) {
            coreCount = CpuFrequencyUtil.coreCount
            clusters = CpuFrequencyUtil.clusterInfo
        }
        val gpuLoad = GpuUtils.getGpuLoad()

        activityManager!!.getMemoryInfo(info)

        var cpuLoad = cpuLoadUtils.cpuLoadSum
        // 尝试获得大核心的最高负载
        val loads = cpuLoadUtils.cpuLoad
        // 一般BigLittle架构的处理器，后面几个核心都是大核，游戏主要依赖大核性能。而小核负载一般来源于后台进程，因此不需要分析小核负载
        val centerIndex = coreCount / 2
        var bigCoreLoadMax = 0.0
        if (centerIndex >= 2) {
            try {
                for (i in centerIndex until coreCount) {
                    val coreLoad = loads[i]!!
                    if (coreLoad > bigCoreLoadMax) {
                        bigCoreLoadMax = coreLoad
                    }
                }
                // 如果某个大核负载超过70%，则将CPU负载显示为此大核的负载
                // 因为迷你监视器更主要的作用是分析CPU负载对游戏性能的影响
                // 通常单核满载会直接导致游戏卡顿，因此单核高负载时，优先显示单核负载而非多核平均负载
                // 以便使用者知晓，此时CPU压力过高可能导致卡顿
                if (bigCoreLoadMax > 70 && bigCoreLoadMax > cpuLoad) {
                    cpuLoad = bigCoreLoadMax
                }
            } catch (ex: java.lang.Exception) {
                Log.e("", "" + ex.message)
            }
        }

        if (cpuLoad < 0) {
            cpuLoad = 0.toDouble()
        }

        val batteryStatus = batteryUnit.getBatteryTemperature()

        val fps = fpsUtils.currentFps

        myHandler.post {
            cpuLoadTextView?.text = cpuLoad.toInt().toString() + "%"
            if (gpuLoad > -1) {
                gpuLoadTextView?.text = gpuLoad.toString() + "%"
            } else {
                gpuLoadTextView?.text = "--"
            }

            temperatureText!!.setText(batteryStatus.temperature.toString())
            if (fps != null) {
                fpsText?.text = fps.toString()
            }
        }
    }

    private fun startTimer() {
        stopTimer()
        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                updateInfo()
            }
        }, 0, 1500)
    }

    /**
     * 隐藏弹出框
     */
    fun hidePopupWindow() {
        stopTimer()
        if (show!! && null != mView) {
            mWindowManager!!.removeView(mView)
            mView = null
            show = false
        }
    }

    @SuppressLint("ApplySharedPref", "ClickableViewAccessibility")
    private fun setUpView(context: Context): View {
        view = LayoutInflater.from(context).inflate(R.layout.fw_monitor_game, null)
        gpuPanel = view!!.findViewById(R.id.fw_gpu)
        temperaturePanel = view!!.findViewById(R.id.fw_battery)

        cpuLoadTextView = view!!.findViewById(R.id.fw_cpu_load)
        gpuLoadTextView = view!!.findViewById(R.id.fw_gpu_load)
        temperatureText = view!!.findViewById(R.id.fw_battery_temp)
        fpsText = view!!.findViewById(R.id.fw_fps)

        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return view!!
    }

    companion object {
        private var mWindowManager: WindowManager? = null
        public var show: Boolean? = false

        @SuppressLint("StaticFieldLeak")
        private var mView: View? = null
        private var timer: Timer? = null
    }
}