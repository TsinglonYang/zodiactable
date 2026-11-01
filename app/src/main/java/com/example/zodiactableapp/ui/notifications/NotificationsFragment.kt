package com.example.zodiactableapp.ui.notifications

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.zodiactableapp.R
import com.example.zodiactableapp.model.Zodiac
import com.example.zodiactableapp.model.ZodiacNumberTables
import com.example.zodiactableapp.shared.BetSharedViewModel
import com.example.zodiactableapp.ui.localizedDatePickerContext
import com.example.zodiactableapp.ui.showLocalized
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    private val sharedVM: BetSharedViewModel by activityViewModels()
    private val yearZodiacKeyPrefix = "year_zodiac_profile_"

    private lateinit var tvDate: TextView
    private lateinit var btnPickDate: Button

    private lateinit var tvYearZodiac: TextView
    private lateinit var spinnerYearZodiac: Spinner
    private lateinit var btnSaveYearZodiac: Button

    private lateinit var spinnerDrawZodiac: Spinner
    private lateinit var btnConfirmDraw: Button
    private lateinit var tvCurrentDraw: TextView

    // 汇总表 UI
    private lateinit var hsvHeader: HorizontalScrollView
    private lateinit var hsvBody: HorizontalScrollView
    private lateinit var headerRow: LinearLayout
    private lateinit var rvTable: RecyclerView
    private lateinit var tvFormulaHint: TextView
    private lateinit var tvSummaryProfile: TextView

    // ✅ 导出按钮
    private lateinit var btnExport: Button

    private lateinit var adapter: SummaryTableAdapter

    // 生肖号码对照表（仅 app1）
    private lateinit var tvZodiacNumberTitle: TextView
    private lateinit var zodiacNumberTable: LinearLayout

    private val zodiacList = Zodiac.values().toList()

    // 1–15级：第一类
    private val amountA = intArrayOf(
        10, 15, 25, 35, 55,
        80, 125, 190, 290, 440,
        665, 1010, 1530, 2320, 3530
    )

    // 1–15级：第二类（当年生肖在该小组中）
    private val amountB = intArrayOf(
        10, 20, 30, 45, 70,
        110, 175, 270, 425, 670,
        1045, 1640, 2570, 4050, 6500
    )

    private var selectedDraw: Zodiac = Zodiac.values()[0]
    private var selectedYearZodiac: Zodiac = Zodiac.values()[0]

    // 当年生肖持久化（只需设置一次）
    private val prefs by lazy {
        requireContext().getSharedPreferences("zodiac_table_prefs", Context.MODE_PRIVATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        tvDate = view.findViewById(R.id.tvDate)
        btnPickDate = view.findViewById(R.id.btnPickDate)

        tvYearZodiac = view.findViewById(R.id.tvYearZodiac)
        spinnerYearZodiac = view.findViewById(R.id.spinnerYearZodiac)
        btnSaveYearZodiac = view.findViewById(R.id.btnSaveYearZodiac)

        spinnerDrawZodiac = view.findViewById(R.id.spinnerDrawZodiac)
        btnConfirmDraw = view.findViewById(R.id.btnConfirmDraw)
        tvCurrentDraw = view.findViewById(R.id.tvCurrentDraw)

        btnExport = view.findViewById(R.id.btnExportSummaryImage)

        hsvHeader = view.findViewById(R.id.hsvSumHeader)
        hsvBody = view.findViewById(R.id.hsvSumBody)
        headerRow = view.findViewById(R.id.sumHeaderRow)
        rvTable = view.findViewById(R.id.rvSumTable)
        tvFormulaHint = view.findViewById(R.id.tvFormulaHint)
        tvSummaryProfile = view.findViewById(R.id.tvSummaryProfile)

        tvZodiacNumberTitle = view.findViewById(R.id.tvZodiacNumberTitle)
        zodiacNumberTable = view.findViewById(R.id.zodiacNumberTable)
        setupZodiacNumberTable()

        // 1) 下拉初始化
        setupYearSpinner()
        setupDrawSpinner()

        // 2) 读取已保存“当年生肖”
        restoreYearZodiac()

        // 3) 表头 + 表格
        buildHeader(headerRow)
        adapter = SummaryTableAdapter(emptyList())
        rvTable.layoutManager = LinearLayoutManager(requireContext())
        rvTable.adapter = adapter
        rvTable.isNestedScrollingEnabled = false
        syncHorizontalScroll(hsvHeader, hsvBody)

        // 4) 监听：日期/订单/开奖变动 → 重新渲染
        sharedVM.currentProfile.observe(viewLifecycleOwner) {
            restoreYearZodiac()
            setupZodiacNumberTable()
            renderAll()
        }
        sharedVM.selectedDate.observe(viewLifecycleOwner) { renderAll() }
        sharedVM.orders.observe(viewLifecycleOwner) { renderAll() }
        sharedVM.drawHistory.observe(viewLifecycleOwner) { renderAll() }

        // 5) 选择日期
        btnPickDate.setOnClickListener { showDatePicker() }

        // 6) 保存当年生肖
        btnSaveYearZodiac.setOnClickListener {
            saveYearZodiac(selectedYearZodiac)
            toast("已保存当年生肖：${selectedYearZodiac.name}")
            renderAll()
        }

        // 7) 录入当日开奖
        btnConfirmDraw.setOnClickListener {
            val dateKey = sharedVM.getSelectedDate()
            sharedVM.recordDraw(dateKey, selectedDraw)
            toast("已录入：$dateKey 开奖 = ${selectedDraw.name}")
            renderAll()
        }

        // ✅ 8) 导出汇总表（图片顶部带：日期 + 当日开奖）
        btnExport.setOnClickListener {
            if (adapter.itemCount == 0) {
                toast("当前没有汇总表可导出")
                return@setOnClickListener
            }

            val dateKey = sharedVM.getSelectedDate()
            val draw = sharedVM.getDraw(dateKey)
            val drawText = draw?.name ?: "未录入"

            val yz = getYearZodiac()

            // ✅ 重要：导出时不要对屏幕上的 View 重新 measure/layout，否则会造成“文字上跳/挤压”
            // 下面全部使用离屏 View（新建的 View）来绘制
            val contentWidth = measureWrapWidth(headerRow).coerceAtLeast(dp(360))

            val titleView = makeExportTitleView(
                text = "档案：${sharedVM.getCurrentProfileLabel()}    日期：$dateKey    当日开奖：$drawText    当年生肖：${yz.name}"
            )

            val headerView = buildOffscreenHeader(contentWidth)

            val footerView = makeExportFooterView(tvFormulaHint.text?.toString().orEmpty())

            val bitmap = buildExportBitmapOffscreen(
                width = contentWidth,
                titleView = titleView,
                headerView = headerView,
                rv = rvTable,
                footerView = footerView
            )

            val uri = saveBitmap(bitmap)
            if (uri != null) toast("已导出图片 ✅") else toast("导出失败（请重试）")
        }

        // 初次渲染
        renderAll()
    }

    // ====================== 渲染总入口 ======================

    private fun renderAll() {
        val dateKey = sharedVM.getSelectedDate()
        tvDate.text = "当前档案：${sharedVM.getCurrentProfileLabel()}  当前日期：$dateKey"
        tvSummaryProfile.text = "汇总表档案：${sharedVM.getCurrentProfileLabel()}"

        // 当日开奖
        val d = sharedVM.getDraw(dateKey)
        tvCurrentDraw.text = if (d == null) "本日开奖：未录入" else "本日开奖：${d.name}"

        // 当年生肖
        val yz = getYearZodiac()
        tvYearZodiac.text = "当年生肖：${yz.name}"

        // 汇总表
        renderSummaryTable(dateKey, yz)
    }

    // ====================== 汇总表（所有单） ======================

    private fun renderSummaryTable(dateKey: String, yearZodiac: Zodiac) {
        val orders = sharedVM.orders.value.orEmpty()
        if (orders.isEmpty()) {
            adapter.submit(emptyList())
            updateTableHeight(0)
            tvFormulaHint.text = "汇总：当前没有任何大单"
            return
        }

        val rows = ArrayList<SummaryTableRow>()
        val subtotalCols = LongArray(zodiacList.size) { 0L }

        for (order in orders.sortedBy { it.orderNo }) {

            val colSums = LongArray(zodiacList.size) { 0L }

            for (gid in order.groupIds.take(15)) {
                val members = sharedVM.getGroupMembers(gid)
                val level = sharedVM.getGroupLevelForDate(gid, dateKey)
                val money = moneyByLevel(level, members, yearZodiac)

                for (z in members) {
                    val idx = zodiacList.indexOf(z)
                    if (idx >= 0) colSums[idx] += money.toLong()
                }
            }

            for (i in colSums.indices) subtotalCols[i] += colSums[i]

            rows.add(
                SummaryTableRow(
                    label = "${order.orderNo}",
                    values = colSums.map { it.toIntSafe() }
                )
            )
        }

        val orderCount = orders.size
        val adjustedSubtotalCols = LongArray(zodiacList.size) { 0L }
        for (i in zodiacList.indices) {
            adjustedSubtotalCols[i] = subtotalCols[i] - orderCount.toLong() * 50L
        }

        // 小计：在原始列汇总基础上减去 50 * 单数
        rows.add(
            SummaryTableRow(
                label = "小计",
                values = adjustedSubtotalCols.map { it.toIntSafe() }
            )
        )

        // 合计行（按你的公式）
        val totalCols = LongArray(zodiacList.size) { 0L }
        for (i in zodiacList.indices) {
            val mul = if (zodiacList[i] == yearZodiac) 5L else 4L
            totalCols[i] = adjustedSubtotalCols[i] * mul
        }

        rows.add(
            SummaryTableRow(
                label = "合计",
                values = totalCols.map { it.toIntSafe() }
            )
        )

        adapter.submit(rows)
        updateTableHeight(rows.size)

        val subtotalAll = adjustedSubtotalCols.sum()
        val totalAll = totalCols.sum()
        tvFormulaHint.text =
            "单数=$orderCount｜小计总额=￥$subtotalAll｜合计总额=￥$totalAll（普通*4，当年生肖列*5；均先减单数*50）"
    }

    private fun moneyByLevel(level: Int, members: Set<Zodiac>, yearZodiac: Zodiac): Int {
        val lv = level.coerceIn(1, 15)
        val useB = members.contains(yearZodiac)
        val arr = if (useB) amountB else amountA
        return arr[lv - 1]
    }

    // ====================== Spinner 初始化 ======================

    private fun setupYearSpinner() {
        val names = Zodiac.values().map { it.name }
        val ad = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerYearZodiac.adapter = ad
        spinnerYearZodiac.setSelection(0)
        spinnerYearZodiac.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedYearZodiac = Zodiac.values()[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDrawSpinner() {
        val names = Zodiac.values().map { it.name }
        val ad = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerDrawZodiac.adapter = ad
        spinnerDrawZodiac.setSelection(0)
        spinnerDrawZodiac.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedDraw = Zodiac.values()[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ====================== 当年生肖：保存/读取 ======================

    private fun saveYearZodiac(z: Zodiac) {
        prefs.edit().putString(yearZodiacKey(), z.name).apply()
    }

    private fun getYearZodiac(): Zodiac {
        val name = prefs.getString(yearZodiacKey(), null)
        return try {
            if (name.isNullOrBlank()) Zodiac.values()[0] else Zodiac.valueOf(name)
        } catch (_: Exception) {
            Zodiac.values()[0]
        }
    }

    private fun yearZodiacKey(): String = "$yearZodiacKeyPrefix${sharedVM.getCurrentProfileId()}"

    private fun restoreYearZodiac() {
        val yz = getYearZodiac()
        val idx = zodiacList.indexOf(yz).coerceAtLeast(0)
        spinnerYearZodiac.setSelection(idx)
        selectedYearZodiac = yz
        tvYearZodiac.text = "当年生肖：${yz.name}"
    }

    // ====================== 表头 + 滚动同步 ======================

    private fun buildHeader(row: LinearLayout) {
        row.removeAllViews()
        row.addView(makeHeaderCell("单号"))
        zodiacList.forEach { row.addView(makeHeaderCell(it.name)) }
    }

    private fun makeHeaderCell(text: String): TextView {
        return TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(if (text == "单号") 56 else 72),
                dp(48)
            )
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.cell_border)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            this.text = text
        }
    }

    // ====================== 生肖号码对照表（仅 app1） ======================

    private fun setupZodiacNumberTable() {
        val show = resources.getBoolean(R.bool.show_zodiac_numbers)
        tvZodiacNumberTitle.visibility = if (show) View.VISIBLE else View.GONE
        zodiacNumberTable.visibility = if (show) View.VISIBLE else View.GONE
        if (show) buildZodiacNumberTable()
    }

    private fun buildZodiacNumberTable() {
        zodiacNumberTable.removeAllViews()
        val zodiacs = Zodiac.values().toList()
        for (r in 0 until 6) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (c in 0 until 2) {
                val z = zodiacs[r * 2 + c]
                val cell = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    background = requireContext().getDrawable(R.drawable.cell_border)
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                cell.addView(TextView(requireContext()).apply {
                    text = z.name
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                cell.addView(TextView(requireContext()).apply {
                    text = ZodiacNumberTables.numbersFor(
                        z,
                        ZodiacNumberTables.groupIndexForProfile(sharedVM.getCurrentProfileId())
                    ).joinToString(" ")
                    textSize = 15f
                    setTextColor(Color.rgb(60, 60, 60))
                })
                row.addView(cell)
            }
            zodiacNumberTable.addView(row)
        }
    }

    // ====================== ✅ 导出：离屏标题/表头/页脚（避免界面跳动） ======================

    private fun makeExportTitleView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
        }
    }

    private fun makeExportFooterView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextColor(Color.DKGRAY)
            setBackgroundColor(Color.WHITE)
        }
    }

    private fun buildOffscreenHeader(width: Int): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT)

            addView(makeHeaderCell("单号"))
            zodiacList.forEach { addView(makeHeaderCell(it.name)) }
        }
    }

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

    // ====================== 日期选择 ======================

    private fun showDatePicker() {
        val cur = sharedVM.getSelectedDate()
        val parts = cur.split("-")
        val cal = Calendar.getInstance()
        if (parts.size == 3) {
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }

        DatePickerDialog(
            localizedDatePickerContext(requireContext()),
            { _, y, m, d ->
                val key = "%04d-%02d-%02d".format(y, m + 1, d)
                sharedVM.setSelectedDate(key)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).showLocalized()
    }

    // ====================== ✅ 导出整表图片（横向全列 + 纵向全行） ======================

    private fun measureWrapWidth(view: View): Int {
        val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        return view.measuredWidth
    }

    private fun drawViewToBitmapExactWidth(view: View, width: Int): Bitmap {
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        view.layout(0, 0, width, view.measuredHeight)

        val bmp = Bitmap.createBitmap(width, max(view.measuredHeight, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        view.draw(c)
        return bmp
    }

    private fun drawViewToBitmapExactWidthOffscreen(view: View, width: Int): Bitmap {
        // 离屏 view：用 EXACT 宽度测量，不会影响界面上的布局
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(wSpec, hSpec)
        view.layout(0, 0, width, view.measuredHeight)

        val bmp = Bitmap.createBitmap(width, max(view.measuredHeight, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        view.draw(c)
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

    private fun buildExportBitmap(
        width: Int,
        titleView: TextView,
        headerRow: LinearLayout,
        rv: RecyclerView,
        footerView: TextView
    ): Bitmap {
        val titleBmp = drawViewToBitmapExactWidth(titleView, width)
        val headerBmp = drawViewToBitmapExactWidth(headerRow, width)
        val tableBmp = drawRecyclerViewToBitmapFull(rv, width)
        val footerBmp = drawViewToBitmapExactWidth(footerView, width)

        val totalHeight = titleBmp.height + headerBmp.height + tableBmp.height + footerBmp.height
        val out = Bitmap.createBitmap(width, max(totalHeight, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        var y = 0
        canvas.drawBitmap(titleBmp, 0f, y.toFloat(), null); y += titleBmp.height
        canvas.drawBitmap(headerBmp, 0f, y.toFloat(), null); y += headerBmp.height
        canvas.drawBitmap(tableBmp, 0f, y.toFloat(), null); y += tableBmp.height
        canvas.drawBitmap(footerBmp, 0f, y.toFloat(), null)

        return out
    }

    private fun buildExportBitmapOffscreen(
        width: Int,
        titleView: TextView,
        headerView: LinearLayout,
        rv: RecyclerView,
        footerView: TextView
    ): Bitmap {
        val titleBmp = drawViewToBitmapExactWidthOffscreen(titleView, width)
        val headerBmp = drawViewToBitmapExactWidthOffscreen(headerView, width)
        val tableBmp = drawRecyclerViewToBitmapFull(rv, width)
        val footerBmp = drawViewToBitmapExactWidthOffscreen(footerView, width)

        val totalHeight = titleBmp.height + headerBmp.height + tableBmp.height + footerBmp.height
        val out = Bitmap.createBitmap(width, max(totalHeight, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        var y = 0
        canvas.drawBitmap(titleBmp, 0f, y.toFloat(), null); y += titleBmp.height
        canvas.drawBitmap(headerBmp, 0f, y.toFloat(), null); y += headerBmp.height
        canvas.drawBitmap(tableBmp, 0f, y.toFloat(), null); y += tableBmp.height
        canvas.drawBitmap(footerBmp, 0f, y.toFloat(), null)

        return out
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val name = "zodiac_summary_" +
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
                val dir = requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                val file = File(dir, name)
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

    // ====================== 工具 ======================

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 让 RecyclerView 完整显示所有行（每行 48dp），整页由外层 ScrollView 滚动 */
    private fun updateTableHeight(rowCount: Int) {
        rvTable.layoutParams = rvTable.layoutParams.apply {
            height = if (rowCount <= 0) 0 else dp(48) * rowCount
        }
        rvTable.requestLayout()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun Long.toIntSafe(): Int? {
        return when {
            this > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
            this < Int.MIN_VALUE.toLong() -> Int.MIN_VALUE
            else -> this.toInt()
        }
    }
}
