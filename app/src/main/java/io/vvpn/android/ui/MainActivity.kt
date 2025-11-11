package io.vvpn.android.ui

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceDataStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import io.vvpn.android.BuildConfig
import io.vvpn.android.GroupType
import io.vvpn.android.Key
import io.vvpn.android.LICENSE
import io.vvpn.android.R
import io.vvpn.android.SagerNet
import io.vvpn.android.SubscriptionType
import io.vvpn.android.aidl.ISagerNetService
import io.vvpn.android.aidl.SpeedDisplayData
import io.vvpn.android.aidl.TrafficData
import io.vvpn.android.bg.BaseService
import io.vvpn.android.bg.SagerConnection
import io.vvpn.android.bg.VpnService
import io.vvpn.android.database.DataStore
import io.vvpn.android.database.GroupManager
import io.vvpn.android.database.ProfileManager
import io.vvpn.android.database.ProxyGroup
import io.vvpn.android.database.SubscriptionBean
import io.vvpn.android.database.preference.OnPreferenceDataStoreChangeListener
import io.vvpn.android.databinding.LayoutMainBinding
import io.vvpn.android.fmt.AbstractBean
import io.vvpn.android.fmt.KryoConverters
import io.vvpn.android.fmt.PluginEntry
import io.vvpn.android.group.GroupInterfaceAdapter
import io.vvpn.android.group.GroupUpdater
import io.vvpn.android.group.RawUpdater
import io.vvpn.android.ktx.Logs
import io.vvpn.android.ktx.SubscriptionFoundException
import io.vvpn.android.ktx.alert
import io.vvpn.android.ktx.b64Decode
import io.vvpn.android.ktx.defaultOr
import io.vvpn.android.ktx.hasPermission
import io.vvpn.android.ktx.launchCustomTab
import io.vvpn.android.ktx.onMainDispatcher
import io.vvpn.android.ktx.parseProxies
import io.vvpn.android.ktx.readableMessage
import io.vvpn.android.ktx.runOnDefaultDispatcher
import io.vvpn.android.ktx.zlibDecompress
import io.vvpn.android.payment.AuthManager
import io.vvpn.android.payment.LicenseManager
import io.vvpn.android.payment.LoginActivity
import io.vvpn.android.payment.PaymentActivity
import io.vvpn.android.payment.ResetPasswordActivity
import io.vvpn.android.ui.configuration.ConfigurationFragment
import io.vvpn.android.ui.dashboard.DashboardFragment
import io.vvpn.android.ui.tools.ToolsFragment
import io.vvpn.android.sfa.utils.MIUIUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest

