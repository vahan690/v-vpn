package com.vvpn.android.ui.profile

import com.vvpn.android.fmt.trojan.TrojanBean
import com.vvpn.android.ktx.applyDefaultValues

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createBean() = TrojanBean().applyDefaultValues()

}
