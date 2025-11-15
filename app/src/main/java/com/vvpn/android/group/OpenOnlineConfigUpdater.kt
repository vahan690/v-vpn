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

package com.vvpn.android.group

import com.vvpn.android.R
import com.vvpn.android.SagerNet.Companion.app
import com.vvpn.android.database.DataStore
import com.vvpn.android.database.GroupManager
import com.vvpn.android.database.ProxyGroup
import com.vvpn.android.database.SubscriptionBean
import com.vvpn.android.fmt.AbstractBean
// import com.vvpn.android.fmt.shadowsocks.ShadowsocksBean
// import com.vvpn.android.fmt.shadowsocks.pluginToLocal
import com.vvpn.android.ktx.Logs
import com.vvpn.android.ktx.addPathSegments
import com.vvpn.android.ktx.applyDefaultValues
import com.vvpn.android.ktx.filterIsInstance
import com.vvpn.android.ktx.generateUserAgent
import com.vvpn.android.ktx.getIntOrNull
import com.vvpn.android.ktx.getLongOrNull
import com.vvpn.android.ktx.getStr
import libcore.Libcore
import libcore.URL
import org.json.JSONObject

/** https://github.com/Shadowsocks-NET/OpenOnlineConfig */
object OpenOnlineConfigUpdater : GroupUpdater() {

    const val OOC_VERSION = 1
    // Note: This updater previously supported shadowsocks which has been removed.
    // OOC_PROTOCOLS is now empty as shadowsocks is no longer supported.
    val OOC_PROTOCOLS = listOf<String>()

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {
        val apiToken: JSONObject
        val baseLink: URL
        val certSha256: String?
        try {
            apiToken = JSONObject(subscription.token)

            val version = apiToken.getIntOrNull("version")
            if (version != OOC_VERSION) {
                if (version != null) {
                    error("Unsupported OOC version $version")
                } else {
                    error("Missing field: version")
                }
            }
            val baseUrl = apiToken.getStr("baseUrl")
            when {
                baseUrl.isNullOrBlank() -> {
                    error("Missing field: baseUrl")
                }

                baseUrl.endsWith("/") -> {
                    error("baseUrl must not contain a trailing slash")
                }

                !baseUrl.startsWith("https://") -> {
                    error("Protocol scheme must be https")
                }

                else -> baseLink = Libcore.parseURL(baseUrl)
            }
            val secret = apiToken.getStr("secret")
            if (secret.isNullOrBlank()) error("Missing field: secret")
            baseLink.addPathSegments(secret, "ooc/v1")

            val userId = apiToken.getStr("userId")
            if (userId.isNullOrBlank()) error("Missing field: userId")
            baseLink.addPathSegments(userId)
            certSha256 = apiToken.getStr("certSha256")
        } catch (e: Exception) {
            Logs.e("OOC token check failed, token = ${subscription.token}", e)
            error(app.getStringCompat(R.string.ooc_subscription_token_invalid))
        }

        val response = Libcore.newHttpClient().apply {
            if (DataStore.serviceState.started) {
                useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
            }
            // Strict !!!
            restrictedTLS()
            if (certSha256 != null) pinnedSHA256(certSha256)
        }.newRequest().apply {
            setURL(baseLink.string)
            setUserAgent(generateUserAgent(subscription.customUserAgent))
        }.execute()

        val oocResponse = JSONObject(response.contentString.value)

        val protocols = oocResponse.getJSONArray("protocols").filterIsInstance<String>()
        for (protocol in protocols) {
            if (protocol !in OOC_PROTOCOLS) {
                userInterface?.alert(
                    app.getStringCompat(R.string.ooc_missing_protocol, protocol)
                )
            }
        }

        subscription.username = oocResponse.getStr("username")
        subscription.bytesUsed = oocResponse.getLongOrNull("bytesUsed") ?: -1
        subscription.bytesRemaining = oocResponse.getLongOrNull("bytesRemaining") ?: -1
        subscription.expiryDate = oocResponse.getLongOrNull("expiryDate") ?: -1
        subscription.applyDefaultValues()

        val proxies = mutableListOf<AbstractBean>()

        // Protocol parsing has been disabled as shadowsocks is no longer supported
        // Previously supported: shadowsocks
        // Currently no protocols are supported by this updater
        /*
        for (protocol in protocols) {
            val profilesInProtocol =
                oocResponse.getJSONArray(protocol).filterIsInstance<JSONObject>()

            when (protocol) {
                "shadowsocks" -> for (profile in profilesInProtocol) {
                    val bean = ShadowsocksBean()

                    bean.name = profile.getStr("name")
                    bean.serverAddress = profile.getStr("address")
                    bean.serverPort = profile.getInt("port")
                    bean.method = profile.getStr("method")
                    bean.password = profile.getStr("password")

                    // check plugin exists?
                    // check pluginVersion?
                    // TODO support pluginArguments
                    val pluginName = profile.getStr("pluginName")
                    if (!pluginName.isNullOrBlank()) {
                        bean.plugin = pluginName + ";" + profile.getStr("pluginOptions")
                    }

                    proxies.add(bean.applyDefaultValues().apply { pluginToLocal() })
                }
            }
        }
        */

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }
}
