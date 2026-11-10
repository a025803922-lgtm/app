package com.example.gchatcompanion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Google Chat WebView with compact controls, selectable screen profile,
 * and explicit WebView microphone permission handling.
 */
class WebChatActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var pageStatus: TextView
    private lateinit var layoutButton: Button
    private lateinit var defaultUserAgent: String

    private var desktopMode = false
    private var sawGoogleSignIn = false
    private var switchingToDesktop = false
    private var pendingAudioRequest: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        desktopMode = savedInstanceState?.getBoolean(STATE_DESKTOP_MODE, false) ?: false
        sawGoogleSignIn = savedInstanceState?.getBoolean(STATE_SAW_SIGN_IN, false) ?: false

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(1), dp(3), dp(1))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        toolbar.addView(compactButton("‹", "Back") {
            if (webView.canGoBack()) webView.goBack() else finish()
        })
        toolbar.addView(compactButton("↻", "Refresh") { webView.reload() })
        layoutButton = compactButton("▣", "Screen layout") { showLayoutMenu(layoutButton) }
        toolbar.addView(layoutButton)
        toolbar.addView(compactButton("⋮", "More options") { showMoreMenu(it) })
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
        ))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
        ))
        pageStatus = TextView(this).apply {
            textSize = 12f
            setPadding(dp(8), dp(2), dp(8), dp(2))
            visibility = View.GONE
        }
        root.addView(pageStatus)

        webView = WebView(this)
        defaultUserAgent = webView.settings.userAgentString
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

            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsAudio = request.resources?.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) == true
                val host = runCatching { Uri.parse(request.origin.toString()).host.orEmpty() }.getOrDefault("")
                val trustedChatOrigin = host == "chat.google.com" || host.endsWith(".google.com")
                if (!wantsAudio || !trustedChatOrigin) {
                    request.deny()
                    return
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    pendingAudioRequest?.deny()
                    pendingAudioRequest = request
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingAudioRequest == request) pendingAudioRequest = null
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                val host = Uri.parse(url ?: "").host.orEmpty()
                if (host == "accounts.google.com" || host.endsWith(".accounts.google.com")) {
                    sawGoogleSignIn = true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                val host = Uri.parse(url ?: "").host.orEmpty()
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
                    pageStatus.visibility = View.VISIBLE
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        if (savedInstanceState == null) webView.loadUrl(CHAT_URL)
        else webView.restoreState(savedInstanceState)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun compactButton(symbol: String, description: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = symbol
            contentDescription = description
            textSize = 19f
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minWidth = dp(42)
            minimumWidth = dp(42)
            setPadding(dp(3), 0, dp(3), 0)
            setOnClickListener { action() }
        }
    }

    private fun showLayoutMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Wide screen (desktop layout)").setOnMenuItemClickListener {
                setScreenMode(true); true
            }
            menu.add("Small screen (mobile layout)").setOnMenuItemClickListener {
                setScreenMode(false); true
            }
            show()
        }
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Open in browser").setOnMenuItemClickListener { openInBrowser(); true }
            menu.add("Reload Google Chat").setOnMenuItemClickListener { webView.reload(); true }
            show()
        }
    }

    private fun setScreenMode(wide: Boolean) {
        if (wide == desktopMode) return
        if (wide) {
            applyDesktopMode()
        } else {
            desktopMode = false
            switchingToDesktop = false
            webView.settings.apply {
                userAgentString = defaultUserAgent
                useWideViewPort = false
                loadWithOverviewMode = false
            }
            pageStatus.visibility = View.GONE
            webView.reload()
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
        pageStatus.visibility = View.GONE
        webView.loadUrl(CHAT_URL)
    }

    @Deprecated("Deprecated in Android API 33; retained for compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            val request = pendingAudioRequest
            pendingAudioRequest = null
            if (request != null) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    request.deny()
                    Toast.makeText(this, "Microphone permission is needed to record voice messages.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openInBrowser() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CHAT_URL))) }
        catch (_: Exception) {
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
        pendingAudioRequest?.deny()
        pendingAudioRequest = null
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
        private const val REQUEST_MICROPHONE = 43
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
