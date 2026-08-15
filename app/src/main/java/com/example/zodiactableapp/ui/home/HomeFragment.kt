package com.example.zodiactableapp.ui.home

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.zodiactableapp.R
import com.example.zodiactableapp.model.Zodiac
import com.example.zodiactableapp.shared.BetOrder
import com.example.zodiactableapp.shared.BetSharedViewModel
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
    private val buildingInitLevels = mutableMapOf<Int, Int>() // 1..15

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCurrentProfile = view.findViewById(R.id.tvCurrentProfile)
        tvDate = view.findViewById(R.id.tvDate)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        btnSelectGroup = view.findViewById(R.id.btnSelectGroup)
        tvSelectedGroups = view.findViewById(R.id.tvSelectedGroups)
        scrollOrders = view.findViewById(R.id.scrollOrders)
        containerOrders = view.findViewById(R.id.containerOrders)

        btnPickDate.setOnClickListener { showDatePicker() }

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
        val orderCount = sharedVM.orders.value?.size ?: 0
        val building = buildingGroupIds.size
        tvSelectedGroups.text = "大组列表（$orderCount 单） | 正在创建：$building/15"
    }

    private fun renderOrders(orders: List<BetOrder>) {
        containerOrders.removeAllViews()
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

            containerOrders.addView(item)
        }
    }

    private fun showOrderDialog(order: BetOrder, editable: Boolean) {
        val root = ScrollView(requireContext())
        val wrap = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        root.addView(wrap)

        val dateKey = sharedVM.getSelectedDate()

        order.groupIds.forEachIndexed { index, gid ->
            val members = sharedVM.getGroupMembers(gid).joinToString("、") { it.name }
            val curLv = sharedVM.getGroupLevelForDate(gid, dateKey)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tv = TextView(requireContext()).apply {
                text = "${index + 1}. $members   Lv$curLv"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(tv)

            if (editable) {
                val btn = Button(requireContext()).apply { text = "修改" }
                btn.setOnClickListener { showEditOneGroupDialog(order, index, gid) }
                row.addView(btn)
            }

            wrap.addView(row)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (editable) "编辑大单" else "查看大单")
            .setView(root)
            .setPositiveButton("关闭", null)
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
            val levels = (1..15).map { "Lv$it" }
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, levels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection((oldLevel - 1).coerceIn(0, 14))
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
        val lvNames = (1..15).map { "Lv$it" }.toTypedArray()
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
        DatePickerDialog(requireContext(),
            { _, y, m, d ->
                val key = "%04d-%02d-%02d".format(y, m + 1, d)
                sharedVM.setSelectedDate(key)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
