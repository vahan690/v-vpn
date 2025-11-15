package com.vvpn.android.fmt

import com.vvpn.android.database.ProxyEntity
import com.vvpn.android.ktx.reverse

object TypeMap : HashMap<String, Int>() {
    init {
        // V-VPN only supports Hysteria2 protocol
        this["hysteria"] = ProxyEntity.TYPE_HYSTERIA
        this["direct"] = ProxyEntity.TYPE_DIRECT
        this["config"] = ProxyEntity.TYPE_CONFIG
    }

    val reversed = reverse()
}