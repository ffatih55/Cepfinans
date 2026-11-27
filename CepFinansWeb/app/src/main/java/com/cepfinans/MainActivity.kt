package com.cepfinans
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var w: WebView
    private lateinit var bg: WebView
    private var lastStop = 0L
    private var tries = 0
    private val h = Handler(Looper.getMainLooper())
    private val kpiUrl = "https://fintables.com/fonlar/KPI"

    // Fintables KPI sayfasında günlük getiri %, fiyat ve fiyat tarihini etiket yakınından arar.
    // Sayfa yapısı doğrulanmadı: sınıf adına değil, "Günlük Getiri / Fiyat / Tarih" metinlerine bakar.
    private val readerJs = """
        (function(){try{
          var txt=function(e){return (e.innerText||e.textContent||'').replace(/\s+/g,' ').trim()};
          var pr=/[+\-−–]?\s*%\s*\d+(?:[.,]\d+)?|[+\-−–]?\s*\d+(?:[.,]\d+)?\s*%/;
          var all=[].slice.call(document.querySelectorAll('body *')).filter(function(e){return e.children.length<=4});
          function near(re,rx){for(var k=0;k<all.length;k++){var e=all[k],t=txt(e);
            if(t.length<160&&re.test(t)){var n=e;for(var i=0;i<4&&n;i++,n=n.parentElement){var tt=txt(n);
              if(tt.length>400)break;var m=tt.match(rx);if(m)return m[0];}}}return '';}
          var pct=near(/günlük\s*(getiri|değişim)|gunluk/i,pr);
          var price=near(/(son\s*)?fiyat|birim\s*pay/i,/\d+[.,]\d{4,6}/);
          var date=near(/tarih|fiyat/i,/\d{2}\.\d{2}\.\d{4}/);
          FinApp.report(pct,price,date);
        }catch(x){FinApp.report('','','');}})();
    """.trimIndent()

    inner class Bridge {
        @JavascriptInterface
        fun canBio(): Boolean = Build.VERSION.SDK_INT >= 28 && packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        @JavascriptInterface
        fun bio() { runOnUiThread { if (Build.VERSION.SDK_INT >= 28) showBio() } }
        @JavascriptInterface
        fun refreshKpi() { runOnUiThread { tries = 0; bg.loadUrl(kpiUrl) } }
    }

    inner class FinBridge {
        @JavascriptInterface
        fun report(pct: String, price: String, date: String) {
            runOnUiThread {
                if (pct.isBlank() && tries < 2) { // SPA henüz çizmemiş olabilir, tekrar dene
                    tries++; h.postDelayed({ bg.evaluateJavascript(readerJs, null) }, 4000); return@runOnUiThread
                }
                w.evaluateJavascript("kpiFromPage(" + JSONObject.quote(pct) + "," + JSONObject.quote(price) + "," + JSONObject.quote(date) + ")", null)
            }
        }
    }

    @RequiresApi(28)
    private fun showBio() {
        val p = BiometricPrompt.Builder(this).setTitle("Cep Finans").setSubtitle("Parmak iziyle aç")
            .setNegativeButton("PIN kullan", mainExecutor) { _, _ -> }.build()
        p.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult?) {
                w.evaluateJavascript("onBio(true)", null)
            }
        })
    }

    private fun setup(v: WebView) {
        v.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccessFromFileURLs = true; allowUniversalAccessFromFileURLs = true
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        scheduleDaily(this)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK

        w = WebView(this); w.setBackgroundColor(Color.BLACK); setup(w)
        // Gizli WebView: 1x1 ve şeffaf. GONE yapılırsa bazı cihazlarda JS çalışmayı durdurur.
        bg = WebView(this); setup(bg); bg.alpha = 0f

        val root = FrameLayout(this)
        root.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(bg, FrameLayout.LayoutParams(1, 1))
        setContentView(root)

        w.addJavascriptInterface(Bridge(), "Native")
        bg.addJavascriptInterface(FinBridge(), "FinApp")
        bg.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                h.postDelayed({ bg.evaluateJavascript(readerJs, null) }, 3500)
            }
        }
        w.webViewClient = WebViewClient()
        w.webChromeClient = object : WebChromeClient() {
            override fun onJsPrompt(v: WebView?, u: String?, m: String?, d: String?, r: JsPromptResult): Boolean {
                val e = EditText(this@MainActivity); e.setText(d)
                AlertDialog.Builder(this@MainActivity).setMessage(m).setView(e)
                    .setPositiveButton("Tamam") { _, _ -> r.confirm(e.text.toString()) }
                    .setNegativeButton("İptal") { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show()
                return true
            }
            override fun onJsConfirm(v: WebView?, u: String?, m: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(m)
                    .setPositiveButton("Evet") { _, _ -> r.confirm() }
                    .setNegativeButton("Hayır") { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show()
                return true
            }
            override fun onJsAlert(v: WebView?, u: String?, m: String?, r: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(m)
                    .setPositiveButton("Tamam") { _, _ -> r.confirm() }.setOnCancelListener { r.confirm() }.show()
                return true
            }
        }
        w.loadUrl("file:///android_asset/index.html")
    }

    override fun onStop() { super.onStop(); lastStop = System.currentTimeMillis() }
    override fun onStart() {
        super.onStart()
        if (lastStop > 0 && System.currentTimeMillis() - lastStop > 15000)
            w.evaluateJavascript("window.lockNow&&lockNow()", null)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (w.canGoBack()) w.goBack() else super.onBackPressed() }
}
