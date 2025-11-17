package com.vvpn.android.ui

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
import com.vvpn.android.BuildConfig
import com.vvpn.android.GroupType
import com.vvpn.android.Key
import com.vvpn.android.LICENSE
import com.vvpn.android.R
import com.vvpn.android.SagerNet
import com.vvpn.android.SubscriptionType
import com.vvpn.android.aidl.ISagerNetService
import com.vvpn.android.aidl.SpeedDisplayData
import com.vvpn.android.aidl.TrafficData
import com.vvpn.android.bg.BaseService
import com.vvpn.android.bg.SagerConnection
import com.vvpn.android.bg.VpnService
import com.vvpn.android.database.DataStore
import com.vvpn.android.database.GroupManager
import com.vvpn.android.database.ProfileManager
import com.vvpn.android.database.ProxyGroup
import com.vvpn.android.database.SubscriptionBean
import com.vvpn.android.database.preference.OnPreferenceDataStoreChangeListener
import com.vvpn.android.databinding.LayoutMainBinding
import com.vvpn.android.fmt.AbstractBean
import com.vvpn.android.fmt.KryoConverters
import com.vvpn.android.fmt.PluginEntry
import com.vvpn.android.group.GroupInterfaceAdapter
import com.vvpn.android.group.GroupUpdater
import com.vvpn.android.group.RawUpdater
import com.vvpn.android.ktx.Logs
import com.vvpn.android.ktx.SubscriptionFoundException
import com.vvpn.android.ktx.alert
import com.vvpn.android.ktx.b64Decode
import com.vvpn.android.ktx.defaultOr
import com.vvpn.android.ktx.hasPermission
import com.vvpn.android.ktx.launchCustomTab
import com.vvpn.android.ktx.onMainDispatcher
import com.vvpn.android.ktx.parseProxies
import com.vvpn.android.ktx.readableMessage
import com.vvpn.android.ktx.runOnDefaultDispatcher
import com.vvpn.android.ktx.zlibDecompress
import com.vvpn.android.payment.AuthManager
import com.vvpn.android.payment.LicenseManager
import com.vvpn.android.payment.LoginActivity
import com.vvpn.android.payment.PaymentActivity
import com.vvpn.android.payment.ResetPasswordActivity
import com.vvpn.android.ui.configuration.ConfigurationFragment
import com.vvpn.android.ui.dashboard.DashboardFragment
import com.vvpn.android.ui.tools.ToolsFragment
import com.vvpn.android.sfa.utils.MIUIUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest
import okhttp3.Request
import org.json.JSONObject
import com.vvpn.android.payment.SecureHttpClient
import com.vvpn.android.security.SecurityManager
import com.vvpn.android.security.RootDetector
import com.vvpn.android.security.TamperDetector

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

        // SECURITY CHECK: Perform root and tamper detection
        performSecurityCheck()

        // Handle deep link FIRST before auth check
        handleDeepLink(intent)

        // Check if we're coming from a fresh login (skip early license check)
        val fromLogin = intent.getBooleanExtra("FROM_LOGIN", false)
        Log.d("MainActivity", "FROM_LOGIN flag: $fromLogin")

        // Check if user is logged in
        if (!authManager.isLoggedIn()) {
            Log.d("MainActivity", "User not logged in, showing login screen")

            // IMPORTANT: Set a placeholder view so app has something to show when returning
            setContentView(android.R.layout.simple_list_item_1)

            isCheckingAuth = true

            startActivityForResult(Intent(this, LoginActivity::class.java), 1002)
            return
        }

        // If coming from login, skip license check and fetch licenses instead
        if (fromLogin) {
            Log.d("MainActivity", "Coming from login, fetching licenses...")
            setContentView(android.R.layout.simple_list_item_1)
            isCheckingAuth = true
            fetchAndSaveLicenses()
            return
        }

        // Check crypto license on startup (only if NOT coming from login)
        if (!licenseManager.isLicenseValid()) {
            Log.d("MainActivity", "No valid license, showing payment screen")

            // IMPORTANT: Set a placeholder view so app has something to show when returning
            setContentView(android.R.layout.simple_list_item_1)

            isCheckingAuth = true

            val intent = Intent(this, PaymentActivity::class.java)
            startActivityForResult(intent, 1001)
            return
        }

        Log.d("MainActivity", "Valid license found, initializing app")
        initializeApp(savedInstanceState)
    }

    private fun performSecurityCheck() {
        // Set security policy
        // WARNING mode: logs threats but allows app to continue
        // Change to STRICT for production if you want to block rooted/tampered devices
        SecurityManager.setPolicy(SecurityManager.Policy.WARNING)

        val securityStatus = SecurityManager.performSecurityCheck(this)

        if (!securityStatus.isSecure) {
            Log.w("MainActivity", "=== SECURITY THREATS DETECTED ===")
            Log.w("MainActivity", securityStatus.getThreatSummary())

            // If policy is STRICT and should block, show dialog and exit
            if (securityStatus.shouldBlock) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Security Warning")
                    .setMessage(
                        "This app cannot run on this device due to security concerns:\n\n" +
                        securityStatus.getThreatSummary() +
                        "\n\nPlease use an official, non-rooted device."
                    )
                    .setPositiveButton("Exit") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                // WARNING mode: Just log, don't block
                Log.w("MainActivity", "Security policy is ${securityStatus.policy}, allowing app to continue")
            }
        } else {
            Log.i("MainActivity", "Security check passed")
        }

        // Log signature for reference (helpful for getting your release certificate hash)
        val signatureSHA256 = TamperDetector.getAppSignatureSHA256(this)
        Log.i("MainActivity", "App signature SHA-256: $signatureSHA256")
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

        // Get user's JWT token to fetch server config from API
        val authManager = com.vvpn.android.payment.AuthManager(this)
        val userToken = authManager.getToken()
        runOnDefaultDispatcher { ProfileManager.ensureDefaultProfile(userToken) }

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

        // Consent dialog - REMOVED: Auto-accept consent to avoid popup
        try {
            val f = File(application.filesDir, "consent")
            if (!f.exists()) {
                f.createNewFile() // Auto-create consent file without showing dialog
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
                Log.d("MainActivity", "Auth successful - fetching licenses and initializing app...")

                // Fetch licenses from the server
                fetchAndSaveLicenses()

                // Initialize the app UI immediately after successful authentication/payment
                // This ensures the app is ready to use without requiring a restart
                if (!::binding.isInitialized) {
                    Log.d("MainActivity", "Initializing app UI after successful license activation")
                    initializeApp(savedInstanceStateStored)
                }
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
            snackbar(getString(R.string.vpn_permission_denied)).show()
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

    // NEW: License fetching functionality - using coroutines for reliability
    private fun fetchAndSaveLicenses() {
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val currentUserEmail = authManager.getUserEmail()

        Log.d("MainActivity", "=== Fetching licenses ===")
        Log.d("MainActivity", "Device ID: $deviceId")
        Log.d("MainActivity", "Current user email: $currentUserEmail")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        // Fetch all licenses for this device using secure HTTP client
                        val request = Request.Builder()
                            .url("https://api.vvpn.space/api/license/device/$deviceId")
                            .get()
                            .build()

                        val response = SecureHttpClient.client.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""

                        Log.d("MainActivity", "License API response code: ${response.code}")

                        if (response.isSuccessful) {
                            Log.d("MainActivity", "License API response: $responseBody")
                            val json = JSONObject(responseBody)

                            if (json.getBoolean("success")) {
                                val licenses = json.getJSONArray("licenses")
                                Log.d("MainActivity", "Found ${licenses.length()} license(s) for device")
                                var foundUserLicense = false

                                // Filter licenses to find one matching the current user's email
                                for (i in 0 until licenses.length()) {
                                    val license = licenses.getJSONObject(i)
                                    val userEmail = license.getString("user_email")
                                    Log.d("MainActivity", "Checking license $i: user_email=$userEmail")

                                    if (userEmail.equals(currentUserEmail, ignoreCase = true)) {
                                        // Found a license for the current user
                                        val licenseKey = license.getString("license_key")
                                        val planId = license.getString("plan_id")
                                        val expiryDate = license.getString("expiry_date")

                                        Log.d("MainActivity", "✓ MATCH! Saving license for $userEmail")
                                        licenseManager.saveLicense(licenseKey, deviceId, expiryDate, planId, userEmail)
                                        foundUserLicense = true
                                        break
                                    } else {
                                        Log.d("MainActivity", "✗ No match: '$userEmail' != '$currentUserEmail'")
                                    }
                                }

                                if (!foundUserLicense) {
                                    // No license found for current user, clear any cached license
                                    Log.d("MainActivity", "No license found for user: $currentUserEmail, clearing cache")
                                    licenseManager.clearLicense()
                                }
                                true // Success
                            } else {
                                Log.e("MainActivity", "API returned success=false")
                                false
                            }
                        } else {
                            Log.e("MainActivity", "Failed to fetch licenses: HTTP ${response.code} - $responseBody")
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to fetch licenses: ${e.message}", e)
                        e.printStackTrace()
                        false
                    }
                }

                // Always proceed after fetching (success or failure)
                checkLicenseAndProceed()
            } catch (e: Exception) {
                Log.e("MainActivity", "Coroutine error: ${e.message}", e)
                e.printStackTrace()
                checkLicenseAndProceed()
            }
        }
    }
    
    private fun checkLicenseAndProceed() {
        val isValid = licenseManager.isLicenseValid()
        Log.d("MainActivity", "License valid after fetch: $isValid")
        
        if (!isValid) {
            Log.d("MainActivity", "No valid license, showing payment")
            showPaymentActivity()
        } else {
            Log.d("MainActivity", "Valid license found, continuing to app")
            // Add a small delay to ensure UI is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!::binding.isInitialized) {
                    initializeApp(savedInstanceStateStored)
                }
            }, 300)
        }
    }
    
    private fun showPaymentActivity() {
        val intent = Intent(this, PaymentActivity::class.java)
        startActivityForResult(intent, 1001)
    }
}
