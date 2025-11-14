package com.vvpn.android.fmt.trojan

import com.vvpn.android.fmt.v2ray.parseDuckSoft
import com.vvpn.android.ktx.parseBoolean
import com.vvpn.android.ktx.queryParameterNotBlank
import libcore.Libcore

fun parseTrojan(link: String): TrojanBean {
    val url = Libcore.parseURL(link)
    return TrojanBean().apply {
        parseDuckSoft(url)
        url.parseBoolean("allowInsecure")
        url.queryParameterNotBlank("peer").let {
            sni = it
        }
    }

}
