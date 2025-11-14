package com.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.vvpn.android.Key
import com.vvpn.android.R
import com.vvpn.android.database.DataStore
import com.vvpn.android.database.preference.EditTextPreferenceModifiers
import com.vvpn.android.fmt.shadowquic.ShadowQUICBean
import com.vvpn.android.ktx.applyDefaultValues
import com.vvpn.android.widget.PasswordSummaryProvider

class ShadowQUICSettingsActivity : ProfileSettingsActivity<ShadowQUICBean>() {
    override fun createBean() = ShadowQUICBean().applyDefaultValues()

    override fun ShadowQUICBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverInitialMTU = initialMTU
        DataStore.serverMinimumMTU = minimumMTU
        DataStore.serverCongestionController = congestionControl
        DataStore.serverZeroRTT = zeroRTT
        DataStore.udpOverTcp = udpOverStream
    }

    override fun ShadowQUICBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        initialMTU = DataStore.serverInitialMTU
        minimumMTU = DataStore.serverMinimumMTU
        congestionControl = DataStore.serverCongestionController
        zeroRTT = DataStore.serverZeroRTT
        udpOverStream = DataStore.udpOverTcp
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.shadowquic_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_INITIAL_MTU)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_MINIMUM_MTU)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }

}