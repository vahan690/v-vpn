package io.vvpn.android.plugin

import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import io.vvpn.android.R
import io.vvpn.android.SagerNet.Companion.app
import io.vvpn.android.database.DataStore
import io.vvpn.android.ktx.mapX
import io.vvpn.android.plugin.PluginManager.loadString
import io.vvpn.android.utils.PackageCache

object Plugins {
    const val AUTHORITIES_PREFIX_HUSI_EXE = "fr.husi.plugin."
    const val AUTHORITIES_PREFIX_SEKAI_EXE =
        "io.vvpn.android.plugin." // https://github.com/SagerNet/SagerNet
    const val AUTHORITIES_PREFIX_NEKO_EXE =
        "moe.matsuri.exe." // https://github.com/MatsuriDayo/plugins
    const val AUTHORITIES_PREFIX_DYHKWONG =
        "com.github.dyhkwong."// https://github.com/dyhkwong/Exclave

    const val ACTION_NATIVE_PLUGIN = "io.vvpn.android.plugin.ACTION_NATIVE_PLUGIN"

    const val METADATA_KEY_ID = "io.vvpn.android.plugin.id"
    const val METADATA_KEY_EXECUTABLE_PATH = "io.vvpn.android.plugin.executable_path"

    val allowedSet = HashSet<String>(DataStore.customPluginPrefix.split("\n").filter {
        it.isNotBlank() && it != "."
    }).apply {
        add(AUTHORITIES_PREFIX_HUSI_EXE)
        add(AUTHORITIES_PREFIX_SEKAI_EXE)
        add(AUTHORITIES_PREFIX_NEKO_EXE)
        add(AUTHORITIES_PREFIX_DYHKWONG)
    }

    fun isPlugin(pkg: PackageInfo): Boolean {
        if (pkg.providers.isNullOrEmpty()) return false
        val auth = pkg.providers!![0].authority ?: return false
        for (prefix in allowedSet) {
            if (auth.startsWith(prefix)) return true
        }
        return false
    }

    fun preferExePrefix(): String {
        return AUTHORITIES_PREFIX_HUSI_EXE
    }

    fun displayExeProvider(pkgName: String): String {
        return when {
            pkgName.startsWith(AUTHORITIES_PREFIX_HUSI_EXE) -> {
                app.getStringCompat(R.string.app_name)
            }

            pkgName.startsWith(AUTHORITIES_PREFIX_SEKAI_EXE) -> "SagerNet"
            pkgName.startsWith(AUTHORITIES_PREFIX_DYHKWONG) -> "dyhkwong"
            pkgName.startsWith(AUTHORITIES_PREFIX_NEKO_EXE) -> "Matsuri"
            else -> "Unknown"
        }
    }

    fun getPlugin(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null
        getPluginExternal(pluginId)?.let { return it }
        // internal so
        return ProviderInfo().apply { authority = AUTHORITIES_PREFIX_HUSI_EXE }
    }

    fun getPluginExternal(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null

        val providers = getExtPlugin(pluginId)

        val preferProvider = providers.find {
            it.authority.startsWith(preferExePrefix())
        }
        if (preferProvider != null) return preferProvider

        return providers.randomOrNull()
    }

    private fun getExtPlugin(pluginId: String): List<ProviderInfo> {
        PackageCache.awaitLoadSync()
        val pkgs = PackageCache.installedPluginPackages
            .map { it.value }
            .filter { it.providers!![0].loadString(METADATA_KEY_ID) == pluginId }
        return pkgs.mapX { it.providers!![0] }
    }

}
