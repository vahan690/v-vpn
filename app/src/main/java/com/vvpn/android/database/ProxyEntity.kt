package com.vvpn.android.database

import android.content.Context
import android.content.Intent
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.esotericsoftware.kryo.io.ByteBufferInput
import com.esotericsoftware.kryo.io.ByteBufferOutput
import com.vvpn.android.SagerNet.Companion.app
import com.vvpn.android.R
import com.vvpn.android.fmt.AbstractBean
import com.vvpn.android.fmt.KryoConverters
import com.vvpn.android.fmt.Serializable
import com.vvpn.android.fmt.buildConfig
import com.vvpn.android.fmt.buildSingBoxOutbound
import com.vvpn.android.fmt.config.ConfigBean
import com.vvpn.android.fmt.direct.DirectBean
import com.vvpn.android.fmt.hysteria.HysteriaBean
import com.vvpn.android.fmt.hysteria.buildHysteriaConfig
import com.vvpn.android.fmt.hysteria.canUseSingBox
import com.vvpn.android.fmt.hysteria.toUri
import com.vvpn.android.fmt.internal.ChainBean
import com.vvpn.android.fmt.internal.ProxySetBean
import com.vvpn.android.fmt.toUniversalLink
import com.vvpn.android.ktx.applyDefaultValues
import com.vvpn.android.ui.profile.ChainSettingsActivity
import com.vvpn.android.ui.profile.DirectSettingsActivity
import com.vvpn.android.ui.profile.HysteriaSettingsActivity
import com.vvpn.android.ui.profile.ProfileSettingsActivity
import com.vvpn.android.ui.profile.ConfigSettingActivity
import com.vvpn.android.ui.profile.ProxySetSettingsActivity

