package io.vvpn.android.ui.profile

import io.vvpn.android.fmt.v2ray.VMessBean

class VMessSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createBean() = VMessBean().apply {
        if (intent?.getBooleanExtra("vless", false) == true) {
            alterId = -1
        }
        initializeDefaultValues()
    }

}