class MainActivity : ThemedActivity(),
    SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener,
    NavigationView.OnNavigationItemSelectedListener {

    lateinit var binding: LayoutMainBinding
    private lateinit var navigation: NavigationView
    private lateinit var licenseManager: LicenseManager
    private lateinit var authManager: AuthManager
    private var savedInstanceStateStored: Bundle? = null
    private var isCheckingAuth = false

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.findFragmentById(R.id.fragment_holder) is ConfigurationFragment) {
                moveTaskToBack(true)
            } else {
                displayFragmentWithId(R.id.nav_configuration)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainActivity", "onCreate - Build type: ${BuildConfig.BUILD_TYPE}")

        // Store savedInstanceState for later use
        savedInstanceStateStored = savedInstanceState

        // Initialize managers
        licenseManager = LicenseManager(this)
        authManager = AuthManager(this)

        // Handle deep link FIRST before auth check
//        handleDeepLink(intent)

        // Check if user is logged in
        if (!authManager.isLoggedIn()) {
            Log.d("MainActivity", "User not logged in, showing login screen")

            // IMPORTANT: Set a placeholder view so app has something to show when returning
            setContentView(android.R.layout.simple_list_item_1)

            isCheckingAuth = true

        // Handle deep link BEFORE login if it's a password reset
        handleDeepLink(intent)  // ADD THIS LINE HERE

            startActivityForResult(Intent(this, LoginActivity::class.java), 1002)
            return
        }

        // Check crypto license on startup
        if (!licenseManager.isLicenseValid()) {
            Log.d("MainActivity", "No valid license, showing payment screen")

            // IMPORTANT: Set a placeholder view so app has something to show when returning
            setContentView(android.R.layout.simple_list_item_1)

            isCheckingAuth = true

        // Handle deep link BEFORE payment screen if it's a password reset
        handleDeepLink(intent)  // ADD THIS LINE HERE TOO

            val intent = Intent(this, PaymentActivity::class.java)
            startActivityForResult(intent, 1001)
            return
        }

        Log.d("MainActivity", "Valid license found, initializing app")
        initializeApp(savedInstanceState)

    // Handle deep link AFTER initialization for logged-in users
    handleDeepLink(intent)  // AND ADD THIS LINE HERE
    }

    private fun initializeApp(savedInstanceState: Bundle?) {
        binding = LayoutMainBinding.inflate(layoutInflater)
        binding.fab.initProgress(binding.fabProgress)

        if (themeResId !in intArrayOf(
                R.style.Theme_SagerNet_Black
            )
        ) {
            navigation = binding.navView
            binding.drawerLayout.removeView(binding.navViewBlack)
        } else {
            navigation = binding.navViewBlack
            binding.drawerLayout.removeView(binding.navView)
        }

        navigation.setNavigationItemSelectedListener(this)

        if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            ViewCompat.setOnApplyWindowInsetsListener(navigation) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    top = bars.top,
                    right = bars.right,
                    bottom = bars.bottom,
                )
                insets
            }
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(navigation) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    top = bars.top,
                    left = bars.left,
                    bottom = bars.bottom,
                )
                insets
            }
        }

        if (savedInstanceState == null) {
            displayFragmentWithId(R.id.nav_configuration)
        }

        // FAB click with crypto license check
        binding.fab.setOnClickListener {
            if (DataStore.serviceState.canStop) {
                Log.d("MainActivity", "Stopping service")
                SagerNet.stopService()
            } else {
                // Check crypto license before connecting
                if (licenseManager.isLicenseValid()) {
                    Log.d("MainActivity", "License valid, connecting")
                    connect.launch(null)
                } else {
                    Log.d("MainActivity", "License expired, showing payment screen")
                    showLicenseExpiredDialog()
                }
            }
        }

        binding.stats.setOnClickListener {
            if (DataStore.serviceState.connected) binding.stats.testConnection()
        }

        setContentView(binding.root)
        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
        GroupManager.userInterface = GroupInterfaceAdapter(this)
        runOnDefaultDispatcher { ProfileManager.ensureDefaultProfile() }

        when (intent.action) {
            Intent.ACTION_VIEW -> onNewIntent(intent)
            Intent.ACTION_PROCESS_TEXT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                parseProxy(intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: "")
            }
            else -> {}
        }

        // SDK 33 notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val checkPermission = ContextCompat.checkSelfPermission(this@MainActivity, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(POST_NOTIFICATIONS),
                    0
                )
            }
        }

        // Consent dialog
        try {
            val f = File(application.filesDir, "consent")
            if (!f.exists()) {
                // Comment out this entire block to remove license popup
                /*
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.license)
//                    .setMessage(LICENSE)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        f.createNewFile()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        finish()
                    }
                    .show()
                */
                // Just create the consent file directly to skip dialog
            }
        } catch (e: Exception) {
            Logs.w(e)
        }

        // Ensure drawer is closed on startup
        binding.drawerLayout.post {
            binding.drawerLayout.closeDrawers()
        }
    }

    private fun showLicenseExpiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Subscription Required")
            .setMessage("Your VPN subscription has expired. Please subscribe to continue using the VPN.")
            .setPositiveButton("Subscribe") { _, _ ->
                val intent = Intent(this, PaymentActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d("MainActivity", "=== onActivityResult called ===")
        Log.d("MainActivity", "requestCode: $requestCode")
        Log.d("MainActivity", "resultCode: $resultCode")
        Log.d("MainActivity", "isCheckingAuth: $isCheckingAuth")
        Log.d("MainActivity", "binding initialized: ${::binding.isInitialized}")

        if (requestCode == 1001 || requestCode == 1002) {
            isCheckingAuth = false

            if (resultCode == RESULT_OK) {
                Log.d("MainActivity", "Auth successful")

                // Check license
                val isValid = licenseManager.isLicenseValid()
                Log.d("MainActivity", "License valid: $isValid")

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!::binding.isInitialized) {
                        Log.d("MainActivity", "About to call initializeApp")
                        try {
                            initializeApp(savedInstanceStateStored)
                            Log.d("MainActivity", "initializeApp completed")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "initializeApp FAILED!", e)
                            e.printStackTrace()
                        }
                    } else {
                        Log.d("MainActivity", "Binding already initialized, skipping")
                    }
                }, 300)
            } else {
                Log.d("MainActivity", "Auth cancelled, finishing")
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // License check removed - onCreate and onActivityResult handle everything
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

private fun handleDeepLink(intent: Intent?) {
    val uri = intent?.data ?: return

    Log.d("MainActivity", "Deep link detected: $uri")

    when {
        // Handle password reset deep link
        (uri.scheme == "vvpn" && uri.host == "reset-password") ||
        (uri.scheme == "https" && uri.host == "vvpn.space" && uri.path == "/reset-password") -> {
            val token = uri.getQueryParameter("token")
            Log.d("MainActivity", "Password reset deep link - token: ${token?.take(10)}...")
            
            // IMPORTANT: Launch ResetPasswordActivity regardless of login state
            val resetIntent = Intent(this, ResetPasswordActivity::class.java).apply {
                putExtra("token", token)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(resetIntent)
            
            // Don't return here - let the auth flow continue if needed
        }
        // Handle subscription import
        uri.scheme == "husi" && uri.host == "subscription" || uri.scheme == "sing-box" -> {
            runOnDefaultDispatcher {
                importSubscription(uri)
            }
        }
        // Handle profile import
        else -> {
            runOnDefaultDispatcher {
                importProfile(uri)
            }
        }
    }
}

    fun urlTest(): Int {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error("not started")
        }
        return connection.service!!.urlTest(null)
    }

    suspend fun importSubscription(uri: Uri) {
        val group: ProxyGroup

        val url = defaultOr(
            "",
            { uri.getQueryParameter("url") },
            {
                when (uri.scheme) {
                    "http", "https" -> uri.toString()
                    else -> null
                }
            }
        )
        if (url.isNotBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            val subscription = SubscriptionBean()
            group.subscription = subscription

            subscription.link = url
            subscription.type = when (uri.getQueryParameter("type")?.lowercase()) {
                "oocv1" -> SubscriptionType.OOCv1
                "sip008" -> SubscriptionType.SIP008
                else -> SubscriptionType.RAW
            }
            group.name = defaultOr(
                "",
                { uri.getQueryParameter("name") },
                { uri.fragment },
            )
        } else {
            val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return
            try {
                group = KryoConverters.deserialize(
                    ProxyGroup().apply { export = true },
                    data.b64Decode().zlibDecompress()
                ).apply {
                    export = false
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    alert(e.readableMessage).show()
                }
                return
            }
        }

        val name = group.name.takeIf { !it.isNullOrBlank() }
            ?: group.subscription?.link
            ?: group.subscription?.token
        if (name.isNullOrBlank()) return

        group.name = group.name.takeIf { !it.isNullOrBlank() }
            ?: ("Subscription #" + System.currentTimeMillis())

        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)

            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.subscription_import)
                .setMessage(getString(R.string.subscription_import_message, name))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runOnDefaultDispatcher {
                        finishImportSubscription(group)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private suspend fun finishImportSubscription(subscription: ProxyGroup) {
        GroupManager.createGroup(subscription)
        GroupUpdater.startUpdate(subscription, true)
    }

    private suspend fun importProfile(uri: Uri) {
        val profile = try {
            parseProxies(uri.toString()).getOrNull(0) ?: error(getString(R.string.no_proxies_found))
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
            }
            return
        }

        onMainDispatcher {
            MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.profile_import)
                .setMessage(getString(R.string.profile_import_message, profile.displayName()))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runOnDefaultDispatcher {
                        finishImportProfile(profile)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private suspend fun finishImportProfile(profile: AbstractBean) {
        val targetId = DataStore.selectedGroupForImport()
        ProfileManager.createProfile(targetId, profile)
        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)
            snackbar(resources.getQuantityString(R.plurals.added, 1, 1)).show()
        }
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
        val pluginEntity = PluginEntry.find(pluginName)

        if (pluginEntity == null) {
            snackbar(getString(R.string.plugin_unknown, pluginName)).show()
            return
        }

        MaterialAlertDialogBuilder(this).setTitle(R.string.missing_plugin)
            .setMessage(
                getString(
                    R.string.profile_requiring_plugin, profileName, pluginEntity.displayName
                )
            )
            .setPositiveButton(R.string.action_download) { _, _ ->
                showDownloadDialog(pluginEntity)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_learn_more) { _, _ ->
                launchCustomTab("https://github.com/xchacha20-poly1305/husi/wiki/Plugin")
            }
            .show()
    }

    private fun showDownloadDialog(pluginEntry: PluginEntry) {
        var index = 0
        var fdroidIndex = -1

        val items = mutableListOf<String>()
        if (pluginEntry.downloadSource.fdroid) {
            items.add(getString(R.string.install_from_fdroid))
            fdroidIndex = index++
        }

        items.add(getString(R.string.download))
        val downloadIndex = index

        MaterialAlertDialogBuilder(this).setTitle(pluginEntry.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    fdroidIndex -> launchCustomTab("https://f-droid.org/packages/${pluginEntry.packageName}/")
                    downloadIndex -> launchCustomTab(pluginEntry.downloadSource.downloadLink)
                }
            }
            .show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Close drawer first
        binding.drawerLayout.closeDrawers()

        // Then navigate to the selected item
        return displayFragmentWithId(item.itemId)
    }

    @SuppressLint("CommitTransaction")
    fun displayFragment(fragment: ToolbarFragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_holder, fragment)
            .commitAllowingStateLoss()
        binding.drawerLayout.closeDrawers()
    }

    fun displayFragmentWithId(@IdRes id: Int): Boolean {
        when (id) {
            R.id.nav_configuration -> {
                displayFragment(ConfigurationFragment())
            }
            R.id.nav_about -> displayFragment(AboutFragment())
            R.id.nav_license -> displayFragment(LicenseFragment())
            R.id.nav_logout -> {
                showLogoutConfirmation()
                return true
            }
            else -> return false
        }
        navigation.menu.findItem(id).isChecked = true
        return true
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout? Your VPN will be disconnected.")
            .setPositiveButton("Logout") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        // Stop VPN if running
        if (DataStore.serviceState.canStop) {
            SagerNet.stopService()
        }

        // Clear auth token
        authManager.logout()

        // Clear license
        licenseManager.clearLicense()

        // CRITICAL: Close drawer before launching LoginActivity
        binding.drawerLayout.closeDrawers()

        // Launch LoginActivity WITHOUT clearing the task
        isCheckingAuth = true
        startActivityForResult(Intent(this, LoginActivity::class.java), 1002)
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        DataStore.serviceState = state
        if (state == BaseService.State.RequiredLocation) requestLocationPermission()

        binding.fab.changeState(state, DataStore.serviceState, animate)
        binding.stats.changeState(state)

        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()

        when (state) {
            BaseService.State.Connected, BaseService.State.Stopped -> {
                runOnDefaultDispatcher {
                    ProfileManager.postUpdate(DataStore.currentProfile)
                }
            }
            else -> {}
        }
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG).apply {
            if (binding.fab.isShown) {
                anchorView = binding.fab
            }
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
    }

    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    override fun onServiceConnected(service: ISagerNetService) = changeState(
        try {
            BaseService.State.entries[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) { denied ->
        if (denied) {
            Log.d("MainActivity", "VPN permission denied")
            snackbar(R.string.vpn_permission_denied).show()
        } else {
            Log.d("MainActivity", "VPN permission granted")
        }
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        binding.stats.updateSpeed(stats.txRateProxy, stats.rxRateProxy)
    }

    override fun cbTrafficUpdate(data: TrafficData) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(data)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.SERVICE_MODE -> onBinderDied()
            Key.PROXY_APPS, Key.BYPASS_MODE, Key.PACKAGES -> {
                if (DataStore.serviceState.canStop) {
                    snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                        SagerNet.reloadService()
                    }.show()
                }
            }
        }
    }

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        GroupManager.userInterface = null
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (super.onKeyDown(keyCode, event)) return true
                binding.drawerLayout.open()
                navigation.requestFocus()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (binding.drawerLayout.isOpen) {
                    binding.drawerLayout.close()
                    return true
                }
            }
        }

        if (super.onKeyDown(keyCode, event)) return true
        if (binding.drawerLayout.isOpen) return false

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        return fragment != null && fragment.onKeyDown(keyCode, event)
    }

    private fun requestLocationPermission() {
        Logs.d("start getting location")
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocationPermission()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun requestFineLocationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_description)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestFineLocationPermission0()
            }
            .setNegativeButton(R.string.no_thanks, null)
            .setCancelable(false)
            .show()
    }

    private fun requestFineLocationPermission0() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            openPermissionSettings()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestBackgroundLocationPermission() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_title)
            .setMessage(R.string.location_permission_background_description)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.no_thanks, null)
            .setCancelable(false)
            .show()
    }

    private fun openPermissionSettings() {
        if (MIUIUtils.isMIUI) {
            try {
                MIUIUtils.openPermissionSettings(this)
                return
            } catch (_: Exception) {
            }
        }

        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = "package:$packageName".toUri()
            startActivity(intent)
        } catch (e: Exception) {
            Logs.e(e.readableMessage)
            snackbarInternal(e.readableMessage)
        }
    }

    fun parseProxy(text: String) {
        if (text.isBlank()) {
            snackbar(getString(R.string.clipboard_empty)).show()
        } else runOnDefaultDispatcher {
            suspend fun parseSubscription() {
                try {
                    val proxies = RawUpdater.parseRaw(text)
                    if (proxies.isNullOrEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                    } else {
                        importProfile(proxies)
                    }
                } catch (e: SubscriptionFoundException) {
                    importSubscription(e.link.toUri())
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }

            val singleURI = try {
                text.toUri()
            } catch (_: Exception) {
                null
            }
            if (singleURI != null) {
                when (singleURI.scheme) {
                    "http", "https" -> onMainDispatcher {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.subscription_import)
                            .setMessage(R.string.import_http_url)
                            .setPositiveButton(R.string.subscription_import) { _, _ ->
                                runOnDefaultDispatcher {
                                    importSubscription(singleURI)
                                }
                            }
                            .setNegativeButton(R.string.profile_import) { _, _ ->
                                runOnDefaultDispatcher {
                                    parseSubscription()
                                }
                            }
                            .show()
                    }
                    else -> parseSubscription()
                }
            } else {
                parseSubscription()
            }
        }
    }

    suspend fun importProfile(proxies: List<AbstractBean>) {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        DataStore.editingGroup = targetId
        onMainDispatcher {
            snackbar(
                resources.getQuantityString(R.plurals.added, proxies.size, proxies.size)
            ).show()
        }
    }
}
