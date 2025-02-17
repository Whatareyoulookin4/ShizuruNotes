package com.github.malitsplus.shizurunotes.db

import android.annotation.SuppressLint
import android.app.Application
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import android.widget.Switch
import com.github.malitsplus.shizurunotes.utils.FileUtils
import com.github.malitsplus.shizurunotes.common.Statics
import com.github.malitsplus.shizurunotes.user.UserSettings
import com.github.malitsplus.shizurunotes.utils.LogUtils
import com.github.malitsplus.shizurunotes.utils.Utils
import java.util.*
import kotlin.collections.ArrayList

class DBHelper private constructor(
    val application: Application
) : SQLiteOpenHelper(application, Statics.DB_FILE_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 1

        @Volatile
        private lateinit var instance: DBHelper

        fun with(application: Application): DBHelper {
            synchronized (DBHelper::class.java) {
                instance = DBHelper(application)
            }
            return instance
        }

        @JvmStatic
        fun get(): DBHelper = instance
    }

    override fun onCreate(db: SQLiteDatabase) {}

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        dropAll(db)
        onCreate(db)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> cursor2List(
        cursor: Cursor,
        theClass: Class<*>
    ): List<T>? {
        val result: MutableList<T> = mutableListOf()
        val arrField = theClass.declaredFields
        try {
            while (cursor.moveToNext()) {
                if (cursor.isBeforeFirst) {
                    continue
                }
                val bean = theClass.newInstance()
                for (f in arrField) {
                    val columnName = f.name
                    val columnIdx = cursor.getColumnIndex(columnName)
                    if (columnIdx != -1) {
                        if (!f.isAccessible) {
                            f.isAccessible = true
                        }
                        val type = f.type
                        if (type == Byte::class.javaPrimitiveType) {
                            f[bean] = cursor.getShort(columnIdx).toByte()
                        } else if (type == Short::class.javaPrimitiveType) {
                            f[bean] = cursor.getShort(columnIdx)
                        } else if (type == Int::class.javaPrimitiveType) {
                            f[bean] = cursor.getInt(columnIdx)
                        } else if (type == Long::class.javaPrimitiveType) {
                            f[bean] = cursor.getLong(columnIdx)
                        } else if (type == String::class.java) {
                            f[bean] = cursor.getString(columnIdx)
                        } else if (type == ByteArray::class.java) {
                            f[bean] = cursor.getBlob(columnIdx)
                        } else if (type == Boolean::class.javaPrimitiveType) {
                            f[bean] = cursor.getInt(columnIdx) == 1
                        } else if (type == Float::class.javaPrimitiveType) {
                            f[bean] = cursor.getFloat(columnIdx)
                        } else if (type == Double::class.javaPrimitiveType) {
                            f[bean] = cursor.getDouble(columnIdx)
                        }
                    }
                }
                result.add(bean as T)
            }
        } catch (e: InstantiationException) {
            e.printStackTrace()
            return null
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
            return null
        } finally {
            cursor.close()
        }
        return result
    }

    /***
     * 准备游标
     * @param tableName 表名
     * @param key WHERE [key] IN ([keyValue])
     * @param keyValue WHERE [key] IN ([keyValue])
     * @return 存有数据的游标
     */
    @SuppressLint("Recycle")
    private fun prepareCursor(
        tableName: String,
        key: String?,
        keyValue: List<String>?
    ): Cursor? {
        if (!FileUtils.checkFile(FileUtils.getDbFilePath())) return null
        val db = readableDatabase ?: return null
        return if (key == null || keyValue == null || keyValue.isEmpty()) {
            db.rawQuery("SELECT * FROM $tableName ", null)
        } else {
            val paraBuilder = StringBuilder()
            paraBuilder.append("(")
            for (s in keyValue) {
                if (!TextUtils.isEmpty(s)) {
                    paraBuilder.append("?")
                    paraBuilder.append(", ")
                }
            }
            paraBuilder.delete(paraBuilder.length - 2, paraBuilder.length)
            paraBuilder.append(")")
            db.rawQuery(
                "SELECT * FROM $tableName WHERE $key IN $paraBuilder ",
                keyValue.toTypedArray()
            )
        }
    }
    /******************* Method For Use  */
    /***
     * 由表名和类名无条件从数据库获取实体列表
     * @param tableName 表名
     * @param theClass 类名
     * @param <T> theClass的类
     * @return 生成的实体列表
    </T> */
    private fun <T> getBeanList(
        tableName: String,
        theClass: Class<*>
    ): List<T>? {
        val cursor = prepareCursor(tableName, null, null) ?: return null
        return cursor2List(cursor, theClass)
    }

    /***
     * 由表名、类名、条件键值从数据库获取实体列表
     * @param tableName 表名
     * @param theClass 类名
     * @param key WHERE [key] IN ([keyValue])
     * @param keyValues WHERE [key] IN ([keyValue])
     * @param <T> theClass的类
     * @return 生成的实体列表
    </T> */
    private fun <T> getBeanList(
        tableName: String,
        theClass: Class<*>,
        key: String?,
        keyValues: List<String>?
    ): List<T>? {
        val cursor = prepareCursor(tableName, key, keyValues) ?: return null
        return cursor2List(cursor, theClass)
    }

    /***
     * 由表名、类名、条件键值从数据库获取单个实体
     * @param tableName 表名
     * @param theClass 类名
     * @param key WHERE [key] IN ([keyValue])
     * @param keyValue WHERE [key] IN ([keyValue])
     * @param <T> theClass的类
     * @return 生成的实体
    </T> */
    private fun <T> getBean(
        tableName: String,
        theClass: Class<*>,
        key: String?,
        keyValue: String
    ): T? {
        val cursor =
            prepareCursor(tableName, key, listOf(keyValue)) ?: return null
        val data: List<T>? = cursor2List(cursor, theClass)
        return if (data?.isNotEmpty() == true) data[0] else null
    }

