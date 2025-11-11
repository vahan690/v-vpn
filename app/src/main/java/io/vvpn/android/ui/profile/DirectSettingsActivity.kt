package io.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.vvpn.android.R
import io.vvpn.android.database.DataStore
import io.vvpn.android.fmt.direct.DirectBean
import io.vvpn.android.ktx.applyDefaultValues

class DirectSettingsActivity : ProfileSettingsActivity<DirectBean>() {
    override fun createBean() = DirectBean().applyDefaultValues()

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        addPreferencesFromResource(R.xml.direct_preferences)
    }

    override fun DirectBean.serialize() {
        name = DataStore.profileName
    }

    override fun DirectBean.init() {
        DataStore.profileName = name
    }
}