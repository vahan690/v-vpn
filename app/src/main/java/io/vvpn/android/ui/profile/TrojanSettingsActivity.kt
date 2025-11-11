package io.vvpn.android.ui.profile

import io.vvpn.android.fmt.trojan.TrojanBean
import io.vvpn.android.ktx.applyDefaultValues

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createBean() = TrojanBean().applyDefaultValues()

}
