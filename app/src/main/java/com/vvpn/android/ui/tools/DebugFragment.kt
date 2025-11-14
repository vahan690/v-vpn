package com.vvpn.android.ui.tools

import android.content.Context
import android.os.Bundle
import android.view.View
import com.vvpn.android.R
import com.vvpn.android.database.DataStore
import com.vvpn.android.databinding.LayoutToolsDebugBinding
import com.vvpn.android.ktx.snackbar

class DebugFragment : NamedFragment(R.layout.layout_tools_debug) {

    override fun getName(context: Context) = "DEBUG"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutToolsDebugBinding.bind(view)

        binding.debugCrash.setOnClickListener {
            error("test crash")
        }
        binding.resetSettings.setOnClickListener {
            DataStore.configurationStore.reset()
            snackbar("Cleared").show()
        }
    }

}