//    /***
//     * 由SQL语句、SQL中的键值从数据库获取单个实体
//     * @param sql SQL语句
//     * @param keyValue IN (?) => ?=keyValue
//     * @param theClass 类名
//     * @param <T> theClass的类
//     * @return 生成的实体
//    </T> */
//    @SuppressLint("Recycle")
//    private fun <T> getBeanByRaw(
//        sql: String?,
//        keyValue: String,
//        theClass: Class<*>
//    ): T? {
//        if (!Utils.checkFile(Statics.DB_PATH + Statics.DB_FILE)) return null
//        val cursor =
//            readableDatabase.rawQuery(sql, arrayOf(keyValue)) ?: return null
//        val data: List<T>? = cursor2List(cursor, theClass)
//        return if (data?.isNotEmpty() == true) data[0] else null
//    }

    /***
     * 由SQL语句、SQL中的键值从数据库获取单个实体
     * @param sql SQL语句
     * @param keyValue IN (?) => ?=keyValue
     * @param theClass 类名
     * @param <T> theClass的类
     * @return 生成的实体
    </T> */
    @SuppressLint("Recycle")
    private fun <T> getBeanByRaw(
        sql: String?,
        theClass: Class<*>
    ): T? {
        if (!FileUtils.checkFile(FileUtils.getDbFilePath())) return null
        try {
            val cursor =
                readableDatabase.rawQuery(sql, null) ?: return null
            val data: List<T>? = cursor2List(cursor, theClass)
            return if (data?.isNotEmpty() == true) data[0] else null
        } catch (e: Exception) {
            LogUtils.file(LogUtils.E, "getBeanByRaw", e.message, e.stackTrace)
            return null
        }
    }

