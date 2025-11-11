package io.vvpn.android.bg.proto

import io.vvpn.android.BuildConfig
import io.vvpn.android.CertProvider
import io.vvpn.android.bg.GuardedProcessPool
import io.vvpn.android.bg.NativeInterface
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.ProxyEntity
import io.vvpn.android.fmt.buildConfig
import io.vvpn.android.ktx.Logs
import io.vvpn.android.ktx.runOnDefaultDispatcher
import io.vvpn.android.ktx.systemCertificates
import io.vvpn.android.ktx.toStringIterator
import io.vvpn.android.ktx.tryResume
import io.vvpn.android.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import libcore.StringIterator
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(underVPN: Boolean): Int {
        return suspendCoroutine { c ->
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                use {
                    try {
                        init(underVPN)
                        launch()
                        if (processes.processCount > 0) {
                            // wait for plugin start
                            delay(500)
                        }

                        var enableCazilla = false
                        var certList: StringIterator? = null
                        when (DataStore.certProvider) {
                            CertProvider.SYSTEM -> {}
                            CertProvider.MOZILLA -> enableCazilla = true
                            CertProvider.SYSTEM_AND_USER -> certList = systemCertificates.let {
                                it.toStringIterator(it.size)
                            }
                        }
                        Libcore.updateRootCACerts(enableCazilla, certList)

                        c.tryResume(box.urlTest(null, link, timeout))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                        Logs.e(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = libcore.BoxInstance(config.config, NativeInterface(true))
    }

}
