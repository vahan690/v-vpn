package io.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.vvpn.android.Key
import io.vvpn.android.R
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.preference.EditTextPreferenceModifiers
import io.vvpn.android.fmt.wireguard.WireGuardBean
import io.vvpn.android.ktx.applyDefaultValues
import io.vvpn.android.widget.PasswordSummaryProvider

class WireGuardSettingsActivity : ProfileSettingsActivity<WireGuardBean>() {

    override fun createBean() = WireGuardBean().applyDefaultValues()

    override fun WireGuardBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.localAddress = localAddress
        DataStore.listenPort = listenPort
        DataStore.privateKey = privateKey
        DataStore.publicKey = publicKey
        DataStore.preSharedKey = preSharedKey
        DataStore.serverMTU = mtu
        DataStore.serverReserved = reserved
        DataStore.serverPersistentKeepaliveInterval = persistentKeepaliveInterval
    }

    override fun WireGuardBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        localAddress = DataStore.localAddress
        listenPort = DataStore.listenPort
        privateKey = DataStore.privateKey
        publicKey = DataStore.publicKey
        preSharedKey = DataStore.preSharedKey
        mtu = DataStore.serverMTU
        reserved = DataStore.serverReserved
        persistentKeepaliveInterval = DataStore.serverPersistentKeepaliveInterval
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.wireguard_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.LISTEN_PORT)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Port
        )
        findPreference<EditTextPreference>(Key.PRIVATE_KEY)!!.setSummaryProvider(
            PasswordSummaryProvider
        )
        findPreference<EditTextPreference>(Key.PRE_SHARED_KEY)!!.setSummaryProvider(
            PasswordSummaryProvider
        )
        findPreference<EditTextPreference>(Key.SERVER_MTU)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Number
        )
        findPreference<EditTextPreference>(Key.SERVER_PERSISTENT_KEEPALIVE_INTERVAL)!!.setOnBindEditTextListener(
            EditTextPreferenceModifiers.Number
        )
    }

}