//    /***
//     * 由SQL语句无条件从数据库获取实体列表
//     * @param sql SQL语句
//     * @param theClass 类名
//     * @param <T> theClass的类
//     * @return 生成的实体列表
//    </T> */
//    @SuppressLint("Recycle")
//    private fun <T> getBeanListByRaw(
//        sql: String?,
//        theClass: Class<*>
//    ): List<T>? {
//        if (!Utils.checkFile(Statics.DB_PATH + Statics.DB_FILE)) return null
//        val cursor = readableDatabase.rawQuery(sql, null) ?: return null
//        return cursor2List(cursor, theClass)
//    }

    /***
     * 由SQL语句无条件从数据库获取实体列表
     * @param sql SQL语句
     * @param keyValue 替换？的值
     * @param theClass 类名
     * @param <T> theClass的类
     * @return 生成的实体列表
    </T> */
    @SuppressLint("Recycle")
    private fun <T> getBeanListByRaw(
        sql: String?,
        theClass: Class<*>
    ): List<T>? {
        if (!FileUtils.checkFile(FileUtils.getDbFilePath())) return null
        try {
            val cursor =
                readableDatabase.rawQuery(sql, null) ?: return null
            return cursor2List(cursor, theClass)
        } catch (e: Exception) {
            LogUtils.file(LogUtils.E, "getBeanListByRaw", e.message, e.stackTrace)
            return null
        }
    }

    /***
     * 删除所有表
     * @param db
     */
    private fun dropAll(db: SQLiteDatabase) {
        val sqls: MutableList<String> =
            ArrayList()
        val op = "DROP TABLE IF EXISTS "
        for (field in this.javaClass.declaredFields) {
            if (field.name.startsWith("TABLE_NAME")) {
                try {
                    sqls.add(op + field[this])
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                }
            }
        }
        for (sql in sqls) {
            db.execSQL(sql)
        }
    }

    /***
     * 删除表
     * @param tableName
     * @return
     */
    fun dropTable(tableName: String): Boolean {
        return try {
            writableDatabase.execSQL("DROP TABLE IF EXISTS $tableName")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /***
     * 获取查询语句的第一行第一列值
     * @param sql
     * @return
     */
    private fun getOne(sql: String?): String? {
        if (!FileUtils.checkFile(FileUtils.getDbFilePath())) return null
        val cursor = readableDatabase.rawQuery(sql, null)
        cursor.moveToNext()
        val result = cursor.getString(0)
        cursor.close()
        return result
    }

    /***
     * 获取 int-string map
     * @param sql
     * @return
     */
    private fun getIntStringMap(
        sql: String,
        key: String?,
        value: String?
    ): Map<Int, String>? {
        if (!FileUtils.checkFile(FileUtils.getDbFilePath())) return null
        val cursor = readableDatabase.rawQuery(sql, null)
        val result: MutableMap<Int, String> = HashMap()
        while (cursor.moveToNext()) {
            result[cursor.getInt(cursor.getColumnIndex(key))] = cursor.getString(cursor.getColumnIndex(value))
        }
        cursor.close()
        return result
    }


    /************************* public field **************************/

    /***
     * 获取角色基础数据
     */
    fun getCharaBase(): List<RawUnitBasic>? {
        return if (getConversionCount == "0") {
            getBeanListByRaw(
                """
                SELECT ud.unit_id
                ,ud.unit_name
                ,ud.kana
                ,ud.prefab_id
                ,ud.move_speed
                ,ud.search_area_width
                ,ud.atk_type
                ,ud.normal_atk_cast_time
                ,ud.guild_id
                ,ud.comment
                ,ud.start_time
                ,up.age
                ,up.guild
                ,up.race
                ,up.height
                ,up.weight
                ,up.birth_month
                ,up.birth_day
                ,up.blood_type
                ,up.favorite
                ,up.voice
                ,up.catch_copy
                ,up.self_text
                ,IFNULL(au.unit_name, ud.unit_name) 'actual_name' 
                FROM unit_data AS ud 
                JOIN unit_profile AS up ON ud.unit_id = up.unit_id 
                LEFT JOIN actual_unit_background AS au ON substr(ud.unit_id,1,4) = substr(au.unit_id,1,4) 
                WHERE ud.comment <> '' 
                AND ud.unit_id < 400000 
                """,
                RawUnitBasic::class.java
            )
        } else {
            getBeanListByRaw(
                """
                SELECT ud.unit_id
                ,ud.unit_name
                ,ud.kana
                ,ud.prefab_id
                ,ud.move_speed
                ,ud.search_area_width
                ,ud.atk_type
                ,ud.normal_atk_cast_time
                ,ud.guild_id
                ,ud.comment
                ,ud.start_time
                ,up.age
                ,up.guild
                ,up.race
                ,up.height
                ,up.weight
                ,up.birth_month
                ,up.birth_day
                ,up.blood_type
                ,up.favorite
                ,up.voice
                ,up.catch_copy
                ,up.self_text
                ,IFNULL(au.unit_name, ud.unit_name) 'actual_name' 
                ,uc.unit_id 'unit_conversion_id' 
                FROM unit_data AS ud 
                JOIN unit_profile AS up ON ud.unit_id = up.unit_id 
                LEFT JOIN actual_unit_background AS au ON substr(ud.unit_id,1,4) = substr(au.unit_id,1,4) 
                LEFT JOIN unit_conversion AS uc ON ud.unit_id = uc.original_unit_id 
                WHERE ud.comment <> '' 
                AND ud.unit_id < 400000 
                """,
                RawUnitBasic::class.java
            )
        }
    }

    private val getConversionCount: String by lazy {
        getOne("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='unit_conversion'")!!
    }

    /***
     * 获取角色星级数据
     */
    fun getUnitRarity(unitId: Int): RawUnitRarity? {
        return getBeanByRaw<RawUnitRarity>(
            """
                SELECT * 
                FROM unit_rarity 
                WHERE unit_id=$unitId 
                ORDER BY rarity DESC 
                """,
            RawUnitRarity::class.java
        )
    }

    /***
     * 获取角色星级数据
     */
    fun getUnitRarityList(unitId: Int): List<RawUnitRarity>? {
        return getBeanListByRaw(
            """
                SELECT * 
                FROM unit_rarity 
                WHERE unit_id=$unitId 
                ORDER BY rarity DESC 
                """,
            RawUnitRarity::class.java
        )
    }

    /***
     * 获取角色剧情数据
     */
    fun getCharaStoryStatus(charaId: Int): List<RawCharaStoryStatus>? {
        // 国服-> 排除还没有实装的角色剧情
        if (UserSettings.get().getUserServer() == UserSettings.SERVER_CN) {
            return getBeanListByRaw(
                """
                SELECT a.* 
                FROM chara_story_status AS a
                INNER JOIN unit_data AS b ON substr(a.story_id,1,4) = substr(b.unit_id,1,4)
                WHERE a.chara_id_1 = $charaId 
                OR a.chara_id_2 = $charaId 
                OR a.chara_id_3 = $charaId 
                OR a.chara_id_4 = $charaId 
                OR a.chara_id_5 = $charaId 
                OR a.chara_id_6 = $charaId 
                OR a.chara_id_7 = $charaId 
                OR a.chara_id_8 = $charaId 
                OR a.chara_id_9 = $charaId 
                OR a.chara_id_10 = $charaId 
                """,
                RawCharaStoryStatus::class.java
            )
        }
        return getBeanListByRaw(
            """
                SELECT * 
                FROM chara_story_status 
                WHERE chara_id_1 = $charaId 
                OR chara_id_2 = $charaId 
                OR chara_id_3 = $charaId 
                OR chara_id_4 = $charaId 
                OR chara_id_5 = $charaId 
                OR chara_id_6 = $charaId 
                OR chara_id_7 = $charaId 
                OR chara_id_8 = $charaId 
                OR chara_id_9 = $charaId 
                OR chara_id_10 = $charaId 
                """,
            RawCharaStoryStatus::class.java
        )
    }

    /***
     * 获取角色Rank汇总数据
     * @param unitId 角色id
     * @return
     */
    fun getCharaPromotionStatus(unitId: Int): List<RawPromotionStatus>? {
        return  getBeanListByRaw(
            """
                SELECT * 
                FROM unit_promotion_status 
                WHERE unit_id=$unitId 
                ORDER BY promotion_level DESC 
                """,
            RawPromotionStatus::class.java
        )
    }

    /***
     * 获取角色Rank Bonus
     * @param unitId 角色id
     * @return
     */
    fun getCharaPromotionBonus(unitId: Int): List<RawPromotionBonus>? {
        // 考虑国服未实装的情况
        val count = getOne("""SELECT COUNT(*) 
                                FROM sqlite_master 
                                WHERE type='table' 
                                AND name='promotion_bonus'""")

        return if (count.equals("1")) {
            getBeanListByRaw(
                """
                    SELECT * 
                    FROM promotion_bonus
                    WHERE unit_id=$unitId 
                    ORDER BY promotion_level DESC 
                    """,
                RawPromotionBonus::class.java
            )
        } else null
    }

    /***
     * 获取角色装备数据
     * @param unitId 角色id
     * @return
     */
    fun getCharaPromotion(unitId: Int): List<RawUnitPromotion>? {
        return getBeanListByRaw(
            """
                SELECT * 
                FROM unit_promotion 
                WHERE unit_id=$unitId
                ORDER BY promotion_level DESC 
                """,
            RawUnitPromotion::class.java
        )
    }

    /***
     * 获取装备数据
     * @param slots 装备ids
     * @return
     */
    fun getEquipments(slots: ArrayList<Int>?): List<RawEquipmentData>? {
        return getBeanListByRaw(
            """
                SELECT 
                a.*
                ,b.max_equipment_enhance_level 
                FROM equipment_data a, 
                ( SELECT promotion_level, max( equipment_enhance_level ) max_equipment_enhance_level FROM equipment_enhance_data GROUP BY promotion_level ) b 
                WHERE a.promotion_level = b.promotion_level 
                AND a.equipment_id IN ( ${Utils.splitIntegerWithComma(slots)} ) 
                """,
            RawEquipmentData::class.java
        )
    }

    /***
     * 获取所有装备数据
     */
    fun getEquipmentAll(): List<RawEquipmentData>? {
        return getBeanListByRaw(
            """
                SELECT 
                a.* 
                ,ifnull(b.max_equipment_enhance_level, 0) 'max_equipment_enhance_level'
                ,e.description 'catalog' 
                ,substr(a.equipment_id,3,1) * 10 + substr(a.equipment_id,6,1) 'rarity' 
                ,f.condition_equipment_id_1
                ,f.consume_num_1
                ,f.condition_equipment_id_2
                ,f.consume_num_2
                ,f.condition_equipment_id_3
                ,f.consume_num_3
                ,f.condition_equipment_id_4
                ,f.consume_num_4
                ,f.condition_equipment_id_5
                ,f.consume_num_5
                ,f.condition_equipment_id_6
                ,f.consume_num_6
                ,f.condition_equipment_id_7
                ,f.consume_num_7
                ,f.condition_equipment_id_8
                ,f.consume_num_8
                ,f.condition_equipment_id_9
                ,f.consume_num_9
                ,f.condition_equipment_id_10
                ,f.consume_num_10
                FROM equipment_data a  
                LEFT JOIN ( SELECT promotion_level, max( equipment_enhance_level ) max_equipment_enhance_level FROM equipment_enhance_data GROUP BY promotion_level ) b ON a.promotion_level = b.promotion_level 
                LEFT JOIN equipment_enhance_rate AS e ON a.equipment_id=e.equipment_id
                LEFT JOIN equipment_craft AS f ON a.equipment_id = f.equipment_id
                WHERE a.equipment_id < 113000 
                ORDER BY substr(a.equipment_id,3,1) * 10 + substr(a.equipment_id,6,1) DESC, a.require_level DESC, a.equipment_id ASC 
                """,
            RawEquipmentData::class.java
        )
    }

    /***
     * 获取装备强化数据
     * @param slots 装备ids
     * @return
     */
    fun getEquipmentEnhance(slots: ArrayList<Int>?): List<RawEquipmentEnhanceData>? {
        return getBeanListByRaw(
            """
                SELECT * 
                FROM equipment_enhance_rate 
                WHERE equipment_id IN ( ${Utils.splitIntegerWithComma(slots)} ) 
                """,
            RawEquipmentEnhanceData::class.java
        )
    }

    /***
     * 获取装备强化数据
     * @param id 装备ids
     * @return
     */
    fun getEquipmentEnhance(equipmentId: Int): RawEquipmentEnhanceData? {
        return getBeanByRaw(
            """
                SELECT * 
                FROM equipment_enhance_rate 
                WHERE equipment_id = $equipmentId 
                """,
            RawEquipmentEnhanceData::class.java
        )
    }

    /***
     * 获取所有装备强化数据
     */
    fun getEquipmentEnhance(): List<RawEquipmentEnhanceData>? {
        return getBeanListByRaw(
            """
                SELECT * 
                FROM equipment_enhance_rate 
                """,
            RawEquipmentEnhanceData::class.java
        )
    }

    /***
     * 获取专属装备数据
     * @param unitId 角色id
     * @return
     */
    fun getUniqueEquipment(unitId: Int): RawUniqueEquipmentData? {
        return getBeanByRaw<RawUniqueEquipmentData>(
            """
                SELECT e.*
                ,c.item_id_1
                ,c.consume_num_1
                ,c.item_id_2
                ,c.consume_num_2
                ,c.item_id_3
                ,c.consume_num_3
                ,c.item_id_4
                ,c.consume_num_4
                ,c.item_id_5
                ,c.consume_num_5
                ,c.item_id_6
                ,c.consume_num_6
                ,c.item_id_7
                ,c.consume_num_7
                ,c.item_id_8
                ,c.consume_num_8
                ,c.item_id_9
                ,c.consume_num_9
                ,c.item_id_10
                ,c.consume_num_10
                FROM unique_equipment_data AS e 
                JOIN unit_unique_equip AS u ON e.equipment_id=u.equip_id 
                LEFT JOIN unique_equipment_craft AS c ON e.equipment_id=c.equip_id
                WHERE u.unit_id=$unitId 
                """,
            RawUniqueEquipmentData::class.java
        )
    }

    /***
     * 获取专属装备强化数据
     * @param unitId 角色id
     * @return
     */
    fun getUniqueEquipmentEnhance(unitId: Int): List<RawUniqueEquipmentEnhanceData>? {
        var tableName = "unique_equip_enhance_rate"
        // 考虑国服未实装的情况
        val count = getOne("""
            SELECT COUNT(*) 
            FROM sqlite_master 
            WHERE type='table' 
            AND name='$tableName'"""
        )
        if (!count.equals("1")) {
            tableName = "unique_equipment_enhance_rate"
        }
        return getBeanListByRaw<RawUniqueEquipmentEnhanceData>(
            """
                SELECT e.* 
                FROM $tableName AS e 
                JOIN unit_unique_equip AS u ON e.equipment_id=u.equip_id 
                WHERE u.unit_id=$unitId 
                """,
            RawUniqueEquipmentEnhanceData::class.java
        )
    }

    /***
     * 获取角色技能数据
     * @param unitId
     * @return
     */
    fun getUnitSkillData(unitId: Int): RawUnitSkillData? {
        return getBeanByRaw<RawUnitSkillData>(
            """
                SELECT * 
                FROM unit_skill_data 
                WHERE unit_id=$unitId 
                """,
            RawUnitSkillData::class.java
        )
    }

    /***
     * 获取技能数据
     * @param skillId
     * @return
     */
    fun getSkillData(skillId: Int): RawSkillData? {
        return getBeanByRaw<RawSkillData>(
            """
                SELECT * 
                FROM skill_data 
                WHERE skill_id=$skillId 
                """,
            RawSkillData::class.java
        )
    }

    /***
     * 获取技能动作数据
     * @param actionId
     * @return
     */
    fun getSkillAction(actionId: Int): RawSkillAction? {
        return getBeanByRaw<RawSkillAction>(
            """
                SELECT * 
                FROM skill_action 
                WHERE action_id=$actionId 
                """,
            RawSkillAction::class.java
        )
    }

    /***
     * 获取行动顺序
     * @param unitId
     * @return
     */
    fun getUnitAttackPattern(unitId: Int): List<RawUnitAttackPattern>? {
        return getBeanListByRaw(
        """
            SELECT * 
            FROM unit_attack_pattern 
            WHERE unit_id=$unitId 
            ORDER BY pattern_id 
            """,
            RawUnitAttackPattern::class.java
        )
    }

    /***
     * 获取会战期次
     * @param
     * @return
     */
    fun getClanBattlePeriod(): List<RawClanBattlePeriod>? {
        // 国服-> 读取1014前记录
//        if (UserSettings.get().getUserServer() == UserSettings.SERVER_CN) {
//            return getBeanListByRaw(
//                """
//                SELECT *
//                FROM clan_battle_period
//                WHERE clan_battle_id <= 1014
//                ORDER BY clan_battle_id DESC
//                """,
//                RawClanBattlePeriod::class.java
//            )
//        }
        return getBeanListByRaw(
            """
                SELECT * 
                FROM clan_battle_period 
                ORDER BY clan_battle_id DESC 
                ${if (UserSettings.get().getClanBattleLimit()) "LIMIT 13 " else ""}
                """,
                RawClanBattlePeriod::class.java
        )
    }

    /***
     * 获取会战phase
     * @param
     * @return
     */
    fun getClanBattlePhase(clanBattleId: Int): List<RawClanBattlePhase>? {
        // 国服-> 迎合日服结构
//        if (UserSettings.get().getUserServer() == UserSettings.SERVER_CN) {
//            return getBeanListByRaw(
//                """
//                SELECT a.clan_battle_id,
//                CASE
//                WHEN a.lap_num_from = 1 THEN 1
//                WHEN a.lap_num_from = 2 AND a.clan_battle_id <= 1009 THEN 2
//                WHEN a.lap_num_from = 2 THEN 1
//                WHEN a.lap_num_from = 4 THEN 2
//                WHEN a.lap_num_from = 6 THEN 3
//                WHEN a.lap_num_from = 11 THEN 3
//                WHEN a.lap_num_from = 35 THEN 4
//                ELSE 1 END 'phase'
//                ,b1.wave_group_id 'wave_group_id_1'
//                ,b2.wave_group_id 'wave_group_id_2'
//                ,b3.wave_group_id 'wave_group_id_3'
//                ,b4.wave_group_id 'wave_group_id_4'
//                ,b5.wave_group_id 'wave_group_id_5'
//                FROM clan_battle_map_data AS a
//                JOIN clan_battle_boss_group AS b1 ON a.clan_battle_boss_group_id = b1.clan_battle_boss_group_id AND b1.order_num = 1
//                JOIN clan_battle_boss_group AS b2 ON a.clan_battle_boss_group_id = b2.clan_battle_boss_group_id AND b2.order_num = 2
//                JOIN clan_battle_boss_group AS b3 ON a.clan_battle_boss_group_id = b3.clan_battle_boss_group_id AND b3.order_num = 3
//                JOIN clan_battle_boss_group AS b4 ON a.clan_battle_boss_group_id = b4.clan_battle_boss_group_id AND b4.order_num = 4
//                JOIN clan_battle_boss_group AS b5 ON a.clan_battle_boss_group_id = b5.clan_battle_boss_group_id AND b5.order_num = 5
//                WHERE 1=1
//                AND a.clan_battle_id = $clanBattleId
//                AND (a.lap_num_from <> a.lap_num_to OR a.rsl_unlock_lap = 1)
//                ORDER BY a.clan_battle_id,a.lap_num_from DESC
//                """,
//                RawClanBattlePhase::class.java
//            )
//        }
        return getBeanListByRaw(
            """
                SELECT DISTINCT 
                phase 
                ,wave_group_id_1 
                ,wave_group_id_2 
                ,wave_group_id_3 
                ,wave_group_id_4 
                ,wave_group_id_5 
                FROM clan_battle_2_map_data WHERE clan_battle_id=$clanBattleId 
                ORDER BY phase DESC 
                """,
            RawClanBattlePhase::class.java
        )
    }

    /***
     * 获取wave
     * @param
     * @return
     */
    fun getWaveGroupData(waveGroupList: List<Int>): List<RawWaveGroup>? {
        return getBeanListByRaw(
                """
                    SELECT * 
                    FROM wave_group_data 
                    WHERE wave_group_id IN ( %s ) 
                    """.format(waveGroupList.toString()
                        .replace("[", "")
                        .replace("]", "")),
            RawWaveGroup::class.java
        )
    }

    /***
     * 获取wave
     * @param
     * @return
     */
    fun getWaveGroupData(waveGroupId: Int): RawWaveGroup? {
        return getBeanByRaw(
            """
            SELECT * 
            FROM wave_group_data 
            WHERE wave_group_id = $waveGroupId 
            """,
            RawWaveGroup::class.java
        )
    }

    /***
     * 获取enemyList
     * @param
     * @return
     */
    fun getEnemy(enemyIdList: List<Int>): List<RawEnemy>? {
        // 国服->去掉 [sre_enemy_parameter] 表
        if (UserSettings.get().getUserServer() == UserSettings.SERVER_CN) {
            return getBeanListByRaw(
                """
                    SELECT 
                    a.* 
                    ,b.union_burst 
                    ,b.union_burst_evolution 
                    ,b.main_skill_1 
                    ,b.main_skill_evolution_1 
                    ,b.main_skill_2 
                    ,b.main_skill_evolution_2 
                    ,b.ex_skill_1 
                    ,b.ex_skill_evolution_1 
                    ,b.main_skill_3 
                    ,b.main_skill_4 
                    ,b.main_skill_5 
                    ,b.main_skill_6 
                    ,b.main_skill_7 
                    ,b.main_skill_8 
                    ,b.main_skill_9 
                    ,b.main_skill_10 
                    ,b.ex_skill_2 
                    ,b.ex_skill_evolution_2 
                    ,b.ex_skill_3 
                    ,b.ex_skill_evolution_3 
                    ,b.ex_skill_4 
                    ,b.ex_skill_evolution_4 
                    ,b.ex_skill_5 
                    ,b.sp_skill_1 
                    ,b.ex_skill_evolution_5 
                    ,b.sp_skill_2 
                    ,b.sp_skill_3 
                    ,b.sp_skill_4 
                    ,b.sp_skill_5 
                    ,c.child_enemy_parameter_1 
                    ,c.child_enemy_parameter_2 
                    ,c.child_enemy_parameter_3 
                    ,c.child_enemy_parameter_4 
                    ,c.child_enemy_parameter_5 
                    ,u.prefab_id 
                    ,u.atk_type 
                    ,u.normal_atk_cast_time
					,u.search_area_width
                    ,u.comment
                    FROM 
                    unit_skill_data b 
                    ,enemy_parameter a 
                    LEFT JOIN enemy_m_parts c ON a.enemy_id = c.enemy_id 
                    LEFT JOIN unit_enemy_data u ON a.unit_id = u.unit_id 
                    WHERE 
                    a.unit_id = b.unit_id 
                    AND a.enemy_id in ( %s )  
                    """.format(enemyIdList.toString()
                        .replace("[", "")
                        .replace("]", "")),
                RawEnemy::class.java
            )
        }
        return getBeanListByRaw(
                """
                    SELECT 
                    a.* 
                    ,b.union_burst 
                    ,b.union_burst_evolution 
                    ,b.main_skill_1 
                    ,b.main_skill_evolution_1 
                    ,b.main_skill_2 
                    ,b.main_skill_evolution_2 
                    ,b.ex_skill_1 
                    ,b.ex_skill_evolution_1 
                    ,b.main_skill_3 
                    ,b.main_skill_4 
                    ,b.main_skill_5 
                    ,b.main_skill_6 
                    ,b.main_skill_7 
                    ,b.main_skill_8 
                    ,b.main_skill_9 
                    ,b.main_skill_10 
                    ,b.ex_skill_2 
                    ,b.ex_skill_evolution_2 
                    ,b.ex_skill_3 
                    ,b.ex_skill_evolution_3 
                    ,b.ex_skill_4 
                    ,b.ex_skill_evolution_4 
                    ,b.ex_skill_5 
                    ,b.sp_skill_1 
                    ,b.ex_skill_evolution_5 
                    ,b.sp_skill_2 
                    ,b.sp_skill_3 
                    ,b.sp_skill_4 
                    ,b.sp_skill_5 
                    ,c.child_enemy_parameter_1 
                    ,c.child_enemy_parameter_2 
                    ,c.child_enemy_parameter_3 
                    ,c.child_enemy_parameter_4 
                    ,c.child_enemy_parameter_5 
                    ,u.prefab_id 
                    ,u.atk_type 
                    ,u.normal_atk_cast_time
					,u.search_area_width
                    ,u.comment
                    FROM 
                    unit_skill_data b 
                    ,(SELECT * FROM enemy_parameter UNION ALL SELECT * FROM sre_enemy_parameter) a 
                    LEFT JOIN enemy_m_parts c ON a.enemy_id = c.enemy_id 
                    LEFT JOIN unit_enemy_data u ON a.unit_id = u.unit_id 
                    WHERE 
                    a.unit_id = b.unit_id 
                    AND a.enemy_id in ( %s )  
                    """.format(enemyIdList.toString()
                        .replace("[", "")
                        .replace("]", "")),
                RawEnemy::class.java
        )
    }

    /***
     * 获取第一个enemy
     * @param
     * @return
     */
    fun getEnemy(enemyId: Int): RawEnemy? {
        return getEnemy(listOf(enemyId))?.get(0)
    }

    /***
     * 获取敌人抗性值
     * @param
     * @return
     */
    fun getResistData(resistStatusId: Int): RawResistData? {
        return getBeanByRaw<RawResistData>(
            """
                SELECT * 
                FROM resist_data 
                WHERE resist_status_id=$resistStatusId  
                """,
            RawResistData::class.java
        )
    }

    /***
     * 获取友方召唤物
     */
    fun getUnitMinion(minionId: Int): RawUnitMinion? {
        return getBeanByRaw<RawUnitMinion>(
            """
                SELECT
                a.*,
                b.union_burst,
                b.union_burst_evolution,
                b.main_skill_1,
                b.main_skill_evolution_1,
                b.main_skill_2,
                b.main_skill_evolution_2,
                b.ex_skill_1,
                b.ex_skill_evolution_1,
                b.main_skill_3,
                b.main_skill_4,
                b.main_skill_5,
                b.main_skill_6,
                b.main_skill_7,
                b.main_skill_8,
                b.main_skill_9,
                b.main_skill_10,
                b.ex_skill_2,
                b.ex_skill_evolution_2,
                b.ex_skill_3,
                b.ex_skill_evolution_3,
                b.ex_skill_4,
                b.ex_skill_evolution_4,
                b.ex_skill_5,
                b.sp_skill_1,
                b.ex_skill_evolution_5,
                b.sp_skill_2,
                b.sp_skill_3,
                b.sp_skill_4,
                b.sp_skill_5
            FROM
                unit_skill_data b,
                unit_data a
            WHERE
                a.unit_id = b.unit_id
                AND a.unit_id = $minionId
                """,
            RawUnitMinion::class.java
        )
    }

    /***
     * 获取敌方召唤物
     */
    fun getEnemyMinion(enemyId: Int): RawEnemy? {
        return getBeanByRaw<RawEnemy>(
            """
                SELECT
                d.unit_name,
                d.prefab_id,
                d.search_area_width,
                d.atk_type,
                d.move_speed,
                a.*,
                b.*,
                d.normal_atk_cast_time,
                c.child_enemy_parameter_1,
                c.child_enemy_parameter_2,
                c.child_enemy_parameter_3,
                c.child_enemy_parameter_4,
                c.child_enemy_parameter_5
                FROM
                enemy_parameter a
                JOIN unit_skill_data AS b ON a.unit_id = b.unit_id
                JOIN unit_enemy_data AS d ON a.unit_id = d.unit_id
                LEFT JOIN enemy_m_parts c ON a.enemy_id = c.enemy_id
                WHERE a.enemy_id = $enemyId
                """,
            RawEnemy::class.java
        )
    }

    /***
     * 获取迷宫bossList
     * @param
     * @return
     */
    fun getDungeons(): List<RawDungeon>? {
        // 考虑国服未实装的情况
        val count = getOne("""SELECT COUNT(*) 
                                FROM sqlite_master 
                                WHERE type='table' 
                                AND name='dungeon_area'""")
        if (!count.equals("1")) {
            return getBeanListByRaw(
                """
                SELECT
                a.dungeon_area_id,
                a.dungeon_name,
                a.description,
                b.*
                FROM
                dungeon_area_data AS a 
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                ORDER BY a.dungeon_area_id DESC 
                """,
                RawDungeon::class.java
            )
        }
        return getBeanListByRaw(
            """
                SELECT
                    a.dungeon_area_id 'dungeon_area_id',
                    a.dungeon_name 'dungeon_name',
                    a.description 'description',
                    sp.mode 'mode',
                    w.*
                FROM
                    dungeon_area AS a
                JOIN dungeon_quest_data AS b ON a.dungeon_area_id = b.dungeon_area_id
                AND b.quest_type = 4
                JOIN dungeon_special_battle AS sp ON b.quest_id = sp.quest_id
                JOIN wave_group_data AS w ON sp.wave_group_id = w.wave_group_id
                UNION ALL
                SELECT
                    a.dungeon_area_id,
                    a.dungeon_name,
                    a.description,
                    0 AS 'mode',
                    w.*
                FROM
                    dungeon_area AS a
                JOIN dungeon_quest_data AS b ON a.dungeon_area_id = b.dungeon_area_id
                AND b.quest_type = 3
                JOIN wave_group_data AS w ON b.wave_group_id = w.wave_group_id
                ORDER BY
                    a.dungeon_area_id DESC,
                    sp.mode DESC
                """,
            RawDungeon::class.java
        )
    }

    /***
     * get secret dungeon schedule list
     */
    fun getSecretDungeonSchedules(): List<RawSecretDungeonSchedule>? {
        // 考虑国服未实装的情况
        val count = getOne("""SELECT COUNT(*) 
                                FROM sqlite_master 
                                WHERE type='table' 
                                AND name='secret_dungeon_schedule'""")
        if (!count.equals("1")) {
            return null;
        }
        return getBeanListByRaw(
            """
                SELECT
                    *
                FROM
                    secret_dungeon_schedule
                ORDER BY
                    start_time DESC;
                """,
            RawSecretDungeonSchedule::class.java
        )
    }

    /***
     * get specified secret dungeon quest list
     */
    fun getSecretDungeonQuests(dungeon_area_id: Int): List<RawSecretDungeonQuestData>? {
        return getBeanListByRaw(
            """
                SELECT
                    a.*, b.*
                FROM
                    secret_dungeon_quest_data AS a
                JOIN wave_group_data AS b ON a.wave_group_id = b.wave_group_id
                WHERE
                    a.dungeon_area_id = $dungeon_area_id
                AND a.wave_group_id <> 0
                ORDER BY
                    a.difficulty DESC,
                    a.floor_num DESC
                """,
            RawSecretDungeonQuestData::class.java
        )
    }

    /***
     * 获取特殊活动
     */
    fun getSpEvents(): List<RawSpEvent>? {
        return when(getSpEventCount()) {
            1 -> getBeanListByRaw(
                """
                SELECT
                a.kaiser_boss_id 'boss_id'
                ,a.name
                ,b.*
                FROM kaiser_quest_data AS a 
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                WHERE NOT EXISTS (SELECT 1 FROM kaiser_special_battle WHERE wave_group_id=a.wave_group_id)
                UNION ALL
                SELECT 
                0 'boss_id'
                ,'メインボスMODE'||a.mode 'name'
                ,b.*
                FROM kaiser_special_battle AS a
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                """,
                RawSpEvent::class.java)
            2 -> getBeanListByRaw(
                """
                SELECT
                a.legion_boss_id 'boss_id'
                ,a.name
                ,b.*
                FROM legion_quest_data AS a 
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                WHERE NOT EXISTS (SELECT 1 FROM legion_special_battle WHERE wave_group_id=a.wave_group_id)
                UNION ALL
                SELECT
                0 'boss_id'
                ,'メインボスMODE'||a.mode 'name'
                ,b.*
                FROM legion_special_battle AS a 
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                UNION ALL
                SELECT
                a.kaiser_boss_id 'boss_id'
                ,a.name
                ,b.*
                FROM kaiser_quest_data AS a 
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                WHERE NOT EXISTS (SELECT 1 FROM kaiser_special_battle WHERE wave_group_id=a.wave_group_id)
                UNION ALL
                SELECT 
                0 'boss_id'
                ,'メインボスMODE'||a.mode 'name'
                ,b.*
                FROM kaiser_special_battle AS a
                JOIN wave_group_data AS b ON a.wave_group_id=b.wave_group_id 
                """,
                RawSpEvent::class.java)
            3 -> getBeanListByRaw(
                """
                SELECT
                    a.sre_boss_id 'boss_id',
                    a.difficulty || '-' || b.phase || ' ' || b.name 'name',
                    c.*
                FROM
                    sre_quest_difficulty_data AS a
                JOIN sre_boss_data AS b ON a.sre_boss_id = b.sre_boss_id
                JOIN sre_wave_group_data AS c ON a.wave_group_id = c.wave_group_id
                UNION ALL
                SELECT
                    a.legion_boss_id 'boss_id',
                    a.name,
                    b.*
                FROM
                    legion_quest_data AS a
                JOIN wave_group_data AS b ON a.wave_group_id = b.wave_group_id
                WHERE
                    NOT EXISTS (
                        SELECT
                            1
                        FROM
                            legion_special_battle
                        WHERE
                            wave_group_id = a.wave_group_id
                    )
                UNION ALL
                SELECT
                    0 'boss_id',
                    'メインボスMODE' || a.mode 'name',
                    b.*
                FROM
                    legion_special_battle AS a
                JOIN wave_group_data AS b ON a.wave_group_id = b.wave_group_id
                UNION ALL
                SELECT
                    a.kaiser_boss_id 'boss_id',
                    a.name,
                    b.*
                FROM
                    kaiser_quest_data AS a
                JOIN wave_group_data AS b ON a.wave_group_id = b.wave_group_id
                WHERE
                    NOT EXISTS (
                        SELECT
                            1
                        FROM
                            kaiser_special_battle
                        WHERE
                            wave_group_id = a.wave_group_id
                    )
                UNION ALL
                SELECT
                    0 'boss_id',
                    'メインボスMODE' || a.mode 'name',
                    b.*
                FROM
                    kaiser_special_battle AS a
                JOIN wave_group_data AS b ON a.wave_group_id = b.wave_group_id
                """,
                RawSpEvent::class.java)
            else -> null
        }
    }

    private fun getSpEventCount(): Int {
        return getOne(
            """
                SELECT COUNT(*) 'num'
                FROM sqlite_master 
                WHERE type = 'table' 
                AND name in ('legion_quest_data', 'kaiser_quest_data', 'sre_quest_difficulty_data')
            """
        ).let {
            it!!.toInt()
        }
    }

    /***
     * 获取所有Quest
     */
    fun getQuests(): List<RawQuest>? {
        return getBeanListByRaw(
            """
                SELECT * FROM quest_data WHERE quest_id < 13000000 ORDER BY daily_limit ASC, quest_id DESC 
                """,
            RawQuest::class.java
        )
    }

    /***
     * 获取掉落奖励
     */
    fun getEnemyRewardData(dropRewardIdList: List<Int>): List<RawEnemyRewardData>? {
        return getBeanListByRaw(
            """
                SELECT * 
                FROM enemy_reward_data 
                WHERE drop_reward_id IN ( %s ) 
                """.format(dropRewardIdList.toString()
                .replace("[", "")
                .replace("]", "")),
            RawEnemyRewardData::class.java
        )
    }

    /***
     * 获取campaign日程
     */
    fun getCampaignSchedule(nowTimeString: String?): List<RawScheduleCampaign>? {
        var sqlString = " SELECT * FROM campaign_schedule WHERE id < 5000 "
        nowTimeString?.let {
            sqlString += " AND end_time > '$it' "
        }
        return getBeanListByRaw(sqlString, RawScheduleCampaign::class.java)
    }

    /***
     * 获取free gacha日程
     */
    fun getFreeGachaSchedule(nowTimeString: String?): List<RawScheduleFreeGacha>? {
        var sqlString = " SELECT * FROM campaign_freegacha "
        nowTimeString?.let {
            sqlString += " WHERE end_time > '$it' "
        }
        return getBeanListByRaw(sqlString, RawScheduleFreeGacha::class.java)
    }

    /***
     * 获取hatsune日程
     */
    fun getHatsuneSchedule(nowTimeString: String?): List<RawScheduleHatsune>? {
        var sqlString = """
            SELECT a.event_id, a.start_time, a.end_time, b.title 
            FROM hatsune_schedule AS a JOIN event_story_data AS b ON a.event_id = b.value
            UNION ALL
            SELECT a.event_id, a.start_time, a.end_time, b.title 
            FROM hatsune_schedule AS a JOIN event_story_data AS b ON a.event_id = b.value-10000
            """
        nowTimeString?.let {
            sqlString += " WHERE a.end_time > '$it' "
        }
        sqlString += " ORDER BY a.event_id DESC "
        return getBeanListByRaw(sqlString, RawScheduleHatsune::class.java)
    }

    /***
     * 获取hatsune一般boss数据
     */
    fun getHatsuneBattle(eventId: Int): List<RawHatsuneBoss>? {
        return getBeanListByRaw(
            """
            SELECT
            a.*
            FROM
            hatsune_boss a
            WHERE
            event_id = $eventId 
            AND area_id <> 0 
            """,
            RawHatsuneBoss::class.java
        )
    }

    /***
     * 获取hatsune SP boss数据
     */
    fun getHatsuneSP(eventId: Int): List<RawHatsuneSpecialBattle>? {
        return getBeanListByRaw(
            """
            SELECT
            a.*
            FROM hatsune_special_battle a
            WHERE event_id = $eventId
            """,
            RawHatsuneSpecialBattle::class.java
        )
    }

    /***
     * 获取露娜塔日程
     */
    fun getTowerSchedule(nowTimeString: String?): List<RawTowerSchedule>? {
        var sqlString = " SELECT * FROM tower_schedule "
        nowTimeString?.let {
            sqlString += " WHERE end_time > '$it' "
        }
        return getBeanListByRaw(sqlString, RawTowerSchedule::class.java)
    }

    /***
     * 获取装备碎片
     */
    fun getEquipmentPiece(): List<RawEquipmentPiece>? {
        return getBeanListByRaw(" SELECT * FROM equipment_data WHERE equipment_id >= 113000 ",
            RawEquipmentPiece::class.java
        )
    }

    /***
     * 获取异常状态map
     * @param
     * @return
     */
    val ailmentMap: Map<Int, String>?
        get() = getIntStringMap(
            "SELECT * FROM ailment_data ",
            "ailment_id",
            "ailment_name"
        )

    val maxCharaLevel: Int
        get() {
            val result = getOne("SELECT max(team_level) FROM experience_team ")
            return result?.toInt() ?: 0
        }

    val maxCharaRank: Int
        get() {
            val result = getOne("SELECT max(promotion_level) FROM unit_promotion ")
            return result?.toInt() ?: 0
        }

    val maxUniqueEquipmentLevel: Int
        get() {
            val result =
                getOne("SELECT max(enhance_level) FROM unique_equipment_enhance_data ")
            return result?.toInt() ?: 0
        }

    val maxEnemyLevel: Int
        get() {
            val result = getOne("SELECT MAX(level) FROM enemy_parameter ")
            return result?.toInt() ?: 0
        }

    /***
     * 随机生成16位随机英数字符串
     * @return
     */
    val randomId: String
        get() {
            val str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val random = Random()
            val sb = StringBuffer()
            for (i in 0..15) {
                val number = random.nextInt(36)
                sb.append(str[number])
            }
            return sb.toString()
        }
}
