package com.example.gchatcompanion

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Displays the real Google Chat website. Authentication and message handling remain with Google.
 * If Google blocks sign-in inside an embedded WebView, the user can continue in their normal browser.
 */
class WebChatActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        toolbar.addView(Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        })
        toolbar.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { webView.reload() }
        })
        toolbar.addView(Button(this).apply {
            text = "Open in browser"
            setOnClickListener { openInBrowser() }
        })
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 4
        ))
        pageStatus = TextView(this).apply {
            text = "Loading Google Chat…"
            textSize = 12f
            setPadding(12, 4, 12, 4)
        }
        root.addView(pageStatus)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
            safeBrowsingEnabled = true
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                pageStatus.text = title ?: "Google Chat"
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageStatus.text = "Loading Google Chat…"
                progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                pageStatus.text = view?.title ?: "Google Chat"
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme ?: return false
                if (scheme != "https" && scheme != "http") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    catch (_: Exception) {
                        Toast.makeText(this@WebChatActivity, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                return false
            }
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    pageStatus.text = "Google Chat could not load. Check your connection or try the browser."
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        if (savedInstanceState == null) {
            webView.loadUrl(CHAT_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun openInBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHAT_URL)))
        } catch (_: Exception) {
            Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API 33; retained for compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHAT_URL = "https://chat.google.com/"
    }
}
