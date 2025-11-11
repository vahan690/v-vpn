package io.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.vvpn.android.Key
import io.vvpn.android.R
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.preference.EditTextPreferenceModifiers
import io.vvpn.android.fmt.shadowtls.ShadowTLSBean
import io.vvpn.android.widget.PasswordSummaryProvider

class ShadowTLSSettingsActivity : ProfileSettingsActivity<ShadowTLSBean>() {

    override fun createBean() = ShadowTLSBean()

    override fun ShadowTLSBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.protocolVersion = protocolVersion
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverUtlsFingerPrint = utlsFingerprint
    }

    override fun ShadowTLSBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        protocolVersion = DataStore.protocolVersion
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
        utlsFingerprint = DataStore.serverUtlsFingerPrint
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowtls_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }

}