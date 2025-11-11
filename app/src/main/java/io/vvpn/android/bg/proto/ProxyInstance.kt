package io.vvpn.android.bg.proto

import io.vvpn.android.BuildConfig
import io.vvpn.android.bg.BaseService
import io.vvpn.android.bg.ServiceNotification
import io.vvpn.android.database.ProxyEntity
import io.vvpn.android.ktx.Logs
import io.vvpn.android.ktx.gson
import io.vvpn.android.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.runBlocking

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var displayProfileName = ServiceNotification.genTitle(profile)

    var trafficLooper: TrafficLooper? = null

    override fun buildConfig() {
        super.buildConfig()
        Logs.d(config.config)
        if (BuildConfig.DEBUG) Logs.d("trafficMap: " + gson.toJson(config.trafficMap))
    }

    override suspend fun init(isVPN: Boolean) {
        super.init(isVPN)
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        super.launch() // start box
        runOnDefaultDispatcher {
            service?.let {
                trafficLooper = TrafficLooper(it.data, this)
            }
            trafficLooper?.start()
        }
    }

    override fun close() {
        super.close()
        runBlocking {
            trafficLooper?.stop()
            trafficLooper = null
        }
    }
}
