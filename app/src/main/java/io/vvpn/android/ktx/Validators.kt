/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.vvpn.android.ktx

import androidx.annotation.RawRes
import io.vvpn.android.R
import io.vvpn.android.fmt.AbstractBean
import io.vvpn.android.fmt.anytls.AnyTLSBean
import io.vvpn.android.fmt.http.HttpBean
import io.vvpn.android.fmt.hysteria.HysteriaBean
import io.vvpn.android.fmt.juicity.JuicityBean
import io.vvpn.android.fmt.shadowquic.ShadowQUICBean
import io.vvpn.android.fmt.shadowsocks.ShadowsocksBean
import io.vvpn.android.fmt.socks.SOCKSBean
import io.vvpn.android.fmt.trojan.TrojanBean
import io.vvpn.android.fmt.tuic.TuicBean
import io.vvpn.android.fmt.v2ray.VMessBean
import io.vvpn.android.fmt.v2ray.isTLS
import io.vvpn.android.fmt.shadowtls.ShadowTLSBean

interface ValidateResult
object ResultSecure : ValidateResult
object ResultLocal : ValidateResult
class ResultDeprecated(@param:RawRes val textRes: Int) : ValidateResult
class ResultInsecure(@param:RawRes val textRes: Int) : ValidateResult

val ssSecureList = "(gcm|poly1305)".toRegex()

fun AbstractBean.isInsecure(): ValidateResult {
    if (serverAddress.isIpAddress()) {
        if (serverAddress.startsWith("127.") || serverAddress.startsWith("::")) {
            return ResultLocal
        }
    }
    when (this) {
        is ShadowsocksBean -> {
            if (plugin.isBlank() || plugin.startsWith("obfs-local;")) {
                if (!method.contains(ssSecureList)) {
                    return ResultInsecure(R.raw.shadowsocks_stream_cipher)
                }
            }
        }

        is HttpBean -> if (!isTLS()) return ResultInsecure(R.raw.not_encrypted)

        is SOCKSBean -> return ResultInsecure(R.raw.not_encrypted)

        is VMessBean -> {
            if (alterId > 0) return ResultInsecure(R.raw.vmess_md5_auth)
            if (isVLESS || encryption in arrayOf("none", "zero")) {
                if (!isTLS()) return ResultInsecure(R.raw.not_encrypted)
            }
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
        }

        is TrojanBean -> {
            if (!isTLS()) return ResultInsecure(R.raw.not_encrypted)
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
        }

        is HysteriaBean -> {
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
            if (protocolVersion < HysteriaBean.PROTOCOL_VERSION_2) return ResultDeprecated(R.raw.hysteria_legacy)
        }

        is TuicBean -> {
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
            if (zeroRTT) return ResultInsecure(R.raw.quic_0_rtt)
        }

        is ShadowTLSBean -> {
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
            if (protocolVersion < 3) return ResultDeprecated(R.raw.shadowtls_legacy)
        }

        is JuicityBean -> {
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
        }

        is AnyTLSBean -> {
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
        }

        is ShadowQUICBean -> {
            if (zeroRTT) return ResultInsecure(R.raw.quic_0_rtt)
        }
    }

    return ResultSecure
}

