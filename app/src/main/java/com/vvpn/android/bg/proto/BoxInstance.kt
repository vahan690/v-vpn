package com.vvpn.android.bg.proto

import android.os.SystemClock
import com.vvpn.android.SagerNet.Companion.app
import com.vvpn.android.bg.AbstractInstance
import com.vvpn.android.bg.GuardedProcessPool
import com.vvpn.android.bg.NativeInterface
import com.vvpn.android.database.DataStore
import com.vvpn.android.database.ProxyEntity
import com.vvpn.android.fmt.ConfigBuildResult
import com.vvpn.android.fmt.buildConfig
import com.vvpn.android.fmt.hysteria.HysteriaBean
import com.vvpn.android.fmt.hysteria.buildHysteriaConfig
import com.vvpn.android.ktx.Logs
import com.vvpn.android.ktx.runOnDefaultDispatcher
import com.vvpn.android.plugin.PluginManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import libcore.BoxInstance
import libcore.Libcore
import java.io.File
import kotlin.system.exitProcess

abstract class BoxInstance(
    val profile: ProxyEntity,
) : AbstractInstance {

    lateinit var config: ConfigBuildResult
    lateinit var box: BoxInstance

    private val pluginPath = hashMapOf<String, PluginManager.InitResult>()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    private val externalInstances = hashMapOf<Int, AbstractInstance>()
    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized && ::box.isInitialized
    }

    protected fun initPlugin(name: String): PluginManager.InitResult {
        return pluginPath.getOrPut(name) { PluginManager.init(name)!! }
    }

    protected open fun buildConfig() {
        config = buildConfig(profile)
    }

    protected open suspend fun loadConfig() {
        box = BoxInstance(config.config, NativeInterface(false))
    }

    open suspend fun init(isVPN: Boolean) {
        buildConfig()
        val logLevel = DataStore.logLevel
        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, profile) ->
                when (val bean = profile.requireBean()) {

                    is HysteriaBean -> {
                        when (bean.protocolVersion) {
                            HysteriaBean.PROTOCOL_VERSION_1 -> initPlugin("hysteria-plugin")
                            HysteriaBean.PROTOCOL_VERSION_2 -> initPlugin("hysteria2-plugin")
                        }
                        pluginConfigs[port] =
                            profile.type to bean.buildHysteriaConfig(port, isVPN) {
                                File(
                                    app.cacheDir,
                                    "hysteria_" + SystemClock.elapsedRealtime() + ".ca",
                                ).apply {
                                    parentFile?.mkdirs()
                                    cacheFiles.add(this)
                                }
                            }
                    }
                }
            }
        }
        loadConfig()
    }

    override fun launch() {
        // TODO move, this is not box
        val cacheDir = File(app.cacheDir, "tmpcfg")
        cacheDir.mkdirs()

        for ((chain) in config.externalIndex) {
            chain.entries.forEach { (port, profile) ->
                val bean = profile.requireBean()
                val (_, config) = pluginConfigs[port] ?: (0 to "")

                when {
                    externalInstances.containsKey(port) -> {
                        externalInstances[port]!!.launch()
                    }

                    bean is HysteriaBean -> {
                        val configFile = File(
                            cacheDir, "hysteria_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf("HYSTERIA_DISABLE_UPDATE_CHECK" to "1")

                        val commands =
                            if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
                                mutableListOf(
                                    initPlugin("hysteria-plugin").path,
                                    "--no-check",
                                    "--config", configFile.absolutePath,
                                    "--log-level", if (DataStore.logLevel > 0) "trace" else "warn",
                                    "client",
                                )
                            } else {
                                mutableListOf(
                                    initPlugin("hysteria2-plugin").path, "client",
                                    "--config", configFile.absolutePath,
                                    "--log-level", if (DataStore.logLevel > 0) "warn" else "error",
                                )
                            }

                        if (bean.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
                            && bean.protocol == HysteriaBean.PROTOCOL_FAKETCP
                        ) {
                            commands.addAll(0, listOf("su", "-c"))
                        }

                        processes.start(commands, envMap)
                    }
                }
            }
        }

        box.start()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun close() {
        for (instance in externalInstances.values) {
            runCatching {
                instance.close()
            }
        }

        cacheFiles.removeAll { it.delete(); true }

        if (::processes.isInitialized) processes.close(GlobalScope + Dispatchers.IO)

        if (::box.isInitialized) {
            try {
                box.close()
            } catch (e: Exception) {
                Logs.w(e)
                // Kill the process if it is not closed properly to clean exist inbound listeners.
                // Do not kill in main process, whose test not starts any listener.
                if (!app.isMainProcess) runOnDefaultDispatcher {
                    delay(500) // Wait for error handling
                    exitProcess(0)
                }
            }
        }
    }

}
