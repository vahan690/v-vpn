package io.nekohasekai.sagernet.ui

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
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.LICENSE
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.bg.VpnService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutMainBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.PluginEntry
import io.nekohasekai.sagernet.group.GroupInterfaceAdapter
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.b64Decode
import io.nekohasekai.sagernet.ktx.defaultOr
import io.nekohasekai.sagernet.ktx.hasPermission
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.zlibDecompress
import io.nekohasekai.sagernet.ui.configuration.ConfigurationFragment
import io.nekohasekai.sagernet.ui.dashboard.DashboardFragment
import io.nekohasekai.sagernet.ui.tools.ToolsFragment
import io.nekohasekai.sfa.utils.MIUIUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
    private lateinit var activationValidator: ActivationValidator
    private var isInitialized = false
    private var storedSavedInstanceState: Bundle? = null
    private var isConnecting = false
    private var validationJob: Job? = null

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
        this.storedSavedInstanceState = savedInstanceState

        // Initialize activation validator
        try {
            activationValidator = ActivationValidator(this)
            Log.d("MainActivity", "ActivationValidator initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize ActivationValidator: ${e.message}")
            e.printStackTrace()
            // If ActivationValidator fails to initialize, redirect to activation screen
            redirectToActivationScreen()
            return
        }

        // Always check activation regardless of build type
        checkActivationWithServer()
    }

    private fun checkActivationWithServer() {
        Log.d("MainActivity", "Starting server activation check...")

        // Cancel any existing validation job
        validationJob?.cancel()

        // Stop any running services first
        try {
            SagerNet.stopService()
            stopService(Intent(this, VpnService::class.java))
        } catch (e: Exception) {
            Log.d("MainActivity", "No service to stop: ${e.message}")
        }

        validationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Add timeout to prevent hanging
                withTimeout(15000) { // 15 second timeout

                    Log.d("MainActivity", "Calling activationValidator.validateActivationWithServer()")
                    val response = activationValidator.validateActivationWithServer()

                    Log.d("MainActivity", "Server response - Valid: ${response.isValid}")
                    Log.d("MainActivity", "Server response - Message: '${response.message}'")
                    Log.d("MainActivity", "Server response - ExpiresAt: '${response.expiresAt}'")
                    Log.d("MainActivity", "Server response - RemainingDays: ${response.remainingDays}")

                    if (response.isValid) {
                        Log.d("MainActivity", "Response is valid, updating local data and initializing app")
                        // Update local storage with server data
                        updateLocalActivationData(response)
                        initializeApp()
                    } else {
                        Log.d("MainActivity", "Response is invalid, redirecting to activation screen")
                        // Clear local data and redirect
                        clearLocalActivationData()
                        redirectToActivationScreen()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("MainActivity", "Server activation check timed out")
                // On timeout, redirect to activation screen
                clearLocalActivationData()
                redirectToActivationScreen()
            } catch (e: Exception) {
                Log.e("MainActivity", "Server activation check failed: ${e.message}")
                e.printStackTrace()
                clearLocalActivationData()
                redirectToActivationScreen()
            }
        }
    }

    private fun updateLocalActivationData(response: ActivationValidator.ActivationCheckResponse) {
        try {
            DataStore.isActivated = response.isValid

            // Parse expiration date from server
            response.expiresAt?.let { expiresAt ->
                try {
                    val serverDate = java.time.Instant.parse(expiresAt)
                    DataStore.activationExpiry = serverDate.toEpochMilli()
                } catch (e: Exception) {
                    // Fallback: calculate from remaining days
                    val remainingMs = response.remainingDays * 24L * 60 * 60 * 1000
                    DataStore.activationExpiry = System.currentTimeMillis() + remainingMs
                }
            }

            // Store device ID and a validation token
            DataStore.deviceId = activationValidator.generateDeviceId()
            DataStore.activationCodeHash = "server_validated_${System.currentTimeMillis()}"

            Log.d("MainActivity", "Local activation data updated - Expires: ${java.util.Date(DataStore.activationExpiry)}")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating local activation data: ${e.message}")
        }
    }

    private fun clearLocalActivationData() {
        DataStore.isActivated = false
        DataStore.activationExpiry = 0L
        DataStore.deviceId = ""
        DataStore.activationCodeHash = ""
        Log.d("MainActivity", "Local activation data cleared")
    }

    private fun initializeApp() {
        if (isInitialized) return
        isInitialized = true

        Log.d("MainActivity", "Initializing app with valid activation")

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

        // Use the stored savedInstanceState
        if (storedSavedInstanceState == null) {
            displayFragmentWithId(R.id.nav_configuration)
        }

        // Enhanced FAB click with proper state management
        binding.fab.setOnClickListener {
//            if (isConnecting) {
//                Log.d("MainActivity", "Already connecting, ignoring click")
//                return@setOnClickListener
//            }

//            Log.d("MainActivity", "FAB clicked - Current state: ${DataStore.serviceState}")

            if (DataStore.serviceState.canStop) {
                Log.d("MainActivity", "Stopping service")
                SagerNet.stopService()
            } else {
                Log.d("MainActivity", "Validating before connecting")
                validateBeforeServiceAction { isValid ->
                    if (isValid) {
                        Log.d("MainActivity", "Validation successful, launching connection")
                        connect.launch(null)
                    } else {
                        Log.d("MainActivity", "Validation failed, showing activation dialog")
                        showActivationExpiredDialog()
                    }
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
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.license)
                    .setMessage(LICENSE)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        f.createNewFile()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        finish()
                    }
                    .show()
            }
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    private fun validateBeforeServiceAction(callback: (Boolean) -> Unit) {
        if (isConnecting) {
            Log.d("MainActivity", "Already validating, skipping")
            callback(false)
            return
        }

        isConnecting = true

        // Cancel any existing validation
        validationJob?.cancel()

        validationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                withTimeout(10000) { // 10 second timeout
                    Log.d("MainActivity", "Starting validation before service action")
                    val response = activationValidator.validateActivationWithServer()

                    Log.d("MainActivity", "Validation response: ${response.isValid}")

                    if (response.isValid) {
                        updateLocalActivationData(response)
                        isConnecting = false
                        callback(true)
                    } else {
                        clearLocalActivationData()
                        isConnecting = false
                        callback(false)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("MainActivity", "Service validation timed out")
                isConnecting = false
                callback(false)
            } catch (e: Exception) {
                Log.e("MainActivity", "Service validation failed: ${e.message}")
                e.printStackTrace()
                isConnecting = false
                callback(false)
            }
        }
    }

    private fun redirectToActivationScreen() {
        Log.d("MainActivity", "Redirecting to activation screen")

        try {
            val intent = Intent(this, ActivationActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // Check if ActivationActivity exists
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                Log.d("MainActivity", "ActivationActivity found, starting it")
                startActivity(intent)
                finish()
            } else {
                Log.e("MainActivity", "ActivationActivity not found!")
                // Fallback: show a dialog instead
                showActivationRequiredDialog()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start ActivationActivity: ${e.message}")
            e.printStackTrace()
            showActivationRequiredDialog()
        }
    }

    private fun showActivationRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Activation Required")
            .setMessage("Please activate your VPN service. ActivationActivity not available.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()

        // Re-check activation every time app resumes (only if app is initialized)
        if (isInitialized && !isConnecting) {
            Log.d("MainActivity", "Re-checking activation on resume")

            // Cancel any existing validation
            validationJob?.cancel()

            validationJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    withTimeout(10000) { // 10 second timeout
                        val response = activationValidator.validateActivationWithServer()
                        if (!response.isValid) {
                            Log.d("MainActivity", "Activation invalid on resume, redirecting")
                            clearLocalActivationData()
                            redirectToActivationScreen()
                        } else {
                            updateLocalActivationData(response)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e("MainActivity", "Resume activation check timed out")
                    // Don't redirect on timeout during resume, just log it
                } catch (e: Exception) {
                    Log.e("MainActivity", "Resume activation check failed: ${e.message}")
                    // Don't redirect on resume errors, just log them
                }
            }
        }
    }

    private fun showActivationExpiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Activation Required")
            .setMessage("Your VPN service activation has expired or is invalid. Please reactivate to continue.")
            .setPositiveButton("Activate") { _, _ ->
                redirectToActivationScreen()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data ?: return

        runOnDefaultDispatcher {
            if (uri.scheme == "husi" && uri.host == "subscription" || uri.scheme == "sing-box") {
                importSubscription(uri)
            } else {
                importProfile(uri)
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
        if (item.isChecked) binding.drawerLayout.closeDrawers() else {
            return displayFragmentWithId(item.itemId)
        }
        return true
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
            else -> return false
        }
        navigation.menu.findItem(id).isChecked = true
        return true
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        DataStore.serviceState = state
        if (state == BaseService.State.RequiredLocation) requestLocationPermission()

        if (::binding.isInitialized) {
            binding.fab.changeState(state, DataStore.serviceState, animate)
            binding.stats.changeState(state)
        }

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
        return if (::binding.isInitialized) {
            Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG).apply {
                if (binding.fab.isShown) {
                    anchorView = binding.fab
                }
            }
        } else {
            Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
//        Log.d("MainActivity", "State changed to: $state")

        // Reset connecting flag when state becomes idle
//        if (state == BaseService.State.Idle) {
//            isConnecting = false
//        }

        // Don't validate activation for every state change - this causes issues
        // Only validate when explicitly needed
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
        isConnecting = false
        if (denied) {
            Log.d("MainActivity", "VPN permission denied")
            snackbar(R.string.vpn_permission_denied).show()
        } else {
            Log.d("MainActivity", "VPN permission granted")
        }
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        if (::binding.isInitialized) {
            binding.stats.updateSpeed(stats.txRateProxy, stats.rxRateProxy)
        }
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
        validationJob?.cancel()
        super.onDestroy()
        GroupManager.userInterface = null
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (super.onKeyDown(keyCode, event)) return true
                if (::binding.isInitialized) {
                    binding.drawerLayout.open()
                    navigation.requestFocus()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (::binding.isInitialized && binding.drawerLayout.isOpen) {
                    binding.drawerLayout.close()
                    return true
                }
            }
        }

        if (super.onKeyDown(keyCode, event)) return true
        if (::binding.isInitialized && binding.drawerLayout.isOpen) return false

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
