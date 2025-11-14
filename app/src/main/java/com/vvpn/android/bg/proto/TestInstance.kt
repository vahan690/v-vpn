package com.vvpn.android.bg.proto

import com.vvpn.android.BuildConfig
import com.vvpn.android.CertProvider
import com.vvpn.android.bg.GuardedProcessPool
import com.vvpn.android.bg.NativeInterface
import com.vvpn.android.database.DataStore
import com.vvpn.android.database.ProxyEntity
import com.vvpn.android.fmt.buildConfig
import com.vvpn.android.ktx.Logs
import com.vvpn.android.ktx.runOnDefaultDispatcher
import com.vvpn.android.ktx.systemCertificates
import com.vvpn.android.ktx.toStringIterator
import com.vvpn.android.ktx.tryResume
import com.vvpn.android.ktx.tryResumeWithException
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
