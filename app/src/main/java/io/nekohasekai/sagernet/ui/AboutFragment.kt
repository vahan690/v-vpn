package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.databinding.ViewAboutCardBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.launchCustomTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import libcore.Libcore

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    private lateinit var binding: LayoutAboutBinding
    private val viewModel by viewModels<AboutFragmentViewModel>()
    private lateinit var adapter: AboutAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutAboutBinding.bind(view)

        toolbar.setTitle(R.string.menu_about)
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutAbout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        binding.aboutRecycler.adapter = AboutAdapter().also {
            adapter = it
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

//        binding.license.text = LICENSE
//        Linkify.addLinks(binding.license, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
    }

    private fun handleUiState(state: AboutFragmentUiState) {
//        val context = requireContext()
//        val shouldRequestBatteryOptimizations = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
//                && !(requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager)
//            .isIgnoringBatteryOptimizations(context.packageName)
        val cards = ArrayList<AboutCard>(0)
//            2 // App version and SingBox version
//                    + state.plugins.size // Plugins
//                    + if (shouldRequestBatteryOptimizations) 1 else 0 // Battery optimization
//        ).apply {
//            add(AboutCard.AppVersion())
//            add(AboutCard.SingBoxVersion())
//            state.plugins.forEach { plugin ->
//                add(AboutCard.Plugin(plugin))
//            }
//            if (shouldRequestBatteryOptimizations) {
//                add(AboutCard.BatteryOptimization(requestIgnoreBatteryOptimizations))
//            }
//        }
        adapter.submitList(cards)
    }

    val requestIgnoreBatteryOptimizations = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) lifecycleScope.launch {
            delay(1000) // Wait for updating battery optimization config
            handleUiState(viewModel.uiState.value)
        }
    }

    private sealed interface AboutCard {
        class AppVersion() : AboutCard
        class SingBoxVersion() : AboutCard
        data class Plugin(val plugin: AboutPlugin) : AboutCard
        class BatteryOptimization(val launcher: ActivityResultLauncher<Intent>) : AboutCard {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                return true
            }

            override fun hashCode(): Int {
                return javaClass.hashCode()
            }
        }
    }

    private class AboutAdapter :
        ListAdapter<AboutCard, AboutPluginHolder>(AboutCardDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AboutPluginHolder {
            return AboutPluginHolder(
                ViewAboutCardBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun onBindViewHolder(
            holder: AboutPluginHolder,
            position: Int,
        ) {
            when (val item = getItem(position)) {
                is AboutCard.AppVersion -> holder.bindAppVersion()
                is AboutCard.SingBoxVersion -> holder.bindSingBoxVersion()
                is AboutCard.Plugin -> holder.bindPlugin(item.plugin)
                is AboutCard.BatteryOptimization ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        holder.bindBatteryOptimization(item.launcher)
                    }
            }
        }

    }

    private class AboutCardDiffCallback : DiffUtil.ItemCallback<AboutCard>() {
        override fun areItemsTheSame(old: AboutCard, new: AboutCard): Boolean {
            return when (old) {
                is AboutCard.AppVersion -> new is AboutCard.AppVersion
                is AboutCard.SingBoxVersion -> new is AboutCard.SingBoxVersion
                is AboutCard.Plugin -> new is AboutCard.Plugin && old.plugin.id == new.plugin.id
                is AboutCard.BatteryOptimization -> new is AboutCard.BatteryOptimization
            }
        }

        override fun areContentsTheSame(old: AboutCard, new: AboutCard): Boolean {
            return old == new
        }
    }

    private class AboutPluginHolder(private val binding: ViewAboutCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindAppVersion() {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_sanitizer_24)
            binding.aboutCardTitle.setText(R.string.app_name)

            var displayVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            if (BuildConfig.DEBUG) {
                displayVersion += " DEBUG"
            }
            binding.aboutCardDescription.text = displayVersion

            binding.root.setOnClickListener { view ->
                view.context.launchCustomTab(
                    if (Libcore.isPreRelease(BuildConfig.VERSION_NAME)) {
                        "https://github.com/xchacha20-poly1305/husi/releases"
                    } else {
                        "https://github.com/xchacha20-poly1305/husi/releases/latest"
                    }
                )
            }
        }

        fun bindSingBoxVersion() {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_layers_24)
            binding.aboutCardTitle.text = binding.aboutCardTitle.context.getString(
                R.string.version_x,
                "sing-box",
            )
            binding.aboutCardDescription.text = Libcore.version()
            binding.root.setOnClickListener { view ->
                view.context.launchCustomTab("https://github.com/SagerNet/sing-box")
            }
        }

        fun bindPlugin(plugin: AboutPlugin) {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_nfc_24)
            binding.aboutCardTitle.text = binding.aboutCardTitle.context.getString(
                R.string.version_x,
                plugin.id,
            ) + " (${plugin.provider})"
            binding.aboutCardDescription.text = "v${plugin.version}"
            binding.root.setOnClickListener { view ->
                view.context.startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", plugin.packageName, null)
                })
            }
            plugin.entry?.let {
                binding.root.setOnLongClickListener { view ->
                    view.context.launchCustomTab(it.downloadSource.downloadLink)
                    true
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.M)
        fun bindBatteryOptimization(launcher: ActivityResultLauncher<Intent>) {
            binding.aboutCardIcon.setImageResource(R.drawable.ic_baseline_running_with_errors_24)
            binding.aboutCardTitle.setText(R.string.ignore_battery_optimizations)
            binding.aboutCardDescription.setText(R.string.ignore_battery_optimizations_sum)
            binding.root.setOnClickListener { view ->
                launcher.launch(Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = "package:${view.context.packageName}".toUri()
                })
            }
        }
    }

}
