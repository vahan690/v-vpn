package io.vvpn.android.fmt.shadowtls

import io.vvpn.android.fmt.v2ray.buildSingBoxOutboundTLS
import io.vvpn.android.fmt.SingBoxOptions

fun buildSingBoxOutboundShadowTLSBean(bean: ShadowTLSBean): SingBoxOptions.Outbound_ShadowTLSOptions {
    return SingBoxOptions.Outbound_ShadowTLSOptions().apply {
        type = SingBoxOptions.TYPE_SHADOWTLS
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.protocolVersion
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
    }
}
