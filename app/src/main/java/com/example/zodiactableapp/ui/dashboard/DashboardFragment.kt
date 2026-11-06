package com.example.zodiactableapp.ui.dashboard

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.zodiactableapp.R
import com.example.zodiactableapp.model.Zodiac
import com.example.zodiactableapp.shared.BetSharedViewModel
import com.example.zodiactableapp.shared.GroupLevelEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val sharedVM: BetSharedViewModel by activityViewModels()

    private lateinit var adapter: ZodiacTableAdapter
    private val zodiacList = Zodiac.values().toList()

    // 9级：第一类（不含当年生肖的小组）
    private val amountsTypeA = intArrayOf(
        10, 15, 25, 35, 55,
        80, 125, 190, 290, 440,
        665, 1010, 1530, 2320, 3530

    )

    // 9级：第二类（含当年生肖的小组）
    private val amountsTypeB = intArrayOf(
        10, 20, 30, 45, 70,
        110, 175, 270, 425, 670,
        1045, 1640, 2570, 4050, 6500
    )

    private lateinit var headerRow: LinearLayout
    private lateinit var rvTable: RecyclerView
    private lateinit var tvCurrentOrder: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvYearZodiac: TextView
    private lateinit var btnExport: Button

    // ===== 当年生肖：用 SharedPreferences 存 =====
    private val PREF_FILE = "zodiac_table_prefs"
    private val KEY_YEAR_ZODIAC_PREFIX = "year_zodiac_profile_"

    private val prefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith(KEY_YEAR_ZODIAC_PREFIX) == true) {
                render()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val hsvHeader = view.findViewById<HorizontalScrollView>(R.id.hsvHeader)
        val hsvBody = view.findViewById<HorizontalScrollView>(R.id.hsvBody)

        headerRow = view.findViewById(R.id.headerRow)
        rvTable = view.findViewById(R.id.rvTable)
        tvCurrentOrder = view.findViewById(R.id.tvCurrentOrder)
        tvYearZodiac = view.findViewById(R.id.tvYearZodiac)
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount)
        btnExport = view.findViewById(R.id.btnExportImage)

        buildHeader(headerRow)

        adapter = ZodiacTableAdapter(emptyList())
        rvTable.layoutManager = LinearLayoutManager(requireContext())
        rvTable.adapter = adapter

        syncHorizontalScroll(hsvHeader, hsvBody)

        sharedVM.currentProfile.observe(viewLifecycleOwner) { render() }
        sharedVM.selectedDate.observe(viewLifecycleOwner) { render() }
        sharedVM.selectedOrder.observe(viewLifecycleOwner) { render() }
        sharedVM.drawHistory.observe(viewLifecycleOwner) { render() }

        btnExport.setOnClickListener {
            if (adapter.itemCount == 0) {
                toast("当前没有表格可导出")
                return@setOnClickListener
            }

            val contentWidth = measureWrapWidth(headerRow).coerceAtLeast(dp(360))

            // ✅ 最小改动：导出时用“离屏副本”绘制标题/表头/总计，避免挤压跳动
            val bitmap = buildExportBitmapOffscreen(
                width = contentWidth,
                tvTitle = tvCurrentOrder,
                tvYear = tvYearZodiac,
                headerRowOnscreen = headerRow,
                rv = rvTable,
                tvTotal = tvTotalAmount
            )

            val uri = saveBitmap(bitmap)
            if (uri != null) toast("已导出图片 ✅") else toast("导出失败（请重试）")
        }

        render()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStop() {
        super.onStop()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val dateKey = sharedVM.getSelectedDate()
        val order = sharedVM.selectedOrder.value

        val yearZodiac = readYearZodiac()
        tvYearZodiac.text = "当年生肖：${yearZodiac?.name ?: "未设置"}"

        val draw = sharedVM.getDraw(dateKey)
        val drawText = draw?.name ?: "未录入"

        if (order == null) {
            adapter.submit(emptyList())
            tvCurrentOrder.text = "档案：${sharedVM.getCurrentProfileLabel()}  日期：$dateKey  开奖：$drawText  当前：-（请在 Home 点击某一单）"
            tvTotalAmount.text = "总金额：￥0"
            return
        }

        tvCurrentOrder.text = "档案：${sharedVM.getCurrentProfileLabel()}  日期：$dateKey  开奖：$drawText  当前：第 ${order.orderNo} 号单"

        val result = buildTable(order.groupIds, dateKey, yearZodiac)
        adapter.submit(result.rows)
        tvTotalAmount.text = "总金额：￥${result.totalAmount}"
    }

    private fun readYearZodiac(): Zodiac? {
        val name = prefs.getString(yearZodiacKey(), null) ?: return null
        return runCatching { Zodiac.valueOf(name) }.getOrNull()
    }

    private fun yearZodiacKey(): String = "$KEY_YEAR_ZODIAC_PREFIX${sharedVM.getCurrentProfileId()}"

    private data class TableResult(
        val rows: List<ZodiacTableRow>,
        val totalAmount: Int
    )

    private fun buildTable(groupIds: List<Int>, dateKey: String, yearZodiac: Zodiac?): TableResult {
        val rows = mutableListOf<ZodiacTableRow>()
        val columnSum = IntArray(zodiacList.size) { 0 }
        var totalAmount = 0

        groupIds.take(15).forEachIndexed { index, gid ->
            val members = sharedVM.getGroupMembers(gid)
            val level = sharedVM.getGroupLevelForDate(gid, dateKey) // 1..9
            val money = moneyForLevel(level, members, yearZodiac)

            val values = MutableList<Int?>(zodiacList.size) { null }

            members.forEach { zodiac ->
                val col = zodiacList.indexOf(zodiac)
                if (col in zodiacList.indices) {
                    values[col] = money
                    columnSum[col] += money
                    totalAmount += money
                }
            }

            rows.add(ZodiacTableRow(label = (index + 1).toString(), values = values))
        }

        rows.add(
            ZodiacTableRow(
                label = "小计",
                values = columnSum.map { it as Int? }
            )
        )

        return TableResult(rows, totalAmount)
    }

    private fun moneyForLevel(level: Int, members: Set<Zodiac>, yearZodiac: Zodiac?): Int {
        val lv = level.coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
        val idx = lv - 1
        val useB = (yearZodiac != null && members.contains(yearZodiac))
        return if (useB) amountsTypeB[idx] else amountsTypeA[idx]
    }

    // ===== 表头 =====
    private fun buildHeader(headerRow: LinearLayout) {
        headerRow.removeAllViews()
        headerRow.addView(makeHeaderCell(""))
        zodiacList.forEach { headerRow.addView(makeHeaderCell(it.name)) }
    }

    private fun makeHeaderCell(text: String): TextView {
        return TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(if (text.isEmpty()) 56 else 72),
                dp(48)
            )
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.cell_border)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            this.text = text
        }
    }

    // ===== 横向滚动同步 =====
    private fun syncHorizontalScroll(h1: HorizontalScrollView, h2: HorizontalScrollView) {
        var lock = false
        val l1 = android.view.ViewTreeObserver.OnScrollChangedListener {
            if (!lock) {
                lock = true
                h2.scrollTo(h1.scrollX, 0)
                lock = false
            }
        }
        val l2 = android.view.ViewTreeObserver.OnScrollChangedListener {
            if (!lock) {
                lock = true
                h1.scrollTo(h2.scrollX, 0)
                lock = false
            }
        }
        h1.viewTreeObserver.addOnScrollChangedListener(l1)
        h2.viewTreeObserver.addOnScrollChangedListener(l2)
    }

    // ===== ✅ 导出：最小改动防“跳动” =====

    private fun measureWrapWidth(view: View): Int {
        val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        return view.measuredWidth
    }

    /**
     * 关键：做一个“离屏 TextView 副本”，把样式/文字拷过去，然后对副本 measure/layout/draw
     * 这样不会影响屏幕上的原控件，也就不会“挤压跳动”
     */
    private fun drawTextViewOffscreen(src: TextView, width: Int): Bitmap {
        val tv = TextView(requireContext()).apply {
            text = src.text
            textSize = src.textSize / resources.displayMetrics.scaledDensity
            typeface = src.typeface
            gravity = src.gravity
            setTextColor(src.currentTextColor)
            setPadding(src.paddingLeft, src.paddingTop, src.paddingRight, src.paddingBottom)
            // 背景可选：如果你想导出也有背景，就复制；不想就注释掉
            background = src.background
        }

        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        tv.measure(wSpec, hSpec)
        tv.layout(0, 0, width, tv.measuredHeight)

        val bmp = Bitmap.createBitmap(width, max(tv.measuredHeight, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        tv.draw(c)
        return bmp
    }

    /**
     * 离屏构建表头（不动屏幕上的 headerRow）
     */
    private fun drawHeaderOffscreen(width: Int): Bitmap {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        // 第一列空白 + 12生肖
        row.addView(makeHeaderCell("").apply { layoutParams = LinearLayout.LayoutParams(dp(56), dp(48)) })
        zodiacList.forEach {
            row.addView(makeHeaderCell(it.name).apply { layoutParams = LinearLayout.LayoutParams(dp(72), dp(48)) })
        }

        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        row.measure(wSpec, hSpec)
        row.layout(0, 0, width, row.measuredHeight)

        val bmp = Bitmap.createBitmap(width, max(row.measuredHeight, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        row.draw(c)
        return bmp
    }

    private fun drawRecyclerViewToBitmapFull(rv: RecyclerView, width: Int): Bitmap {
        val ad = rv.adapter ?: return Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888)

        val bitmaps = ArrayList<Bitmap>(ad.itemCount)
        var totalH = 0

        for (i in 0 until ad.itemCount) {
            val holder = ad.createViewHolder(rv, ad.getItemViewType(i))
            ad.bindViewHolder(holder, i)

            val item = holder.itemView
            val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            item.measure(wSpec, hSpec)
            item.layout(0, 0, width, item.measuredHeight)

            val bmp = Bitmap.createBitmap(width, max(item.measuredHeight, 1), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            item.draw(c)

            bitmaps.add(bmp)
            totalH += item.measuredHeight
        }

        val out = Bitmap.createBitmap(width, max(totalH, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        var y = 0
        bitmaps.forEach { b ->
            canvas.drawBitmap(b, 0f, y.toFloat(), null)
            y += b.height
        }
        return out
    }

    /**
     * ✅ 用离屏副本拼接：标题 + 当年生肖 + 表头 + 全表 + 总计
     */
    private fun buildExportBitmapOffscreen(
        width: Int,
        tvTitle: TextView,
        tvYear: TextView,
        headerRowOnscreen: LinearLayout,
        rv: RecyclerView,
        tvTotal: TextView
    ): Bitmap {

        val titleBmp = drawTextViewOffscreen(tvTitle, width)
        val yearBmp = drawTextViewOffscreen(tvYear, width)
        // 不用 headerRowOnscreen，离屏重建表头，避免动原 view
        val headerBmp = drawHeaderOffscreen(width)

        val tableBmp = drawRecyclerViewToBitmapFull(rv, width)
        val totalBmp = drawTextViewOffscreen(tvTotal, width)

        val totalHeight =
            titleBmp.height + yearBmp.height + headerBmp.height + tableBmp.height + totalBmp.height

        val out = Bitmap.createBitmap(width, max(totalHeight, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        var y = 0
        canvas.drawBitmap(titleBmp, 0f, y.toFloat(), null); y += titleBmp.height
        canvas.drawBitmap(yearBmp, 0f, y.toFloat(), null); y += yearBmp.height
        canvas.drawBitmap(headerBmp, 0f, y.toFloat(), null); y += headerBmp.height
        canvas.drawBitmap(tableBmp, 0f, y.toFloat(), null); y += tableBmp.height
        canvas.drawBitmap(totalBmp, 0f, y.toFloat(), null)

        return out
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val name = "zodiac_table_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) +
                ".png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZodiacTableApp")
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
                uri
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
