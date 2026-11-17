package no.pleiboks.skall

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Pleiboks-skallet: viser Pleiboks (nettsiden) i fullskjerm og gjør det nettsiden ikke kan selv:
 *  • leser NFC-brikker og sender ID-en til siden: window.pleiboksBrikke("04A31F")
 *  • holder skjermen på og låser telefonen til appen (skjermfesting / startskjerm)
 *
 * Pleiboks styrer alt annet (onboarding, foreldrepanel) og kaller skallet via objektet
 * PleiboksSkall — se src/skall.js i pleiboks-repoet.
 */
class MainActivity : Activity(), NfcAdapter.ReaderCallback {

    private lateinit var web: WebView
    private var nfc: NfcAdapter? = null
    private val prefs by lazy { getSharedPreferences("skall", Context.MODE_PRIVATE) }
    private val pleiboksHost by lazy { Uri.parse(BuildConfig.PLEIBOKS_URL).host }

    private var sistUid = ""
    private var sistTid = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        nfc = NfcAdapter.getDefaultAdapter(this)

        web = WebView(this)
        web.setBackgroundColor(0xFFAEE4FF.toInt())
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false   // brikken skal starte lyden uten trykk
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString PleiboksSkall/1"
        }
        web.addJavascriptInterface(Bro(), "PleiboksSkall")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url
                if (url.host == pleiboksHost || url.scheme == "file") return false
                try {
                    when {
                        // f.eks. «Åpne Bluetooth-innstillinger» i foreldrepanelet
                        url.scheme == "intent" -> startActivity(Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME))
                        !erKiosk() -> startActivity(Intent(Intent.ACTION_VIEW, url))
                    }
                } catch (_: Exception) {}
                return true
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                // Ingen nett og ingenting lagret ennå (første oppstart): vis en enkel side med «Prøv igjen»
                if (req.isForMainFrame) view.loadUrl("file:///android_asset/ingen-nett.html")
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(BuildConfig.PLEIBOKS_URL)
    }

    override fun onResume() {
        super.onResume()
        fullskjerm()
        web.onResume()
        nfc?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500) }
        )
        // Kommer vi tilbake fra «velg startskjerm» etter at kiosk er slått på: lås nå
        if (erKiosk()) startLaas()
    }

    override fun onPause() {
        nfc?.disableReaderMode(this)
        web.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullskjerm()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (erKiosk()) return
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ── NFC ─────────────────────────────────────────────────────────
    override fun onTagDiscovered(tag: Tag) {
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        val naa = SystemClock.elapsedRealtime()
        if (uid == sistUid && naa - sistTid < 1500) return   // samme brikke lest to ganger på rad
        sistUid = uid
        sistTid = naa
        runOnUiThread {
            web.evaluateJavascript("window.pleiboksBrikke && window.pleiboksBrikke('$uid')", null)
        }
    }

    // ── Kiosk ───────────────────────────────────────────────────────
    private fun erKiosk() = prefs.getBoolean("kiosk", false)

    private fun erStartskjerm(): Boolean {
        val hjem = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(hjem, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun startLaas() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    private fun aapne(handling: String) {
        try { startActivity(Intent(handling)) }
        catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
    }

    @Suppress("DEPRECATION")
    private fun fullskjerm() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    // ── Broen Pleiboks bruker (window.PleiboksSkall) ────────────────
    inner class Bro {
        @JavascriptInterface fun versjon(): String = "1"

        @JavascriptInterface fun nfcPaa(): Boolean = nfc?.isEnabled == true

        @JavascriptInterface fun aapneNfcInnstillinger() {
            runOnUiThread { aapne(Settings.ACTION_NFC_SETTINGS) }
        }

        /** Startskjerm + skjermfesting. Er Pleiboks ikke startskjerm ennå, åpnes valget først. */
        @JavascriptInterface fun laas() {
            runOnUiThread {
                prefs.edit().putBoolean("kiosk", true).apply()
                if (!erStartskjerm()) aapne(Settings.ACTION_HOME_SETTINGS) else startLaas()
            }
        }

        /** Slipper låsen og åpner valget av startskjerm, så forelderen kan bruke telefonen. */
        @JavascriptInterface fun avsluttKiosk() {
            runOnUiThread {
                prefs.edit().putBoolean("kiosk", false).apply()
                try { stopLockTask() } catch (_: Exception) {}
                aapne(Settings.ACTION_HOME_SETTINGS)
            }
        }

        @JavascriptInterface fun provIgjen() {
            runOnUiThread { web.loadUrl(BuildConfig.PLEIBOKS_URL) }
        }
    }
}
