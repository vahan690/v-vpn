package io.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.vvpn.android.Key
import io.vvpn.android.database.DataStore
import io.vvpn.android.fmt.anytls.AnyTLSBean
import io.vvpn.android.ktx.applyDefaultValues
import io.vvpn.android.R
import io.vvpn.android.database.preference.EditTextPreferenceModifiers
import io.vvpn.android.widget.DurationPreference
import io.vvpn.android.widget.MaterialSwitchPreference
import io.vvpn.android.widget.PasswordSummaryProvider

class AnyTLSSettingsActivity : ProfileSettingsActivity<AnyTLSBean>() {
    override fun createBean() = AnyTLSBean().applyDefaultValues()

    override fun AnyTLSBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        password = DataStore.serverPassword
        idleSessionCheckInterval = DataStore.serverIdleSessionCheckInterval
        idleSessionTimeout = DataStore.serverIdleSessionTimeout
        minIdleSession = DataStore.serverMinIdleSession
        serverName = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        utlsFingerprint = DataStore.serverUtlsFingerPrint
        allowInsecure = DataStore.serverAllowInsecure
        disableSNI = DataStore.serverDisableSNI
        fragment = DataStore.serverFragment
        fragmentFallbackDelay = DataStore.serverFragmentFallbackDelay
        recordFragment = DataStore.serverRecordFragment
        ech = DataStore.serverECH
        echConfig = DataStore.serverECHConfig
    }

    override fun AnyTLSBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverPassword = password
        DataStore.serverIdleSessionCheckInterval = idleSessionCheckInterval
        DataStore.serverIdleSessionTimeout = idleSessionTimeout
        DataStore.serverMinIdleSession = minIdleSession
        DataStore.serverSNI = serverName
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverUtlsFingerPrint = utlsFingerprint
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverDisableSNI = disableSNI
        DataStore.serverFragment = fragment
        DataStore.serverFragmentFallbackDelay = fragmentFallbackDelay
        DataStore.serverRecordFragment = recordFragment
        DataStore.serverECH = ech
        DataStore.serverECHConfig = echConfig
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.anytls_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_MIN_IDLE_SESSION)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        val fragment = findPreference<MaterialSwitchPreference>(Key.SERVER_FRAGMENT)!!
        val fragmentFallbackDelay =
            findPreference<DurationPreference>(Key.SERVER_FRAGMENT_FALLBACK_DELAY)!!

        fun updateFragmentFallbackDelay(enabled: Boolean = fragment.isChecked) {
            fragmentFallbackDelay.isEnabled = enabled
        }
        updateFragmentFallbackDelay()
        fragment.setOnPreferenceChangeListener { _, newValue ->
            updateFragmentFallbackDelay(newValue as Boolean)
            true
        }
    }
}