package com.lilyan_lefevre.puzzleit

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.lilyan_lefevre.puzzleit.databinding.ActivityMainNavBinding
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

/**
 * Main Activity with Navigation Component and centralized Toolbar management
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var accountStore: AccountStore

    private lateinit var binding: ActivityMainNavBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainNavBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply padding to AppBarLayout instead of Toolbar to fix alignment issues
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = topInset)
            insets
        }

        setSupportActionBar(binding.toolbar)
        setupNavigation()
        
        // Initialize OpenCV
        initializeOpenCV()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Nobody signed in: the app opens on the login screen, never on the list.
        val signedIn = accountStore.account.value != null
        navController.graph = navController.navInflater.inflate(R.navigation.nav_graph).apply {
            setStartDestination(if (signedIn) R.id.projectListFragment else R.id.loginFragment)
        }

        // Connect the action bar with the NavController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // These screens draw their own header (with its own back button): showing the toolbar too gave two arrows.
        val ownHeader = setOf(
            R.id.loginFragment, R.id.projectListFragment, R.id.puzzleWorkingFragment, R.id.pieceCaptureFragment,
            R.id.accountFragment, R.id.historyFragment, R.id.progressFragment,
        )
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.toolbar.visibility = if (dest.id in ownHeader) View.GONE else View.VISIBLE
        }

        // Signing in leaves the login screen; signing out (or the server refusing the token) brings it back, whatever screen is open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                accountStore.account.collect { account ->
                    val onLogin = navController.currentDestination?.id == R.id.loginFragment
                    if (account == null && !onLogin) {
                        navController.navigate(R.id.loginFragment, null, navOptions { popUpTo(navController.graph.id) { inclusive = true } })
                    } else if (account != null && onLogin) {
                        navController.navigate(R.id.projectListFragment, null, navOptions { popUpTo(R.id.loginFragment) { inclusive = true } })
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }

    /**
     * Initialize OpenCV library
     * This must be called before using any OpenCV functions
     */
    private fun initializeOpenCV() {
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully")
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
