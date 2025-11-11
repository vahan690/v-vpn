package io.vvpn.android.ui

import android.os.Bundle
import io.vvpn.android.R
import io.vvpn.android.SagerNet
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.ProfileManager
import io.vvpn.android.ktx.runOnMainDispatcher
import io.vvpn.android.ui.configuration.ConfigurationFragment

class SwitchActivity : ThemedActivity(R.layout.layout_empty),
    ConfigurationFragment.SelectCallback {

    override val isDialog = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_holder,
                ConfigurationFragment(true, null, R.string.action_switch)
            )
            .commitAllowingStateLoss()
    }

    override fun returnProfile(profileId: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = profileId
        runOnMainDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(profileId, true)
        }
        SagerNet.reloadService()
        finish()
    }
}