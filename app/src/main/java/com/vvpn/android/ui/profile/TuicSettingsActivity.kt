package com.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.vvpn.android.Key
import com.vvpn.android.R
import com.vvpn.android.database.DataStore
import com.vvpn.android.fmt.tuic.TuicBean
import com.vvpn.android.ktx.applyDefaultValues
import com.vvpn.android.widget.MaterialSwitchPreference
import com.vvpn.android.widget.PasswordSummaryProvider

class TuicSettingsActivity : ProfileSettingsActivity<TuicBean>() {

    override fun createBean() = TuicBean().applyDefaultValues()

    override fun TuicBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = uuid
        DataStore.serverPassword = token
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverUDPRelayMode = udpRelayMode
        DataStore.serverCongestionController = congestionController
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverSNI = sni
        DataStore.serverZeroRTT = zeroRTT
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig
    }

    override fun TuicBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        uuid = DataStore.serverUsername
        token = DataStore.serverPassword
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        udpRelayMode = DataStore.serverUDPRelayMode
        congestionController = DataStore.serverCongestionController
        disableSNI = DataStore.serverDisableSNI
        sni = DataStore.serverSNI
        zeroRTT = DataStore.serverZeroRTT
        allowInsecure = DataStore.serverAllowInsecure
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tuic_preferences)

        val disableSNI = findPreference<MaterialSwitchPreference>(Key.SERVER_DISABLE_SNI)!!
        val sni = findPreference<EditTextPreference>(Key.SERVER_SNI)!!
        sni.isEnabled = !disableSNI.isChecked
        disableSNI.setOnPreferenceChangeListener { _, newValue ->
            sni.isEnabled = !(newValue as Boolean)
            true
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}