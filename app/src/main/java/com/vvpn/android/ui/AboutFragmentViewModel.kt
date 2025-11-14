package com.vvpn.android.ui

import android.content.pm.PackageInfo
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vvpn.android.fmt.PluginEntry
import com.vvpn.android.ktx.Logs
import com.vvpn.android.plugin.PluginManager.loadString
import com.vvpn.android.plugin.Plugins
import com.vvpn.android.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class AboutFragmentUiState(
    val plugins: List<AboutPlugin> = emptyList(),
)

internal data class AboutPlugin(
    val id: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val provider: String,
    val entry: PluginEntry? = null,
)

internal class AboutFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AboutFragmentUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        viewModelScope.launch(Dispatchers.IO) {
            loadPlugins0()
        }
    }

    private suspend fun loadPlugins0() {
        PackageCache.awaitLoadSync()
        for ((packageName, plugin) in PackageCache.installedPluginPackages) try {
            val id = plugin.providers!![0].loadString(Plugins.METADATA_KEY_ID)
            if (id.isNullOrBlank()) continue

            val old = _uiState.value
            _uiState.emit(old.copy(
                plugins = old.plugins + AboutPlugin(
                    id = id,
                    packageName = packageName,
                    version = plugin.versionName ?: "unknown",
                    versionCode = plugin.versionCodeCompat(),
                    provider = Plugins.displayExeProvider(packageName),
                    entry = PluginEntry.find(id),
                )
            ))
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    private fun PackageInfo.versionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }
}