package io.vvpn.android.ui.profile

import io.vvpn.android.fmt.http.HttpBean
import io.vvpn.android.ktx.applyDefaultValues

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createBean() = HttpBean().applyDefaultValues()

}
