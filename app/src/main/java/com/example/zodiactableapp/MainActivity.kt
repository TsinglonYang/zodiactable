package com.example.zodiactableapp

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.zodiactableapp.databinding.ActivityMainBinding
import com.example.zodiactableapp.model.ZodiacNumberTables
import com.example.zodiactableapp.shared.BetSharedViewModel
import com.example.zodiactableapp.shared.ProfileSubtotal
import com.example.zodiactableapp.ui.localizedDatePickerContext
import com.example.zodiactableapp.ui.showLocalized
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedVM: BetSharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 日期选择器等系统控件按简体中文显示（需在创建 DatePicker 之前设置）
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        super.onCreate(savedInstanceState)

        // 关键：我们自己处理系统栏(insets)，避免不同手机顶部/底部遮挡
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        binding.navView.setupWithNavController(navController)

        buildProfileLauncher(sharedVM.getProfileIds())
        sharedVM.profileIds.observe(this) { buildProfileLauncher(it) }
        showProfileLauncher()
        setupBackHandling()
        applyInsets()

        binding.btnNumberValueTable.setOnClickListener { showNumberValueDatePicker() }

        binding.btnDrawNumber.setOnClickListener { showDrawNumberPicker() }

        binding.btnDrawHistory.setOnClickListener { showDrawHistory() }
    }

    private fun showDrawNumberPicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            localizedDatePickerContext(this),
            { _, y, m, d ->
                val dateKey = "%04d-%02d-%02d".format(y, m + 1, d)
                showDrawNumberPickerForDate(dateKey)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).showLocalized()
    }

    private fun showDrawNumberPickerForDate(dateKey: String) {
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 49
            value = 1
        }
        AlertDialog.Builder(this)
            .setTitle("选择开奖数字（$dateKey，1-49）")
            .setView(picker)
            .setPositiveButton("确定") { _, _ ->
                val number = picker.value
                sharedVM.recordDrawNumber(dateKey, number)
                val z = ZodiacNumberTables.zodiacForNumber(
                    number, ZodiacNumberTables.groupIndexForProfile(sharedVM.getCurrentProfileId())
                )
                toast("已录入 $dateKey 开奖数字：$number（当前档案生肖：${z.name}）")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDrawHistory() {
        val history = sharedVM.getDrawNumberHistory()
        val root = ScrollView(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        root.addView(wrap)
        if (history.isEmpty()) {
            wrap.addView(TextView(this).apply {
                text = "暂无历史录入"
                textSize = 15f
            })
        } else {
            history.forEach { (date, number) ->
                val z = ZodiacNumberTables.zodiacForNumber(
                    number, ZodiacNumberTables.groupIndexForProfile(1)
                )
                wrap.addView(TextView(this).apply {
                    text = "$date　开奖数字：$number（生肖：${z.name}）"
                    textSize = 15f
                    setPadding(0, dp(6), 0, dp(6))
                })
            }
        }
        AlertDialog.Builder(this)
            .setTitle("开奖历史录入")
            .setView(root)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun buildProfileLauncher(profileIds: List<Int>) {
        binding.profileGrid.removeAllViews()

        val canDelete = profileIds.size > 1
        for (profileId in profileIds) {
            val item = makeProfileItem(profileId, canDelete)
            binding.profileGrid.addView(item)
        }

        binding.profileGrid.addView(makeCreateProfileItem())
    }

    private fun makeProfileItem(profileId: Int, canDelete: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            layoutParams = makeGridItemLayoutParams()
            background = makeCardBackground(
                fillColor = Color.WHITE,
                strokeColor = Color.rgb(220, 220, 220)
            )
            isClickable = true
            isFocusable = true

            addView(ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = "${profileId}生肖投注助手"
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            })

            addView(TextView(context).apply {
                text = "${profileId}生肖投注助手"
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            })

            setOnClickListener { enterProfile(profileId) }
            if (canDelete) {
                setOnLongClickListener {
                    confirmDeleteProfile(profileId)
                    true
                }
            }
        }
    }

    private fun makeCardBackground(
        fillColor: Int,
        strokeColor: Int,
        cornerDp: Int = 8
    ): GradientDrawable {
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
        return cardBg
    }

    private fun makeGridItemLayoutParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(12))
        }
    }

    private fun makeCreateProfileItem(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            background = makeCardBackground(
                fillColor = Color.rgb(248, 248, 248),
                strokeColor = Color.rgb(180, 180, 180)
            )
            isClickable = true
            isFocusable = true

            layoutParams = makeGridItemLayoutParams()

            addView(TextView(context).apply {
                text = "+"
                textSize = 42f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(80, 80, 80))
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            })

            addView(TextView(context).apply {
                text = "创建新应用"
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            })

            setOnClickListener { confirmCreateProfile() }
        }
    }

    private fun confirmCreateProfile() {
        val nextProfileId = (sharedVM.getProfileIds().maxOrNull() ?: 0) + 1
        AlertDialog.Builder(this)
            .setTitle("创建新应用")
            .setMessage("是否创建 ${nextProfileId}生肖投注助手？")
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val newProfileId = sharedVM.createProfile()
                enterProfile(newProfileId)
            }
            .show()
    }

    private fun confirmDeleteProfile(profileId: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除应用")
            .setMessage("是否删除 ${profileId}生肖投注助手？删除后该应用的大单、等级和开奖记录都会被清除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val deleted = sharedVM.deleteProfile(profileId)
                if (!deleted) {
                    toast("至少保留一个应用")
                }
            }
            .show()
    }

    private fun enterProfile(profileId: Int) {
        sharedVM.switchProfile(profileId)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        if (navController.currentDestination?.id != R.id.navigation_home) {
            navController.navigate(R.id.navigation_home)
        }

        binding.profileLauncher.visibility = View.GONE
        findViewById<View>(R.id.nav_host_fragment_activity_main).visibility = View.VISIBLE
        binding.navView.visibility = View.VISIBLE
    }

    private fun showProfileLauncher() {
        binding.profileLauncher.visibility = View.VISIBLE
        findViewById<View>(R.id.nav_host_fragment_activity_main).visibility = View.GONE
        binding.navView.visibility = View.GONE
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.profileLauncher.visibility == View.VISIBLE) {
                    finish()
                } else {
                    showProfileLauncher()
                }
            }
        })
    }

    private fun applyInsets() {
        // 1) 内容容器：吃掉状态栏高度，避免顶部重叠；底部交给约束到 nav_view 解决
        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = sys.left,
                top = sys.top,
                right = sys.right,
                bottom = 0
            )
            insets
        }

        // 2) BottomNavigation：加上手势条高度，避免按钮被底部系统手势区域挡住
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = sys.bottom)
            insets
        }
    }

    private fun showNumberValueDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            localizedDatePickerContext(this),
            { _, y, m, d ->
                val dateKey = "%04d-%02d-%02d".format(y, m + 1, d)
                sharedVM.computeAllProfileSubtotals(dateKey) { subtotals ->
                    showNumberValueTable(dateKey, subtotals)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).showLocalized()
    }

    /**
     * 对一行 49 个值做扣减：取第三小值，每个值减去它，小于 0 的置 0。
     * 等价于把倒数第一小、倒数第二小、倒数第三小都变成 0。
     */
    private fun subtractThirdSmallest(values: List<Long>): List<Long> {
        if (values.size < 3) return values
        val thirdSmallest = values.sorted()[2]
        return values.map { (it - thirdSmallest).coerceAtLeast(0L) }
    }

    private fun showNumberValueTable(dateKey: String, subtotals: List<ProfileSubtotal>) {
        val content = ScrollView(this)
        val hsv = HorizontalScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        hsv.addView(table)
        content.addView(hsv)

        // 先算好每个档案的 49 个值 + 汇总（保留 Long，末尾再追加“总金额”列）
        val profileRows = subtotals.sortedBy { it.profileId }.map { ps ->
            val group = ZodiacNumberTables.groupIndexForProfile(ps.profileId)
            val values = (1..49).map { n ->
                val z = ZodiacNumberTables.zodiacForNumber(n, group)
                ps.zodiacSubtotals[z] ?: 0L
            }
            ps.profileId to values
        }

        val totals = (1..49).map { n ->
            subtotals.sumOf { ps ->
                val group = ZodiacNumberTables.groupIndexForProfile(ps.profileId)
                val z = ZodiacNumberTables.zodiacForNumber(n, group)
                ps.zodiacSubtotals[z] ?: 0L
            }
        }
        val grandTotal = totals.sum()
        val adjustedTotals = subtractThirdSmallest(totals)

        // 对话框：49 列横向滑动（不折叠），末尾加“总金额”
        table.addView(makeNumberRow("档案", (1..49).map { it.toString() } + "总金额", bold = true))
        profileRows.forEach { (pid, values) ->
            table.addView(
                makeNumberRow("档$pid", values.map { it.toString() } + values.sum().toString(), bold = false)
            )
        }
        table.addView(makeNumberRow("汇总", totals.map { it.toString() } + grandTotal.toString(), bold = true))
        table.addView(
            makeNumberRow(
                "汇总调整",
                adjustedTotals.map { it.toString() } + adjustedTotals.sum().toString(),
                bold = true
            )
        )

        AlertDialog.Builder(this)
            .setTitle("号码价值表（$dateKey · 1-49 × ${subtotals.size} 档案）")
            .setView(content)
            .setPositiveButton("关闭", null)
            .setNegativeButton("导出图片") { _, _ ->
                // 导出：整体折叠（1-25 上、26-49 下）
                val folded = buildFoldedTable(dateKey, profileRows, totals, grandTotal)
                val bmp = drawTableToBitmap(folded)
                val uri = saveBitmap(bmp)
                toast(if (uri != null) "已导出图片 ✅" else "导出失败")
            }
            .show()
    }

    private fun buildFoldedTable(
        dateKey: String,
        profileRows: List<Pair<Int, List<Long>>>,
        totals: List<Long>,
        grandTotal: Long
    ): LinearLayout {
        val adjustedTotals = subtractThirdSmallest(totals)
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // 顶部：日期 + 总金额
        table.addView(TextView(this).apply {
            text = "日期：$dateKey    总金额：$grandTotal"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setTextColor(Color.BLACK)
        })

        // 上半：1..25
        table.addView(makeNumberRow("档案", (1..25).map { it.toString() }, bold = true))
        profileRows.forEach { (pid, values) ->
            table.addView(makeNumberRow("档$pid", values.subList(0, 25).map { it.toString() }, bold = false))
        }
        table.addView(makeNumberRow("汇总", totals.subList(0, 25).map { it.toString() }, bold = true))
        table.addView(makeNumberRow("汇总调整", adjustedTotals.subList(0, 25).map { it.toString() }, bold = true))
        // 下半：26..49（末尾加“总金额”）
        table.addView(makeNumberRow("档案", (26..49).map { it.toString() } + "总金额", bold = true))
        profileRows.forEach { (pid, values) ->
            table.addView(
                makeNumberRow("档$pid", values.subList(25, 49).map { it.toString() } + values.sum().toString(), bold = false)
            )
        }
        table.addView(makeNumberRow("汇总", totals.subList(25, 49).map { it.toString() } + grandTotal.toString(), bold = true))
        table.addView(
            makeNumberRow(
                "汇总调整",
                adjustedTotals.subList(25, 49).map { it.toString() } + adjustedTotals.sum().toString(),
                bold = true
            )
        )
        return table
    }

    /** 把整张表（全 50 列 × 12 行）离屏绘制成位图 */
    private fun drawTableToBitmap(table: LinearLayout): Bitmap {
        val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        table.measure(wSpec, hSpec)
        val w = table.measuredWidth
        val h = table.measuredHeight
        table.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        table.draw(canvas)
        return bmp
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val name = "number_value_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZodiacTableApp")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
                uri
            } else {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun makeNumberRow(label: String, values: List<String>, bold: Boolean): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(makeNumberCell(label, 52, bold))
        values.forEach { v -> row.addView(makeNumberCell(v, 42, bold)) }
        return row
    }

    private fun makeNumberCell(text: String, widthDp: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.cell_border)
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(36))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