@Entity(
    tableName = "proxy_entities", indices = [Index("groupId", name = "groupId")]
)
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var groupId: Long = 0L,
    var type: Int = 0,
    var userOrder: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
    var status: Int = STATUS_INITIAL,
    var ping: Int = 0,
    var uuid: String = "",
    var error: String? = null,
    var hysteriaBean: HysteriaBean? = null,
    var directBean: DirectBean? = null,
    var proxySetBean: ProxySetBean? = null,
    var chainBean: ChainBean? = null,
    var configBean: ConfigBean? = null,
) : Serializable() {

    companion object {
        const val TYPE_CHAIN = 8
        const val TYPE_HYSTERIA = 15
        const val TYPE_DIRECT = 23
        const val TYPE_PROXY_SET = 26
        const val TYPE_CONFIG = 998

        /** Plugin not found or not support this ping type */
        const val STATUS_INVALID = -1
        const val STATUS_INITIAL = 0
        const val STATUS_AVAILABLE = 1

        /** Unclear */
        const val STATUS_UNREACHABLE = 2

        /** Has obvious error */
        const val STATUS_UNAVAILABLE = 3

        val chainName by lazy { app.getStringCompat(R.string.proxy_chain) }

        private val placeHolderBean = HysteriaBean().applyDefaultValues()

        @JvmField
        val CREATOR = object : Serializable.CREATOR<ProxyEntity>() {

            override fun newInstance(): ProxyEntity {
                return ProxyEntity()
            }

            override fun newArray(size: Int): Array<ProxyEntity?> {
                return arrayOfNulls(size)
            }
        }
    }

    @Ignore
    @Transient
    var dirty: Boolean = false

    override fun initializeDefaultValues() {
    }

    override fun serializeToBuffer(output: ByteBufferOutput) {
        output.writeInt(0)

        output.writeLong(id)
        output.writeLong(groupId)
        output.writeInt(type)
        output.writeLong(userOrder)
        output.writeLong(tx)
        output.writeLong(rx)
        output.writeInt(status)
        output.writeInt(ping)
        output.writeString(uuid)
        output.writeString(error)

        val data = KryoConverters.serialize(requireBean())
        output.writeVarInt(data.size, true)
        output.writeBytes(data)

        output.writeBoolean(dirty)
    }

    override fun deserializeFromBuffer(input: ByteBufferInput) {
        val version = input.readInt()

        id = input.readLong()
        groupId = input.readLong()
        type = input.readInt()
        userOrder = input.readLong()
        tx = input.readLong()
        rx = input.readLong()
        status = input.readInt()
        ping = input.readInt()
        uuid = input.readString()
        error = input.readString()
        putByteArray(input.readBytes(input.readVarInt(true)))

        dirty = input.readBoolean()
    }


    fun putByteArray(byteArray: ByteArray) {
        when (type) {
            TYPE_HYSTERIA -> hysteriaBean = KryoConverters.hysteriaDeserialize(byteArray)
            TYPE_DIRECT -> directBean = KryoConverters.directDeserialize(byteArray)
            TYPE_PROXY_SET -> proxySetBean = KryoConverters.proxySetDeserialize(byteArray)
            TYPE_CHAIN -> chainBean = KryoConverters.chainDeserialize(byteArray)
            TYPE_CONFIG -> configBean = KryoConverters.configDeserialize(byteArray)
        }
    }

    fun displayType(): String = when (type) {
        TYPE_HYSTERIA -> "Hysteria" + hysteriaBean!!.protocolVersion
        TYPE_DIRECT -> "Direct"
        TYPE_PROXY_SET -> proxySetBean!!.displayType()
        TYPE_CHAIN -> chainName
        TYPE_CONFIG -> configBean!!.displayType()
        else -> "Undefined type $type"
    }

    fun displayName() = requireBean().displayName()
    fun displayAddress() = requireBean().displayAddress()

    fun requireBean(): AbstractBean {
        return when (type) {
            TYPE_HYSTERIA -> hysteriaBean
            TYPE_DIRECT -> directBean
            TYPE_PROXY_SET -> proxySetBean
            TYPE_CHAIN -> chainBean
            TYPE_CONFIG -> configBean
            else -> error("Undefined type $type")
        } ?: error("Null ${displayType()} profile")
    }

    fun setBean(bean: AbstractBean) {
        when (type) {
            TYPE_HYSTERIA -> hysteriaBean = bean as HysteriaBean
            TYPE_DIRECT -> directBean = bean as DirectBean
            TYPE_PROXY_SET -> proxySetBean = bean as ProxySetBean
            TYPE_CHAIN -> chainBean = bean as ChainBean
            TYPE_CONFIG -> configBean = bean as ConfigBean
            else -> error("Undefined type $type")
        }
    }

    /** Determines if has internal link. */
    fun haveLink(): Boolean = when (type) {
        TYPE_PROXY_SET -> false
        TYPE_CHAIN -> false
        TYPE_DIRECT -> false
        else -> true
    }

    /** Determines if has standard link. */
    fun haveStandardLink(): Boolean = when (type) {
        TYPE_PROXY_SET -> false
        TYPE_CHAIN -> false
        TYPE_CONFIG -> false
        else -> true
    }

    fun toStdLink(): String = with(requireBean()) {
        when (this) {
            is HysteriaBean -> toUri()
            else -> toUniversalLink()
        }
    }

    fun mustUsePlugin(): Boolean = false

    fun exportConfig(): Pair<String, String> {
        var name = "${requireBean().displayName()}.json"

        return with(requireBean()) {
            StringBuilder().apply {
                val config = buildConfig(this@ProxyEntity, forExport = true)
                append(config.config)

                if (!config.externalIndex.all { it.chain.isEmpty() }) {
                    name = "profiles.txt"
                }

                val logLevel = DataStore.logLevel
                for ((chain) in config.externalIndex) {
                    chain.entries.forEach { (port, profile) ->
                        when (val bean = profile.requireBean()) {
                            is HysteriaBean -> {
                                append("\n\n")
                                append(bean.buildHysteriaConfig(port, false, null))
                            }
                        }
                    }
                }
            }.toString()
        } to name
    }

    fun exportOutbound(): String = buildSingBoxOutbound(requireBean())

    fun needExternal(): Boolean {
        return when (type) {
            TYPE_HYSTERIA -> !hysteriaBean!!.canUseSingBox()
            else -> false
        }
    }

    fun putBean(bean: AbstractBean): ProxyEntity {
        hysteriaBean = null
        directBean = null
        proxySetBean = null
        chainBean = null
        configBean = null

        when (bean) {
            is HysteriaBean -> {
                type = TYPE_HYSTERIA
                hysteriaBean = bean
            }

            is DirectBean -> {
                type = TYPE_DIRECT
                directBean = bean
            }

            is ProxySetBean -> {
                type = TYPE_PROXY_SET
                proxySetBean = bean
            }

            is ChainBean -> {
                type = TYPE_CHAIN
                chainBean = bean
            }

            is ConfigBean -> {
                type = TYPE_CONFIG
                configBean = bean
            }

            else -> error("Undefined type $type")
        }
        return this
    }

    fun settingIntent(ctx: Context, isSubscription: Boolean): Intent {
        return Intent(
            ctx, when (type) {
                TYPE_HYSTERIA -> HysteriaSettingsActivity::class.java
                TYPE_DIRECT -> DirectSettingsActivity::class.java
                TYPE_PROXY_SET -> ProxySetSettingsActivity::class.java
                TYPE_CHAIN -> ChainSettingsActivity::class.java
                TYPE_CONFIG -> ConfigSettingActivity::class.java
                else -> throw IllegalArgumentException()
            }
        ).apply {
            putExtra(ProfileSettingsActivity.EXTRA_PROFILE_ID, id)
            putExtra(ProfileSettingsActivity.EXTRA_IS_SUBSCRIPTION, isSubscription)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("select * from proxy_entities")
        fun getAll(): List<ProxyEntity>

        @Query("SELECT id FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getIdsByGroup(groupId: Long): List<Long>

        @Query("SELECT * FROM proxy_entities WHERE groupId = :groupId ORDER BY userOrder")
        fun getByGroup(groupId: Long): List<ProxyEntity>

        @Query("SELECT * FROM proxy_entities WHERE id in (:proxyIds)")
        fun getEntities(proxyIds: List<Long>): List<ProxyEntity>

        @Query("SELECT COUNT(*) FROM proxy_entities WHERE groupId = :groupId")
        fun countByGroup(groupId: Long): Long

        @Query("SELECT  MAX(userOrder) + 1 FROM proxy_entities WHERE groupId = :groupId")
        fun nextOrder(groupId: Long): Long?

        @Query("SELECT * FROM proxy_entities WHERE id = :proxyId")
        fun getById(proxyId: Long): ProxyEntity?

        @Query("DELETE FROM proxy_entities WHERE id IN (:proxyId)")
        fun deleteById(proxyId: Long): Int

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteByGroup(groupId: Long)

        @Query("DELETE FROM proxy_entities WHERE groupId in (:groupId)")
        fun deleteByGroup(groupId: LongArray)

        @Delete
        fun deleteProxy(proxy: ProxyEntity): Int

        @Delete
        fun deleteProxy(proxies: List<ProxyEntity>): Int

        @Update
        fun updateProxy(proxy: ProxyEntity): Int

        @Update
        fun updateProxy(proxies: List<ProxyEntity>): Int

        @Insert
        fun addProxy(proxy: ProxyEntity): Long

        @Insert
        fun insert(proxies: List<ProxyEntity>)

        @Query("DELETE FROM proxy_entities WHERE groupId = :groupId")
        fun deleteAll(groupId: Long): Int

        @Query("DELETE FROM proxy_entities")
        fun reset()

        /**
         * Though UI disallow edit config when it is running,
         * but like chain and front/landing proxy still can be edited when running.
         * This can just update the traffic of a proxy entity when not influence other settings.
         */
        @Query(
            """
        UPDATE proxy_entities
           SET tx = CASE WHEN :tx  IS NULL THEN tx  ELSE :tx  END,
               rx = CASE WHEN :rx  IS NULL THEN rx  ELSE :rx  END
         WHERE id = :id
    """
        )
        fun updateTraffic(id: Long, tx: Long?, rx: Long?): Int
    }

    override fun describeContents(): Int {
        return 0
    }
}
