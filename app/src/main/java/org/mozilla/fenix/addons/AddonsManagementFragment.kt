/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.addons

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.fonts.FontStyle.FONT_WEIGHT_MEDIUM
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.AddonsManagerAdapter
import mozilla.components.feature.addons.ui.AddonsManagerAdapterDelegate
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.databinding.FragmentAddOnsManagementBinding
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.runIfFragmentIsAttached
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.settings.SupportUtils.AMO_HOMEPAGE_FOR_ANDROID
import org.mozilla.fenix.theme.ThemeManager
import java.util.Locale
import com.google.android.material.R as materialR
import mozilla.components.feature.addons.R as addonsR

/**
 * Fragment use for managing add-ons.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagementFragment : Fragment(R.layout.fragment_add_ons_management), SystemInsetsPaddedFragment {

    private val logger = Logger("AddonsManagementFragment")

    private var binding: FragmentAddOnsManagementBinding? = null

    private var addons: List<Addon> = emptyList()

    private var adapter: AddonsManagerAdapter? = null

    private var addonImportFilePicker: ActivityResultLauncher<Intent>? = null

    private val browsingModeManager by lazy {
        (activity as HomeActivity).browsingModeManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logger.info("View created for AddonsManagementFragment")
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAddOnsManagementBinding.bind(view)
        bindRecyclerView()
        setupMenu()
        addonImportFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if(result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let{uri ->
                    requireComponents.intentProcessors.addonInstallIntentProcessor.fromUri(uri)
                        .let{ tmpFile ->
                            val extURI = requireComponents.intentProcessors.addonInstallIntentProcessor.parseExtension(tmpFile)
                            requireComponents.intentProcessors.addonInstallIntentProcessor.installExtension(
                                extURI,
                                onSuccess = {
                                    val installedState = provideAddonManager().toInstalledState(it)
                                    val ao = Addon.newFromWebExtension(it, installedState)
                                    runIfFragmentIsAttached {
                                        adapter?.updateAddon(ao)
                                        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                                    }
                                }
                            )
                        }
                }
            }
        }
    }

    private fun setupMenu() {
        val menuHost = requireActivity() as MenuHost

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                    inflater.inflate(R.menu.addons_menu, menu)
                    val searchItem = menu.findItem(R.id.search)
                    val searchView: SearchView = searchItem.actionView as SearchView
                    searchView.imeOptions = EditorInfo.IME_ACTION_DONE
                    searchView.queryHint = getString(R.string.addons_search_hint)

                    searchView.setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String): Boolean {
                                searchAddons(query.trim())
                                return false
                            }

                            override fun onQueryTextChange(newText: String): Boolean {
                                searchAddons(newText.trim())
                                return false
                            }
                        },
                    )
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    // Handle the menu selection
                    return when (menuItem.itemId) {
                        R.id.addons_delete_cache -> {
                            showAlertDialog()
                            true
                        }
                        R.id.addons_sideload -> {
                            installFromFile()
                            true
                        }
                        R.id.search -> {
                            true
                        }
                        else -> {true}
                    }
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED,
        )
    }

    private fun installFromFile() {
        val intent = Intent()
            .setType("application/x-xpinstall")
            .setAction(Intent.ACTION_GET_CONTENT)

        addonImportFilePicker!!.launch(intent)
    }

    private fun showAlertDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder
            .setMessage(R.string.confirm_addons_delete_cache)
            .setPositiveButton(R.string.confirm_addons_delete_cache_yes) { _, _ ->
                requireComponents.clearAddonCache()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton(R.string.confirm_addons_delete_cache_no) { _, _ ->
                // User cancelled the dialog.
            }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun searchAddons(addonSearchText: String): Boolean {
        if (adapter == null) {
            return false
        }

        val searchedAddons = arrayListOf<Addon>()
        addons.forEach { addon ->
            val names = addon.translatableName
            val language = Locale.getDefault().language
            names[language]?.let { name ->
                if (name.lowercase().contains(addonSearchText.lowercase())) {
                    searchedAddons.add(addon)
                }
            }
            val description = addon.translatableDescription
            description[language]?.let { desc ->
                if (desc.lowercase().contains(addonSearchText.lowercase())) {
                    if (!searchedAddons.contains(addon)) {
                        searchedAddons.add(addon)
                    }
                }
            }
        }
        updateUI(searchedAddons)

        return true
    }

    private fun updateUI(searchedAddons: List<Addon>) {
        adapter?.updateAddons(searchedAddons)

        if (searchedAddons.isEmpty()) {
            binding?.addOnsEmptyMessage?.visibility = View.VISIBLE
            binding?.addOnsList?.visibility = View.GONE
        } else {
            binding?.addOnsEmptyMessage?.visibility = View.GONE
            binding?.addOnsList?.visibility = View.VISIBLE
        }
    }


    override fun onResume() {
        logger.info("Resumed AddonsManagementFragment")

        super.onResume()
        showToolbar(getString(R.string.preferences_extensions))
        view?.hideKeyboard()
    }

    override fun onDestroyView() {
        logger.info("Destroyed view for AddonsManagementFragment")

        super.onDestroyView()
        // letting go of the resources to avoid memory leak.
        adapter = null
        binding = null
    }

    @Suppress("CognitiveComplexMethod")
    private fun bindRecyclerView() {
        logger.info("Binding recycler view for AddonsManagementFragment")

        val managementView = AddonsManagementView(
            navController = findNavController(),
            onInstallButtonClicked = ::installAddon,
            onMoreAddonsButtonClicked = ::openAMO,
            onLearnMoreClicked = { link, addon ->
                binding?.root?.openLearnMoreLink(link, addon)
            },
        )

        val recyclerView = binding?.addOnsList
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        val shouldRefresh = adapter != null


        logger.info("AddonsManagementFragment should refresh? $shouldRefresh")

        // If the fragment was launched to install an "external" add-on from AMO, we deactivate
        // the cache to get the most up-to-date list of add-ons to match against.
        lifecycleScope.launch(IO) {
            try {
                logger.info("AddonsManagementFragment asking for addons")

                addons = requireContext().components.addonManager.getAddons()
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        if (!shouldRefresh) {
                            adapter = AddonsManagerAdapter(
                                addonsManagerDelegate = managementView,
                                addons = addons,
                                style = createAddonStyle(requireContext()),
                                store = requireComponents.core.store,
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = false

                        recyclerView?.adapter = adapter
                        recyclerView?.accessibilityDelegate = object : View.AccessibilityDelegate() {
                            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                super.onInitializeAccessibilityNodeInfo(host, info)

                                adapter?.let {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                                            it.itemCount,
                                            1,
                                            false,
                                        )
                                    }
                                }
                            }
                        }

                        if (shouldRefresh) {
                            adapter?.updateAddons(addons)
                        }
                    }
                }
            } catch (e: AddonManagerException) {
                lifecycleScope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        binding?.let {
                            showSnackBar(
                                it.root,
                                getString(addonsR.string.mozac_feature_addons_failed_to_load_extensions),
                            )
                        }
                        binding?.addOnsProgressBar?.isVisible = false
                        binding?.addOnsEmptyMessage?.isVisible = true
                    }
                }
            }
        }
    }

    private fun createAddonStyle(context: Context): AddonsManagerAdapter.Style {
        val sectionsTypeFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Typeface.create(Typeface.DEFAULT, FONT_WEIGHT_MEDIUM, false)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        return AddonsManagerAdapter.Style(
            sectionsTextColor = ThemeManager.resolveAttribute(materialR.attr.colorOnSurface, context),
            addonNameTextColor = ThemeManager.resolveAttribute(materialR.attr.colorOnSurface, context),
            addonSummaryTextColor = ThemeManager.resolveAttribute(materialR.attr.colorOnSurfaceVariant, context),
            sectionsTypeFace = sectionsTypeFace,
            addonAllowPrivateBrowsingLabelDrawableRes = R.drawable.ic_add_on_private_browsing_label,
        )
    }

    @VisibleForTesting
    internal fun provideAddonManager(): AddonManager {
        return requireContext().components.addonManager
    }

    internal fun installAddon(addon: Addon) {
        binding?.addonProgressOverlay?.overlayCardView?.visibility = View.VISIBLE

        if (requireComponents.appStore.state.mode.isPrivate) {
            binding?.addonProgressOverlay?.overlayCardView?.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.fx_mobile_private_layer_color_3,
                ),
            )
        }

        val installOperation = provideAddonManager().installAddon(
            url = addon.downloadUrl,
            installationMethod = InstallationMethod.MANAGER,
            onSuccess = {
                runIfFragmentIsAttached {
                    adapter?.updateAddon(it)
                    binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            },
            onError = { _ ->
                binding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
            },
        )
        binding?.addonProgressOverlay?.cancelButton?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                val safeBinding = binding
                // Hide the installation progress overlay once cancellation is successful.
                if (installOperation.cancel().await()) {
                    safeBinding?.addonProgressOverlay?.overlayCardView?.visibility = View.GONE
                }
            }
        }
    }

    private fun openAMO() {
        findNavController().openToBrowser()
        requireComponents.useCases.fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = AMO_HOMEPAGE_FOR_ANDROID,
            newTab = true,
        )
    }
}
