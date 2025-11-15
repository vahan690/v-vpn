package com.vvpn.android.fmt

import com.vvpn.android.MuxStrategy
import com.vvpn.android.MuxType
import com.vvpn.android.database.DataStore
import com.vvpn.android.fmt.SingBoxOptions.BrutalOptions
import com.vvpn.android.fmt.SingBoxOptions.OutboundECHOptions
import com.vvpn.android.fmt.SingBoxOptions.OutboundMultiplexOptions
import com.vvpn.android.fmt.SingBoxOptions.OutboundRealityOptions
import com.vvpn.android.fmt.SingBoxOptions.OutboundTLSOptions
import com.vvpn.android.fmt.SingBoxOptions.OutboundUTLSOptions
import com.vvpn.android.fmt.SingBoxOptions.TYPE_HYSTERIA
import com.vvpn.android.fmt.SingBoxOptions.TYPE_HYSTERIA2
import com.vvpn.android.fmt.config.ConfigBean
import com.vvpn.android.fmt.direct.DirectBean
import com.vvpn.android.fmt.direct.buildSingBoxOutboundDirectBean
import com.vvpn.android.fmt.hysteria.HysteriaBean
import com.vvpn.android.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import com.vvpn.android.fmt.hysteria.parseHysteria1Outbound
import com.vvpn.android.fmt.hysteria.parseHysteria2Outbound
import com.vvpn.android.ktx.JSONMap
import com.vvpn.android.ktx.forEach
import com.vvpn.android.ktx.gson
import org.json.JSONArray
import org.json.JSONObject

fun buildSingBoxOutbound(bean: AbstractBean): String {
    val map = when (bean) {
        is ConfigBean -> return bean.config // What if full config?
        is DirectBean -> buildSingBoxOutboundDirectBean(bean)
        is HysteriaBean -> buildSingBoxOutboundHysteriaBean(bean)
        else -> error("invalid bean: ${bean.javaClass.simpleName}")
    }
    map.type = bean.outboundType()
    map.tag = bean.name
    return gson.toJson(map)
}

fun buildSingBoxMux(bean: AbstractBean): OutboundMultiplexOptions? {
    if (!bean.serverMux) return null

    return OutboundMultiplexOptions().apply {
        padding = bean.serverMuxPadding
        protocol = when (bean.serverMuxType) {
            MuxType.H2MUX -> "h2mux"
            MuxType.SMUX -> "smux"
            MuxType.YAMUX -> "yamux"
            else -> throw IllegalArgumentException("unknown mux type: ${bean.serverMuxType}")
        }

        if (bean.serverBrutal) {
            max_connections = 1
            brutal = BrutalOptions().apply {
                enabled = true
                up_mbps = -1 // need kernel module
                down_mbps = DataStore.downloadSpeed
            }
        } else when (bean.serverMuxStrategy) {
            MuxStrategy.MAX_CONNECTIONS -> max_connections = bean.serverMuxNumber
            MuxStrategy.MIN_STREAMS -> min_streams = bean.serverMuxNumber
            MuxStrategy.MAX_STREAMS -> max_streams = bean.serverMuxNumber
            else -> throw IllegalStateException("unknown mux strategy: ${bean.serverMuxStrategy}")
        }
    }
}

fun parseOutbound(json: JSONMap): AbstractBean? = when (json["type"].toString()) {
    TYPE_HYSTERIA -> parseHysteria1Outbound(json)

    TYPE_HYSTERIA2 -> parseHysteria2Outbound(json)

    else -> null
}

/**
 * Parses a JSON map and updates the properties of the AbstractBean.
 *
 * This function iterates over the entries in the provided JSON map and updates the properties of the AbstractBean
 * based on the keys and values found. If a key does not match any known property, the unmatched callback is invoked.
 *
 * @param json The JSON map to parse.
 * @param unmatched A callback function to handle entries that do not match any known property.
 */
