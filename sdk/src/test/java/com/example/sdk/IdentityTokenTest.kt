package com.example.sdk

import com.google.gson.Gson
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.DepositWithdrawTransferData
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppDepositData
import com.payme.sdk.models.OpenMiniAppKYCData
import com.payme.sdk.models.OpenMiniAppOpenData
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppPaymentData
import com.payme.sdk.models.OpenMiniAppPaymentDirectData
import com.payme.sdk.models.OpenMiniAppServiceData
import com.payme.sdk.models.OpenMiniAppTransferData
import com.payme.sdk.models.OpenMiniAppTransferQRData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.OpenMiniAppWithdrawData
import com.payme.sdk.models.PaymentData
import com.payme.sdk.models.PaymentDirectData
import com.payme.sdk.models.ServiceData
import com.payme.sdk.models.TransferQRData
import com.payme.sdk.models.getIdentityTokenFromOpenMiniAppData
import com.payme.sdk.presentation.AccountPresentation
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.ui.MiniAppFragment
import com.payme.sdk.ui.miniapp.JsLogTelemetry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IdentityTokenTest {
    private val token = "dummy-identity-token"
    private val config = PayMEConfig(
        appId = "dummy-app",
        publicKey = "dummy-public",
        privateKey = "dummy-private",
        env = ENV.SANDBOX,
        locale = Locale.vi
    )
    private val walletData = DepositWithdrawTransferData("dummy", 1000, null, true, true)

    private fun withToken(): List<OpenMiniAppDataInterface> = listOf(
        OpenMiniAppOpenData(identityToken = token),
        OpenMiniAppKYCData(identityToken = token),
        OpenMiniAppServiceData(identityToken = token, additionalData = ServiceData("POWE", null, true, true)),
        OpenMiniAppDepositData(identityToken = token, additionalData = walletData),
        OpenMiniAppWithdrawData(identityToken = token, additionalData = walletData),
        OpenMiniAppTransferData(identityToken = token, additionalData = walletData),
        OpenMiniAppPaymentData(
            identityToken = token,
            paymentData = PaymentData("dummy-tx", 1000, null, null, null, true)
        ),
        OpenMiniAppPaymentDirectData(identityToken = token, paymentDirectData = PaymentDirectData("dummy-tx", null, true)),
        OpenMiniAppTransferQRData(
            identityToken = token,
            transferQRData = TransferQRData(1000, "dummy-bank", "dummy-swift", "dummy-holder", null, null, null, true)
        )
    )

    @Before
    fun setUp() {
        removeAllSessions()
        PayMERuntime.initialize(config)
        MiniAppFragment.clearOpenMiniAppData()
    }

    @After
    fun tearDown() {
        removeAllSessions()
        MiniAppFragment.clearOpenMiniAppData()
    }

    @Test
    fun bootstrapPayloadCarriesIdentityTokenAndNeverPhone() {
        withToken().forEach { data ->
            val json = data.toJsonData(config)
            assertEquals("${data.action}", token, json.get("identityToken").asString)
            assertFalse("${data.action} still sends phone", json.has("phone"))
        }
    }

    @Test
    fun paymentIdentityTokenIsOptionalAndPayMEHasNone() {
        val guest = OpenMiniAppPaymentDirectData(paymentDirectData = PaymentDirectData("dummy-tx", null, true))
        assertNull(guest.identityToken)
        assertFalse(guest.toJsonData(config).has("identityToken"))
        assertFalse(OpenMiniAppPaymentDirectData("", PaymentDirectData("dummy-tx", null, true)).toJsonData(config).has("identityToken"))

        val payme = OpenMiniAppPayMEData().toJsonData(config)
        assertFalse(payme.has("identityToken"))
        assertFalse(payme.has("phone"))
        assertTrue(OpenMiniAppPayMEData::class.java.declaredConstructors.all { it.parameterCount == 0 })
    }

    @Test
    fun identityTokenAccessorSkipsMissingTokens() {
        withToken().forEach { assertEquals(token, getIdentityTokenFromOpenMiniAppData(it)) }
        assertNull(getIdentityTokenFromOpenMiniAppData(OpenMiniAppPayMEData()))
        assertNull(getIdentityTokenFromOpenMiniAppData(OpenMiniAppPaymentDirectData(null, PaymentDirectData("t", null, true))))
        assertNull(getIdentityTokenFromOpenMiniAppData(OpenMiniAppKYCData(identityToken = "")))
    }

    @Test
    fun getAccountInfoRequestSendsIdentityTokenInsteadOfPhone() {
        val params = AccountPresentation.accountInfoParams(token, "dummy-client")
        assertEquals(mapOf("identityToken" to token, "clientId" to "dummy-client"), params)
    }

    @Test
    fun toStringNeverPrintsIdentityToken() {
        withToken().forEach { data ->
            val printed = data.toString()
            assertFalse("$printed leaks identityToken", printed.contains(token))
            assertTrue(printed.contains("${data.action}"))
        }
        assertFalse(withToken().toString().contains(token))
    }

    @Test
    fun jsLogTelemetryDropsIdentityToken() {
        val log = mapOf("type" to "OPEN-MINI-APP", "action" to "OPEN", "identityToken" to token, "code" to 1)
        val properties = JsLogTelemetry.sanitize(Gson().toJson(Gson().toJson(log)))
        assertEquals(mapOf("type" to "OPEN-MINI-APP", "action" to "OPEN", "code" to 1L), properties)
        assertNull(JsLogTelemetry.sanitize(Gson().toJson("OPEN-MINI-APP - {\"identityToken\":\"$token\"}"))["identityToken"])
        assertFalse(properties.values.any { "$it".contains(token) })
    }

    @Test
    fun fragmentNeverWritesOpenMiniAppDataIntoTheSavedStateBundle() {
        // The system persists the saved-state Bundle; the session is restored from EXTRA_SESSION_ID instead.
        val overrides = MiniAppFragment::class.java.declaredMethods.filter { it.name == "onSaveInstanceState" }
        assertTrue("MiniAppFragment must not override onSaveInstanceState", overrides.isEmpty())
    }

    @Test
    fun viewModelKeepsOpenMiniAppDataOnlyForALiveSessionBeingRecreated() {
        val data = OpenMiniAppOpenData(identityToken = token)
        assertSame(data, MiniAppFragment.retainedOpenMiniAppData(true, true, data))
        assertNull(MiniAppFragment.retainedOpenMiniAppData(true, false, data))
        assertNull(MiniAppFragment.retainedOpenMiniAppData(false, true, data))
        assertNull(MiniAppFragment.retainedOpenMiniAppData(false, false, data))
    }

    @Test
    fun closingTheSessionDropsEveryIdentityTokenReference() {
        val data = OpenMiniAppOpenData(identityToken = token)
        val session = PayMERuntime.createSession(OpenMiniAppType.screen, data)
        MiniAppFragment.setOpenMiniAppData(data)
        assertSame(data, PayMERuntime.getSession(session.id)?.openMiniAppData)

        PayMEMiniApp.isOpen = false
        PayMERuntime.closeSession(session.id)
        PayMERuntime.removeSession(session.id)
        MiniAppFragment.clearOpenMiniAppData()

        assertNull(PayMERuntime.getSession(session.id))
        assertNull(PayMERuntime.activeSession())
        assertFalse(MiniAppFragment.isOpenMiniAppInit())
    }

    private fun removeAllSessions() {
        while (true) {
            val activeSession = PayMERuntime.activeSession() ?: return
            PayMERuntime.removeSession(activeSession.id)
        }
    }
}
