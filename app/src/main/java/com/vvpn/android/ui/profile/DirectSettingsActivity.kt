package com.vvpn.android.ui.profile

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.vvpn.android.R
import com.vvpn.android.database.DataStore
import com.vvpn.android.fmt.direct.DirectBean
import com.vvpn.android.ktx.applyDefaultValues

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