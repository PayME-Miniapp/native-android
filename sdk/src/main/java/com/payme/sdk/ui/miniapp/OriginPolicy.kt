package com.payme.sdk.ui.miniapp

import java.net.URI
import java.net.URLEncoder

internal enum class NavigationAction { LOAD, SUB_WEB_VIEW, EXTERNAL, BLOCK }

internal object OriginPolicy {
    fun isLocal(url: String, origin: String?): Boolean = try {
        val expected = URI(origin ?: "")
        val actual = URI(url)
        expected.scheme == "http" && expected.host == "localhost" && expected.port in 1..65535 &&
            actual.scheme == expected.scheme && actual.host == expected.host &&
            actual.port == expected.port && actual.rawUserInfo == null
    } catch (_: Exception) { false }

    fun mainPageUrl(requestedUrl: String, origin: String): String =
        if (isLocal(requestedUrl, origin)) requestedUrl else "$origin/"

    fun acceptsBridge(sourceOrigin: String, origin: String?, isMainFrame: Boolean): Boolean =
        isMainFrame && isLocal(sourceOrigin, origin)

    /**
     * Main-frame navigation reported by shouldOverrideUrlLoading (never called for POST).
     * GETs leaving the trusted document open in SubWebView. Redirects, and navigations started
     * by an off-origin document (3DS ACS, Napas, gateways reached by POST), stay in the main
     * WebView so the flow can return to localhost. The bridge is origin-locked either way.
     * PDFs always go to SubWebView (through [subWebViewUrl]): WebView cannot render them.
     */
    fun mainFrameNavigation(currentUrl: String?, targetUrl: String, isRedirect: Boolean, origin: String?): NavigationAction {
        if (isLocal(targetUrl, origin)) return NavigationAction.LOAD
        return when (ExternalUrlPolicy.schemeOf(targetUrl)) {
            "http", "https" -> when {
                isPdfDocument(targetUrl) -> NavigationAction.SUB_WEB_VIEW
                isRedirect || isOffOriginDocument(currentUrl, origin) -> NavigationAction.LOAD
                else -> NavigationAction.SUB_WEB_VIEW
            }
            else -> externalAction(targetUrl)
        }
    }

    /** URL to open in SubWebView: PDFs go through the Google Docs viewer, as in baseline. */
    fun subWebViewUrl(url: String): String =
        if (isPdfDocument(url)) PDF_VIEWER_PREFIX + URLEncoder.encode(url.trim(), "UTF-8") else url

    fun isPdfDocument(url: String): Boolean = try {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && uri.path.orEmpty().endsWith(".pdf", ignoreCase = true)
    } catch (_: Exception) { false }

    private const val PDF_VIEWER_PREFIX = "https://docs.google.com/gview?embedded=true&url="

    /** Web schemes load inside the frame; app schemes follow the external-link policy. */
    fun subFrameNavigation(targetUrl: String): NavigationAction =
        when (ExternalUrlPolicy.schemeOf(targetUrl)) {
            "http", "https", "about", "data", "blob" -> NavigationAction.LOAD
            else -> externalAction(targetUrl)
        }

    private fun externalAction(url: String): NavigationAction =
        if (ExternalUrlPolicy.classify(url) == ExternalUrlPolicy.Target.Blocked) NavigationAction.BLOCK else NavigationAction.EXTERNAL

    private fun isOffOriginDocument(currentUrl: String?, origin: String?): Boolean {
        if (currentUrl.isNullOrBlank() || isLocal(currentUrl, origin)) return false
        val uri = try { URI(currentUrl.trim()) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty() &&
            !uri.host.equals("localhost", ignoreCase = true)
    }
}

/** Pure external-link policy. Android Intent building lives in [ExternalUrlLauncher]. */
internal object ExternalUrlPolicy {
    sealed class Target {
        object Blocked : Target()
        data class IntentUri(val uri: String) : Target()
        data class View(val uri: String) : Target()
    }

    private val SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")
    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data")

    fun schemeOf(raw: String?): String? {
        val url = normalize(raw) ?: return null
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        val scheme = url.substring(0, colon)
        return if (SCHEME.matches(scheme)) scheme.lowercase() else null
    }

    fun classify(raw: String?): Target {
        val url = normalize(raw) ?: return Target.Blocked
        val scheme = schemeOf(url) ?: return Target.Blocked
        if (scheme in BLOCKED_SCHEMES || url.length == scheme.length + 1) return Target.Blocked
        return if (scheme == "intent") Target.IntentUri("intent:" + url.substring(scheme.length + 1)) else Target.View(url)
    }

    /** Applied to the data scheme of a parsed intent: URL (null means the intent has no data). */
    fun blocksDataScheme(scheme: String?): Boolean = scheme != null && scheme.lowercase() in BLOCKED_SCHEMES

    fun httpsFallback(raw: String?): String? {
        val url = normalize(raw) ?: return null
        val uri = try { URI(url) } catch (_: Exception) { return null }
        return url.takeIf { uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null }
    }

    private fun normalize(raw: String?): String? =
        raw?.trim { it <= ' ' || it.isWhitespace() }?.takeIf { it.isNotEmpty() }
}
