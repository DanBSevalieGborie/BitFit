package com.codepath.bitfit.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.codepath.bitfit.R
import com.codepath.bitfit.databinding.ActivityMainBinding
import com.codepath.bitfit.util.padForSystemBars
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationBarView

/**
 * Hosts the three tabs (Entries, Dashboard, Settings).
 * Portrait uses a bottom navigation bar; landscape swaps it for a navigation rail
 * (see res/layout-land/activity_main.xml).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nav: NavigationBarView
    private lateinit var fab: View

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nav = findViewById(R.id.main_nav)
        // Portrait: extended FAB above the bottom bar. Landscape: FAB in the navigation rail header.
        fab = findViewById<View>(R.id.fab_add) ?: findViewById(R.id.rail_fab)

        binding.appBar.padForSystemBars(top = true)
        if (nav !is BottomNavigationView) {
            // Landscape: navigation rail on the side, pad the content row instead of the bottom bar
            findViewById<View>(R.id.content_row).padForSystemBars(bottom = true)
        }

        fab.setOnClickListener { startActivity(EntryActivity.newIntent(this)) }

        // Restore the selected tab BEFORE attaching listeners so no callback fires,
        // then show it explicitly (selecting an already-selected item only triggers "reselect").
        val selected = savedInstanceState?.getInt(KEY_TAB) ?: R.id.nav_entries
        nav.selectedItemId = selected
        nav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        nav.setOnItemReselectedListener { /* no-op, keeps scroll position */ }
        showTab(selected)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, nav.selectedItemId)
    }

    private fun showTab(itemId: Int) {
        updateChrome(itemId)
        val tag = itemId.toString()
        // Already showing (e.g. restored after rotation) -> keep the existing fragment and its state
        if (supportFragmentManager.findFragmentByTag(tag) != null) return
        val fragment: Fragment = when (itemId) {
            R.id.nav_dashboard -> DashboardFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> EntriesFragment()
        }
        supportFragmentManager.commit {
            setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            replace(R.id.fragment_container, fragment, tag)
        }
    }

    private fun updateChrome(itemId: Int) {
        binding.toolbar.subtitle = getString(
            when (itemId) {
                R.id.nav_dashboard -> R.string.tab_dashboard_subtitle
                R.id.nav_settings -> R.string.tab_settings_subtitle
                else -> R.string.tab_entries_subtitle
            }
        )
        fab.isVisible = itemId != R.id.nav_settings
    }

    /** Called by tabs while their content scrolls: collapse the "Log today" FAB to an icon going down. */
    fun onContentScrolled(dy: Int) {
        val extended = fab as? ExtendedFloatingActionButton ?: return
        if (dy > 6 && extended.isExtended) extended.shrink()
        else if (dy < -6 && !extended.isExtended) extended.extend()
    }

    /** Where Snackbars should anchor so they don't cover the FAB / bottom bar. */
    fun snackbarAnchor(): View? = if (nav is BottomNavigationView) fab.takeIf { it.isVisible } ?: nav else null

    companion object {
        private const val KEY_TAB = "selected_tab"
    }
}
