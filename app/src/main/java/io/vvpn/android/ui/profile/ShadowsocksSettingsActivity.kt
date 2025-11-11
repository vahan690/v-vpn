package io.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.vvpn.android.Key
import io.vvpn.android.R
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.preference.EditTextPreferenceModifiers
import io.vvpn.android.fmt.shadowsocks.ShadowsocksBean
import io.vvpn.android.widget.MaterialSwitchPreference
import io.vvpn.android.widget.PasswordSummaryProvider
import rikka.preference.SimpleMenuPreference

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override fun createBean() = ShadowsocksBean()

    override fun ShadowsocksBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverMethod = method
        DataStore.serverPassword = password
        DataStore.serverMux = serverMux
        DataStore.serverBrutal = serverBrutal
        DataStore.serverMuxType = serverMuxType
        DataStore.serverMuxNumber = serverMuxNumber
        DataStore.serverMuxStrategy = serverMuxStrategy
        DataStore.serverMuxPadding = serverMuxPadding
        DataStore.pluginName = plugin.substringBefore(";")
        DataStore.pluginConfig = plugin.substringAfter(";")
        DataStore.udpOverTcp = udpOverTcp
    }

    override fun ShadowsocksBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        method = DataStore.serverMethod
        password = DataStore.serverPassword
        serverMux = DataStore.serverMux
        serverBrutal = DataStore.serverBrutal
        serverMuxType = DataStore.serverMuxType
        serverMuxNumber = DataStore.serverMuxNumber
        serverMuxStrategy = DataStore.serverMuxStrategy
        serverMuxPadding = DataStore.serverMuxPadding
        udpOverTcp = DataStore.udpOverTcp

        val pluginName = DataStore.pluginName
        val pluginConfig = DataStore.pluginConfig
        plugin = if (pluginName.isNotBlank()) {
            "$pluginName;$pluginConfig"
        } else {
            ""
        }
    }

    private lateinit var serverBrutal: MaterialSwitchPreference
    private lateinit var serverMuxType: SimpleMenuPreference
    private lateinit var serverMuxNumber: EditTextPreference
    private lateinit var serverMuxStrategy: SimpleMenuPreference
    private lateinit var serverMuxPadding: MaterialSwitchPreference
    private lateinit var udpOverTcp: MaterialSwitchPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)

        serverMuxType = findPreference(Key.SERVER_MUX_TYPE)!!
        serverMuxStrategy = findPreference(Key.SERVER_MUX_STRATEGY)!!
        serverMuxPadding = findPreference(Key.SERVER_MUX_PADDING)!!
        serverMuxNumber = findPreference<EditTextPreference>(Key.SERVER_MUX_NUMBER)!!.also {
            it.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        fun onBrutalChange(brutal: Boolean) {
            serverMuxStrategy.isEnabled = !brutal
            serverMuxNumber.isEnabled = !brutal
        }
        serverBrutal = findPreference(Key.SERVER_BRUTAL)!!
        onBrutalChange(serverBrutal.isChecked)
        serverBrutal.setOnPreferenceChangeListener { _, newValue ->
            onBrutalChange(newValue as Boolean)
            true
        }
        udpOverTcp = findPreference(Key.UDP_OVER_TCP)!!
        updateMuxState(DataStore.serverMux)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }

        findPreference<MaterialSwitchPreference>(Key.SERVER_MUX)!!.apply {
            setOnPreferenceChangeListener { _, newValue ->
                updateMuxState(newValue as Boolean)
                true
            }
        }
    }

    private fun updateMuxState(enabled: Boolean) {
        serverBrutal.isVisible = enabled
        serverMuxType.isVisible = enabled
        serverMuxStrategy.isVisible = enabled
        serverMuxNumber.isVisible = enabled
        serverMuxPadding.isVisible = enabled

        udpOverTcp.isEnabled = !enabled
    }

}
