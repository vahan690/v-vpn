package com.vvpn.android.ui.profile

import com.vvpn.android.fmt.http.HttpBean
import com.vvpn.android.ktx.applyDefaultValues

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createBean() = HttpBean().applyDefaultValues()

}
