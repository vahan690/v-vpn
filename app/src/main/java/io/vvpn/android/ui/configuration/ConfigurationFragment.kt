package io.vvpn.android.ui.configuration

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceDataStore
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.vvpn.android.GroupOrder
import io.vvpn.android.GroupType
import io.vvpn.android.Key
import io.vvpn.android.R
import io.vvpn.android.SagerNet
import io.vvpn.android.aidl.TrafficData
import io.vvpn.android.bg.BaseService
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.GroupManager
import io.vvpn.android.database.ProfileManager
import io.vvpn.android.database.ProxyEntity
import io.vvpn.android.database.ProxyGroup
import io.vvpn.android.database.SagerDatabase
import io.vvpn.android.database.preference.OnPreferenceDataStoreChangeListener
import io.vvpn.android.databinding.LayoutProfileListBinding
import io.vvpn.android.databinding.LayoutProgressListBinding
import io.vvpn.android.fmt.AbstractBean
import io.vvpn.android.fmt.config.ConfigBean
import io.vvpn.android.fmt.toUniversalLink
import io.vvpn.android.group.GroupUpdater.Companion.Deduplication
import io.vvpn.android.group.RawUpdater
import io.vvpn.android.ktx.Logs
import io.vvpn.android.ktx.ResultDeprecated
import io.vvpn.android.ktx.ResultInsecure
import io.vvpn.android.ktx.ResultLocal
import io.vvpn.android.ktx.SubscriptionFoundException
import io.vvpn.android.ktx.alert
import io.vvpn.android.ktx.closeQuietly
import io.vvpn.android.ktx.dp2px
import io.vvpn.android.ktx.getColorAttr
import io.vvpn.android.ktx.getColour
import io.vvpn.android.ktx.isInsecure
import io.vvpn.android.ktx.mapX
import io.vvpn.android.ktx.needReload
import io.vvpn.android.ktx.onMainDispatcher
import io.vvpn.android.ktx.readableMessage
import io.vvpn.android.ktx.runOnDefaultDispatcher
import io.vvpn.android.ktx.runOnMainDispatcher
import io.vvpn.android.ktx.scrollTo
import io.vvpn.android.ktx.setOnFocusCancel
import io.vvpn.android.ktx.showAllowingStateLoss
import io.vvpn.android.ktx.snackbar
import io.vvpn.android.ktx.startFilesForResult
import io.vvpn.android.ktx.urlTestMessage
import io.vvpn.android.ui.MainActivity
import io.vvpn.android.ui.ThemedActivity
import io.vvpn.android.ui.ToolbarFragment
import io.vvpn.android.ui.profile.AnyTLSSettingsActivity
import io.vvpn.android.ui.profile.ChainSettingsActivity
import io.vvpn.android.ui.profile.ConfigSettingActivity
import io.vvpn.android.ui.profile.DirectSettingsActivity
import io.vvpn.android.ui.profile.HttpSettingsActivity
import io.vvpn.android.ui.profile.HysteriaSettingsActivity
import io.vvpn.android.ui.profile.JuicitySettingsActivity
import io.vvpn.android.ui.profile.MieruSettingsActivity
import io.vvpn.android.ui.profile.NaiveSettingsActivity
import io.vvpn.android.ui.profile.ProxySetSettingsActivity
import io.vvpn.android.ui.profile.SSHSettingsActivity
import io.vvpn.android.ui.profile.ShadowQUICSettingsActivity
import io.vvpn.android.ui.profile.ShadowTLSSettingsActivity
import io.vvpn.android.ui.profile.ShadowsocksSettingsActivity
import io.vvpn.android.ui.profile.SocksSettingsActivity
import io.vvpn.android.ui.profile.TrojanSettingsActivity
import io.vvpn.android.ui.profile.TuicSettingsActivity
import io.vvpn.android.ui.profile.VMessSettingsActivity
import io.vvpn.android.ui.profile.WireGuardSettingsActivity
import io.vvpn.android.widget.QRCodeDialog
import io.vvpn.android.widget.UndoSnackbarManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.zip.ZipInputStream

/**
 * @param select marks this fragment starts for configuration selecting
 * but not display common configurations.
 *
 * @param titleRes custom title ID.
 */
