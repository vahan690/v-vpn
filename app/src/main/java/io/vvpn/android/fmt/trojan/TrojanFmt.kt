package io.vvpn.android.fmt.trojan

import io.vvpn.android.fmt.v2ray.parseDuckSoft
import io.vvpn.android.ktx.parseBoolean
import io.vvpn.android.ktx.queryParameterNotBlank
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
