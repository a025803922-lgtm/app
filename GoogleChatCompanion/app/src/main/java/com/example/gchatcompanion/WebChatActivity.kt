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
 * Displays Google Chat in a WebView.
 * Keep the default Android WebView user-agent during Google sign-in, then optionally
 * switch to desktop mode in the same WebView so the existing cookies/session are retained.
 */
class WebChatActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView
    private lateinit var desktopButton: Button

    private var desktopMode = false
    private var sawGoogleSignIn = false
    private var switchingToDesktop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        desktopMode = savedInstanceState?.getBoolean(STATE_DESKTOP_MODE, false) ?: false
        sawGoogleSignIn = savedInstanceState?.getBoolean(STATE_SAW_SIGN_IN, false) ?: false

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
        desktopButton = Button(this).apply {
            text = if (desktopMode) "Mobile view" else "Desktop view"
            setOnClickListener { toggleDesktopMode() }
        }
        toolbar.addView(desktopButton)
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

            // Use Android WebView's normal user-agent for sign-in. Desktop mode is applied later.
            if (desktopMode) userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = desktopMode
            loadWithOverviewMode = desktopMode
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
                val host = Uri.parse(url ?: "").host.orEmpty()
                if (host == "accounts.google.com" || host.endsWith(".accounts.google.com")) {
                    sawGoogleSignIn = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                pageStatus.text = view?.title ?: "Google Chat"

                val host = Uri.parse(url ?: "").host.orEmpty()
                // When Google returns from its sign-in domain to Chat, switch to desktop mode
                // once. The same WebView is retained, so its cookies are not discarded.
                if (!desktopMode && sawGoogleSignIn && host == "chat.google.com" && !switchingToDesktop) {
                    switchingToDesktop = true
                    applyDesktopMode()
                }
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

    private fun toggleDesktopMode() {
        if (desktopMode) {
            desktopMode = false
            switchingToDesktop = false
            webView.settings.apply {
                userAgentString = DEFAULT_USER_AGENT
                useWideViewPort = false
                loadWithOverviewMode = false
            }
            desktopButton.text = "Desktop view"
            webView.reload()
        } else {
            applyDesktopMode()
        }
    }

    private fun applyDesktopMode() {
        if (desktopMode) return
        desktopMode = true
        webView.settings.apply {
            userAgentString = DESKTOP_USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        desktopButton.text = "Mobile view"
        pageStatus.text = "Switching to desktop Chat…"
        webView.reload()
    }

    private fun openInBrowser() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHAT_URL)))
        } catch (_: Exception) {
            Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DESKTOP_MODE, desktopMode)
        outState.putBoolean(STATE_SAW_SIGN_IN, sawGoogleSignIn)
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
        private const val STATE_DESKTOP_MODE = "desktop_mode"
        private const val STATE_SAW_SIGN_IN = "saw_google_sign_in"
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