class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false, val selectedItem: ProxyEntity? = null, val titleRes: Int = 0,
) : ToolbarFragment(R.layout.layout_group_list), PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener, SearchView.OnQueryTextListener,
    OnPreferenceDataStoreChangeListener {

    interface SelectCallback {
        fun returnProfile(profileId: Long)
    }

    val activity: ThemedActivity? get() = super.getActivity() as? ThemedActivity
    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout
    lateinit var groupPager: ViewPager2
    private val viewModel: ConfigurationFragmentViewModel by viewModels()
    val securityAdvisory by lazy { DataStore.securityAdvisory }

    val alwaysShowAddress by lazy { DataStore.alwaysShowAddress }
    val blurredAddress by lazy { DataStore.blurredAddress }

    fun getCurrentGroupFragment(): GroupFragment? {
        return try {
            childFragmentManager.findFragmentByTag("f" + DataStore.selectedGroup) as GroupFragment?
        } catch (e: Exception) {
            Logs.e(e)
            null
        }
    }

    val updateSelectedCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(
            position: Int, positionOffset: Float, positionOffsetPixels: Int,
        ) {
            if (adapter.groupList.size > position) {
                DataStore.selectedGroup = adapter.groupList[position].id
            }
        }
    }

    override fun onQueryTextChange(query: String): Boolean {
        getCurrentGroupFragment()?.adapter?.filter(query)
        return false
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

    @SuppressLint("DetachAndAttachSameFragment")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(false)
                .detach(this)
                .attach(this)
                .commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
            toolbar.setOnMenuItemClickListener(this)
            toolbar.setTitleTextAppearance(context, R.style.AppNameAppearanceHusi)
        } else {
            toolbar.setTitle(titleRes)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }
        val activity = activity
        val searchView = toolbar.findViewById<SearchView>(R.id.action_search)
        if (searchView != null) {
            searchView.setOnQueryTextListener(this)
            searchView.maxWidth = Int.MAX_VALUE
            searchView.setOnFocusCancel { hasFocus ->
                activity?.onBackPressedCallback?.isEnabled = hasFocus
            }
        }

        groupPager = view.findViewById(R.id.group_pager)

        tabLayout = view.findViewById(R.id.group_tab)
        ViewCompat.setOnApplyWindowInsetsListener(tabLayout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }

        adapter = GroupPagerAdapter()
        ProfileManager.addListener(adapter)
        GroupManager.addListener(adapter)

        groupPager.adapter = adapter
        groupPager.offscreenPageLimit = 2

        // Clean up search status when shifting tab.
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {}

            override fun onTabUnselected(tab: TabLayout.Tab) {
                searchView?.setQuery(null, true)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}

        })

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            if (adapter.groupList.size > position) {
                tab.text = adapter.groupList[position].displayName()
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        toolbar.setOnClickListener {
            val fragment = getCurrentGroupFragment()

            if (fragment != null) {
                val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                val selectedProfileIndex =
                    fragment.adapter!!.configurationIdList.indexOf(selectedProxy)
                if (selectedProfileIndex != -1) {
                    val layoutManager =
                        fragment.configurationListView.layoutManager as LinearLayoutManager
                    val first = layoutManager.findFirstVisibleItemPosition()
                    val last = layoutManager.findLastVisibleItemPosition()

                    if (selectedProfileIndex !in first..last) {
                        fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                        return@setOnClickListener
                    }

                }

                fragment.configurationListView.scrollTo(0)
            }

        }

        toolbar.setOnLongClickListener {
            val selectedProxy =
                selectedItem ?: SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                ?: return@setOnLongClickListener true
            val groupIndex = adapter.groupList.indexOfFirst {
                it.id == selectedProxy.groupId
            }
            if (groupIndex < 0) return@setOnLongClickListener true
            DataStore.selectedGroup = selectedProxy.groupId
            groupPager.currentItem = groupIndex

            getCurrentGroupFragment()?.let { fragment ->
                val selectedProfileIndex = fragment.adapter!!.configurationIdList.indexOfFirst {
                    it == selectedProxy.id
                }
                if (selectedProfileIndex > 0) {
                    fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                }
            }

            true
        }

        DataStore.profileCacheStore.registerChangeListener(this)

        activity?.onBackPressedCallback?.isEnabled = false

        viewModel.uiTestState.observe(viewLifecycleOwner, ::handleTestState)
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        runOnMainDispatcher {
            // editingGroup
            if (key == Key.PROFILE_GROUP) {
                val targetId = DataStore.editingGroup
                if (targetId > 0 && targetId != DataStore.selectedGroup) {
                    DataStore.selectedGroup = targetId
                    val targetIndex = adapter.groupList.indexOfFirst { it.id == targetId }
                    if (targetIndex >= 0) {
                        groupPager.setCurrentItem(targetIndex, false)
                    } else {
                        adapter.reload()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)

        if (::adapter.isInitialized) {
            GroupManager.removeListener(adapter)
            ProfileManager.removeListener(adapter)
        }

        super.onDestroy()
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        val fragment = getCurrentGroupFragment()
        fragment?.configurationListView?.apply {
            if (!hasFocus()) requestFocus()
        }
        return super.onKeyDown(ketCode, event)
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file != null) runOnDefaultDispatcher {
                try {
                    val fileName =
                        requireContext().contentResolver.query(file, null, null, null, null)
                            ?.use { cursor ->
                                cursor.moveToFirst()
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                    .let(cursor::getString)
                            }
                    val proxies = mutableListOf<AbstractBean>()
                    if (fileName != null && fileName.endsWith(".zip")) {
                        // try parse wireguard zip
                        val zip =
                            ZipInputStream(requireContext().contentResolver.openInputStream(file)!!)
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) continue
                            val fileText = zip.bufferedReader().readText()
                            RawUpdater.parseRaw(fileText, entry.name)
                                ?.let { pl -> proxies.addAll(pl) }
                            zip.closeEntry()
                        }
                        zip.closeQuietly()
                    } else {
                        val fileText =
                            requireContext().contentResolver.openInputStream(file)!!.use {
                                it.bufferedReader().readText()
                            }
                        RawUpdater.parseRaw(fileText, fileName ?: "")
                            ?.let { pl -> proxies.addAll(pl) }
                    }
                    if (proxies.isEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_file)).show()
                    } else {
                        (requireActivity() as MainActivity).importProfile(proxies)
                    }
                } catch (e: SubscriptionFoundException) {
                    (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    override fun onMenuItemClick(item: MenuItem): Boolean {

//      when (item.itemId) {
//          R.id.action_scan_qr_code -> {
//              startActivity(Intent(context, ScannerActivity::class.java))
//          }
//
//          R.id.action_import_clipboard -> {
//              (requireActivity() as MainActivity).parseProxy(SagerNet.getClipboardText())
//          }
//
//          R.id.action_import_file -> {
//              startFilesForResult(importFile, "*/*")
//          }
//
//          R.id.action_new_socks -> {
//              startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
//          }
//
//          R.id.action_new_http -> {
//              startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
//          }
//
//          R.id.action_new_ss -> {
//              startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
//          }
//
//          R.id.action_new_vmess -> {
//              startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
//          }
//
//          R.id.action_new_vless -> {
//              startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
//                  putExtra("vless", true)
//              })
//          }
//
//          R.id.action_new_trojan -> {
//              startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
//          }
//
//          R.id.action_new_mieru -> {
//              startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
//          }
//
//          R.id.action_new_naive -> {
//              startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
//          }
//
//          R.id.action_new_hysteria -> {
//              startActivity(Intent(requireActivity(), HysteriaSettingsActivity::class.java))
//          }
//
//          R.id.action_new_tuic -> {
//              startActivity(Intent(requireActivity(), TuicSettingsActivity::class.java))
//          }
//
//          R.id.action_new_juicity -> {
//              startActivity(Intent(requireActivity(), JuicitySettingsActivity::class.java))
//          }
//
//          R.id.action_new_direct -> {
//              startActivity(Intent(requireActivity(), DirectSettingsActivity::class.java))
//          }
//
//          R.id.action_new_ssh -> {
//              startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
//          }
//
//          R.id.action_new_wg -> {
//              startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
//          }
//
//          R.id.action_new_shadowtls -> {
//              startActivity(Intent(requireActivity(), ShadowTLSSettingsActivity::class.java))
//          }
//
//          R.id.action_new_anytls -> {
//              startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
//          }
//
//          R.id.action_new_shadowquic -> {
//              startActivity(Intent(requireActivity(), ShadowQUICSettingsActivity::class.java))
//          }
//
//          R.id.action_new_proxy_set -> {
//              startActivity(Intent(requireActivity(), ProxySetSettingsActivity::class.java))
//          }
//
//          R.id.action_new_config -> {
//              startActivity(Intent(requireActivity(), ConfigSettingActivity::class.java))
//          }
//
//          R.id.action_new_chain -> {
//              startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
//          }
//
//          R.id.action_clear_traffic_statistics -> runOnDefaultDispatcher {
//              val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
//              val toClear = mutableListOf<ProxyEntity>()
//              if (profiles.isNotEmpty()) {
//                  for (profile in profiles) {
//                      if (profile.tx != 0L || profile.rx != 0L) {
//                          profile.tx = 0
//                          profile.rx = 0
//                          toClear.add(profile)
//                      }
//                  }
//              }
//              if (toClear.isNotEmpty()) {
//                  ProfileManager.updateProfile(toClear)
//              }
//          }
//
//          R.id.action_connection_test_clear_results -> runOnDefaultDispatcher {
//              val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
//              val toClear = mutableListOf<ProxyEntity>()
//              if (profiles.isNotEmpty()) {
//                  for (profile in profiles) {
//                      if (profile.status != ProxyEntity.STATUS_INITIAL) {
//                          profile.status = ProxyEntity.STATUS_INITIAL
//                          profile.ping = 0
//                          profile.error = null
//                          toClear.add(profile)
//                      }
//                  }
//              }
//              if (toClear.isNotEmpty()) {
//                  ProfileManager.updateProfile(toClear)
//              }
//          }
//
//          R.id.action_connection_test_delete_unavailable -> runOnDefaultDispatcher {
//              val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
//              val toClear = mutableListOf<ProxyEntity>()
//              if (profiles.isNotEmpty()) {
//                  for (profile in profiles) {
//                      if (profile.status != ProxyEntity.STATUS_INITIAL && profile.status != ProxyEntity.STATUS_AVAILABLE) {
//                          toClear.add(profile)
//                      }
//                  }
//              }
//              if (toClear.isNotEmpty()) {
//                  onMainDispatcher {
//                      MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
//                          .setMessage(R.string.delete_confirm_prompt)
//                          .setPositiveButton(android.R.string.ok) { _, _ ->
//                              for (profile in toClear) {
//                                  adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
//                                      val index = configurationIdList.indexOf(profile.id)
//                                      if (index >= 0) {
//                                          configurationIdList.removeAt(index)
//                                          configurationList.remove(profile.id)
//                                          notifyItemRemoved(index)
//                                      }
//                                  }
//                              }
//                              runOnDefaultDispatcher {
//                                  for (profile in toClear) {
//                                      ProfileManager.deleteProfile2(
//                                          profile.groupId, profile.id
//                                      )
//                                  }
//                              }
//                          }.setNegativeButton(android.R.string.cancel, null).show()
//                  }
//              }
//          }
//
//          R.id.action_remove_duplicate -> runOnDefaultDispatcher {
//              val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
//              val toClear = mutableListOf<ProxyEntity>()
//              val uniqueProxies = LinkedHashSet<Deduplication>()
//              for (pf in profiles) {
//                  val proxy = Deduplication(pf.requireBean(), pf.displayType())
//                  if (!uniqueProxies.add(proxy)) {
//                      toClear += pf
//                  }
//              }
//              if (toClear.isNotEmpty()) {
//                  onMainDispatcher {
//                      MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
//                          .setMessage(
//                              getString(R.string.delete_confirm_prompt) + "\n" + toClear.mapIndexedNotNull { index, proxyEntity ->
//                                  if (index < 20) {
//                                      proxyEntity.displayName()
//                                  } else if (index == 20) {
//                                      "......"
//                                  } else {
//                                      null
//                                  }
//                              }.joinToString("\n")
//                          ).setPositiveButton(android.R.string.ok) { _, _ ->
//                              for (profile in toClear) {
//                                  adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
//                                      val index = configurationIdList.indexOf(profile.id)
//                                      if (index >= 0) {
//                                          configurationIdList.removeAt(index)
//                                          configurationList.remove(profile.id)
//                                          notifyItemRemoved(index)
//                                      }
//                                  }
//                              }
//                              runOnDefaultDispatcher {
//                                  for (profile in toClear) {
//                                      ProfileManager.deleteProfile2(
//                                          profile.groupId, profile.id
//                                      )
//                                  }
//                              }
//                          }.setNegativeButton(android.R.string.cancel, null).show()
//                  }
//              }
//          }
//
//          R.id.action_connection_icmp_ping -> {
//              viewModel.doTest(DataStore.currentGroupId(), TestType.ICMPPing)
//          }
//
//          R.id.action_connection_tcp_ping -> {
//              viewModel.doTest(DataStore.currentGroupId(), TestType.TCPPing)
//          }
//
//          R.id.action_connection_url_test -> {
//              viewModel.doTest(DataStore.currentGroupId(), TestType.URLTest)
//          }
//      }
        return false
    }

    private var testDialog: AlertDialog? = null

    private fun handleTestState(state: UiTestState) {
        when (state) {
            is UiTestState.Start -> {
                createDialog()
            }

            is UiTestState.Idle -> {
                testDialog?.dismiss()
                testDialog = null
            }

            is UiTestState.InProgress -> {
                createDialog()
                updateDialogContent(state)
            }
        }
    }

    /** Recreate dialog if configuration change. */
    private fun createDialog() {
        if (testDialog != null || !isAdded) return

        val binding = LayoutProgressListBinding.inflate(layoutInflater)
        testDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.cancelTest()
            }
            .setOnDismissListener {
                viewModel.cancelTest()
                testDialog = null
            }
            .setCancelable(false)
            .show()
    }

    private fun updateDialogContent(state: UiTestState.InProgress) {
        if (testDialog == null) return

        val profile = state.latestResult.profile
        val result = state.latestResult.result

        val (statusText, statusColor) = getStatusTextAndColor(result)
        profile.error = statusText

        val spannableText = SpannableStringBuilder().apply {
            append("\n" + profile.displayName())
            append("\n")
            append(
                profile.displayType(),
                ForegroundColorSpan(requireContext().getColorAttr(com.google.android.material.R.attr.colorAccent)),
                SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            append(" ")
            append(
                statusText,
                ForegroundColorSpan(statusColor),
                SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            append("\n")
        }

        testDialog!!.findViewById<TextView>(R.id.now_testing)!!.text = spannableText
        testDialog!!.findViewById<TextView>(R.id.progress)!!.text =
            "${state.processedCount} / ${state.totalCount}"
    }

    private fun getStatusTextAndColor(result: TestResult): Pair<String, Int> {
        val context = requireContext()
        when (result) {
            is TestResult.Success -> {
                return getString(
                    R.string.available,
                    result.ping
                ) to context.getColour(R.color.material_green_500)
            }

            is TestResult.Failure -> {
                val color = context.getColour(R.color.material_red_500)
                val text = when (val reason = result.reason) {
                    FailureReason.InvalidConfig -> getString(R.string.unavailable)
                    FailureReason.DomainNotFound -> getString(R.string.connection_test_domain_not_found)
                    FailureReason.IcmpUnavailable -> getString(R.string.connection_test_icmp_ping_unavailable)
                    FailureReason.TcpUnavailable -> getString(R.string.connection_test_tcp_ping_unavailable)
                    FailureReason.ConnectionRefused -> getString(R.string.connection_test_refused)
                    FailureReason.NetworkUnreachable -> getString(R.string.connection_test_unreachable)
                    FailureReason.Timeout -> getString(R.string.connection_test_timeout)

                    is FailureReason.Generic -> reason.message?.let {
                        urlTestMessage(requireContext(), it)
                    } ?: getString(R.string.unavailable)

                    is FailureReason.PluginNotFound -> reason.message
                }
                return text to color
            }
        }
    }

    inner class GroupPagerAdapter : FragmentStateAdapter(this), ProfileManager.Listener,
        GroupManager.Listener {

        var selectedGroupIndex = 0
        var groupList: ArrayList<ProxyGroup> = ArrayList()
        var groupFragments: HashMap<Long, GroupFragment> = HashMap()

        fun reload(now: Boolean = false) {

            if (!select) {
                groupPager.unregisterOnPageChangeCallback(updateSelectedCallback)
            }

            runOnDefaultDispatcher {
                var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (newGroupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }
                newGroupList.find { it.ungrouped }?.let {
                    if (SagerDatabase.proxyDao.countByGroup(it.id) == 0L) {
                        newGroupList.remove(it)
                    }
                }

                var selectedGroup = selectedItem?.groupId ?: DataStore.currentGroupId()
                var set = false
                if (selectedGroup > 0L) {
                    selectedGroupIndex = newGroupList.indexOfFirst { it.id == selectedGroup }
                    set = true
                } else if (groupList.size == 1) {
                    selectedGroup = groupList[0].id
                    if (DataStore.selectedGroup != selectedGroup) {
                        DataStore.selectedGroup = selectedGroup
                    }
                }

                val runFunc = if (now) activity?.let { it::runOnUiThread } else groupPager::post
                if (runFunc != null) {
                    runFunc {
                        groupList = newGroupList
                        notifyDataSetChanged()
                        if (set) groupPager.setCurrentItem(selectedGroupIndex, false)
                        val hideTab = groupList.size < 2
                        tabLayout.isGone = hideTab
                        toolbar.elevation = if (hideTab) 0F else dp2px(4).toFloat()
                        if (!select) {
                            groupPager.registerOnPageChangeCallback(updateSelectedCallback)
                        }
                    }
                }
            }
        }

        init {
            reload(true)
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun createFragment(position: Int): Fragment {
            return GroupFragment().apply {
                proxyGroup = groupList[position]
                groupFragments[proxyGroup.id] = this
                if (position == selectedGroupIndex) {
                    selected = true
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return groupList.any { it.id == itemId }
        }

        override suspend fun groupAdd(group: ProxyGroup) {
            tabLayout.post {
                groupList.add(group)

                if (groupList.any { !it.ungrouped }) tabLayout.post {
                    tabLayout.visibility = View.VISIBLE
                }

                notifyItemInserted(groupList.size - 1)
                tabLayout.getTabAt(groupList.size - 1)?.select()
            }
        }

        override suspend fun groupRemoved(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) return

            tabLayout.post {
                groupList.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        override suspend fun groupUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index == -1) return

            tabLayout.post {
                tabLayout.getTabAt(index)?.text = group.displayName()
            }
        }

        override suspend fun groupUpdated(groupId: Long) = Unit

        override suspend fun onAdd(profile: ProxyEntity) {
            if (groupList.find { it.id == profile.groupId } == null) {
                DataStore.selectedGroup = profile.groupId
                reload()
            }
        }

        override suspend fun onUpdated(data: TrafficData) = Unit

        override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) = Unit

        override suspend fun onRemoved(groupId: Long, profileId: Long) {
            val group = groupList.find { it.id == groupId } ?: return
            if (group.ungrouped && SagerDatabase.proxyDao.countByGroup(groupId) == 0L) {
                reload()
            }
        }
    }

    class GroupFragment : Fragment() {

        lateinit var proxyGroup: ProxyGroup
        var selected = false

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            return LayoutProfileListBinding.inflate(inflater).root
        }

        lateinit var undoManager: UndoSnackbarManager<ProxyEntity>
        var adapter: ConfigurationAdapter? = null

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)

            if (::proxyGroup.isInitialized) {
                outState.putParcelable("proxyGroup", proxyGroup)
            }
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            savedInstanceState?.let {
                BundleCompat.getParcelable(savedInstanceState, "proxyGroup", ProxyGroup::class.java)
                    ?.let { group ->
                        proxyGroup = group
                        onViewCreated(requireView(), null)
                    }
            }
        }

        private val isEnabled: Boolean
            get() {
                return DataStore.serviceState.let { it.canStop || it == BaseService.State.Stopped }
            }

        lateinit var configurationListView: RecyclerView

        val select by lazy {
            try {
                (parentFragment as ConfigurationFragment).select
            } catch (e: Exception) {
                Logs.e(e)
                false
            }
        }
        val selectedItem by lazy {
            try {
                (parentFragment as ConfigurationFragment).selectedItem
            } catch (e: Exception) {
                Logs.e(e)
                null
            }
        }

        override fun onResume() {
            super.onResume()

            if (::configurationListView.isInitialized && configurationListView.size == 0) {
                configurationListView.adapter = adapter
                runOnDefaultDispatcher {
                    adapter?.reloadProfiles()
                }
            } else if (!::configurationListView.isInitialized) {
                onViewCreated(requireView(), null)
            }
            checkOrderMenu()
            configurationListView.requestFocus()
        }

        fun checkOrderMenu() {
            if (select) return

            val pf = requireParentFragment() as? ToolbarFragment ?: return
            val menu = pf.toolbar.menu
            val origin = menu.findItem(R.id.action_order_origin)
            val byName = menu.findItem(R.id.action_order_by_name)
            val byDelay = menu.findItem(R.id.action_order_by_delay)
            when (proxyGroup.order) {
                GroupOrder.ORIGIN -> {
                    origin.isChecked = true
                }

                GroupOrder.BY_NAME -> {
                    byName.isChecked = true
                }

                GroupOrder.BY_DELAY -> {
                    byDelay.isChecked = true
                }
            }

            fun updateTo(order: Int) {
                if (proxyGroup.order == order) return
                runOnDefaultDispatcher {
                    proxyGroup.order = order
                    GroupManager.updateGroup(proxyGroup)
                }
            }

            origin.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.ORIGIN)
                true
            }
            byName.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.BY_NAME)
                true
            }
            byDelay.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.BY_DELAY)
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            if (!::proxyGroup.isInitialized) return

            configurationListView = view.findViewById(R.id.configuration_list)
            ViewCompat.setOnApplyWindowInsetsListener(configurationListView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = bars.left + dp2px(4),
                    right = bars.right + dp2px(4),
                    bottom = bars.bottom + dp2px(64),
                )
                insets
            }
            adapter = ConfigurationAdapter()
            ProfileManager.addListener(adapter!!)
            GroupManager.addListener(adapter!!)
            configurationListView.adapter = adapter
            configurationListView.setItemViewCacheSize(20)

            if (!select) {

                undoManager = UndoSnackbarManager(activity as MainActivity, adapter!!)

                ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
                ) {
                    override fun getSwipeDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        return 0
                    }

                    override fun getDragDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) = if (isEnabled) super.getDragDirs(recyclerView, viewHolder) else 0

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    }

                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                    ): Boolean {
                        adapter?.move(
                            viewHolder.bindingAdapterPosition, target.bindingAdapterPosition
                        )
                        return true
                    }

                    override fun clearView(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        adapter?.commitMove()
                    }
                }).attachToRecyclerView(configurationListView)

            }

        }

        override fun onDestroy() {
            adapter?.let {
                ProfileManager.removeListener(it)
                GroupManager.removeListener(it)
            }

            super.onDestroy()

            if (!::undoManager.isInitialized) return
            undoManager.flush()
        }

        inner class ConfigurationAdapter : RecyclerView.Adapter<ConfigurationHolder>(),
            ProfileManager.Listener, GroupManager.Listener,
            UndoSnackbarManager.Interface<ProxyEntity> {

            init {
                setHasStableIds(true)
            }

            var configurationIdList: MutableList<Long> = mutableListOf()
            val configurationList = HashMap<Long, ProxyEntity>()

            private fun getItem(profileId: Long): ProxyEntity {
                var profile = configurationList[profileId]
                if (profile == null) {
                    profile = ProfileManager.getProfile(profileId)
                    if (profile != null) {
                        configurationList[profileId] = profile
                    }
                }
                return profile!!
            }

            private fun getItemAt(index: Int) = getItem(configurationIdList[index])

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): ConfigurationHolder {
                return ConfigurationHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_profile, parent, false)
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationIdList[position]
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                try {
                    holder.bind(getItemAt(position))
                } catch (_: NullPointerException) { // when group deleted
                }
            }

            override fun getItemCount(): Int {
                return configurationIdList.size
            }

            private val updated = HashSet<ProxyEntity>()

            fun filter(name: String) {
                if (name.isEmpty()) {
                    reloadProfiles()
                    return
                }
                configurationIdList.clear()
                val lower = name.lowercase()
                configurationIdList.addAll(configurationList.filter {
                    it.value.displayName().lowercase().contains(lower) || it.value.displayType()
                        .lowercase().contains(lower) || it.value.displayAddress().lowercase()
                        .contains(lower)
                }.keys)
                notifyDataSetChanged()
            }

            fun move(from: Int, to: Int) {
                val first = getItemAt(from)
                var previousOrder = first.userOrder
                val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                    -1, to + 1 downTo from
                )
                for (i in range) {
                    val next = getItemAt(i + step)
                    val order = next.userOrder
                    next.userOrder = previousOrder
                    previousOrder = order
                    configurationIdList[i] = next.id
                    updated.add(next)
                }
                first.userOrder = previousOrder
                configurationIdList[to] = first.id
                updated.add(first)
                notifyItemMoved(from, to)
            }

            fun commitMove() = runOnDefaultDispatcher {
                updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
                updated.clear()
            }

            fun remove(pos: Int) {
                if (pos < 0) return
                configurationIdList.removeAt(pos)
                notifyItemRemoved(pos)
            }

            override fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                for ((index, item) in actions) {
                    configurationListView.post {
                        configurationList[item.id] = item
                        configurationIdList.add(index, item.id)
                        notifyItemInserted(index)
                    }
                }
            }

            override fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                val profiles = actions.mapX { it.second }
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                }
            }

            override suspend fun onAdd(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return

                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    val pos = itemCount
                    configurationList[profile.id] = profile
                    configurationIdList.add(profile.id)
                    notifyItemInserted(pos)
                }
            }

            override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
                if (profile.groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profile.id)
                if (index < 0) return
                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    configurationList[profile.id] = profile
                    notifyItemChanged(index)
                    //
                    val oldProfile = configurationList[profile.id]
                    if (noTraffic && oldProfile != null) {
                        runOnDefaultDispatcher {
                            onUpdated(
                                TrafficData(
                                    id = profile.id, rx = oldProfile.rx, tx = oldProfile.tx
                                )
                            )
                        }
                    }
                }
            }

            override suspend fun onUpdated(data: TrafficData) {
                try {
                    val index = configurationIdList.indexOf(data.id)
                    if (index != -1) {
                        val holder = (configurationListView.layoutManager as LinearLayoutManager)
                            .findViewByPosition(index)
                            ?.let { configurationListView.getChildViewHolder(it) } as ConfigurationHolder?
                        if (holder != null) {
                            onMainDispatcher {
                                holder.bind(holder.entity, data)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }

            override suspend fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profileId)
                if (index < 0) return

                configurationListView.post {
                    configurationIdList.removeAt(index)
                    configurationList.remove(profileId)
                    notifyItemRemoved(index)
                }
            }

            override suspend fun groupAdd(group: ProxyGroup) = Unit
            override suspend fun groupRemoved(groupId: Long) = Unit

            override suspend fun groupUpdated(group: ProxyGroup) {
                if (group.id != proxyGroup.id) return
                proxyGroup = group
                reloadProfiles()
            }

            override suspend fun groupUpdated(groupId: Long) {
                if (groupId != proxyGroup.id) return
                proxyGroup = SagerDatabase.groupDao.getById(groupId)!!
                reloadProfiles()
            }

            fun reloadProfiles() {
                var newProfiles = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
                when (proxyGroup.order) {
                    GroupOrder.BY_NAME -> {
                        newProfiles = newProfiles.sortedBy { it.displayName() }

                    }

                    GroupOrder.BY_DELAY -> {
                        newProfiles = newProfiles.sortedBy {
                            if (it.status == ProxyEntity.STATUS_AVAILABLE) {
                                it.ping
                            } else {
                                Int.MAX_VALUE
                            }
                        }
                    }
                }

                configurationList.clear()
                configurationList.putAll(newProfiles.associateBy { it.id })
                val newProfileIds = newProfiles.mapX { it.id }

                var selectedProfileIndex = -1

                if (selected) {
                    val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                    selectedProfileIndex = newProfileIds.indexOf(selectedProxy)
                }

                configurationListView.post {
                    configurationIdList.clear()
                    configurationIdList.addAll(newProfileIds)
                    notifyDataSetChanged()

                    if (selectedProfileIndex != -1) {
                        configurationListView.scrollTo(selectedProfileIndex, true)
                    } else if (newProfiles.isNotEmpty()) {
                        configurationListView.scrollTo(0, true)
                    }

                }
            }

        }

        val profileAccess = Mutex()
        val reloadAccess = Mutex()

        inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
            PopupMenu.OnMenuItemClickListener {

            lateinit var entity: ProxyEntity

            val profileName: TextView = view.findViewById(R.id.profile_name)
            val profileType: TextView = view.findViewById(R.id.profile_type)
            val profileAddress: TextView = view.findViewById(R.id.profile_address)
            val profileStatus: TextView = view.findViewById(R.id.profile_status)

            val trafficText: TextView = view.findViewById(R.id.traffic_text)
            val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
            val editButton: MaterialButton = view.findViewById(R.id.edit)
            val shareButton: MaterialButton = view.findViewById(R.id.share)
            val removeButton: MaterialButton = view.findViewById(R.id.remove)

            fun bind(proxyEntity: ProxyEntity, trafficData: TrafficData? = null) {
                val pf = parentFragment as? ConfigurationFragment ?: return

                entity = proxyEntity

                if (select) {
                    view.setOnClickListener {
                        (requireActivity() as SelectCallback).returnProfile(proxyEntity.id)
                    }
                } else {
                    view.setOnClickListener {
                        runOnDefaultDispatcher {
                            var update: Boolean
                            var lastSelected: Long
                            profileAccess.withLock {
                                update = DataStore.selectedProxy != proxyEntity.id
                                lastSelected = DataStore.selectedProxy
                                DataStore.selectedProxy = proxyEntity.id
                                onMainDispatcher {
                                    selectedView.visibility = View.VISIBLE
                                }
                            }

                            if (update) {
                                ProfileManager.postUpdate(lastSelected)
                                if (DataStore.serviceState.canStop && reloadAccess.tryLock()) {
                                    SagerNet.reloadService()
                                    reloadAccess.unlock()
                                }
                            } else if (SagerNet.isTv) {
                                if (DataStore.serviceState.started) {
                                    SagerNet.stopService()
                                } else {
                                    SagerNet.startService()
                                }
                            }
                        }

                    }
                }

                profileName.text = proxyEntity.displayName()
                // Check if this is Unlock the World profile and hide technical details
                val isGermanyProfile = proxyEntity.displayName() == "Unlock the World"
                if (isGermanyProfile) {
                    profileType.text = "VPN Server"
                    profileAddress.text = ""
                    editButton.visibility = View.GONE
                    shareButton.visibility = View.GONE
                    removeButton.visibility = View.GONE
                } else {
                    profileType.text = proxyEntity.displayType()
                }

                var rx = proxyEntity.rx
                var tx = proxyEntity.tx
                if (trafficData != null) {
                    // use new data
                    tx = trafficData.tx
                    rx = trafficData.rx
                }

                val showTraffic = rx + tx != 0L
                trafficText.isVisible = showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(
                        R.string.traffic,
                        Formatter.formatFileSize(trafficText.context, tx),
                        Formatter.formatFileSize(trafficText.context, rx),
                    )
                }

                val tmpAddress by lazy { proxyEntity.displayAddress() }
                val address = when {
                    pf.blurredAddress -> {
                        val blurredAddress = tmpAddress.blur()
                        if (profileName.text == tmpAddress) profileName.text = blurredAddress
                        blurredAddress
                    }

                    proxyEntity.requireBean().name.isBlank() || !pf.alwaysShowAddress -> ""

                    else -> tmpAddress
                }

                profileAddress.text = address
                (trafficText.parent as View).isGone =
                    (!showTraffic || proxyEntity.status <= ProxyEntity.STATUS_INITIAL) && address.isBlank()

                if (proxyEntity.status <= ProxyEntity.STATUS_INITIAL) {
                    if (showTraffic) {
                        profileStatus.text = trafficText.text
                        profileStatus.setTextColor(profileStatus.context.getColorAttr(android.R.attr.textColorSecondary))
                        trafficText.text = ""
                    } else {
                        profileStatus.text = ""
                    }
                } else if (proxyEntity.status == ProxyEntity.STATUS_AVAILABLE) {
                    profileStatus.text = profileStatus.context.getString(
                        R.string.available,
                        proxyEntity.ping,
                    )
                    profileStatus.setTextColor(profileStatus.context.getColour(R.color.material_green_500))
                } else {
                    profileStatus.setTextColor(profileStatus.context.getColour(R.color.material_red_500))
                    if (proxyEntity.status == ProxyEntity.STATUS_UNREACHABLE) {
                        profileStatus.text = proxyEntity.error
                    }
                }

                if (proxyEntity.status == ProxyEntity.STATUS_UNAVAILABLE) {
                    val err = proxyEntity.error ?: "<?>"
                    val msg = urlTestMessage(profileStatus.context, err)
                    profileStatus.text = if (msg != err) {
                        msg
                    } else {
                        profileStatus.context.getString(R.string.unavailable)
                    }
                    profileStatus.setOnClickListener {
                        alert(err).show()
                    }
                } else {
                    profileStatus.setOnClickListener(null)
                }

                editButton.setOnClickListener {
                    editProfileLauncher.launch(
                        proxyEntity.settingIntent(
                            it.context,
                            proxyGroup.type == GroupType.SUBSCRIPTION,
                        )
                    )
                }

                removeButton.setOnClickListener {
                    adapter?.let {
                        val index = it.configurationIdList.indexOf(proxyEntity.id)
                        it.remove(index)
                        undoManager.remove(index to proxyEntity)
                    }
                }

                val isGermany = proxyEntity.displayName() == "Unlock the World"
                shareButton.isGone = true  // Always hide share button
                editButton.isGone = select || isGermany
                removeButton.isGone = select || isGermany

                runOnDefaultDispatcher {
                    val selected = (selectedItem?.id ?: DataStore.selectedProxy) == proxyEntity.id
                    val started =
                        selected && DataStore.serviceState.started && DataStore.currentProfile == proxyEntity.id
                    onMainDispatcher {
                        removeButton.isEnabled = !started
                        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    }

                    fun showShare(anchor: View) {
                        val popup = PopupMenu(requireContext(), anchor)
                        popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                        if (!proxyEntity.haveStandardLink()) {
                            popup.menu.findItem(R.id.action_group_qr).subMenu?.removeItem(R.id.action_standard_qr)
                            popup.menu.findItem(R.id.action_group_clipboard).subMenu?.removeItem(R.id.action_standard_clipboard)
                        }

                        if (!proxyEntity.haveLink()) {
                            popup.menu.removeItem(R.id.action_group_qr)
                            popup.menu.removeItem(R.id.action_group_clipboard)
                        }

                        val bean = proxyEntity.requireBean()
                        if (proxyEntity.type == ProxyEntity.TYPE_CHAIN ||
                            proxyEntity.type == ProxyEntity.TYPE_PROXY_SET ||
                            proxyEntity.mustUsePlugin() ||
                            (bean as? ConfigBean)?.type == ConfigBean.TYPE_CONFIG
                        ) {
                            popup.menu.removeItem(R.id.action_group_outbound)
                        }

                        popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                        popup.show()
                    }

                    if (!select) {
//                        val validateResult =
//                            if ((parentFragment as? ConfigurationFragment)?.securityAdvisory == true) {
//                                proxyEntity.requireBean().isInsecure()
//                            } else ResultLocal
                        val validateResult = if ((parentFragment as? ConfigurationFragment)?.securityAdvisory == true) {
                            // Skip security check for Germany profile
                            if (proxyEntity.displayName() == "Unlock the World" || proxyEntity.displayName() == "VPN Server") {
                                ResultLocal
                            } else {
                                proxyEntity.requireBean().isInsecure()
                            }
                        } else ResultLocal

                        when (validateResult) {
                            is ResultInsecure -> onMainDispatcher {
                                shareButton.isVisible = false

                                shareButton.setBackgroundColor(Color.RED)
                                shareButton.setIconResource(R.drawable.ic_baseline_warning_24)
                                shareButton.setIconTint(ColorStateList.valueOf(Color.WHITE))

                                shareButton.setOnClickListener {
                                    MaterialAlertDialogBuilder(it.context).setTitle(R.string.insecure)
                                        .setMessage(
                                            resources.openRawResource(validateResult.textRes)
                                                .bufferedReader().use { it.readText() })
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            showShare(it)
                                        }.show().apply {
                                            findViewById<TextView>(android.R.id.message)?.apply {
                                                Linkify.addLinks(this, Linkify.WEB_URLS)
                                                movementMethod = LinkMovementMethod.getInstance()
                                            }
                                        }
                                }
                            }

                            is ResultDeprecated -> onMainDispatcher {
                                shareButton.isVisible = false

                                shareButton.setBackgroundColor(Color.YELLOW)
                                shareButton.setIconResource(R.drawable.ic_baseline_warning_24)
                                shareButton.setIconTint(ColorStateList.valueOf(Color.GRAY))

                                shareButton.setOnClickListener {
                                    MaterialAlertDialogBuilder(it.context).setTitle(R.string.deprecated)
                                        .setMessage(
                                            resources.openRawResource(validateResult.textRes)
                                                .bufferedReader().use { it.readText() })
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            showShare(it)
                                        }.show().apply {
                                            findViewById<TextView>(android.R.id.message)?.apply {
                                                Linkify.addLinks(this, Linkify.WEB_URLS)
                                                movementMethod = LinkMovementMethod.getInstance()
                                            }
                                        }
                                }
                            }

                            else -> onMainDispatcher {
                                shareButton.setBackgroundColor(Color.TRANSPARENT)
                                shareButton.setIconResource(R.drawable.ic_social_share)
                                shareButton.setIconTint(
                                    ColorStateList.valueOf(
                                        shareButton.context.getColorAttr(
                                            com.google.android.material.R.attr.colorOnSurface
                                        )
                                    )
                                )
                                shareButton.isVisible = false

                                shareButton.setOnClickListener {
                                    showShare(it)
                                }
                            }
                        }
                    }
                }
            }

            var currentName = ""
            fun showCode(link: String) {
                QRCodeDialog(link, currentName).showAllowingStateLoss(parentFragmentManager)
            }

            fun export(link: String) {
                val success = SagerNet.trySetPrimaryClip(link)
                (activity as MainActivity).snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                    .show()
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                try {
                    currentName = entity.displayName()!!
                    when (item.itemId) {
                        R.id.action_standard_qr -> showCode(entity.toStdLink())
                        R.id.action_standard_clipboard -> export(entity.toStdLink())

                        R.id.action_universal_qr -> showCode(entity.requireBean().toUniversalLink())
                        R.id.action_universal_clipboard -> export(
                            entity.requireBean().toUniversalLink()
                        )

                        R.id.action_config_export_clipboard -> export(entity.exportConfig().first)
                        R.id.action_config_export_file -> {
                            val cfg = entity.exportConfig()
                            DataStore.serverConfig = cfg.first
                            startFilesForResult(
                                (parentFragment as ConfigurationFragment).exportConfig, cfg.second
                            )
                        }

                        R.id.action_outbound_export_clipboard -> export(entity.exportOutbound())
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    (activity as MainActivity).snackbar(e.readableMessage).show()
                    return true
                }
                return true
            }
        }

        private val editProfileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    if (DataStore.currentProfile == DataStore.editingId) {
                        needReload()
                    }
                }
            }

    }

    private val exportConfig =
        registerForActivityResult(CreateDocument("application/json")) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(data)!!
                            .bufferedWriter().use {
                                it.write(DataStore.serverConfig)
                            }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

}

/** Make server address blurred. */
private fun String.blur(): String = when (length) {
    in 0 until 20 -> {
        val halfLength = length / 2
        substring(0, halfLength) + "*".repeat(length - halfLength)
    }

    in 20..30 -> substring(0, 15) + "*".repeat(length - 15)

    else -> substring(0, 15) + "*".repeat(15)
}
