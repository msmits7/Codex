package nl.mikesmits.p2000

import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import nl.mikesmits.p2000.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduceert de foldable-situatie: layout-w600dp (binnenscherm van een
 * Galaxy Z Fold) versus de telefoonlayout.
 */
@RunWith(RobolectricTestRunner::class)
class DualPaneTest {

    private fun inflate() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_P2000
        )
        LayoutInflater.from(context).inflate(R.layout.activity_main, null)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h800dp")
    fun inflatePhoneLayout() = inflate()

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h800dp")
    fun inflateDualPaneLayout() = inflate()

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h800dp-night")
    fun inflateDualPaneDarkLayout() = inflate()

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h800dp")
    fun launchActivityDualPane() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h800dp")
    fun launchActivityPhone() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
    }
}
