[Tiếng Việt](README.md) | **English**

# PayME Miniapp Android SDK

An Android library that opens the PayME Miniapp (wallet, payments, deposit/withdraw/transfer, bill services, eKYC) inside a partner app.

**Current version:** `0.8.3` (SDK 1.0.24, mini-app patch 0501). See the [release history](https://github.com/PayME-Miniapp/native-android/releases).

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [App configuration](#app-configuration)
- [Quick start](#quick-start)
- [identityToken](#identitytoken)
- [API reference](#api-reference)
  - [PayMEMiniApp constructor](#paymeminiapp-constructor)
  - [setUpListener](#setuplistener)
  - [openMiniApp](#openminiapp)
  - [OpenMiniApp*Data classes](#openminiappdata-classes)
  - [getBalance](#getbalance)
  - [getAccountInformation](#getaccountinformation)
  - [setLanguage](#setlanguage)
  - [setMode](#setmode)
  - [close](#close)
- [Error codes](#error-codes)
- [Upgrading from 0.7.x](#upgrading-from-07x)
- [Mini-app OTA updates](#mini-app-ota-updates)
- [Changelog and support](#changelog-and-support)

## Requirements

| Item | Requirement |
| --- | --- |
| `minSdk` | 26 (Android 8.0) or higher |
| `compileSdk` | 37 or higher (required by the `androidx.core` 1.19.1 dependency) |
| Android Gradle Plugin | 9.1.0 or higher (same reason) |
| Build JDK | 17 or higher (the SDK targets Java 17 bytecode) |
| Kotlin | 2.1 or higher for Kotlin apps (the SDK is compiled with Kotlin 2.2 and brings `kotlin-stdlib` 2.2.10) |
| Activity for `modal` | A `FragmentActivity` (for example `AppCompatActivity`) |
| Android System WebView | Must support `WEB_MESSAGE_LISTENER`; otherwise the mini-app reports `UNSUPPORTED_WEBVIEW` |

Partner credentials issued by PayME: `appId`, `publicKey`, `privateKey`, and the server-side API that issues the [`identityToken`](#identitytoken).

## Installation

**1. Add the JitPack repository.** All other dependencies resolve from `google()` and `mavenCentral()`.

If your project declares repositories in `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

If your project declares repositories in the root `build.gradle`:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

**2. Add the dependency** to your app module's `build.gradle`:

```groovy
dependencies {
    implementation 'com.github.PayME-Miniapp:native-android:0.8.3'
}
```

> [!WARNING]
> Do not use `0.8.0`: the JitPack artifact for that tag was built from an older commit. `0.8.1` and `0.8.2` have the same API as `0.8.3`; use `0.8.3`.

## App configuration

### Permissions

The SDK declares the permissions below in its library manifest; they are merged into your app at build time. The SDK requests runtime permissions (camera, contacts, storage) itself when the user reaches the feature that needs them; your app does not need to request them in advance.

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Call PayME APIs and download mini-app updates |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Check connectivity |
| `ACCESS_LOCAL_NETWORK` | The SDK runs a local web server (`http://localhost`) from which the WebView loads the mini-app |
| `CAMERA` | eKYC: document capture, face detection and face authentication |
| `NFC` | eKYC: reading the chip of the national ID card |
| `READ_CONTACTS` | Picking a phone number from contacts |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | Picking images and saving QR code images (older Android versions) |

The SDK declares `<uses-feature android:name="android.hardware.camera" />` (required by default), so Google Play hides the app on devices without a camera. To support those devices, override it in your app manifest:

```xml
<uses-feature
    android:name="android.hardware.camera"
    android:required="false"
    tools:replace="android:required" />
```

### Manifest merging

The SDK's `<application>` element sets `android:networkSecurityConfig`, `android:dataExtractionRules`, `android:fullBackupContent`, `android:requestLegacyExternalStorage="true"` and `android:supportsRtl="true"`. If your app sets any of these to a different value, the manifest merger fails; add `tools:replace` for that attribute.

If your app uses its own `networkSecurityConfig`, it must allow cleartext traffic to `localhost`, because the mini-app is served from `http://localhost`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
    <!-- your app's own configuration -->
</network-security-config>
```

### ProGuard / R8

The SDK ships no consumer rules and does not use reflection on its public classes; the JS bridge uses `WebViewCompat.addWebMessageListener`, not `@JavascriptInterface`. No extra rules are currently required. If you hit an R8 issue, report it to PayME with the build log.

### Other notes

- The full-screen mini-app (`screen`) and the eKYC screens are locked to portrait.
- The SDK sends telemetry events, with sensitive data filtered out, to Mixpanel. Take this into account in your Google Play Data safety form.

## Quick start

```kotlin
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppOpenData
import com.payme.sdk.models.OpenMiniAppType

class WalletActivity : AppCompatActivity() {
    private lateinit var payME: PayMEMiniApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        payME = PayMEMiniApp(this, "<APP_ID>", "<PUBLIC_KEY>", "<PRIVATE_KEY>", ENV.SANDBOX, Locale.en)
        payME.setMode("miniapp_sandbox") // only with ENV.SANDBOX
        payME.setUpListener(
            onResponse = { action, data -> Log.d("PayME", "onResponse $action") },
            onError = { action, error -> Log.w("PayME", "onError $action ${error.code}") }
        )
    }

    // Fetch identityToken from the partner server right before opening
    private fun openWallet(identityToken: String) {
        payME.openMiniApp(OpenMiniAppType.screen, OpenMiniAppOpenData(identityToken = identityToken))
    }
}
```

Never log `identityToken`, `publicKey` or `privateKey`, and never put real keys in sample code or a repository.

## identityToken

Since 0.8.0 (SDK 1.0.24), the SDK no longer accepts a phone number. The app passes an `identityToken` instead: a string signed by the PayME backend for the partner's user and bound to the `appId`.

### Integration flow

1. **The partner server** calls the PayME backend `identityToken/init` API (server to server) with the user's details and receives an `identityToken`. Never call this API from the app. Contact PayME for the API documentation.
2. **The partner server** returns the `identityToken` to the app over the partner's own authenticated channel.
3. **The app** passes the `identityToken` to an `OpenMiniApp*Data` class, `getBalance` or `getAccountInformation`.

### Lifetime and errors

- An `identityToken` is valid for 15 minutes and can be reused within that window. Once it expires, the partner server requests a new one.
- Invalid or expired token: the mini-app closes and `onError` receives `code = "401"`. `getBalance` and `getAccountInformation` also return `"401"`.
- Blank token for an action that requires one, or for `getBalance`/`getAccountInformation`: the SDK does not open the mini-app and calls `onError` with `code = "IDENTITY_TOKEN_REQUIRED"`.
- If the system kills the app process while the mini-app is open, the mini-app closes itself on restore (no crash, no callback), because the session and the `identityToken` live in memory only. Open it again with a valid `identityToken`.

### Which actions need it

| Data class | identityToken |
| --- | --- |
| `OpenMiniAppOpenData`, `OpenMiniAppKYCData`, `OpenMiniAppServiceData`, `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData`, `OpenMiniAppPaymentData`, `OpenMiniAppTransferQRData` | Required (`String`) |
| `OpenMiniAppPaymentDirectData` (`PAYMENT` action) | Optional (`String? = null`). With a token the user can pay from the PayME wallet; the PayME backend checks that the token's user matches the transaction. Without one, the user pays as a guest (card, QR) |
| `OpenMiniAppPayMEData` (`PAYME` action) | No parameters. The user enters a phone number and signs in with a PIN |
| `getBalance`, `getAccountInformation` | Required (`String`) |

### Security rules

- Keep the `identityToken` in memory only. Do not log it, persist it (SharedPreferences, `Bundle`/saved state, files, databases) or put it in a URL or deep link.
- The SDK does not store or log the token and drops its reference when the mini-app closes. `toString()` on the `OpenMiniApp*Data` classes prints only the class name and action.

## API reference

Main class: `com.payme.sdk.PayMEMiniApp`. Models and enums live in `com.payme.sdk.models`.

### PayMEMiniApp constructor

```kotlin
PayMEMiniApp(
    context: Context,
    appId: String,
    publicKey: String,
    privateKey: String,
    env: ENV = ENV.PRODUCTION,
    locale: Locale = Locale.vi
)
```

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `context` | `Context` | Yes | Pass an `Activity`. `modal` requires a `FragmentActivity` |
| `appId` | `String` | Yes | Partner ID issued by PayME |
| `publicKey` | `String` | Yes | Public key issued by PayME during partner registration |
| `privateKey` | `String` | Yes | The partner's private key registered with PayME |
| `env` | `ENV` | No, defaults to `ENV.PRODUCTION` | Environment. Partners use `ENV.PRODUCTION` or `ENV.SANDBOX`; `STAGING`, `DEV` and `LOCAL` are for PayME |
| `locale` | `Locale` | No, defaults to `Locale.vi` | Display language: `Locale.vi` or `Locale.en` (`com.payme.sdk.models.Locale`, not `java.util.Locale`) |

### setUpListener

```kotlin
fun setUpListener(
    onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit)? = null,
    onError: ((ActionOpenMiniApp, PayMEError) -> Unit)? = null
)
```

- `onResponse`: called when an action completes successfully, with the action and a `JSONObject` returned by the mini-app or the API.
- `onError`: called on errors or when the user closes the mini-app, with the action and a `PayMEError`.
- Listeners are shared by all `PayMEMiniApp` instances; a later call replaces the earlier one. Passing `null` keeps the existing listener.
- Callbacks run on the main thread, except `IDENTITY_TOKEN_REQUIRED`, `MINIAPP_ALREADY_OPEN` and `MINIAPP_CANNOT_PRESENT`, which are delivered synchronously on the calling thread.

If the `onResponse` data contains `"isCloseMiniApp": true`, the SDK closes the mini-app after the callback. The `JSONObject` content for actions that open the mini-app is defined by the mini-app; the payloads of `getBalance` and `getAccountInformation` are described in their sections.

**`ActionOpenMiniApp`**

| Value | Meaning |
| --- | --- |
| `PAYME` | Opens the full PayME wallet (the user signs in) |
| `OPEN` | Opens the wallet home; activates a wallet if the user has none |
| `PAY` | Pays an order (`OpenMiniAppPaymentData`) |
| `PAYMENT` | Pays a transaction the partner has already created (`OpenMiniAppPaymentDirectData`) |
| `TRANSFER_QR` | Transfers money to a destination account supplied by the partner |
| `SERVICE` | Pays a bill service |
| `DEPOSIT` | Tops up the wallet |
| `WITHDRAW` | Withdraws from the wallet |
| `TRANSFER` | Transfers money |
| `KYC` | Identity verification (eKYC) |
| `GET_BALANCE` | Result of `getBalance` |
| `GET_ACCOUNT_INFO` | Result of `getAccountInformation` |

**`PayMEError`**

| Property | Type | Description |
| --- | --- | --- |
| `type` | `PayMEErrorType` | `MiniApp`, `UserCancel` or `Network` |
| `code` | `String` | Error code, see [Error codes](#error-codes) |
| `description` | `String` | Localized description that can be shown to the user |
| `isCloseMiniApp` | `Boolean` | `true` if the mini-app was closed because of this error |

### openMiniApp

```kotlin
fun openMiniApp(
    openType: OpenMiniAppType = OpenMiniAppType.screen,
    openMiniAppData: OpenMiniAppDataInterface,
)
```

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `openType` | `OpenMiniAppType` | No, defaults to `screen` | `OpenMiniAppType.screen`: full screen in the SDK's own Activity. `OpenMiniAppType.modal`: a bottom sheet over the current Activity; `context` must be a `FragmentActivity` (otherwise nothing opens and `onError` reports `MINIAPP_CANNOT_PRESENT`) |
| `openMiniAppData` | `OpenMiniAppDataInterface` | Yes | One of the `OpenMiniApp*Data` classes below; the class determines the action |

Paying a pre-created transaction without an `identityToken` (use the named argument because `identityToken` comes first):

```kotlin
payME.openMiniApp(
    OpenMiniAppType.modal,
    OpenMiniAppPaymentDirectData(
        paymentDirectData = PaymentDirectData(
            transaction = "<TRANSACTION_CODE>",
            extraData = null,
            isShowResult = true
        )
    )
)
```

Deposit:

```kotlin
payME.openMiniApp(
    OpenMiniAppType.modal,
    OpenMiniAppDepositData(
        identityToken = identityToken,
        additionalData = DepositWithdrawTransferData(
            description = "Top up",
            amount = 100000,
            extraData = mapOf("orderId" to "<ORDER_ID>"),
            isBackToApp = true,
            isShowResult = true
        )
    )
)
```

#### Opening, closing and the back button

- **One mini-app at a time.** Calling `openMiniApp` while a mini-app is open (including from another Activity while a modal is showing) is rejected with `MINIAPP_ALREADY_OPEN`; the open mini-app is left as it is.
- **Reopening from a closing callback works.** While a mini-app is closing (`USER_CANCEL`, `isCloseMiniApp = true` in `onResponse`/`onError`, or after `close()`), calling `openMiniApp` inside the callback or right after `close()` opens a new mini-app.
- **System back button** (both `screen` and `modal`): the mini-app web handles it first (closes its top popup, or blocks back on a payment screen); otherwise it goes back one page inside the mini-app; with no page left it closes the mini-app and `onError` reports `USER_CANCEL`.
- **Dismissing the modal** by tapping outside or with the mini-app's close button reports `USER_CANCEL` with `type = UserCancel`.
- **The app finishing the Activity that hosts the mini-app** (`finish()` on the Activity showing the modal, removing the task...) while it is open: `onError` reports `USER_CANCEL` once. Rotation, dark mode/language changes or the system temporarily destroying the Activity do not close the mini-app.
- **15-second start window:** if a just-opened mini-app still has not appeared after 15 seconds (its Activity/modal was never created, for example the app went to the background right away), the next `openMiniApp` treats that session as lost: it reports `USER_CANCEL` for the old session and opens the new one instead of returning `MINIAPP_ALREADY_OPEN`.

### OpenMiniApp*Data classes

| Class | Action | Parameters |
| --- | --- | --- |
| `OpenMiniAppPayMEData()` | `PAYME` | None |
| `OpenMiniAppOpenData` | `OPEN` | `identityToken: String` |
| `OpenMiniAppKYCData` | `KYC` | `identityToken: String` |
| `OpenMiniAppPaymentData` | `PAY` | `identityToken: String`, `paymentData: PaymentData` |
| `OpenMiniAppPaymentDirectData` | `PAYMENT` | `identityToken: String? = null`, `paymentDirectData: PaymentDirectData` |
| `OpenMiniAppServiceData` | `SERVICE` | `identityToken: String`, `additionalData: ServiceData` |
| `OpenMiniAppDepositData` | `DEPOSIT` | `identityToken: String`, `additionalData: DepositWithdrawTransferData` |
| `OpenMiniAppWithdrawData` | `WITHDRAW` | `identityToken: String`, `additionalData: DepositWithdrawTransferData` |
| `OpenMiniAppTransferData` | `TRANSFER` | `identityToken: String`, `additionalData: DepositWithdrawTransferData` |
| `OpenMiniAppTransferQRData` | `TRANSFER_QR` | `identityToken: String`, `transferQRData: TransferQRData` |

The nested data classes below have no default values: every parameter must be passed. Nullable (`?`) parameters accept `null`, in which case the mini-app uses its own default. `extraData` is free-form partner data (`String`, `Int`, `Boolean` or nested `Map` values; other types are converted to strings).

**`PaymentData`** (for `OpenMiniAppPaymentData`)

| Property | Type | Description |
| --- | --- | --- |
| `transactionId` | `String` | Partner transaction ID |
| `amount` | `Int` | Amount (VND) |
| `note` | `String?` | Transaction note |
| `ipnUrl` | `String?` | URL the PayME server calls (IPN) when the transaction status changes |
| `extraData` | `Map<String, Any>?` | Extra data returned to the partner on successful payment |
| `isShowResult` | `Boolean?` | Whether to show the PayME result screen |

**`PaymentDirectData`** (for `OpenMiniAppPaymentDirectData`)

| Property | Type | Description |
| --- | --- | --- |
| `transaction` | `String` | Code of the transaction already created on PayME |
| `extraData` | `Map<String, Any>?` | Extra data returned to the partner on successful payment |
| `isShowResult` | `Boolean?` | Whether to show the PayME result screen |

**`ServiceData`** (for `OpenMiniAppServiceData`)

| Property | Type | Description |
| --- | --- | --- |
| `service` | `String` | Service code provided by PayME, for example `POWE` (electricity) |
| `extraData` | `Map<String, Any>?` | Extra data returned to the partner on successful payment |
| `isBackToApp` | `Boolean?` | Return to the partner app when done |
| `isShowResult` | `Boolean?` | Whether to show the PayME result screen |

**`DepositWithdrawTransferData`** (for `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData`)

| Property | Type | Description |
| --- | --- | --- |
| `description` | `String?` | Transaction description |
| `amount` | `Int?` | Amount (VND) |
| `extraData` | `Map<String, Any>?` | Extra data returned to the partner on success |
| `isBackToApp` | `Boolean?` | Return to the partner app when done |
| `isShowResult` | `Boolean?` | Whether to show the PayME result screen |

**`TransferQRData`** (for `OpenMiniAppTransferQRData`)

| Property | Type | Description |
| --- | --- | --- |
| `amount` | `Int` | Amount (VND); pass `0` to let the user enter it |
| `bankNumber` | `String` | Destination card or bank account number |
| `swiftCode` | `String` | SWIFT code of the destination bank |
| `cardHolder` | `String` | Destination account holder name |
| `note` | `String?` | Transaction note |
| `partnerTransaction` | `String?` | Partner transaction ID |
| `extraData` | `Map<String, Any>?` | Extra data returned to the partner on success |
| `isShowResult` | `Boolean?` | Whether to show the PayME result screen |

### getBalance

```kotlin
fun getBalance(identityToken: String)
```

Fetches the user's wallet balance without opening any UI. The result arrives in `onResponse` with action `GET_BALANCE`:

```json
{ "linked": true, "balance": 150000, "message": "<message from PayME>" }
```

If the `identityToken` is valid but no wallet session is available (for example, the user has not linked a wallet to the partner yet):

```json
{ "linked": false, "message": "Account is not linked" }
```

Errors arrive in `onError` with action `GET_BALANCE`: `IDENTITY_TOKEN_REQUIRED`, `401` or a network error.

### getAccountInformation

```kotlin
fun getAccountInformation(identityToken: String)
```

Fetches the user's PayME account information. The result arrives in `onResponse` with action `GET_ACCOUNT_INFO`: the `accountInfo` object returned by the PayME backend. It always contains `accountId` (integer); other fields are defined by the PayME backend.

```json
{ "accountId": 123456, "...": "..." }
```

Errors arrive in `onError` with action `GET_ACCOUNT_INFO`: `IDENTITY_TOKEN_REQUIRED`, `401`, `OTHER` (no account information) or a network error.

### setLanguage

```kotlin
fun setLanguage(lang: Locale)
```

Changes the mini-app language (`Locale.vi` or `Locale.en`) for subsequent openings. The language applies only to the SDK's own UI and strings: the SDK does not change the default `java.util.Locale` or the app's resources, and this method does not call `onChangeLocale` (that callback fires only when the user changes the language inside the mini-app).

### setMode

```kotlin
fun setMode(mode: String)
```

Selects the mini-app OTA update channel (see [OTA updates](#mini-app-ota-updates)). Defaults to `"miniapp_product"`. With `ENV.SANDBOX`, use `"miniapp_sandbox"` as the demo app does. Other values (`miniapp_staging`, `pm_*`, `bank`) are for PayME.

### close

```kotlin
fun close()
```

Closes the open mini-app (both `screen` and `modal`). Closing it with `close()` does not call `onError`. `close()` never throws (even while the Activity hosting the modal is temporarily destroyed by the system) and does nothing when no mini-app is open; you can call `openMiniApp` right after it.

> `setChangeEnvFunction`, `setChangeLocaleFunction` and `setOneSignalFunctions` are for the PayME wallet app only; partners do not need them.

## Error codes

Handle errors by `code`, not by `type`.

| `code` | `type` | When | Suggested handling |
| --- | --- | --- | --- |
| `IDENTITY_TOKEN_REQUIRED` | `MiniApp` | Blank `identityToken` for a required action, `getBalance` or `getAccountInformation`. The mini-app is not opened | Get an `identityToken` from your server before calling |
| `401` | `MiniApp` | Invalid or expired `identityToken` (or the session expired while in use). The mini-app closes (`isCloseMiniApp = true`); `getBalance`/`getAccountInformation` return the same code | Get a new `identityToken` on the server and open again |
| `USER_CANCEL` | `UserCancel` | The user closed the mini-app (in-app close button, system back with no page left, tapping outside the modal), or the app finished the Activity hosting the mini-app. Reported once per close | Not an error; return to the app flow |
| `MINIAPP_ALREADY_OPEN` | `MiniApp` | `openMiniApp` was called while a mini-app is open (including from another Activity). The new one does not open; the open one is left as it is | Wait for the current mini-app to close, or call `close()` and open again |
| `MINIAPP_CANNOT_PRESENT` | `MiniApp` | The mini-app cannot be shown: `modal` with a `context` that is not a `FragmentActivity`, or the Activity/modal could not be opened (for example the Activity has already saved its state) | Pass the visible `FragmentActivity`; retry when the Activity is in the foreground |
| `CONNECTION_LOST` | `Network` | No connection, device registration failed, or the response could not be decrypted | Ask the user to check the network and retry |
| `TIMED_OUT` | `Network` | Server timeout | Retry |
| `SERVER_ERROR` | `Network` | The server returned an HTTP error | Retry later |
| `DECODE_FAILED` | `Network` | The response is not valid JSON | Retry; report to PayME if it persists |
| `OTHER` | `Network` or `MiniApp` | `Network`: other network error. `MiniApp`: `getAccountInformation` found no account information | Show `description` |
| `UNSUPPORTED_WEBVIEW` | `MiniApp` | Android System WebView is too old. The mini-app closes | Ask the user to update Android System WebView |
| `RESOURCE_NOT_FOUND` | `MiniApp` | A mini-app resource was not found (404). The mini-app closes | Report to PayME |
| `UNZIP_FAILED` | `MiniApp` | The mini-app package could not be extracted. The mini-app closes | Retry; report to PayME if it persists |
| `INSUFFICIENT_STORAGE` | `MiniApp` | Not enough storage for the mini-app package. The mini-app closes | Ask the user to free up storage |
| Any other code | `MiniApp` | Business error reported by the mini-app | Show `description`; check `isCloseMiniApp` |

`PayMENetworkErrorCode` also declares `ENCODE_FAILED` and `NO_RESPONSE`, but the current version never returns them.

## Upgrading from 0.7.x

0.8.x (SDK 1.0.24) is not backward compatible with 0.7.x (SDK 1.0.23): the `phone` parameter is replaced by `identityToken`.

| 0.7.x | 0.8.x |
| --- | --- |
| `implementation '...:native-android:0.7.x'` | `implementation 'com.github.PayME-Miniapp:native-android:0.8.3'` |
| `OpenMiniAppOpenData(phone = ...)` and likewise for `KYC`, `Service`, `Deposit`, `Withdraw`, `Transfer`, `Payment`, `TransferQR` | `OpenMiniAppOpenData(identityToken = ...)` and likewise |
| `OpenMiniAppPaymentDirectData(phone = ..., paymentDirectData = ...)` | `OpenMiniAppPaymentDirectData(identityToken = ..., paymentDirectData = ...)`, or omit `identityToken` (guest) |
| `OpenMiniAppPayMEData()` | Unchanged |
| `getBalance(phone)` | `getBalance(identityToken)` |
| `getAccountInformation(phone)` | `getAccountInformation(identityToken)` |
| Invalid phone number: `getBalance` returned `linked: false` | Invalid or expired `identityToken`: `onError` with code `401` |
| Not available | `onError` with code `IDENTITY_TOKEN_REQUIRED` for a blank token |

What partners need to do: add a server-side call that obtains the `identityToken`, replace every `phone = ...` with `identityToken = ...`, and handle the `401` and `IDENTITY_TOKEN_REQUIRED` codes. The 0.7.x line keeps using `phone` and receives bug fixes only.

## Mini-app OTA updates

The SDK bundles a mini-app build (0.8.3: patch 0501) and can receive web patches over the air without an app release. Partners only need to pick the right [`setMode`](#setmode).

- The SDK reads the manifest at `https://static.payme.vn/frontend/miniapp-store/PayMEMiniAppVersion.json` and uses the entry for the current mode.
- A patch is installed only if its manifest entry carries a valid PayME ECDSA P-256 signature, the download URL is on `https://static.payme.vn`, the size is at most 128 MB, and the downloaded file matches `size` and `sha256`. Otherwise the SDK keeps the current build.
- The SDK never installs a patch lower than the one already installed for the same mode.

<details>
<summary>For PayME: manifest entry format (SDK 1.0.24 and later)</summary>

Each mode in the manifest (`miniapp_*`, `pm_*`, `bank`, …) looks like:

```json
{
  "patch": 501,
  "latestMandatoryPatch": 501,
  "url": "https://static.payme.vn/.../p0501.zip",
  "sha256": "<64 lowercase hex characters: SHA-256 of the zip>",
  "size": 4287341,
  "sig": "<base64 DER ECDSA P-256/SHA-256 signature>"
}
```

- Signed payload (UTF-8, no trailing newline): `payme-ota-v1|<sdkVersion>|<mode>|<patch>|<latestMandatoryPatch>|<url>|<sha256>|<size>`. Integers in decimal.
- `patch: 0` with `latestMandatoryPatch: 0` is the kill switch: revert to the bundled build; no `sig` needed.
- An entry with `patch > 0` is ignored if: the signature is missing or invalid; the URL is not `https://static.payme.vn`; `sha256` or `size` is malformed; `size` exceeds 128 MB; `latestMandatoryPatch < 1` or `latestMandatoryPatch > patch`.
- Each SDK version needs its own signed entry because `sdkVersion` is part of the signed payload. Sign with the script in the mini-app web repository:

```sh
node script/sign-ota.cjs --zip sdkWebapp3-main.zip --sdk-version 1.0.24 --mode miniapp_product \
  --patch 501 --latest-mandatory 501 --url https://static.payme.vn/.../p0501.zip --key <signing-key.pem>
```

See [RELEASE.md](RELEASE.md) for the release process.

</details>

## Changelog and support

- Release notes: [GitHub Releases](https://github.com/PayME-Miniapp/native-android/releases).
- Demo app: the [`app`](app) module (`app/src/main/java/com/example/app/MainActivity.kt`). Demo credentials go in `local.properties` (see `local.properties.example`).
- Issues: open a [GitHub issue](https://github.com/PayME-Miniapp/native-android/issues) (never attach keys, an `identityToken` or user data), or contact your PayME integration contact.
