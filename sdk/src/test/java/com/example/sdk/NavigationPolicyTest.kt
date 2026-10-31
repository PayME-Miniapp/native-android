package com.example.sdk

import com.payme.sdk.ui.miniapp.ExternalUrlPolicy
import com.payme.sdk.ui.miniapp.ExternalUrlPolicy.Target
import com.payme.sdk.ui.miniapp.NavigationAction
import com.payme.sdk.ui.miniapp.OriginPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    private val origin = "http://localhost:4646"

    @Test fun blockedSchemesIgnoreCaseAndSurroundingWhitespace() {
        listOf(
            "javascript:alert(1)", "JavaScript:alert(1)", "  javascript:alert(1)", "\tJAVASCRIPT:alert(1)\n",
            "\u0000javascript:alert(1)", "file:///data/data/x", "FILE:///sdcard/x", " content://contacts/people",
            "Content://x", "data:text/html,<b>x</b>", "DATA:text/html,x"
        ).forEach { assertEquals(it, Target.Blocked, ExternalUrlPolicy.classify(it)) }
    }

    @Test fun malformedOrEmptyInputIsBlocked() {
        listOf(null, "", "   ", "no-scheme", ":nothing", "1abc:x", "java script:alert(1)", "java\tscript:alert(1)", "tel:", "%6Aavascript:alert(1)")
            .forEach { assertEquals(it.toString(), Target.Blocked, ExternalUrlPolicy.classify(it)) }
    }

    @Test fun intentUrlsAreParsedAsIntentsWithNormalizedScheme() {
        val url = "intent://scan/#Intent;scheme=zxing;package=com.example.bank;S.browser_fallback_url=https%3A%2F%2Fbank.example;end"
        assertEquals(Target.IntentUri(url), ExternalUrlPolicy.classify(url))
        assertEquals(Target.IntentUri("intent:#Intent;package=com.example.bank;end"), ExternalUrlPolicy.classify("  INTENT:#Intent;package=com.example.bank;end "))
    }

    @Test fun bankSchemesHttpsTelAndMailtoOpenAsView() {
        listOf("vcbdigibank://x", "momo://app?action=pay", "https://payme.vn/terms", "tel:1900", "mailto:support@payme.vn", "market://details?id=x")
            .forEach { assertEquals(Target.View(it), ExternalUrlPolicy.classify(it)) }
        assertEquals(Target.View("vcbdigibank://x"), ExternalUrlPolicy.classify(" vcbdigibank://x\n"))
    }

    @Test fun parsedIntentDataSchemeAndFallbackAreChecked() {
        listOf("file", "CONTENT", "javascript", "data").forEach { assertTrue(ExternalUrlPolicy.blocksDataScheme(it)) }
        listOf(null, "https", "vcbdigibank").forEach { assertFalse(ExternalUrlPolicy.blocksDataScheme(it)) }
        assertEquals("https://bank.example/app", ExternalUrlPolicy.httpsFallback(" https://bank.example/app "))
        listOf(null, "", "http://bank.example", "javascript:alert(1)", "https://", "https://user@bank.example", "intent://x#Intent;end")
            .forEach { assertNull(it.toString(), ExternalUrlPolicy.httpsFallback(it)) }
    }

    @Test fun trustedDocumentOpensExternalGetsInSubWebViewAndKeepsLocalRoutes() {
        val local = "$origin/mini-app/payment"
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation(local, "$origin/home", false, origin))
        for (current in listOf(local, null, "", "about:blank", "http://localhost:4645/stale-port")) {
            assertEquals(NavigationAction.SUB_WEB_VIEW, OriginPolicy.mainFrameNavigation(current, "https://payme.vn/terms", false, origin))
            assertEquals(NavigationAction.SUB_WEB_VIEW, OriginPolicy.mainFrameNavigation(current, "http://localhost.evil.com:4646/", false, origin))
        }
    }

    @Test fun offOriginDocumentsAndRedirectsStayInMainWebView() {
        // 3DS / Napas / gateway: POST from localhost (never reported), then the ACS document navigates.
        val acs = "https://acs.bank.example/challenge"
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation(acs, "https://acs.bank.example/otp", false, origin))
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation(acs, "https://gateway.payme.vn/return", false, origin))
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation(acs, "$origin/mini-app/payment/result", false, origin))
        // Server redirects while the committed URL is still localhost (POST response 302 to the ACS).
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation("$origin/", "https://acs.bank.example/start", true, origin))
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation(acs, "https://gateway.payme.vn/next", true, origin))
    }

    @Test fun pdfLinksOpenInSubWebViewThroughEncodedViewerUrl() {
        val pdf = "https://cdn.payme.vn/docs/Terms Of Use.PDF?v=1&lang=vi#page=2"
        assertTrue(OriginPolicy.isPdfDocument(pdf.replace(" ", "%20")))
        assertEquals(
            "https://docs.google.com/gview?embedded=true&url=" +
                "https%3A%2F%2Fcdn.payme.vn%2Fdocs%2FTerms%2520Of%2520Use.PDF%3Fv%3D1%26lang%3Dvi%23page%3D2",
            OriginPolicy.subWebViewUrl(pdf.replace(" ", "%20"))
        )
        // Non-PDF URLs are opened unchanged; a ".pdf" outside the path is not a PDF document.
        listOf("https://payme.vn/terms", "https://payme.vn/download?name=a.pdf", "https://payme.vn/a.pdf.html", "$origin/contract.pdf.js")
            .forEach { assertEquals(it, it, OriginPolicy.subWebViewUrl(it)) }
        listOf("file:///sdcard/a.pdf", "content://x/a.pdf", "not a url .pdf").forEach { assertFalse(it, OriginPolicy.isPdfDocument(it)) }
    }

    @Test fun mainFramePdfGoesToSubWebViewFromAnyDocumentButLocalPdfStaysLocal() {
        val pdf = "https://bank.example/statement.pdf"
        for (current in listOf("$origin/home", "https://acs.bank.example/challenge", null)) {
            assertEquals(NavigationAction.SUB_WEB_VIEW, OriginPolicy.mainFrameNavigation(current, pdf, false, origin))
            assertEquals(NavigationAction.SUB_WEB_VIEW, OriginPolicy.mainFrameNavigation(current, pdf, true, origin))
        }
        assertEquals(NavigationAction.LOAD, OriginPolicy.mainFrameNavigation("$origin/home", "$origin/files/contract.pdf", false, origin))
        assertEquals(NavigationAction.LOAD, OriginPolicy.subFrameNavigation(pdf))
    }

    @Test fun mainFrameAppSchemesFollowExternalPolicy() {
        for (current in listOf("$origin/", "https://acs.bank.example/challenge")) {
            assertEquals(NavigationAction.EXTERNAL, OriginPolicy.mainFrameNavigation(current, "vcbdigibank://x", false, origin))
            assertEquals(NavigationAction.EXTERNAL, OriginPolicy.mainFrameNavigation(current, "intent://pay#Intent;package=com.example.bank;end", true, origin))
            assertEquals(NavigationAction.BLOCK, OriginPolicy.mainFrameNavigation(current, "file:///data/data/x", false, origin))
            assertEquals(NavigationAction.BLOCK, OriginPolicy.mainFrameNavigation(current, "Content://x", false, origin))
        }
    }

    @Test fun subframesLoadWebSchemesAndRouteAppSchemesExternally() {
        listOf("https://napas.example/form", "http://localhost:4646/callback", "about:srcdoc", "about:blank", "data:text/html,x", "blob:http://localhost:4646/id")
            .forEach { assertEquals(it, NavigationAction.LOAD, OriginPolicy.subFrameNavigation(it)) }
        listOf("vcbdigibank://x", "intent://pay#Intent;end", "tel:1900")
            .forEach { assertEquals(it, NavigationAction.EXTERNAL, OriginPolicy.subFrameNavigation(it)) }
        listOf("file:///data/data/x", "content://x", "javascript:alert(1)", "", "garbage")
            .forEach { assertEquals(it, NavigationAction.BLOCK, OriginPolicy.subFrameNavigation(it)) }
    }
}
