package com.example.zodiactableapp.ui.home

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.zodiactableapp.BuildConfig
import com.example.zodiactableapp.R
import com.example.zodiactableapp.model.Zodiac
import com.example.zodiactableapp.model.ZodiacNumberTables
import com.example.zodiactableapp.shared.BetOrder
import com.example.zodiactableapp.shared.BetSharedViewModel
import com.example.zodiactableapp.shared.GroupLevelEngine
import com.example.zodiactableapp.ui.localizedDatePickerContext
import com.example.zodiactableapp.ui.showLocalized
import java.util.Calendar

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val sharedVM: BetSharedViewModel by activityViewModels()

    private lateinit var tvCurrentProfile: TextView
    private lateinit var tvDate: TextView
    private lateinit var btnPickDate: Button
    private lateinit var btnSelectGroup: Button
    private lateinit var tvSelectedGroups: TextView
    private lateinit var scrollOrders: ScrollView
    private lateinit var containerOrders: LinearLayout

    // ======= 正在创建中的“新大单” =======
    private val buildingGroupIds = mutableListOf<Int>()
    private val buildingInitLevels = mutableMapOf<Int, Int>() // 1..9

    /** d1/d2/d3 构建：只保留一张单，禁止新增、删除、改单号/改内容 */
    private val singleOrderMode = BuildConfig.SEED_ORDER_NO in 1..3

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCurrentProfile = view.findViewById(R.id.tvCurrentProfile)
        tvDate = view.findViewById(R.id.tvDate)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        btnSelectGroup = view.findViewById(R.id.btnSelectGroup)
        tvSelectedGroups = view.findViewById(R.id.tvSelectedGroups)
        scrollOrders = view.findViewById(R.id.scrollOrders)
        containerOrders = view.findViewById(R.id.containerOrders)

        btnPickDate.setOnClickListener { showDatePicker() }

        if (singleOrderMode) {
            btnSelectGroup.visibility = View.GONE
        }

        btnSelectGroup.setOnClickListener {
            if (buildingGroupIds.size >= 15) {
                toast("当前新单已满 15 组，将自动生成")
                commitNewOrderIfReady()
                return@setOnClickListener
            }

            pick4Zodiacs { members ->
                val gid = sharedVM.findGroupId(members)

                if (buildingGroupIds.contains(gid)) {
                    toast("该小组已在当前新单中")
                    return@pick4Zodiacs
                }

                if (sharedVM.isGroupUsed(gid)) {
                    toast("该小组已被其它大单使用，不能重复")
                    return@pick4Zodiacs
                }

                pickInitialLevel(defaultLv = 1) { lv ->
                    buildingGroupIds.add(gid)
                    buildingInitLevels[gid] = lv
                    toast("已加入：${members.joinToString("、") { it.name }}  Lv$lv  （${buildingGroupIds.size}/15）")

                    if (buildingGroupIds.size == 15) {
                        commitNewOrderIfReady()
                    } else {
                        refreshTitle()
                    }
                }
            }
        }

        sharedVM.selectedDate.observe(viewLifecycleOwner) {
            tvDate.text = "当前日期：$it"
            renderOrders(sharedVM.orders.value ?: emptyList())
        }

        sharedVM.currentProfile.observe(viewLifecycleOwner) {
            tvCurrentProfile.text = "当前应用：${sharedVM.getCurrentProfileId()}生肖投注助手"
            buildingGroupIds.clear()
            buildingInitLevels.clear()
            renderOrders(sharedVM.orders.value ?: emptyList())
            refreshTitle()
        }

        sharedVM.orders.observe(viewLifecycleOwner) {
            renderOrders(it)
            refreshTitle()
        }

        sharedVM.drawHistory.observe(viewLifecycleOwner) {
            renderOrders(sharedVM.orders.value ?: emptyList())
        }

        sharedVM.selectedOrder.observe(viewLifecycleOwner) {
            renderOrders(sharedVM.orders.value ?: emptyList())
        }

        refreshTitle()
    }

    override fun onResume() {
        super.onResume()
        if (singleOrderMode) {
            sharedVM.invalidateLevels()
            renderOrders(sharedVM.orders.value.orEmpty())
            refreshTitle()
        }
    }

    private fun commitNewOrderIfReady() {
        if (buildingGroupIds.size != 15) {
            toast("当前新单不足 15 组：${buildingGroupIds.size}/15")
            return
        }

        try {
            sharedVM.addOrder(
                groupIds = buildingGroupIds.toList(),
                initLevels = buildingInitLevels.toMap()
            )
            toast("新增大单成功")

            buildingGroupIds.clear()
            buildingInitLevels.clear()
            refreshTitle()

            scrollOrders.post { scrollOrders.fullScroll(View.FOCUS_DOWN) }
        } catch (e: Exception) {
            toast("新增失败：${e.message}")
        }
    }

    private fun refreshTitle() {
        val orders = sharedVM.orders.value.orEmpty()
        if (singleOrderMode) {
            val order = orders.minByOrNull { it.orderNo }
            tvSelectedGroups.text = if (order != null) {
                "第 ${order.orderNo} 号单 · ${order.groupIds.size} 组"
            } else {
                "第 ? 号单"
            }
            return
        }
        val building = buildingGroupIds.size
        tvSelectedGroups.text = "大组列表（${orders.size} 单） | 正在创建：$building/15"
    }

    private fun renderOrders(orders: List<BetOrder>) {
        containerOrders.removeAllViews()
        if (singleOrderMode) {
            renderSingleOrderExpanded(orders)
            return
        }
        val selectedId = sharedVM.selectedOrder.value?.id
        val dateKey = sharedVM.getSelectedDate()

        orders.forEach { order ->
            val item = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_home_order, containerOrders, false)

            val tvTitle = item.findViewById<TextView>(R.id.tvOrderTitle)
            val tvSub = item.findViewById<TextView>(R.id.tvOrderSub)
            val btnView = item.findViewById<Button>(R.id.btnView)
            val btnEdit = item.findViewById<Button>(R.id.btnEdit)
            val btnRenumber = item.findViewById<Button>(R.id.btnRenumber)
            val btnDelete = item.findViewById<Button>(R.id.btnDelete)

            val checked = (order.id == selectedId)
            tvTitle.text = (if (checked) "✅ " else "") + "第 ${order.orderNo} 号单"
            tvSub.text = "创建日期：${order.createdDateKey}   组数：${order.groupIds.size}/15\n当前等级($dateKey)：${order.groupIds.take(6).joinToString { "Lv${sharedVM.getGroupLevelForDate(it, dateKey)}" }}"

            btnView.setOnClickListener {
                sharedVM.selectOrder(order.id)
                showOrderDialog(order, false)
            }

            btnEdit.setOnClickListener {
                sharedVM.selectOrder(order.id)
                showOrderDialog(order, true)
            }

            btnRenumber.setOnClickListener { showRenumberDialog(order) }

            btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("确认删除")
                    .setMessage("是否删除 第 ${order.orderNo} 号单？")
                    .setPositiveButton("删除") { _, _ -> sharedVM.deleteOrder(order.id) }
                    .setNegativeButton("取消", null)
                    .show()
            }

            // 点击单号（卡片）→ 选中该单并跳转到「金额」页
            item.setOnClickListener {
                sharedVM.selectOrder(order.id)
                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.navigation_home, false, true)
                    .build()
                findNavController().navigate(R.id.navigation_dashboard, null, options)
            }

            containerOrders.addView(item)
        }
    }

    /** 单张单模式：直接展开这一单的 15 个小组，并显示号码与当日金额 */
    private fun renderSingleOrderExpanded(orders: List<BetOrder>) {
        val order = orders.minByOrNull { it.orderNo } ?: return
        val dateKey = sharedVM.getSelectedDate()

        containerOrders.addView(
            buildSingleOrderHeader(
                order = order,
                dateKey = dateKey,
                totalAmount = sharedVM.totalAmountForDate(order, dateKey)
            )
        )

        order.groupIds.take(15).forEachIndexed { index, gid ->
            val members = sharedVM.getGroupMembers(gid)
            containerOrders.addView(
                buildGroupCard(
                    index = index,
                    level = sharedVM.getGroupLevelForDate(gid, dateKey),
                    zodiacs = members.toList(),
                    showNumbers = true,
                    onEdit = { showGroupLevelDialog(order, index, gid) },
                    amountPerZodiac = sharedVM.amountPerZodiacForDate(gid, dateKey)
                )
            )
        }
    }

    private fun buildSingleOrderHeader(
        order: BetOrder,
        dateKey: String,
        totalAmount: Int
    ): LinearLayout {
        val drawText = sharedVM.getDraw(dateKey)?.name ?: "未录入"
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.rgb(240, 247, 255))
                setStroke(dp(1), Color.rgb(210, 225, 245))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }

            addView(TextView(context).apply {
                text = "第 ${order.orderNo} 号单"
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "创建日期：${order.createdDateKey}   组数：${order.groupIds.size}/15"
                textSize = 13f
                setTextColor(Color.rgb(110, 110, 110))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
            addView(TextView(context).apply {
                text = "当日开奖：$drawText    当日总金额：￥$totalAmount"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(0, 122, 255))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            })
        }
    }

    private fun showOrderDialog(order: BetOrder, editable: Boolean) {
        val showNumbers = resources.getBoolean(R.bool.show_zodiac_numbers)
        val dateKey = sharedVM.getSelectedDate()

        val root = ScrollView(requireContext())
        val wrap = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(wrap)

        order.groupIds.forEachIndexed { index, gid ->
            val onEdit: (() -> Unit)? = if (editable) {
                { showEditOneGroupDialog(order, index, gid) }
            } else null

            wrap.addView(
                buildGroupCard(
                    index = index,
                    level = sharedVM.getGroupLevelForDate(gid, dateKey),
                    zodiacs = sharedVM.getGroupMembers(gid).toList(),
                    showNumbers = showNumbers,
                    onEdit = onEdit
                )
            )
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (editable) "编辑大单" else "查看大单")
            .setView(root)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun buildGroupCard(
        index: Int,
        level: Int,
        zodiacs: List<Zodiac>,
        showNumbers: Boolean,
        onEdit: (() -> Unit)?,
        amountPerZodiac: Int? = null
    ): LinearLayout {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(230, 230, 230))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        // 头部：第 N 组 + Lv 等级 +（编辑时）修改按钮
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(requireContext()).apply {
            text = "第 ${index + 1} 组"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(requireContext()).apply {
            text = "Lv$level"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(0, 122, 255))
        })
        if (onEdit != null) {
            val btn = Button(requireContext()).apply {
                text = "修改"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            }
            btn.setOnClickListener { onEdit() }
            header.addView(btn)
        }
        card.addView(header)

        if (amountPerZodiac != null) {
            card.addView(TextView(requireContext()).apply {
                text = "每个生肖 ￥$amountPerZodiac    本组小计 ￥${amountPerZodiac * zodiacs.size}"
                textSize = 12f
                setTextColor(Color.rgb(0, 122, 255))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            })
        }

        // 生肖行：4 列，每列 = 生肖名 + 号码
        val grid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        zodiacs.forEach { zodiac ->
            val cell = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            cell.addView(TextView(requireContext()).apply {
                text = zodiac.name
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            if (showNumbers) {
                cell.addView(TextView(requireContext()).apply {
                    text = ZodiacNumberTables.numbersFor(
                        zodiac,
                        ZodiacNumberTables.groupIndexForProfile(sharedVM.getCurrentProfileId())
                    ).joinToString(" ")
                    textSize = 14f
                    setTextColor(Color.rgb(130, 130, 130))
                    gravity = Gravity.CENTER
                })
            }
            grid.addView(cell)
        }
        card.addView(grid)

        return card
    }

    /** 单张单模式：只调整该小组在当前查看日期的实时等级，不修改生肖组合 */
    private fun showGroupLevelDialog(order: BetOrder, index: Int, groupId: Int) {
        val dateKey = sharedVM.getSelectedDate()
        val currentLevel = sharedVM.getGroupLevelForDate(groupId, dateKey)
        val lvNames = (1..GroupLevelEngine.MAX_LEVEL).map { "Lv$it" }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("修改第 ${index + 1} 组等级（$dateKey）")
            .setSingleChoiceItems(lvNames, currentLevel - 1) { dialog, which ->
                sharedVM.updateOrderGroupCurrentLevel(
                    orderId = order.id,
                    index = index,
                    newMembers = sharedVM.getGroupMembers(groupId),
                    newLevel = which + 1
                )
                toast("第 ${index + 1} 组已改为 Lv${which + 1}")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditOneGroupDialog(order: BetOrder, index: Int, oldGroupId: Int) {
        val zodiacs = Zodiac.values().toList()
        val names = zodiacs.map { it.name }

        val oldMembers = sharedVM.getGroupMembers(oldGroupId).toList()
        val dateKey = sharedVM.getSelectedDate()
        val oldLevel = sharedVM.getGroupLevelForDate(oldGroupId, dateKey)

        fun spinner(): Spinner {
            return Spinner(requireContext()).apply {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            }
        }

        val sp1 = spinner().apply { setSelection(zodiacs.indexOf(oldMembers.getOrNull(0))) }
        val sp2 = spinner().apply { setSelection(zodiacs.indexOf(oldMembers.getOrNull(1))) }
        val sp3 = spinner().apply { setSelection(zodiacs.indexOf(oldMembers.getOrNull(2))) }
        val sp4 = spinner().apply { setSelection(zodiacs.indexOf(oldMembers.getOrNull(3))) }

        val spLv = Spinner(requireContext()).apply {
            val levels = (1..GroupLevelEngine.MAX_LEVEL).map { "Lv$it" }
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, levels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection((oldLevel - 1).coerceIn(0, GroupLevelEngine.MAX_LEVEL - 1))
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        root.addView(sp1)
        root.addView(sp2)
        root.addView(sp3)
        root.addView(sp4)
        root.addView(spLv)

        AlertDialog.Builder(requireContext())
            .setTitle("编辑小组（序号 ${index + 1}）")
            .setView(root)
            .setPositiveButton("保存") { _, _ ->
                val members = setOf(
                    zodiacs[sp1.selectedItemPosition],
                    zodiacs[sp2.selectedItemPosition],
                    zodiacs[sp3.selectedItemPosition],
                    zodiacs[sp4.selectedItemPosition]
                )

                if (members.size != 4) {
                    toast("4 个生肖不能重复")
                    return@setPositiveButton
                }

                val lv = spLv.selectedItemPosition + 1

                sharedVM.updateOrderGroupCurrentLevel(
                    orderId = order.id,
                    index = index,
                    newMembers = members,
                    newLevel = lv
                )

                toast("已更新第 ${index + 1} 组")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenumberDialog(order: BetOrder) {
        val et = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(requireContext())
            .setTitle("改单号（原：${order.orderNo}）")
            .setView(et)
            .setPositiveButton("确定") { _, _ ->
                val newNo = et.text.toString().toIntOrNull()
                if (newNo != null && newNo > 0) {
                    sharedVM.updateOrderNo(order.id, newNo)
                } else toast("请输入合法单号")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pick4Zodiacs(onDone: (Set<Zodiac>) -> Unit) {
        val z = Zodiac.values()
        val names = z.map { it.name }.toTypedArray()
        val checked = BooleanArray(z.size)
        val selected = mutableSetOf<Int>()

        AlertDialog.Builder(requireContext())
            .setTitle("请选择 4 个生肖")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                if (isChecked) selected.add(which) else selected.remove(which)
            }
            .setPositiveButton("确定") { _, _ ->
                if (selected.size == 4) onDone(selected.map { z[it] }.toSet())
                else toast("必须选择 4 个生肖")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickInitialLevel(defaultLv: Int = 1, onDone: (Int) -> Unit) {
        val lvNames = (1..GroupLevelEngine.MAX_LEVEL).map { "Lv$it" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("设置初始等级")
            .setSingleChoiceItems(lvNames, defaultLv - 1) { dialog, which ->
                onDone(which + 1)
                dialog.dismiss()
            }
            .show()
    }

    private fun showDatePicker() {
        val cur = sharedVM.getSelectedDate()
        val parts = cur.split("-")
        val cal = Calendar.getInstance()
        if (parts.size == 3) {
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        DatePickerDialog(localizedDatePickerContext(requireContext()),
            { _, y, m, d ->
                val key = "%04d-%02d-%02d".format(y, m + 1, d)
                sharedVM.setSelectedDate(key)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).showLocalized()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
