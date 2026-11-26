package com.imad.planner

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Instagram's own login page, on a screen of its own (not a dialog). The password goes only to Instagram;
 * this app keeps nothing but the sign-in cookie Instagram gives back, and closes as soon as it appears.
 */
class IgLoginActivity : ComponentActivity() {
    private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            if (Ig.loggedIn()) { CookieManager.getInstance().flush(); finish() } else main.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { text = "بفتح صفحة إنستغرام…"; setPadding(32, 24, 32, 12); setTextColor(Color.DKGRAY); textSize = 14f }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val w = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString = Ig.UA
            setBackgroundColor(Color.WHITE)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) { bar.progress = p; bar.visibility = if (p >= 100) android.view.View.GONE else android.view.View.VISIBLE }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url.toString()
                    // Links that leave the web (app links, "open in app") are ignored instead of leaving a blank page.
                    return !(u.startsWith("http://") || u.startsWith("https://")) || !Ig.loginAllowed(u)
                }
                override fun onPageFinished(v: WebView, url: String) { status.text = "سجّل دخول. كلمة السر بتروح لإنستغرام بس، والتطبيق ما بيحفظها." }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) status.text = "ما فتحت الصفحة (${e.description}). تأكد من النت وارجع جرّب."
                }
            }
            loadUrl("https://www.instagram.com/accounts/login/")
        }
        web = w
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(w, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        main.postDelayed(check, 1000)
    }

    @Deprecated("Back goes back inside the login pages first.")
    override fun onBackPressed() {
        if (web?.canGoBack() == true) web?.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        main.removeCallbacks(check)
        web?.destroy(); web = null
        super.onDestroy()
    }
}
