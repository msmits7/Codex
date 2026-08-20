package nl.mikesmits.p2000.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import nl.mikesmits.p2000.PollService
import nl.mikesmits.p2000.data.AardExtractor
import nl.mikesmits.p2000.data.MeldingGroep
import nl.mikesmits.p2000.data.PolitieStats
import nl.mikesmits.p2000.data.Prefs
import nl.mikesmits.p2000.data.ServiceType
import nl.mikesmits.p2000.databinding.ActivityMainBinding
import nl.mikesmits.p2000.databinding.SheetDetailBinding
import nl.mikesmits.p2000.databinding.SheetFiltersBinding
import java.text.SimpleDateFormat
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private val viewModel: MainViewModel by viewModels()
    private val adapter = MeldingAdapter { groep -> showDetailSheet(groep) }
    private val allesAdapter = MeldingAdapter { groep -> showDetailSheet(groep) }
    private val markers = mutableListOf<Marker>()
    private val markerByGuid = mutableMapOf<String, Marker>()
    private var lastMarkerSignature: List<String>? = null
    private val markerDrawables = mutableMapOf<ServiceType, android.graphics.drawable.Drawable?>()

    companion object {
        // Cap zodat de kaart (altijd zichtbaar op foldables) nooit duizenden
        // markers hoeft te tekenen; de lijst toont wel alles.
        private const val MAX_MAP_MARKERS = 500
    }

    /** True op foldables/tablets (w600dp): lijst en kaart staan naast elkaar. */
    private val isDualPane: Boolean
        get() = resources.getBoolean(R.bool.is_dual_pane)
    private var pendingRadiusKm: Int? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Service starten ongeacht het antwoord; zonder toestemming is de
            // notificatie onzichtbaar maar blijft de service werken.
            startPollService()
        }

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
        setupAlles()
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

        // Achtergrond-verversing hervatten als die aan stond
        if (prefs.backgroundEnabled && !PollService.running) {
            startPollService()
        }

        maybeShowCrashInfo()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.groups.collect { list ->
                        adapter.submitList(list)
                        binding.textEmpty.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                        updateMapMarkers(list)
                    }
                }
                launch {
                    viewModel.allesGroups.collect { list ->
                        allesAdapter.submitList(list)
                        binding.textEmptyAlles.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
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

    private fun setupAlles() {
        binding.recyclerAlles.layoutManager = LinearLayoutManager(this)
        binding.recyclerAlles.adapter = allesAdapter
        binding.inputZoekAlles.doAfterTextChanged {
            viewModel.setZoekAlles(it?.toString() ?: "")
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
                // Aangevinkt = alleen dit type tonen; niets aangevinkt = alles
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
        // In two-pane staat de kaart al naast de lijst; dat tabblad is dan overbodig
        binding.bottomNav.menu.findItem(R.id.nav_map).isVisible = !isDualPane
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> showPanes(list = false, map = true, alles = false)
                R.id.nav_alles -> showPanes(list = false, map = false, alles = true)
                else -> showPanes(list = true, map = isDualPane, alles = false)
            }
            true
        }
        showPanes(list = true, map = isDualPane, alles = false)
    }

    private fun showPanes(list: Boolean, map: Boolean, alles: Boolean) {
        binding.listContainer.visibility = if (list) View.VISIBLE else View.GONE
        binding.mapContainer.visibility = if (map) View.VISIBLE else View.GONE
        binding.allesContainer.visibility = if (alles) View.VISIBLE else View.GONE
    }

    private fun showDetailSheet(g: MeldingGroep) {
        val m = g.primary
        val sheetBinding = SheetDetailBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)

        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy · HH:mm:ss", Locale("nl", "NL"))

        sheetBinding.detailIcon.setImageResource(MeldingAdapter.iconFor(m.type))
        sheetBinding.detailIcon.backgroundTintList =
            ContextCompat.getColorStateList(this, MeldingAdapter.colorFor(m.type))
        val typesLabel = g.types.joinToString(" + ") { it.label }
        sheetBinding.detailType.text = listOfNotNull(typesLabel, g.prio).joinToString(" · ")
        sheetBinding.detailTime.text = dateFormat.format(m.time)

        // Aard van de melding
        if (g.aard != null) {
            sheetBinding.detailAard.text = g.aard
        } else {
            sheetBinding.detailAard.text = getString(R.string.detail_aard_onbekend)
            if (g.types.all { it == ServiceType.AMBULANCE }) {
                sheetBinding.detailOmschrijving.text =
                    "${m.description}\n\n${getString(R.string.detail_aard_ambu_privacy)}"
            }
        }
        if (sheetBinding.detailOmschrijving.text.isNullOrEmpty()) {
            sheetBinding.detailOmschrijving.text = m.description.ifEmpty { m.rawTitle }
        }

        // Prioriteit met uitleg
        val prioUitleg = AardExtractor.prioUitleg(g.prio)
        val dia = if (g.directeInzet) " · ${getString(R.string.detail_dia)}" else ""
        sheetBinding.detailPrio.visibility = if (prioUitleg != null || dia.isNotEmpty()) View.VISIBLE else View.GONE
        sheetBinding.detailPrio.text = getString(R.string.detail_prio, (prioUitleg ?: g.prio ?: "-") + dia)

        // Locatie en regio
        val locatie = listOfNotNull(m.street, m.postcode, m.city).joinToString(", ")
        sheetBinding.detailLocatie.visibility = if (locatie.isNotEmpty()) View.VISIBLE else View.GONE
        sheetBinding.detailLocatie.text = getString(R.string.detail_locatie, locatie)
        val regio = listOfNotNull(m.region, m.province).joinToString(" · ")
        sheetBinding.detailRegio.visibility = if (regio.isNotEmpty()) View.VISIBLE else View.GONE
        sheetBinding.detailRegio.text = getString(R.string.detail_regio, regio)
        sheetBinding.detailBron.text =
            getString(R.string.detail_bron, g.meldingen.map { it.bron }.distinct().joinToString(", "))

        // Eenheden en rit-/bonnummer (samengevoegd over alle gekoppelde meldingen)
        sheetBinding.detailEenheden.visibility = if (g.eenheden.isNotEmpty()) View.VISIBLE else View.GONE
        sheetBinding.detailEenheden.text = getString(R.string.detail_eenheden, g.eenheden.joinToString(", "))
        sheetBinding.detailDossier.visibility = if (g.dossier != null) View.VISIBLE else View.GONE
        sheetBinding.detailDossier.text = g.dossier ?: ""

        // Elke opgeroepen dienst als eigen ingesprongen regel met pagertekst
        sheetBinding.detailSubHeader.text = if (g.meldingen.size > 1) {
            getString(R.string.detail_gekoppeld_count, g.meldingen.size)
        } else {
            getString(R.string.detail_raw_title)
        }
        SubMeldingBinder.bind(sheetBinding.detailSubContainer, g.meldingen, showRaw = true)

        // Verrijk achteraf met gemeentecijfers van data.politie.nl
        val gemeenteCode = g.meldingen.firstNotNullOfOrNull { it.gemeenteCode }
        val gemeenteNaam = g.meldingen.firstNotNullOfOrNull { it.gemeenteNaam }
        if (gemeenteCode != null && gemeenteNaam != null) {
            lifecycleScope.launch {
                val stats = PolitieStats.forGemeente(gemeenteCode, gemeenteNaam, g.aard)
                if (stats != null && dialog.isShowing) {
                    val soort = if (stats.soortLabel != null && stats.soortAantal != null) {
                        getString(R.string.stats_soort, stats.soortAantal, stats.soortLabel)
                    } else ""
                    sheetBinding.detailStats.text = getString(
                        R.string.stats_line,
                        stats.gemeenteNaam,
                        stats.periodeLabel,
                        stats.totaalMisdrijven
                    ) + soort
                    sheetBinding.detailStats.visibility = View.VISIBLE
                }
            }
        }

        sheetBinding.buttonShowOnMap.isEnabled = g.lat != null
        sheetBinding.buttonShowOnMap.setOnClickListener {
            dialog.dismiss()
            focusOnMap(g)
        }
        sheetBinding.buttonOpenBrowser.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.link))) }
        }
        dialog.show()
    }

    private fun focusOnMap(g: MeldingGroep) {
        val lat = g.lat ?: run {
            Toast.makeText(this, R.string.no_coordinates, Toast.LENGTH_SHORT).show()
            return
        }
        val lon = g.lon ?: return
        if (!isDualPane) {
            binding.bottomNav.selectedItemId = R.id.nav_map
        }
        binding.map.controller.animateTo(GeoPoint(lat, lon), 14.0, 600L)
        markerByGuid[g.primary.guid]?.showInfoWindow()
    }

    private fun updateMapMarkers(list: List<MeldingGroep>) {
        // Nieuwste eerst; alleen groepen met coördinaten, gemaximeerd
        val toShow = list.filter { it.lat != null && it.lon != null }.take(MAX_MAP_MARKERS)
        // Niets herbouwen als er effectief niets veranderd is (elke 5s ververst de UI)
        val signature = toShow.map { "${it.primary.guid}|${it.meldingen.size}" }
        if (signature == lastMarkerSignature) return
        lastMarkerSignature = signature

        markers.forEach { binding.map.overlays.remove(it) }
        markers.clear()
        markerByGuid.clear()
        for (g in toShow) {
            val lat = g.lat ?: continue
            val lon = g.lon ?: continue
            val m = g.primary
            val typesLabel = g.types.joinToString(" + ") { it.label }
            val marker = Marker(binding.map).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = listOfNotNull(typesLabel, g.prio, g.aard).joinToString(" · ")
                snippet = m.description.ifEmpty { m.rawTitle }
                subDescription = m.locationLabel +
                    if (g.meldingen.size > 1) " · ${getString(R.string.group_count, g.meldingen.size)}" else ""
                icon = markerDrawables.getOrPut(m.type) {
                    ContextCompat.getDrawable(this@MainActivity, iconFor(m.type))
                }
            }
            // Tikken op een marker opent hetzelfde detailscherm als in de lijst
            marker.setOnMarkerClickListener { _, _ ->
                showDetailSheet(g)
                true
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
        ServiceType.POLITIEBERICHT -> R.drawable.marker_politiebericht
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
        // Historievenster-chips
        val windowOptions = listOf(
            15 to R.string.window_15m, 30 to R.string.window_30m,
            60 to R.string.window_1h, 180 to R.string.window_3h,
            360 to R.string.window_6h, 720 to R.string.window_12h,
            1440 to R.string.window_24h, 2880 to R.string.window_48h,
            4320 to R.string.window_72h, 10080 to R.string.window_1w
        )
        for ((minutes, labelRes) in windowOptions) {
            val chip = Chip(this).apply {
                text = getString(labelRes)
                isCheckable = true
                isChecked = f.windowMinutes == minutes
                setOnCheckedChangeListener { _, checked ->
                    if (checked) viewModel.setWindowMinutes(minutes)
                }
            }
            sheetBinding.chipGroupWindow.addView(chip)
        }

        // Achtergrond-verversing
        sheetBinding.switchBackground.isChecked = prefs.backgroundEnabled
        sheetBinding.switchBackground.setOnCheckedChangeListener { _, checked ->
            prefs.backgroundEnabled = checked
            if (checked) {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startPollService()
                }
            } else {
                stopService(Intent(this, PollService::class.java))
            }
        }

        sheetBinding.buttonLog.setOnClickListener {
            dialog.dismiss()
            toonDiagnose()
        }

        sheetBinding.buttonClearFilters.setOnClickListener {
            viewModel.clearTypes()
            for (i in 0 until binding.chipGroupTypes.childCount) {
                (binding.chipGroupTypes.getChildAt(i) as? Chip)?.isChecked = false
            }
            viewModel.setLocationQuery("")
            viewModel.setRadiusKm(0)
            viewModel.setWindowMinutes(1440)
            dialog.dismiss()
        }
        dialog.show()
    }

    /** Diagnoserapport tonen met knoppen om te kopiëren of te delen. */
    private fun toonDiagnose() {
        val rapport = viewModel.diagnoseRapport()
        val weergave = android.widget.TextView(this).apply {
            text = rapport
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 10f
            setTextIsSelectable(true)
            val p = (12 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        val scroll = android.widget.ScrollView(this).apply { addView(weergave) }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.log_titel)
            .setView(scroll)
            .setPositiveButton(R.string.log_kopieren) { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("P2000 diagnose", rapport))
                Toast.makeText(this, R.string.log_gekopieerd, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.log_delen) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "P2000 Live diagnose")
                    putExtra(Intent.EXTRA_TEXT, rapport)
                }
                runCatching { startActivity(Intent.createChooser(send, null)) }
            }
            .setNegativeButton(R.string.crash_dismiss, null)
            .show()
    }

    private fun startPollService() {
        ContextCompat.startForegroundService(this, Intent(this, PollService::class.java))
    }

    /** Toon (eenmalig) de details van een eerdere crash, met een deel-knop. */
    private fun maybeShowCrashInfo() {
        val file = java.io.File(getExternalFilesDir(null) ?: filesDir, "last_crash.txt")
        if (!file.exists()) return
        val text = runCatching { file.readText() }.getOrNull()?.take(4000) ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_title)
            .setMessage(text)
            .setPositiveButton(R.string.crash_share) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "P2000 Live crashrapport")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                runCatching { startActivity(Intent.createChooser(send, null)) }
            }
            .setNegativeButton(R.string.crash_dismiss, null)
            .setOnDismissListener { file.delete() }
            .show()
    }

    private fun radiusLabel(km: Int) =
        if (km == 0) getString(R.string.radius_off) else getString(R.string.radius_km, km)

    private fun buildFilterSummary(f: FilterState): String {
        val parts = mutableListOf<String>()
        if (f.types.isNotEmpty()) {
            parts.add("alleen " + f.types.joinToString(", ") { it.label })
        }
        if (f.locationQuery.isNotEmpty()) parts.add("“${f.locationQuery}”")
        if (f.radiusKm > 0) parts.add(getString(R.string.radius_km, f.radiusKm))
        if (f.windowMinutes != 1440) {
            parts.add(
                when {
                    f.windowMinutes < 60 -> "${f.windowMinutes} min terug"
                    f.windowMinutes < 2880 -> "${f.windowMinutes / 60} uur terug"
                    f.windowMinutes >= 10080 -> "1 week terug"
                    else -> "${f.windowMinutes / 1440} dagen terug"
                }
            )
        }
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