fun AbstractBean.parseBoxOutbound(json: JSONMap, unmatched: (key: String, value: Any) -> Unit) {
    // Note:
    // Use .toString().to*() instead of as.
    // because all integer will turn to Long.

    for (entry in json) {
        val value = entry.value ?: continue

        when (val key = entry.key) {
            "tag" -> name = value.toString()
            "server" -> serverAddress = value.toString()
            "server_port" -> serverPort = value.toString().toIntOrNull() ?: 443

            "multiplex" -> {
                val mux = (value as JSONObject)
                if (mux.optBoolean("enabled") != true) continue

                serverMux = true
                serverMuxPadding = mux.optBoolean("padding")
                serverMuxType = when (mux.optString("protocol")) {
                    "smux" -> MuxType.SMUX
                    "yamux" -> MuxType.YAMUX
                    else -> MuxType.H2MUX
                }

                serverBrutal = mux.optJSONObject("brutal")?.optBoolean("enabled")

                mux.optInt("max_connections").takeIf { it > 0 }?.let {
                    serverMuxStrategy = MuxStrategy.MAX_CONNECTIONS
                    serverMuxNumber = it
                }
                mux.optInt("min_streams").takeIf { it > 0 }?.let {
                    serverMuxStrategy = MuxStrategy.MIN_STREAMS
                    serverMuxNumber = it
                }
                mux.optInt("max_streams").takeIf { it > 0 }?.let {
                    serverMuxStrategy = MuxStrategy.MAX_STREAMS
                    serverMuxNumber = it
                }
            }

            else -> unmatched(key, value)
        }
    }
}

/**
 * Converts a given value to a mutable list of a specified type.
 *
 * This function takes an input value and attempts to convert it to a mutable list of the specified type [T].
 * If the value is null, it returns null. If the value is already a list, it maps the elements to the specified type [T]
 * and returns a mutable list. If the value is of type [T], it returns a mutable list containing that single value.
 * If the value is not of type [T] or a list, it attempts to cast the value to [T] and returns a mutable list containing it.
 *
 * @param T The type of elements in the resulting list.
 * @param value The value to be converted to a mutable list.
 * @return A mutable list of type [T] or null if the input value is null.
 */
inline fun <reified T : Any> listable(value: Any?): MutableList<T>? = when (value) {
    null -> null
    is List<*> -> value.mapNotNull { it as? T }.toMutableList()
    is JSONArray -> {
        val length = value.length()
        val list = ArrayList<T>(length)
        value.forEach { _, element ->
            (element as? T)?.let { list.add(it) }
        }
        list
    }

    is T -> mutableListOf(value)
    else -> (value as? T)?.let { mutableListOf(it) }
}

fun parseBoxUot(field: Any?): Boolean {
    if (field as? Boolean == true) return true
    return (field as? JSONObject)?.optBoolean("enabled") == true
}

fun parseBoxTLS(field: JSONMap): OutboundTLSOptions = OutboundTLSOptions().apply {
    for (entry in field) {
        val value = entry.value ?: continue

        when (entry.key) {
            "enabled" -> enabled = value.toString().toBoolean()
            "server_name" -> server_name = value.toString()
            "insecure" -> insecure = value.toString().toBoolean()
            "disable_sni" -> disable_sni = value.toString().toBoolean()

            "alpn" -> alpn = listable<String>(value)

            "certificate" -> certificate = listable<String>(value)

            "fragment" -> fragment = value.toString().toBoolean()
            "fragment_fallback_delay" -> fragment_fallback_delay = value.toString()
            "record_fragment" -> record_fragment = value.toString().toBoolean()

            "utls" -> {
                val utlsField = value as JSONObject
                utls = OutboundUTLSOptions().also {
                    it.enabled = utlsField.optBoolean("enabled")
                    it.fingerprint = utlsField.optString("fingerprint")
                }
            }

            "ech" -> {
                val echField = value as JSONObject
                ech = OutboundECHOptions().also {
                    it.enabled = echField.optBoolean("enabled")
                    it.config = listable<String>(echField.opt("config"))
                }
            }

            "reality" -> {
                val realityField = value as JSONObject
                reality = OutboundRealityOptions().also {
                    it.enabled = realityField.optBoolean("enabled")
                    it.public_key = realityField.optString("public_key")
                    it.short_id = realityField.optString("short_id")
                }
            }
        }
    }
}