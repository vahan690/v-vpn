package io.vvpn.android.ui.configuration

import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat
import io.vvpn.android.R
import io.vvpn.android.database.ProxyEntity
import io.vvpn.android.ui.ThemedActivity

class ProfileSelectActivity : ThemedActivity(R.layout.layout_empty),
    ConfigurationFragment.SelectCallback {

    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_PROFILE_ID = "id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected = IntentCompat.getParcelableExtra(
            intent,
            EXTRA_SELECTED, ProxyEntity::class.java,
        )

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_holder,
                ConfigurationFragment(true, selected, R.string.select_profile)
            )
            .commitAllowingStateLoss()
    }

    override fun returnProfile(profileId: Long) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_PROFILE_ID, profileId)
        })
        finish()
    }

}