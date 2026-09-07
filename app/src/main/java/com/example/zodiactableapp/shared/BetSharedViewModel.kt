package com.example.zodiactableapp.shared

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.zodiactableapp.model.Zodiac
import com.example.zodiactableapp.shared.db.AppDatabase
import com.example.zodiactableapp.shared.db.DrawEntity
import com.example.zodiactableapp.shared.db.GroupInitEntity
import com.example.zodiactableapp.shared.db.LevelOverrideEntity
import com.example.zodiactableapp.shared.db.OrderEntity
import com.example.zodiactableapp.shared.db.OrderItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BetOrder(
    val id: Long,
    var orderNo: Int,
    val groupIds: List<Int>,      // 顺序=pos(1..15)
    val createdDateKey: String
)

/** 某个档案在 12 个生肖上的「小计」 */
data class ProfileSubtotal(
    val profileId: Int,
    val zodiacSubtotals: Map<Zodiac, Long>
)

class BetSharedViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val INITIAL_PROFILE_COUNT = 12
        private const val PROFILE_PREFS = "workspace_prefs"
        private const val KEY_CURRENT_PROFILE = "current_profile"
        private const val KEY_PROFILE_IDS = "profile_ids"
        private const val KEY_PROFILE_COUNT_LEGACY = "profile_count"
        private const val PROFILE_META_PREFS = "zodiac_table_prefs"
        private const val KEY_YEAR_ZODIAC_PREFIX = "year_zodiac_profile_"
        private const val LEGACY_DB_NAME = "zodiac_table.db"
        private const val DEFAULT_SEED_ORDER_COUNT = 1
        private const val LEGACY_SEED_ORDER_COUNT = 15
        private const val DEFAULT_SEED_VERSION = 2
        private const val DEFAULT_SEED_DATE = "2026-02-20"
        private const val KEY_DEFAULT_SEED_VERSION_PREFIX = "default_seed_version_"
        private const val SEED_ORDER_ID_BASE = 1700000000000L
    }

    private val appContext = app.applicationContext
    private val profilePrefs =
        appContext.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
    private var loadProfileJob: Job? = null
    private val profileIdsValue = loadProfileIds().toMutableList()
    private var currentProfileId: Int = loadSavedProfileId()

    private val _profileIds = MutableLiveData(profileIdsValue.toList())
    val profileIds: LiveData<List<Int>> = _profileIds

    private val _currentProfile = MutableLiveData(currentProfileId)
    val currentProfile: LiveData<Int> = _currentProfile

    // ========= Room：DB 连接 =========
    private var db = openDatabaseForProfile(currentProfileId)
    private var drawDao = db.drawDao()
    private var groupInitDao = db.groupInitDao()
    private var orderDao = db.orderDao()
    private var levelOverrideDao = db.levelOverrideDao()

    // ========= 基础：495 组合 =========
    private val allGroups: List<Set<Zodiac>> = buildAllGroups()

    fun findGroupId(members: Set<Zodiac>): Int {
        require(members.size == 4) { "必须选择 4 个生肖" }
        val idx = allGroups.indexOf(members)
        require(idx >= 0) { "非法组合：$members" }
        return idx
    }

    fun getGroupMembers(groupId: Int): Set<Zodiac> {
        require(groupId in allGroups.indices) { "groupId 越界：$groupId" }
        return allGroups[groupId]
    }

    // ========= 当前查看日期（Home/Dash 都显示它） =========
    private val _selectedDate = MutableLiveData(loadSelectedDateForProfile(currentProfileId))
    val selectedDate: LiveData<String> = _selectedDate

    fun setSelectedDate(dateKey: String) {
        _selectedDate.value = dateKey
        profilePrefs.edit().putString(selectedDateKey(currentProfileId), dateKey).apply()
        clearLevelCache()
    }

    fun getSelectedDate(): String = _selectedDate.value ?: loadSelectedDateForProfile(currentProfileId)

    fun getCurrentProfileId(): Int = _currentProfile.value ?: currentProfileId

    fun getCurrentProfileLabel(): String = "${getCurrentProfileId()}号档案"

    fun getProfileIds(): List<Int> = _profileIds.value ?: profileIdsValue.toList()

    fun createProfile(): Int {
        val nextProfileId = (profileIdsValue.maxOrNull() ?: 0) + 1
        profileIdsValue.add(nextProfileId)
        persistProfileIds()
        _profileIds.value = profileIdsValue.toList()
        return nextProfileId
    }

    fun deleteProfile(profileId: Int): Boolean {
        if (!profileIdsValue.contains(profileId) || profileIdsValue.size <= 1) return false

        profileIdsValue.remove(profileId)
        persistProfileIds()
        _profileIds.value = profileIdsValue.toList()

        profilePrefs.edit().remove(selectedDateKey(profileId)).apply()
        appContext.getSharedPreferences(PROFILE_META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_YEAR_ZODIAC_PREFIX$profileId")
            .apply()
        AppDatabase.delete(appContext, databaseNameForProfile(profileId))

        if (currentProfileId == profileId) {
            val fallbackId = profileIdsValue.first()
            activateProfile(fallbackId, forceReload = true)
        }
        return true
    }

    fun switchProfile(profileId: Int) {
        if (!profileIdsValue.contains(profileId)) return
        activateProfile(profileId)
    }

    // ========= 大组（固定保留，不清空） =========
    private val ordersInternal = mutableListOf<BetOrder>()

    private val _orders = MutableLiveData<List<BetOrder>>(emptyList())
    val orders: LiveData<List<BetOrder>> = _orders

    private val _selectedOrder = MutableLiveData<BetOrder?>(null)
    val selectedOrder: LiveData<BetOrder?> = _selectedOrder

    // groupId 全局占用：跨大组不允许重复
    private val usedGroupIds = HashSet<Int>()
    fun isGroupUsed(groupId: Int): Boolean = usedGroupIds.contains(groupId)

    // ========= 按日期记录开奖（历史可回看） =========
    private val draws = linkedMapOf<String, Zodiac>() // dateKey -> drawZodiac
    private val _drawHistory = MutableLiveData<Map<String, Zodiac>>(emptyMap())
    val drawHistory: LiveData<Map<String, Zodiac>> = _drawHistory

    fun recordDraw(dateKey: String, zodiac: Zodiac) {
        draws[dateKey] = zodiac
        _drawHistory.value = draws.toMap()
        clearLevelCache()

        // ✅ 持久化保存：同步写入所有档案（含当前档案），保证 11 个 app 的当日生肖一致
        viewModelScope.launch(Dispatchers.IO) {
            profileIdsValue.forEach { pid ->
                AppDatabase.get(appContext, databaseNameForProfile(pid))
                    .drawDao()
                    .upsert(DrawEntity(dateKey = dateKey, zodiac = zodiac))
            }
        }
    }

    fun getDraw(dateKey: String): Zodiac? = draws[dateKey]

    // ========= 组合激活/初始等级（新增大组要重新定级） =========
    private val activationDate: Array<String?> = Array(GroupLevelEngine.GROUP_COUNT) { null }
    private val initialLevel: IntArray = IntArray(GroupLevelEngine.GROUP_COUNT) { GroupLevelEngine.MIN_LEVEL }

    /** Home/编辑显示用：取某个小组的“初始等级”（首次进入大组时定的） */
    fun getInitialLevel(groupId: Int): Int {
        require(groupId in 0 until GroupLevelEngine.GROUP_COUNT) { "groupId 越界：$groupId" }
        if (activationDate[groupId] == null) return GroupLevelEngine.MIN_LEVEL
        return initialLevel[groupId].coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
    }

    // ========= ✅ 新增：按日期覆盖的“实时等级” =========
    // dateKey -> (groupId -> level)
    private val levelOverrides: MutableMap<String, MutableMap<Int, Int>> = linkedMapOf()

    private fun putOverride(dateKey: String, groupId: Int, level: Int) {
        val map = levelOverrides.getOrPut(dateKey) { linkedMapOf() }
        map[groupId] = level
    }

    private fun getOverride(dateKey: String, groupId: Int): Int? {
        return levelOverrides[dateKey]?.get(groupId)
    }

    // ========= 历史等级计算（按日期缓存） =========
    private val engine = GroupLevelEngine(allGroups)
    private val levelCache = HashMap<String, IntArray>() // dateKey -> levels(495)

    private fun clearLevelCache() {
        levelCache.clear()
    }

    /** Dashboard 用：取“当前查看日期”的等级 */
    fun getGroupLevel(groupId: Int): Int {
        return getGroupLevelForDate(groupId, getSelectedDate())
    }

    /** Dashboard/Home 显示/导出用：按指定日期取等级（实时等级） */
    fun getGroupLevelForDate(groupId: Int, dateKey: String): Int {
        val levels = computeLevelsForDate(dateKey)
        return levels.getOrNull(groupId) ?: GroupLevelEngine.MIN_LEVEL
    }

    private fun computeLevelsForDate(dateKey: String): IntArray {
        levelCache[dateKey]?.let { return it }

        val dateSet = HashSet<String>()
        draws.keys.forEach { if (it <= dateKey) dateSet.add(it) }
        activationDate.forEach { d -> if (d != null && d <= dateKey) dateSet.add(d) }
        // ✅ override 的日期也要参与推演链（否则无法影响后续）
        levelOverrides.keys.forEach { if (it <= dateKey) dateSet.add(it) }

        val dates = dateSet.toList().sorted()
        val levels = IntArray(GroupLevelEngine.GROUP_COUNT) { GroupLevelEngine.MIN_LEVEL }
        val activeBet = BooleanArray(GroupLevelEngine.GROUP_COUNT) { false }

        for (d in dates) {
            // 1) 当天激活：设置初始等级 + 标记已激活（参与 contains-draw 置1 规则）
            for (gid in 0 until GroupLevelEngine.GROUP_COUNT) {
                if (activationDate[gid] == d) {
                    activeBet[gid] = true
                    levels[gid] = initialLevel[gid].coerceIn(
                        GroupLevelEngine.MIN_LEVEL,
                        GroupLevelEngine.MAX_LEVEL
                    )
                }
            }

            // 2) 当天有开奖才推进
            val draw = draws[d]
            if (draw != null) {
                val next = engine.evolveOneDay(levels, activeBet, draw)
                for (i in levels.indices) levels[i] = next[i]
            }

            // ✅ 3) 当天手动修改“实时等级”：在当天推演后覆盖，影响后续日期
            val overridesToday = levelOverrides[d]
            if (!overridesToday.isNullOrEmpty()) {
                overridesToday.forEach { (gid, lv) ->
                    if (gid in 0 until GroupLevelEngine.GROUP_COUNT) {
                        levels[gid] = lv.coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
                    }
                }
            }
        }

        levelCache[dateKey] = levels
        return levels
    }

    // ========= 对外：新增/删除/改序号/选中 =========

    /**
     * 新增一单（15 个 groupId）并为每个小组设置初始等级（1..15）
     * createdDateKey = 当前 selectedDate
     */
    fun addOrder(
        groupIds: List<Int>,
        initLevels: Map<Int, Int>
    ) {
        val cleaned = groupIds.distinct().take(GroupLevelEngine.ORDER_GROUP_COUNT)
        require(cleaned.size == GroupLevelEngine.ORDER_GROUP_COUNT) { "一单必须正好 ${GroupLevelEngine.ORDER_GROUP_COUNT} 个小组" }
        require(cleaned.all { it in 0 until GroupLevelEngine.GROUP_COUNT }) { "存在非法 groupId" }
        require(cleaned.all { !isGroupUsed(it) }) { "存在已被其他大组使用的小组" }

        cleaned.forEach { usedGroupIds.add(it) }

        val dateKey = getSelectedDate()

        // ✅ 写入 activation/initLevel（只在首次出现时才真正意义）
        for (gid in cleaned) {
            if (activationDate[gid] == null) {
                activationDate[gid] = dateKey
                initialLevel[gid] = (initLevels[gid] ?: GroupLevelEngine.MIN_LEVEL).coerceIn(
                    GroupLevelEngine.MIN_LEVEL,
                    GroupLevelEngine.MAX_LEVEL
                )
            }
        }

        val nextNo = (ordersInternal.maxOfOrNull { it.orderNo } ?: 0) + 1
        val orderId = System.currentTimeMillis() + nextNo

        val order = BetOrder(
            id = orderId,
            orderNo = nextNo,
            groupIds = cleaned,
            createdDateKey = dateKey
        )

        ordersInternal.add(order)
        publishOrders()
        _selectedOrder.value = order
        clearLevelCache()

        // ✅ 持久化保存：orders + items + group_init(仅首次)
        viewModelScope.launch(Dispatchers.IO) {
            orderDao.upsertOrder(OrderEntity(id = orderId, orderNo = nextNo, createdDateKey = dateKey))

            val items = cleaned.mapIndexed { idx, gid ->
                OrderItemEntity(
                    orderId = orderId,
                    pos = idx + 1,
                    groupId = gid,
                    initLevel = (initLevels[gid] ?: GroupLevelEngine.MIN_LEVEL).coerceIn(
                        GroupLevelEngine.MIN_LEVEL,
                        GroupLevelEngine.MAX_LEVEL
                    )
                )
            }
            orderDao.upsertItems(items)

            // group_init：如果该 groupId 之前从未激活过，写入一次
            cleaned.forEach { gid ->
                if (groupInitDao.get(gid) == null) {
                    groupInitDao.upsert(
                        GroupInitEntity(
                            groupId = gid,
                            activationDateKey = activationDate[gid] ?: dateKey,
                            initLevel = initialLevel[gid]
                        )
                    )
                }
            }
        }
    }

    fun deleteOrder(orderId: Long) {
        val target = ordersInternal.firstOrNull { it.id == orderId } ?: return
        ordersInternal.remove(target)

        val releasedGroupIds = target.groupIds.filter { gid ->
            ordersInternal.none { order -> order.groupIds.contains(gid) }
        }

        // 释放占用（允许后续再建单使用这些 groupId）
        clearReleasedGroupState(releasedGroupIds)

        if (_selectedOrder.value?.id == orderId) _selectedOrder.value = null
        publishOrders()
        clearLevelCache()

        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                orderDao.deleteOrder(orderId) // CASCADE 自动删 items
                releasedGroupIds.forEach { gid ->
                    groupInitDao.delete(gid)
                    levelOverrideDao.deleteByGroupId(gid)
                }
            }
        }
    }

    private fun clearReleasedGroupState(groupIds: List<Int>) {
        groupIds.forEach { gid ->
            usedGroupIds.remove(gid)
            if (gid in 0 until GroupLevelEngine.GROUP_COUNT) {
                activationDate[gid] = null
                initialLevel[gid] = GroupLevelEngine.MIN_LEVEL
            }
        }

        levelOverrides.values.forEach { overrides ->
            groupIds.forEach { gid -> overrides.remove(gid) }
        }
        levelOverrides
            .filterValues { it.isEmpty() }
            .keys
            .toList()
            .forEach { dateKey -> levelOverrides.remove(dateKey) }
    }

    fun updateOrderNo(orderId: Long, newOrderNo: Int) {
        val target = ordersInternal.firstOrNull { it.id == orderId } ?: return
        target.orderNo = newOrderNo.coerceAtLeast(1)
        publishOrders()

        viewModelScope.launch(Dispatchers.IO) {
            orderDao.updateOrderNo(orderId, target.orderNo)
        }
    }

    fun selectOrder(orderId: Long) {
        _selectedOrder.value = ordersInternal.firstOrNull { it.id == orderId }
    }

    private fun publishOrders() {
        _orders.value = ordersInternal.sortedBy { it.orderNo }
    }

    // ========= ✅ Home“编辑”用：改生肖 + 改“当前日期实时等级” =========
    /**
     * @param index 0..14（对应 pos=1..15）
     * @param newLevel 1..15（实时等级覆盖）
     */
    fun updateOrderGroupCurrentLevel(
        orderId: Long,
        index: Int,
        newMembers: Set<Zodiac>,
        newLevel: Int
    ) {
        require(index in 0 until GroupLevelEngine.ORDER_GROUP_COUNT) { "index 必须在 0..${GroupLevelEngine.ORDER_GROUP_COUNT - 1}" }
        require(newMembers.size == 4) { "必须是4个不重复生肖" }

        val order = ordersInternal.firstOrNull { it.id == orderId }
            ?: error("找不到订单：$orderId")

        val dateKey = getSelectedDate()

        val oldIds = order.groupIds.toMutableList()
        val oldGid = oldIds[index]
        val newGid = findGroupId(newMembers)
        val lv = newLevel.coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
        var changedGroup = false
        var releasedGroupIds: List<Int> = emptyList()

        // 1) 如果换了 gid：先做占用校验 + 更新 orderItem
        if (newGid != oldGid) {
            val usedByOther = usedGroupIds.contains(newGid) && !oldIds.contains(newGid)
            require(!usedByOther) { "该小组已被其它大组使用，不能重复" }
            require(!oldIds.contains(newGid)) { "本单内已存在该小组" }

            oldIds[index] = newGid

            releasedGroupIds = listOf(oldGid).filter { gid ->
                ordersInternal.none { existingOrder ->
                    existingOrder.id != orderId && existingOrder.groupIds.contains(gid)
                }
            }
            clearReleasedGroupState(releasedGroupIds)
            activateGroupForOrder(newGid, dateKey, lv)
            usedGroupIds.add(newGid)

            val newOrder = order.copy(groupIds = oldIds)
            val i = ordersInternal.indexOfFirst { it.id == orderId }
            ordersInternal[i] = newOrder
            if (_selectedOrder.value?.id == orderId) _selectedOrder.value = newOrder
            changedGroup = true
        }

        // 2) ✅ “改实时等级”：写入 override（当天推演后覆盖，影响后续）
        putOverride(dateKey, newGid, lv)
        clearLevelCache()
        publishOrders()

        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                if (changedGroup) {
                    orderDao.updateItem(
                        orderId = orderId,
                        pos = index + 1,
                        groupId = newGid,
                        initLevel = getInitialLevel(newGid)
                    )
                    releasedGroupIds.forEach { gid ->
                        groupInitDao.delete(gid)
                        levelOverrideDao.deleteByGroupId(gid)
                    }
                    groupInitDao.upsert(
                        GroupInitEntity(
                            groupId = newGid,
                            activationDateKey = dateKey,
                            initLevel = getInitialLevel(newGid)
                        )
                    )
                }
                levelOverrideDao.upsert(LevelOverrideEntity(dateKey = dateKey, groupId = newGid, level = lv))
            }
        }
        // ✅ 强制触发 orders LiveData 刷新
        _orders.value = ordersInternal.sortedBy { it.orderNo }
    }

    private fun activateGroupForOrder(groupId: Int, dateKey: String, level: Int) {
        if (groupId !in 0 until GroupLevelEngine.GROUP_COUNT) return
        activationDate[groupId] = dateKey
        initialLevel[groupId] = level.coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
    }

    // ========= ✅ 首次启动：恢复 + 若DB空则注入默认15单 =========
    init {
        loadActiveProfile()
    }

    private suspend fun restoreFromDb() {
        // 1) draws
        draws.clear()
        drawDao.getAll().forEach { draws[it.dateKey] = it.zodiac }
        _drawHistory.postValue(draws.toMap())

        // 2) group_init
        for (i in 0 until GroupLevelEngine.GROUP_COUNT) {
            activationDate[i] = null
            initialLevel[i] = GroupLevelEngine.MIN_LEVEL
        }
        groupInitDao.getAll().forEach { gi ->
            if (gi.groupId in 0 until GroupLevelEngine.GROUP_COUNT) {
                activationDate[gi.groupId] = gi.activationDateKey
                initialLevel[gi.groupId] = gi.initLevel.coerceIn(
                    GroupLevelEngine.MIN_LEVEL,
                    GroupLevelEngine.MAX_LEVEL
                )
            }
        }

        // 3) ✅ overrides
        levelOverrides.clear()
        levelOverrideDao.getAll().forEach { e ->
            putOverride(e.dateKey, e.groupId, e.level)
        }

        // 4) orders + items
        ordersInternal.clear()
        usedGroupIds.clear()
        _selectedOrder.postValue(null)

        val all = orderDao.getAllWithItems()
        all.forEach { ow ->
            val items = ow.items.sortedBy { it.pos }
            val gids = items.map { it.groupId }
            gids.forEach { usedGroupIds.add(it) }

            ordersInternal.add(
                BetOrder(
                    id = ow.order.id,
                    orderNo = ow.order.orderNo,
                    groupIds = gids,
                    createdDateKey = ow.order.createdDateKey
                )
            )
        }

        _orders.postValue(ordersInternal.sortedBy { it.orderNo })
        clearLevelCache()
    }

    private data class SeedItem(val members: Set<Zodiac>, val level: Int)

    private fun legacySeedOrderIds(): Set<Long> =
        (1..LEGACY_SEED_ORDER_COUNT).map { defaultSeedOrderId(it) }.toSet()

    private fun defaultSeedVersionKey(profileId: Int): String =
        "$KEY_DEFAULT_SEED_VERSION_PREFIX$profileId"

    private fun markDefaultSeedCurrent(profileId: Int) {
        profilePrefs.edit()
            .putInt(defaultSeedVersionKey(profileId), DEFAULT_SEED_VERSION)
            .apply()
    }

    private fun matchesCurrentDefaultSeed(order: BetOrder): Boolean {
        val orderNo = (order.id - SEED_ORDER_ID_BASE).toInt()
        if (orderNo !in 1..DEFAULT_SEED_ORDER_COUNT) return false

        val expectedGroupIds = defaultSeedItems()[orderNo - 1].map { findGroupId(it.members) }
        return order.groupIds == expectedGroupIds
    }

    private suspend fun replaceLegacyDefaultSeedIfNeeded(profileId: Int): Boolean {
        val legacySeedIds = legacySeedOrderIds()
        val currentSeedIds = defaultSeedOrderIds()
        val legacyOrders = ordersInternal.filter { it.id in legacySeedIds }
        val hasLegacyExtraOrders = legacyOrders.any { it.id !in currentSeedIds }
        val seedVersion = profilePrefs.getInt(defaultSeedVersionKey(profileId), 0)

        if (seedVersion >= DEFAULT_SEED_VERSION && !hasLegacyExtraOrders) return false

        if (legacyOrders.isEmpty()) {
            markDefaultSeedCurrent(profileId)
            return false
        }

        val hasMissingCurrentSeedOrders = currentSeedIds.any { id ->
            legacyOrders.none { it.id == id }
        }
        val hasChangedCurrentSeedContent = legacyOrders
            .filter { it.id in currentSeedIds }
            .any { !matchesCurrentDefaultSeed(it) }

        if (!hasLegacyExtraOrders && !hasMissingCurrentSeedOrders && !hasChangedCurrentSeedContent) {
            markDefaultSeedCurrent(profileId)
            return false
        }

        val legacyGroupIds = legacyOrders.flatMap { it.groupIds }.toSet()
        val remainingGroupIds = ordersInternal
            .asSequence()
            .filter { it.id !in legacySeedIds }
            .flatMap { it.groupIds.asSequence() }
            .toSet()
        val releasedGroupIds = legacyGroupIds - remainingGroupIds
        val seed = defaultSeedItems()
        validateDefaultSeed(seed)

        db.withTransaction {
            legacySeedIds.forEach { orderId ->
                orderDao.deleteItemsOfOrder(orderId)
                orderDao.deleteOrder(orderId)
            }
            releasedGroupIds.forEach { gid ->
                groupInitDao.delete(gid)
                levelOverrideDao.deleteByGroupId(gid)
            }
            insertDefaultSeedOrders(seed, existingSeedIds = emptySet())
        }

        markDefaultSeedCurrent(profileId)
        return true
    }

    private fun bindDatabase(profileId: Int) {
        db = openDatabaseForProfile(profileId)
        drawDao = db.drawDao()
        groupInitDao = db.groupInitDao()
        orderDao = db.orderDao()
        levelOverrideDao = db.levelOverrideDao()
    }

    private fun activateProfile(profileId: Int, forceReload: Boolean = false) {
        if (!forceReload && profileId == currentProfileId) return

        currentProfileId = profileId
        profilePrefs.edit().putInt(KEY_CURRENT_PROFILE, profileId).apply()
        _currentProfile.value = profileId
        _selectedDate.value = loadSelectedDateForProfile(profileId)

        bindDatabase(profileId)
        clearInMemoryProfileState()
        loadActiveProfile()
    }

    private fun openDatabaseForProfile(profileId: Int): AppDatabase {
        return AppDatabase.get(appContext, databaseNameForProfile(profileId))
    }

    private fun databaseNameForProfile(profileId: Int): String {
        return if (profileId == 1) LEGACY_DB_NAME else "zodiac_table_profile_${profileId}.db"
    }

    private fun loadSavedProfileId(): Int {
        val savedId = profilePrefs.getInt(KEY_CURRENT_PROFILE, 1)
        return if (profileIdsValue.contains(savedId)) savedId else profileIdsValue.firstOrNull() ?: 1
    }

    private fun loadProfileIds(): List<Int> {
        val stored = profilePrefs.getString(KEY_PROFILE_IDS, null)
        val parsed = stored
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        if (parsed.isNotEmpty()) return parsed

        val legacyCount = profilePrefs
            .getInt(KEY_PROFILE_COUNT_LEGACY, INITIAL_PROFILE_COUNT)
            .coerceAtLeast(INITIAL_PROFILE_COUNT)
        return (1..legacyCount).toList()
    }

    private fun persistProfileIds() {
        profilePrefs.edit()
            .putString(KEY_PROFILE_IDS, profileIdsValue.joinToString(","))
            .apply()
    }

    private fun loadSelectedDateForProfile(profileId: Int): String {
        return profilePrefs.getString(selectedDateKey(profileId), null) ?: todayKey()
    }

    private fun selectedDateKey(profileId: Int): String = "selected_date_$profileId"

    private fun clearInMemoryProfileState() {
        draws.clear()
        _drawHistory.value = emptyMap()

        ordersInternal.clear()
        _orders.value = emptyList()
        _selectedOrder.value = null
        usedGroupIds.clear()

        levelOverrides.clear()
        for (i in 0 until GroupLevelEngine.GROUP_COUNT) {
            activationDate[i] = null
            initialLevel[i] = GroupLevelEngine.MIN_LEVEL
        }

        clearLevelCache()
    }

    private fun loadActiveProfile() {
        loadProfileJob?.cancel()
        val profileId = currentProfileId
        loadProfileJob = viewModelScope.launch(Dispatchers.IO) {
            restoreFromDb()

            val migratedDefaultSeed = replaceLegacyDefaultSeedIfNeeded(profileId)
            if (migratedDefaultSeed) {
                restoreFromDb()
            }

            val seedOrderIds = defaultSeedOrderIds()
            val existingSeedIds = ordersInternal
                .map { it.id }
                .filter { it in seedOrderIds }
                .toSet()
            val isPartialDefaultSeed = existingSeedIds.isNotEmpty() &&
                existingSeedIds.size < DEFAULT_SEED_ORDER_COUNT &&
                ordersInternal.all { it.id in seedOrderIds }

            if (ordersInternal.isEmpty() || isPartialDefaultSeed) {
                seedDefault15OrdersToDb(existingSeedIds)
                markDefaultSeedCurrent(profileId)
                restoreFromDb()
            }
        }
    }

    /**
     * 写入默认种子单；如果某个入口曾被半初始化，只补缺失的默认单。
     * 放在事务里执行，避免切换入口时留下半写入状态。
     */
    private fun defaultSeedOrderId(orderNo: Int): Long = SEED_ORDER_ID_BASE + orderNo

    private fun defaultSeedOrderIds(): Set<Long> =
        (1..DEFAULT_SEED_ORDER_COUNT).map { defaultSeedOrderId(it) }.toSet()

    private fun defaultSeedItems(): List<List<SeedItem>> = listOf(
        listOf(
            SeedItem(setOf(Zodiac.鼠, Zodiac.牛, Zodiac.虎, Zodiac.兔), 1),
            SeedItem(setOf(Zodiac.龙, Zodiac.蛇, Zodiac.马, Zodiac.羊), 1),
            SeedItem(setOf(Zodiac.猴, Zodiac.鸡, Zodiac.狗, Zodiac.猪), 1)
        )
    )

    private fun validateDefaultSeed(seed: List<List<SeedItem>>) {
        require(seed.size == DEFAULT_SEED_ORDER_COUNT)
        require(seed.all { it.size == GroupLevelEngine.ORDER_GROUP_COUNT })
    }

    private suspend fun seedDefault15OrdersToDb(existingSeedIds: Set<Long> = emptySet()) {
        val seed = defaultSeedItems()
        validateDefaultSeed(seed)

        db.withTransaction {
            insertDefaultSeedOrders(seed, existingSeedIds)
        }
    }

    private suspend fun insertDefaultSeedOrders(
        seed: List<List<SeedItem>>,
        existingSeedIds: Set<Long>
    ) {
        seed.forEachIndexed { idxOrder, orderItems ->
            val orderNo = idxOrder + 1
            val orderId = defaultSeedOrderId(orderNo)
            if (orderId in existingSeedIds) return@forEachIndexed

            val groupIds = ArrayList<Int>(orderItems.size)
            val initMap = HashMap<Int, Int>(orderItems.size)

            orderItems.forEach { item ->
                val gid = findGroupId(item.members)
                groupIds.add(gid)
                initMap[gid] = item.level.coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
            }

            orderDao.upsertOrder(
                OrderEntity(
                    id = orderId,
                    orderNo = orderNo,
                    createdDateKey = DEFAULT_SEED_DATE
                )
            )

            val items = groupIds.mapIndexed { i, gid ->
                OrderItemEntity(
                    orderId = orderId,
                    pos = i + 1,
                    groupId = gid,
                    initLevel = initMap[gid] ?: GroupLevelEngine.MIN_LEVEL
                )
            }
            orderDao.upsertItems(items)

            groupIds.forEach { gid ->
                if (groupInitDao.get(gid) == null) {
                    groupInitDao.upsert(
                        GroupInitEntity(
                            groupId = gid,
                            activationDateKey = DEFAULT_SEED_DATE,
                            initLevel = initMap[gid] ?: GroupLevelEngine.MIN_LEVEL
                        )
                    )
                }
            }
        }
    }

    // ========= 汇总表小计：供“号码价值表”跨档案取用 =========

    private val amountA = intArrayOf(
        10, 15, 25, 35, 55, 80, 125, 190, 290, 440, 665, 1010, 1530, 2320, 3530
    )
    private val amountB = intArrayOf(
        10, 20, 30, 45, 70, 110, 175, 270, 425, 670, 1045, 1640, 2570, 4050, 6500
    )

    private fun moneyByLevel(level: Int, members: Set<Zodiac>, yearZodiac: Zodiac): Int {
        val lv = level.coerceIn(1, 15)
        val arr = if (members.contains(yearZodiac)) amountB else amountA
        return arr[lv - 1]
    }

    private fun loadYearZodiac(profileId: Int): Zodiac {
        val name = appContext
            .getSharedPreferences(PROFILE_META_PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_YEAR_ZODIAC_PREFIX$profileId", null)
        return runCatching { if (name.isNullOrBlank()) Zodiac.values()[0] else Zodiac.valueOf(name) }
            .getOrElse { Zodiac.values()[0] }
    }

    /** 纯函数：给定某档案的开奖/激活/覆盖，按日期推进出 495 个小组的等级 */
    private fun computeLevelsForProfile(
        dateKey: String,
        draws: Map<String, Zodiac>,
        activationDate: Array<String?>,
        initialLevel: IntArray,
        levelOverrides: Map<String, Map<Int, Int>>
    ): IntArray {
        val dateSet = HashSet<String>()
        draws.keys.forEach { if (it <= dateKey) dateSet.add(it) }
        activationDate.forEach { d -> if (d != null && d <= dateKey) dateSet.add(d) }
        levelOverrides.keys.forEach { if (it <= dateKey) dateSet.add(it) }
        val dates = dateSet.toList().sorted()
        val levels = IntArray(GroupLevelEngine.GROUP_COUNT) { GroupLevelEngine.MIN_LEVEL }
        val activeBet = BooleanArray(GroupLevelEngine.GROUP_COUNT) { false }
        for (d in dates) {
            for (gid in 0 until GroupLevelEngine.GROUP_COUNT) {
                if (activationDate[gid] == d) {
                    activeBet[gid] = true
                    levels[gid] = initialLevel[gid].coerceIn(
                        GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL
                    )
                }
            }
            val draw = draws[d]
            if (draw != null) {
                val next = engine.evolveOneDay(levels, activeBet, draw)
                for (i in levels.indices) levels[i] = next[i]
            }
            val overridesToday = levelOverrides[d]
            if (!overridesToday.isNullOrEmpty()) {
                overridesToday.forEach { (gid, lv) ->
                    if (gid in 0 until GroupLevelEngine.GROUP_COUNT) {
                        levels[gid] = lv.coerceIn(GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL)
                    }
                }
            }
        }
        return levels
    }

    /** 如果某档案还没初始化（无任何订单），先种入默认 3 单，保证号码价值表能直接取到数据 */
    private suspend fun seedProfileIfEmpty(db: AppDatabase) {
        val orderDao = db.orderDao()
        val groupInitDao = db.groupInitDao()
        if (orderDao.getAllWithItems().isNotEmpty()) return

        val seed = defaultSeedItems()
        db.withTransaction {
            seed.forEachIndexed { idxOrder, orderItems ->
                val orderNo = idxOrder + 1
                val orderId = defaultSeedOrderId(orderNo)
                val groupIds = ArrayList<Int>(orderItems.size)
                val initMap = HashMap<Int, Int>(orderItems.size)
                orderItems.forEach { item ->
                    val gid = findGroupId(item.members)
                    groupIds.add(gid)
                    initMap[gid] = item.level.coerceIn(
                        GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL
                    )
                }
                orderDao.upsertOrder(
                    OrderEntity(id = orderId, orderNo = orderNo, createdDateKey = DEFAULT_SEED_DATE)
                )
                val items = groupIds.mapIndexed { i, gid ->
                    OrderItemEntity(
                        orderId = orderId,
                        pos = i + 1,
                        groupId = gid,
                        initLevel = initMap[gid] ?: GroupLevelEngine.MIN_LEVEL
                    )
                }
                orderDao.upsertItems(items)
                groupIds.forEach { gid ->
                    if (groupInitDao.get(gid) == null) {
                        groupInitDao.upsert(
                            GroupInitEntity(
                                groupId = gid,
                                activationDateKey = DEFAULT_SEED_DATE,
                                initLevel = initMap[gid] ?: GroupLevelEngine.MIN_LEVEL
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun computeProfileSubtotals(profileId: Int, dateKey: String): Map<Zodiac, Long> {
        val db = AppDatabase.get(appContext, databaseNameForProfile(profileId))
        seedProfileIfEmpty(db)

        val draws = linkedMapOf<String, Zodiac>()
        db.drawDao().getAll().forEach { draws[it.dateKey] = it.zodiac }

        val activationDate = Array<String?>(GroupLevelEngine.GROUP_COUNT) { null }
        val initialLevel = IntArray(GroupLevelEngine.GROUP_COUNT) { GroupLevelEngine.MIN_LEVEL }
        db.groupInitDao().getAll().forEach { gi ->
            if (gi.groupId in 0 until GroupLevelEngine.GROUP_COUNT) {
                activationDate[gi.groupId] = gi.activationDateKey
                initialLevel[gi.groupId] = gi.initLevel.coerceIn(
                    GroupLevelEngine.MIN_LEVEL, GroupLevelEngine.MAX_LEVEL
                )
            }
        }

        val overrides = linkedMapOf<String, MutableMap<Int, Int>>()
        db.levelOverrideDao().getAll().forEach { e ->
            overrides.getOrPut(e.dateKey) { linkedMapOf() }[e.groupId] = e.level
        }

        val orders = db.orderDao().getAllWithItems().map { ow ->
            val items = ow.items.sortedBy { it.pos }
            BetOrder(ow.order.id, ow.order.orderNo, items.map { it.groupId }, ow.order.createdDateKey)
        }

        val yearZodiac = loadYearZodiac(profileId)
        val levels = computeLevelsForProfile(dateKey, draws, activationDate, initialLevel, overrides)

        val zodiacList = Zodiac.values().toList()
        val subtotalCols = LongArray(zodiacList.size) { 0L }
        for (order in orders) {
            for (gid in order.groupIds.take(GroupLevelEngine.ORDER_GROUP_COUNT)) {
                val members = allGroups[gid]
                val money = moneyByLevel(levels[gid], members, yearZodiac)
                for (z in members) subtotalCols[zodiacList.indexOf(z)] += money.toLong()
            }
        }
        val orderCount = orders.size
        // 昨日/今日开奖出现的生肖，其对应数字价值统一置零
        val zeroZodiacs = setOfNotNull(draws[todayKey()], draws[yesterdayKey()])
        return zodiacList.mapIndexed { i, z ->
            val v = subtotalCols[i] - orderCount.toLong() * 10L
            z to if (z in zeroZodiacs) 0L else v
        }.toMap()
    }

    /** 计算全部档案的小计（结果在主线程回调） */
    fun computeAllProfileSubtotals(onDone: (List<ProfileSubtotal>) -> Unit) {
        val dateKey = getSelectedDate()
        viewModelScope.launch(Dispatchers.IO) {
            val results = profileIdsValue.map { pid ->
                ProfileSubtotal(pid, computeProfileSubtotals(pid, dateKey))
            }
            withContext(Dispatchers.Main) { onDone(results) }
        }
    }

    // ========= 工具 =========
    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun yesterdayKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    private fun buildAllGroups(): List<Set<Zodiac>> {
        val result = mutableListOf<Set<Zodiac>>()
        val z = Zodiac.values()
        for (i in 0 until z.size - 3)
            for (j in i + 1 until z.size - 2)
                for (k in j + 1 until z.size - 1)
                    for (l in k + 1 until z.size)
                        result.add(setOf(z[i], z[j], z[k], z[l]))
        return result
    }
}
