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
import com.vvpn.android.fmt.anytls.AnyTLSBean
import com.vvpn.android.fmt.anytls.toUri
import com.vvpn.android.fmt.buildConfig
import com.vvpn.android.fmt.buildSingBoxOutbound
import com.vvpn.android.fmt.config.ConfigBean
import com.vvpn.android.fmt.direct.DirectBean
import com.vvpn.android.fmt.http.HttpBean
import com.vvpn.android.fmt.http.toUri
import com.vvpn.android.fmt.hysteria.HysteriaBean
import com.vvpn.android.fmt.hysteria.buildHysteriaConfig
import com.vvpn.android.fmt.hysteria.canUseSingBox
import com.vvpn.android.fmt.hysteria.toUri
import com.vvpn.android.fmt.internal.ChainBean
import com.vvpn.android.fmt.internal.ProxySetBean
import com.vvpn.android.fmt.juicity.JuicityBean
import com.vvpn.android.fmt.juicity.buildJuicityConfig
import com.vvpn.android.fmt.juicity.toUri
import com.vvpn.android.fmt.mieru.MieruBean
import com.vvpn.android.fmt.mieru.buildMieruConfig
import com.vvpn.android.fmt.mieru.toUri
import com.vvpn.android.fmt.naive.NaiveBean
import com.vvpn.android.fmt.naive.buildNaiveConfig
import com.vvpn.android.fmt.naive.toUri
import com.vvpn.android.fmt.shadowquic.ShadowQUICBean
import com.vvpn.android.fmt.shadowquic.buildShadowQUICConfig
import com.vvpn.android.fmt.shadowsocks.ShadowsocksBean
import com.vvpn.android.fmt.shadowsocks.toUri
import com.vvpn.android.fmt.socks.SOCKSBean
import com.vvpn.android.fmt.socks.toUri
import com.vvpn.android.fmt.ssh.SSHBean
import com.vvpn.android.fmt.toUniversalLink
import com.vvpn.android.fmt.trojan.TrojanBean
import com.vvpn.android.fmt.tuic.TuicBean
import com.vvpn.android.fmt.tuic.toUri
import com.vvpn.android.fmt.v2ray.VMessBean
import com.vvpn.android.fmt.v2ray.isTLS
import com.vvpn.android.fmt.v2ray.toUriVMessVLESSTrojan
import com.vvpn.android.fmt.wireguard.WireGuardBean
import com.vvpn.android.ktx.applyDefaultValues
import com.vvpn.android.ui.profile.ChainSettingsActivity
import com.vvpn.android.ui.profile.DirectSettingsActivity
import com.vvpn.android.ui.profile.HttpSettingsActivity
import com.vvpn.android.ui.profile.HysteriaSettingsActivity
import com.vvpn.android.ui.profile.JuicitySettingsActivity
import com.vvpn.android.ui.profile.MieruSettingsActivity
import com.vvpn.android.ui.profile.NaiveSettingsActivity
import com.vvpn.android.ui.profile.ProfileSettingsActivity
import com.vvpn.android.ui.profile.SSHSettingsActivity
import com.vvpn.android.ui.profile.ShadowsocksSettingsActivity
import com.vvpn.android.ui.profile.SocksSettingsActivity
import com.vvpn.android.ui.profile.TrojanSettingsActivity
import com.vvpn.android.ui.profile.TuicSettingsActivity
import com.vvpn.android.ui.profile.VMessSettingsActivity
import com.vvpn.android.ui.profile.WireGuardSettingsActivity
import com.vvpn.android.ui.profile.ConfigSettingActivity
import com.vvpn.android.fmt.shadowtls.ShadowTLSBean
import com.vvpn.android.ui.profile.AnyTLSSettingsActivity
import com.vvpn.android.ui.profile.ProxySetSettingsActivity
import com.vvpn.android.ui.profile.ShadowQUICSettingsActivity
import com.vvpn.android.ui.profile.ShadowTLSSettingsActivity

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
    var socksBean: SOCKSBean? = null,
    var httpBean: HttpBean? = null,
    var ssBean: ShadowsocksBean? = null,
    var vmessBean: VMessBean? = null,
    var trojanBean: TrojanBean? = null,
    var mieruBean: MieruBean? = null,
    var naiveBean: NaiveBean? = null,
    var hysteriaBean: HysteriaBean? = null,
    var tuicBean: TuicBean? = null,
    var juicityBean: JuicityBean? = null,
    var sshBean: SSHBean? = null,
    var wgBean: WireGuardBean? = null,
    var shadowTLSBean: ShadowTLSBean? = null,
    var directBean: DirectBean? = null,
    var anyTLSBean: AnyTLSBean? = null,
    var shadowQUICBean: ShadowQUICBean? = null,
    var proxySetBean: ProxySetBean? = null,
    var chainBean: ChainBean? = null,
    var configBean: ConfigBean? = null,
) : Serializable() {

    companion object {
        const val TYPE_SOCKS = 0
        const val TYPE_HTTP = 1
        const val TYPE_SS = 2
        const val TYPE_VMESS = 4 // And VLESS
        const val TYPE_TROJAN = 6
        const val TYPE_TROJAN_GO = 7 // Deleted
        const val TYPE_CHAIN = 8
        const val TYPE_NAIVE = 9
        const val TYPE_HYSTERIA = 15
        const val TYPE_SSH = 17
        const val TYPE_WG = 18
        const val TYPE_SHADOWTLS = 19
        const val TYPE_TUIC = 20
        const val TYPE_MIERU = 21
        const val TYPE_JUICITY = 22
        const val TYPE_DIRECT = 23
        const val TYPE_ANYTLS = 24
        const val TYPE_SHADOWQUIC = 25
        const val TYPE_PROXY_SET = 26
        const val TYPE_CONFIG = 998
        const val TYPE_NEKO = 999 // Deleted

        /** Plugin not found or not support this ping type */
        const val STATUS_INVALID = -1
        const val STATUS_INITIAL = 0
        const val STATUS_AVAILABLE = 1

        /** Unclear */
        const val STATUS_UNREACHABLE = 2

        /** Has obvious error */
        const val STATUS_UNAVAILABLE = 3

        val chainName by lazy { app.getStringCompat(R.string.proxy_chain) }

        private val placeHolderBean = SOCKSBean().applyDefaultValues()

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
            TYPE_SOCKS -> socksBean = KryoConverters.socksDeserialize(byteArray)
            TYPE_HTTP -> httpBean = KryoConverters.httpDeserialize(byteArray)
            TYPE_SS -> ssBean = KryoConverters.shadowsocksDeserialize(byteArray)
            TYPE_VMESS -> vmessBean = KryoConverters.vmessDeserialize(byteArray)
            TYPE_TROJAN -> trojanBean = KryoConverters.trojanDeserialize(byteArray)
            TYPE_MIERU -> mieruBean = KryoConverters.mieruDeserialize(byteArray)
            TYPE_NAIVE -> naiveBean = KryoConverters.naiveDeserialize(byteArray)
            TYPE_HYSTERIA -> hysteriaBean = KryoConverters.hysteriaDeserialize(byteArray)
            TYPE_SSH -> sshBean = KryoConverters.sshDeserialize(byteArray)
            TYPE_WG -> wgBean = KryoConverters.wireguardDeserialize(byteArray)
            TYPE_TUIC -> tuicBean = KryoConverters.tuicDeserialize(byteArray)
            TYPE_JUICITY -> juicityBean = KryoConverters.juicityDeserialize(byteArray)
            TYPE_DIRECT -> directBean = KryoConverters.directDeserialize(byteArray)
            TYPE_SHADOWTLS -> shadowTLSBean = KryoConverters.shadowTLSDeserialize(byteArray)
            TYPE_ANYTLS -> anyTLSBean = KryoConverters.anyTLSDeserialize(byteArray)
            TYPE_SHADOWQUIC -> shadowQUICBean = KryoConverters.shadowQUICDeserialize(byteArray)
            TYPE_PROXY_SET -> proxySetBean = KryoConverters.proxySetDeserialize(byteArray)
            TYPE_CHAIN -> chainBean = KryoConverters.chainDeserialize(byteArray)
            TYPE_CONFIG -> configBean = KryoConverters.configDeserialize(byteArray)
        }
    }

    fun displayType(): String = when (type) {
        TYPE_SOCKS -> socksBean!!.protocolName()
        TYPE_HTTP -> if (httpBean!!.isTLS()) "HTTPS" else "HTTP"
        TYPE_SS -> "Shadowsocks"
        TYPE_VMESS -> if (vmessBean!!.isVLESS) "VLESS" else "VMess"
        TYPE_TROJAN -> "Trojan"
        TYPE_MIERU -> "Mieru"
        TYPE_NAIVE -> "Naïve"
        TYPE_HYSTERIA -> "Hysteria" + hysteriaBean!!.protocolVersion
        TYPE_SSH -> "SSH"
        TYPE_WG -> "WireGuard"
        TYPE_TUIC -> "TUIC"
        TYPE_JUICITY -> "Juicity"
        TYPE_SHADOWTLS -> "ShadowTLS"
        TYPE_DIRECT -> "Direct"
        TYPE_ANYTLS -> "AnyTLS"
        TYPE_SHADOWQUIC -> "Shadow QUIC"
        TYPE_PROXY_SET -> proxySetBean!!.displayType()
        TYPE_CHAIN -> chainName
        TYPE_CONFIG -> configBean!!.displayType()
        else -> "Undefined type $type"
    }

    fun displayName() = requireBean().displayName()
    fun displayAddress() = requireBean().displayAddress()

    fun requireBean(): AbstractBean {
        return when (type) {
            TYPE_SOCKS -> socksBean
            TYPE_HTTP -> httpBean
            TYPE_SS -> ssBean
            TYPE_VMESS -> vmessBean
            TYPE_TROJAN -> trojanBean
            TYPE_MIERU -> mieruBean
            TYPE_NAIVE -> naiveBean
            TYPE_HYSTERIA -> hysteriaBean
            TYPE_SSH -> sshBean
            TYPE_WG -> wgBean
            TYPE_TUIC -> tuicBean
            TYPE_JUICITY -> juicityBean
            TYPE_DIRECT -> directBean
            TYPE_ANYTLS -> anyTLSBean
            TYPE_SHADOWQUIC -> shadowQUICBean
            TYPE_SHADOWTLS -> shadowTLSBean
            TYPE_PROXY_SET -> proxySetBean
            TYPE_CHAIN -> chainBean
            TYPE_CONFIG -> configBean
            else -> error("Undefined type $type")
        } ?: error("Null ${displayType()} profile")
    }

    fun setBean(bean: AbstractBean) {
        when (type) {
            TYPE_SOCKS -> socksBean = bean as SOCKSBean
            TYPE_HTTP -> httpBean = bean as HttpBean
            TYPE_SS -> ssBean = bean as ShadowsocksBean
            TYPE_VMESS -> vmessBean = bean as VMessBean
            TYPE_TROJAN -> trojanBean = bean as TrojanBean
            TYPE_MIERU -> mieruBean = bean as MieruBean
            TYPE_NAIVE -> naiveBean = bean as NaiveBean
            TYPE_HYSTERIA -> hysteriaBean = bean as HysteriaBean
            TYPE_SSH -> sshBean = bean as SSHBean
            TYPE_WG -> wgBean = bean as WireGuardBean
            TYPE_TUIC -> tuicBean = bean as TuicBean
            TYPE_JUICITY -> juicityBean = bean as JuicityBean
            TYPE_DIRECT -> directBean = bean as DirectBean
            TYPE_ANYTLS -> anyTLSBean = bean as AnyTLSBean
            TYPE_SHADOWQUIC -> shadowQUICBean = bean as ShadowQUICBean
            TYPE_SHADOWTLS -> shadowTLSBean = bean as ShadowTLSBean
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
        TYPE_SSH -> false
        TYPE_WG -> false
        TYPE_SHADOWQUIC -> false
        TYPE_SHADOWTLS -> false
        TYPE_PROXY_SET -> false
        TYPE_CHAIN -> false
        TYPE_CONFIG -> false
        else -> true
    }

    fun toStdLink(): String = with(requireBean()) {
        when (this) {
            is SOCKSBean -> toUri()
            is HttpBean -> toUri()
            is ShadowsocksBean -> toUri()
            is VMessBean -> toUriVMessVLESSTrojan()
            is TrojanBean -> toUriVMessVLESSTrojan()
            is NaiveBean -> toUri()
            is HysteriaBean -> toUri()
            is TuicBean -> toUri()
            is JuicityBean -> toUri()
            is MieruBean -> toUri()
            is AnyTLSBean -> toUri()
            else -> toUniversalLink()
        }
    }

    fun mustUsePlugin(): Boolean = when (type) {
        TYPE_MIERU -> true
        TYPE_NAIVE -> true
        TYPE_JUICITY -> true
        TYPE_SHADOWQUIC -> true
        else -> false
    }

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
                            is MieruBean -> {
                                append("\n\n")
                                append(bean.buildMieruConfig(port, logLevel))
                            }

                            is NaiveBean -> {
                                append("\n\n")
                                append(bean.buildNaiveConfig(port))
                            }

                            is HysteriaBean -> {
                                append("\n\n")
                                append(bean.buildHysteriaConfig(port, false, null))
                            }

                            is JuicityBean -> {
                                append("\n\n")
                                append(bean.buildJuicityConfig(port, false))
                            }

                            is ShadowQUICBean -> {
                                append("\n\n")
                                append(bean.buildShadowQUICConfig(port, false, logLevel))
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
            TYPE_MIERU -> true
            TYPE_NAIVE -> true
            TYPE_HYSTERIA -> !hysteriaBean!!.canUseSingBox()
            TYPE_JUICITY -> true
            TYPE_SHADOWQUIC -> true
            else -> false
        }
    }

    fun putBean(bean: AbstractBean): ProxyEntity {
        socksBean = null
        httpBean = null
        ssBean = null
        vmessBean = null
        trojanBean = null
        mieruBean = null
        naiveBean = null
        hysteriaBean = null
        sshBean = null
        wgBean = null
        tuicBean = null
        juicityBean = null
        directBean = null
        shadowTLSBean = null
        anyTLSBean = null
        shadowQUICBean = null
        proxySetBean = null
        chainBean = null
        configBean = null

        when (bean) {
            is SOCKSBean -> {
                type = TYPE_SOCKS
                socksBean = bean
            }

            is HttpBean -> {
                type = TYPE_HTTP
                httpBean = bean
            }

            is ShadowsocksBean -> {
                type = TYPE_SS
                ssBean = bean
            }

            is VMessBean -> {
                type = TYPE_VMESS
                vmessBean = bean
            }

            is TrojanBean -> {
                type = TYPE_TROJAN
                trojanBean = bean
            }

            is MieruBean -> {
                type = TYPE_MIERU
                mieruBean = bean
            }

            is NaiveBean -> {
                type = TYPE_NAIVE
                naiveBean = bean
            }

            is HysteriaBean -> {
                type = TYPE_HYSTERIA
                hysteriaBean = bean
            }

            is SSHBean -> {
                type = TYPE_SSH
                sshBean = bean
            }

            is WireGuardBean -> {
                type = TYPE_WG
                wgBean = bean
            }

            is TuicBean -> {
                type = TYPE_TUIC
                tuicBean = bean
            }

            is JuicityBean -> {
                type = TYPE_JUICITY
                juicityBean = bean
            }

            is DirectBean -> {
                type = TYPE_DIRECT
                directBean = bean
            }

            is ShadowTLSBean -> {
                type = TYPE_SHADOWTLS
                shadowTLSBean = bean
            }

            is AnyTLSBean -> {
                type = TYPE_ANYTLS
                anyTLSBean = bean
            }

            is ShadowQUICBean -> {
                type = TYPE_SHADOWQUIC
                shadowQUICBean = bean
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
                TYPE_SOCKS -> SocksSettingsActivity::class.java
                TYPE_HTTP -> HttpSettingsActivity::class.java
                TYPE_SS -> ShadowsocksSettingsActivity::class.java
                TYPE_VMESS -> VMessSettingsActivity::class.java
                TYPE_TROJAN -> TrojanSettingsActivity::class.java
                TYPE_MIERU -> MieruSettingsActivity::class.java
                TYPE_NAIVE -> NaiveSettingsActivity::class.java
                TYPE_HYSTERIA -> HysteriaSettingsActivity::class.java
                TYPE_SSH -> SSHSettingsActivity::class.java
                TYPE_WG -> WireGuardSettingsActivity::class.java
                TYPE_TUIC -> TuicSettingsActivity::class.java
                TYPE_JUICITY -> JuicitySettingsActivity::class.java
                TYPE_DIRECT -> DirectSettingsActivity::class.java
                TYPE_SHADOWTLS -> ShadowTLSSettingsActivity::class.java
                TYPE_ANYTLS -> AnyTLSSettingsActivity::class.java
                TYPE_SHADOWQUIC -> ShadowQUICSettingsActivity::class.java
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