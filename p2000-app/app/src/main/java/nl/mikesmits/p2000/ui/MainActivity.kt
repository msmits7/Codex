package nl.mikesmits.p2000.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfiguration
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import nl.mikesmits.p2000.R
import nl.mikesmits.p2000.data.Melding
import nl.mikesmits.p2000.data.Prefs
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.databinding.ActivityMainBinding
import nl.mikesmits.p2000.databinding.SheetFiltersBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val viewModel: MainViewModel by viewModels()
    private val adapter = MeldingAdapter { melding -> focusOnMap(melding) }
    private val markers = mutableListOf<Marker>()
    private val markerByGuid = mutableMapOf<String, Marker>()

    /** True on foldables/tablets (sw600dp layout): list and map are shown side by side. */
    private val isDualPane: Boolean
        get() = binding.bottomNav.visibility == View.GONE
    private var pendingRadiusKm: Int? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                fetchMyLocation()
            } else {
                Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show()
                viewModel.setRadiusKm(0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid needs a configured user agent before the MapView inflates
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        prefs = Prefs(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupList()
        setupMap()
        setupTypeChips()
        setupNavigation()
        setupThemeButton()
        binding.buttonFilters.setOnClickListener { showFilterSheet() }

        // Straalfilter uit vorige sessie weer activeren als locatie al is toegestaan
        val savedRadius = viewModel.filter.value.radiusKm
        if (savedRadius > 0 && hasLocationPermission() && viewModel.filter.value.myLocation == null) {
            pendingRadiusKm = savedRadius
            fetchMyLocation()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filtered.collect { list ->
                        adapter.submitList(list)
                        binding.textEmpty.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                        updateMapMarkers(list)
                    }
                }
                launch { viewModel.status.collect { binding.textStatus.text = it } }
                launch {
                    viewModel.filter.collect { f ->
                        binding.textActiveFilter.text = buildFilterSummary(f)
                    }
                }
            }
        }
    }

    private fun setupList() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshNow()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(8.0)
        binding.map.controller.setCenter(GeoPoint(52.2, 5.3)) // centre of the Netherlands
        // In dark mode de kaarttegels mee verdonkeren
        val night = (resources.configuration.uiMode and AndroidConfiguration.UI_MODE_NIGHT_MASK) ==
            AndroidConfiguration.UI_MODE_NIGHT_YES
        binding.map.overlayManager.tilesOverlay.setColorFilter(
            if (night) TilesOverlay.INVERT_COLORS else null
        )
    }

    private fun setupTypeChips() {
        val activeTypes = viewModel.filter.value.types
        for (type in ServiceType.values()) {
            val chip = Chip(this).apply {
                text = type.label
                isCheckable = true
                isChecked = type in activeTypes
                setChipIconResource(MeldingAdapter.iconFor(type))
                isChipIconVisible = true
                chipIconTint = ContextCompat.getColorStateList(
                    this@MainActivity, MeldingAdapter.colorFor(type)
                )
                chipBackgroundColor = ContextCompat.getColorStateList(
                    this@MainActivity, R.color.chip_background
                )
                setOnCheckedChangeListener { _, checked -> viewModel.toggleType(type, checked) }
            }
            binding.chipGroupTypes.addView(chip)
        }
    }

    private fun setupThemeButton() {
        binding.buttonTheme.setIconResource(themeIcon(prefs.themeMode))
        binding.buttonTheme.setOnClickListener {
            val next = when (prefs.themeMode) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_NO
                AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.themeMode = next
            AppCompatDelegate.setDefaultNightMode(next)
        }
    }

    private fun themeIcon(mode: Int) = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> R.drawable.ic_theme_light
        AppCompatDelegate.MODE_NIGHT_YES -> R.drawable.ic_theme_dark
        else -> R.drawable.ic_theme_auto
    }

    private fun setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val showMap = item.itemId == R.id.nav_map
            binding.mapContainer.visibility = if (showMap) View.VISIBLE else View.GONE
            binding.listContainer.visibility = if (showMap) View.GONE else View.VISIBLE
            true
        }
    }

    private fun focusOnMap(melding: Melding) {
        val lat = melding.lat ?: run {
            Toast.makeText(this, R.string.no_coordinates, Toast.LENGTH_SHORT).show()
            return
        }
        val lon = melding.lon ?: return
        if (!isDualPane) {
            binding.bottomNav.selectedItemId = R.id.nav_map
        }
        binding.map.controller.animateTo(GeoPoint(lat, lon), 14.0, 600L)
        markerByGuid[melding.guid]?.showInfoWindow()
    }

    private fun updateMapMarkers(list: List<Melding>) {
        markers.forEach { binding.map.overlays.remove(it) }
        markers.clear()
        markerByGuid.clear()
        for (m in list) {
            val lat = m.lat ?: continue
            val lon = m.lon ?: continue
            val marker = Marker(binding.map).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "${m.type.label}${m.prio?.let { " · $it" } ?: ""}"
                snippet = m.description.ifEmpty { m.rawTitle }
                subDescription = m.locationLabel
                icon = ContextCompat.getDrawable(this@MainActivity, iconFor(m.type))
            }
            markers.add(marker)
            markerByGuid[m.guid] = marker
            binding.map.overlays.add(marker)
        }
        binding.map.invalidate()
    }

    private fun iconFor(type: ServiceType) = when (type) {
        ServiceType.AMBULANCE -> R.drawable.marker_ambulance
        ServiceType.BRANDWEER -> R.drawable.marker_brandweer
        ServiceType.POLITIE -> R.drawable.marker_politie
        ServiceType.TRAUMA -> R.drawable.marker_trauma
        ServiceType.WATER -> R.drawable.marker_water
        ServiceType.OVERIG -> R.drawable.marker_overig
    }

    private fun showFilterSheet() {
        val sheetBinding = SheetFiltersBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)

        val f = viewModel.filter.value
        sheetBinding.inputLocation.setText(f.locationQuery)
        sheetBinding.sliderRadius.value = f.radiusKm.toFloat()
        sheetBinding.textRadiusValue.text = radiusLabel(f.radiusKm)

        sheetBinding.inputLocation.doAfterTextChanged {
            viewModel.setLocationQuery(it?.toString() ?: "")
        }
        sheetBinding.sliderRadius.addOnChangeListener { _, value, _ ->
            sheetBinding.textRadiusValue.text = radiusLabel(value.toInt())
        }
        sheetBinding.sliderRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                val km = slider.value.toInt()
                if (km > 0) {
                    ensureLocationThen(km)
                } else {
                    viewModel.setRadiusKm(0)
                }
            }
        })
        sheetBinding.buttonClearFilters.setOnClickListener {
            viewModel.setLocationQuery("")
            viewModel.setRadiusKm(0)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun radiusLabel(km: Int) =
        if (km == 0) getString(R.string.radius_off) else getString(R.string.radius_km, km)

    private fun buildFilterSummary(f: FilterState): String {
        val parts = mutableListOf<String>()
        if (f.types.size < ServiceType.values().size) {
            parts.add(f.types.joinToString(", ") { it.label })
        }
        if (f.locationQuery.isNotEmpty()) parts.add("“${f.locationQuery}”")
        if (f.radiusKm > 0) parts.add(getString(R.string.radius_km, f.radiusKm))
        return if (parts.isEmpty()) getString(R.string.filter_none) else parts.joinToString(" · ")
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureLocationThen(km: Int) {
        pendingRadiusKm = km
        if (hasLocationPermission()) {
            fetchMyLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchMyLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val location = providers
            .filter { lm.allProviders.contains(it) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (location != null) {
            viewModel.setMyLocation(location)
            pendingRadiusKm?.let { viewModel.setRadiusKm(it) }
            binding.map.controller.setCenter(GeoPoint(location.latitude, location.longitude))
            binding.map.controller.setZoom(11.0)
        } else {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show()
            viewModel.setRadiusKm(0)
        }
        pendingRadiusKm = null
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}
