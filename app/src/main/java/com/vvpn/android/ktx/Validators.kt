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

package com.vvpn.android.ktx

import androidx.annotation.RawRes
import com.vvpn.android.R
import com.vvpn.android.fmt.AbstractBean
import com.vvpn.android.fmt.hysteria.HysteriaBean

interface ValidateResult
object ResultSecure : ValidateResult
object ResultLocal : ValidateResult
class ResultDeprecated(@param:RawRes val textRes: Int) : ValidateResult
class ResultInsecure(@param:RawRes val textRes: Int) : ValidateResult

/**
 * V-VPN only supports Hysteria2 protocol validation
 */
fun AbstractBean.isInsecure(): ValidateResult {
    if (serverAddress.isIpAddress()) {
        if (serverAddress.startsWith("127.") || serverAddress.startsWith("::")) {
            return ResultLocal
        }
    }

    when (this) {
        is HysteriaBean -> {
            if (allowInsecure) return ResultInsecure(R.raw.insecure)
            if (protocolVersion < HysteriaBean.PROTOCOL_VERSION_2) {
                return ResultDeprecated(R.raw.hysteria_legacy)
            }
        }
    }

    return ResultSecure
}
