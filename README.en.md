[Tiếng Việt](README.md) | **English**

# PayME Miniapp Android SDK

An Android library that opens the PayME Miniapp (wallet, payments, deposit, withdraw, transfer, services, eKYC) inside your app.

Current version: **0.8.6** (SDK 1.0.24).

## Contents

1. [Requirements](#requirements)
2. [Installation](#installation)
3. [App configuration](#app-configuration)
4. [Quick start](#quick-start)
5. [identityToken](#identitytoken)
6. [API](#api)
7. [Error codes](#error-codes)
8. [Upgrading from 0.7.x](#upgrading-from-07x)
9. [Support](#support)

## Requirements

- `minSdk` 26, `compileSdk` 37 or higher.
- Android Gradle Plugin 9.1.0 or higher, built with JDK 17 or higher.
- Kotlin 2.1 or higher (if your app uses Kotlin).
- Opening as `modal` requires the Activity to be a `FragmentActivity` (for example `AppCompatActivity`).
- Android System WebView with `WEB_MESSAGE_LISTENER` support. On a WebView that is too old, the Miniapp returns `UNSUPPORTED_WEBVIEW`.
- The `appId`, `publicKey`, `privateKey` key set issued by PayME.
- Your server can call PayME's `identityToken` API.

## Installation

1. **JitPack.** Add `maven { url = uri("https://jitpack.io") }` to your repositories, next to `google()` and `mavenCentral()` (in `settings.gradle` or in `allprojects` of the root `build.gradle`):

   ```groovy
   dependencyResolutionManagement {
       repositories {
           google()
           mavenCentral()
           maven { url = uri("https://jitpack.io") }
       }
   }
   ```

2. **Dependency** in the app module's `build.gradle`:

   ```groovy
   dependencies {
       implementation 'com.github.PayME-Miniapp:native-android:0.8.6'
   }
   ```

3. **ProGuard / R8.** No extra rules are needed. If you hit an error with R8 enabled, send the build log to PayME.

## App configuration

No permissions to declare. The required permissions are already in the SDK, and the Miniapp asks for them when needed.

- The SDK declares `android.hardware.camera` as required, so Google Play hides your app on devices without a camera. To support those devices, add to your app manifest:

  ```xml
  <uses-feature
      android:name="android.hardware.camera"
      android:required="false"
      tools:replace="android:required" />
  ```

- The SDK sets `networkSecurityConfig` that only allows cleartext to `localhost` (the Miniapp runs on `http://localhost`), so `android:usesCleartextTraffic` in your app no longer applies. If your app has its own `networkSecurityConfig`, add `tools:replace="android:networkSecurityConfig"` to `<application>` and allow cleartext to `localhost` in your file:

  ```xml
  <domain-config cleartextTrafficPermitted="true">
      <domain>localhost</domain>
  </domain-config>
  ```

- React Native: the debug build on an emulator loads the JS bundle from `10.0.2.2`. Add a debug-only `android/app/src/debug/res/xml/payme_miniapp_network_security_config.xml` that also allows cleartext to `10.0.2.2`.
- The SDK sends telemetry events (sensitive data filtered) to Mixpanel. Keep this in mind for the Data safety form on Google Play.

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
        payME = PayMEMiniApp(this, "<APP_ID>", "<PUBLIC_KEY>", "<PRIVATE_KEY>", ENV.SANDBOX, Locale.vi)
        payME.setMode("miniapp_sandbox") // only with ENV.SANDBOX
        payME.setUpListener(
            onResponse = { action, data -> Log.d("PayME", "onResponse $action") },
            onError = { action, error -> Log.w("PayME", "onError $action ${error.code}") }
        )
    }

    // identityToken is fetched from PayME by your server
    private fun openWallet(identityToken: String) {
        payME.openMiniApp(OpenMiniAppType.screen, OpenMiniAppOpenData(identityToken = identityToken))
    }
}
```

> Do not put real keys in public source code. Do not log `identityToken`, `publicKey` or `privateKey`.

## identityToken

`identityToken` is a string PayME issues for each of your users. Your app passes it every time it opens the Miniapp.

1. Your app calls your server.
2. Your server calls PayME's `token-init` API (`POST /be/ewallet/sdk/token-init`) with the user's information (contact PayME for the API docs). Do not call this API from the app.
3. Your app passes the returned `identityToken` to `OpenMiniApp*Data`, `getBalance` or `getAccountInformation`.

| Data class / function | `identityToken` |
| --- | --- |
| `OpenMiniAppPaymentDirectData` (`PAYMENT`) | Optional. With it, the user can pay with the PayME wallet; without it, the user pays as a guest (card, QR) |
| All other classes, `getBalance`, `getAccountInformation` | Required |

- The string is valid for 15 minutes and can be reused during that time.
- Invalid or expired: `onError` returns `401` (the Miniapp closes if open). Get a new string and call again.
- Keep the string in memory only: do not store it (SharedPreferences, saved state, files), do not log it.

## API

Main class `com.payme.sdk.PayMEMiniApp`; models and enums are in `com.payme.sdk.models`. All functions return `Unit`. Results and errors arrive through `onResponse` and `onError`.

### Initialization

```kotlin
PayMEMiniApp(context, appId, publicKey, privateKey, env = ENV.PRODUCTION, locale = Locale.vi)
```

| Parameter | Description |
| --- | --- |
| `context` | Pass an `Activity`. Opening as `modal` requires a `FragmentActivity` |
| `appId` | App ID issued by PayME |
| `publicKey` | PayME's public key |
| `privateKey` | Your app's private key |
| `env` | `ENV.PRODUCTION` (default) or `ENV.SANDBOX` |
| `locale` | `Locale.vi` (default) or `Locale.en` (`com.payme.sdk.models.Locale`, not `java.util.Locale`) |

With `ENV.SANDBOX`, also call `setMode("miniapp_sandbox")`. Production does not need it.

### openMiniApp

```kotlin
payME.openMiniApp(openType, openMiniAppData)
```

`openType`: `OpenMiniAppType.screen` (full screen, default) or `OpenMiniAppType.modal` (bottom sheet over the current Activity). The `openMiniAppData` class decides the action:

| Class | Action | Purpose | Data |
| --- | --- | --- | --- |
| `OpenMiniAppOpenData` | `OPEN` | Wallet home (opens activation if not yet activated) | None |
| `OpenMiniAppKYCData` | `KYC` | Identity verification (eKYC) | None |
| `OpenMiniAppPaymentData` | `PAY` | Pay for your order | `paymentData`: [`PaymentData`](#paymentdata-pay) |
| `OpenMiniAppPaymentDirectData` | `PAYMENT` | Pay a transaction created via the PayME API | `paymentDirectData`: [`PaymentDirectData`](#paymentdirectdata-payment) |
| `OpenMiniAppTransferQRData` | `TRANSFER_QR` | Transfer to a bank account | `transferQRData`: [`TransferQRData`](#transferqrdata-transfer_qr) |
| `OpenMiniAppServiceData` | `SERVICE` | Pay for services (electricity, water, cards…) | `additionalData`: [`ServiceData`](#servicedata-service) |
| `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData` | `DEPOSIT`, `WITHDRAW`, `TRANSFER` | Deposit, withdraw, transfer | `additionalData`: [`DepositWithdrawTransferData`](#depositwithdrawtransferdata-deposit-withdraw-transfer) |

Every class takes `identityToken` as its first parameter.

```kotlin
payME.openMiniApp(
    OpenMiniAppType.screen,
    OpenMiniAppPaymentData(
        identityToken = identityToken,
        paymentData = PaymentData(
            transactionId = "ORDER_123", amount = 10000, note = "Order 123",
            ipnUrl = null, extraData = null, isShowResult = true
        )
    )
)

// Guest: omit identityToken, use named parameters
payME.openMiniApp(
    OpenMiniAppType.modal,
    OpenMiniAppPaymentDirectData(
        paymentDirectData = PaymentDirectData(transaction = "<TRANSACTION>", extraData = null, isShowResult = true)
    )
)

payME.openMiniApp(
    OpenMiniAppType.modal,
    OpenMiniAppDepositData(
        identityToken = identityToken,
        additionalData = DepositWithdrawTransferData(
            description = "Deposit", amount = 50000, extraData = null, isBackToApp = true, isShowResult = true
        )
    )
)
```

The data classes below have no default values: pass every parameter. For a nullable (`?`) parameter, passing `null` makes the Miniapp use its own default. `amount` is an integer in VND.

Every data class also has two fields: `extraData: Map<String, Any>?` (your own data, returned when the transaction succeeds) and `isShowResult: Boolean?` (show PayME's result screen).

#### PaymentData (PAY)

| Field | Type | Description |
| --- | --- | --- |
| `transactionId` | `String` | Your transaction ID |
| `amount` | `Int` | Amount |
| `note` | `String?` | Note |
| `ipnUrl` | `String?` | URL that receives transaction status notifications |

#### PaymentDirectData (PAYMENT)

| Field | Type | Description |
| --- | --- | --- |
| `transaction` | `String` | Transaction code PayME returned when you created the transaction |

#### TransferQRData (TRANSFER_QR)

| Field | Type | Description |
| --- | --- | --- |
| `amount` | `Int` | Amount (`0` lets the user enter it) |
| `bankNumber` | `String` | Receiving account or card number |
| `swiftCode` | `String` | SWIFT code of the receiving bank |
| `cardHolder` | `String` | Account holder name |
| `note` | `String?` | Note |
| `partnerTransaction` | `String?` | Your transaction ID |

#### ServiceData (SERVICE)

| Field | Type | Description |
| --- | --- | --- |
| `service` | `String` | Service code from PayME, for example `POWE` (electricity) |
| `isBackToApp` | `Boolean?` | Return to your app when done |

#### DepositWithdrawTransferData (DEPOSIT, WITHDRAW, TRANSFER)

| Field | Type | Description |
| --- | --- | --- |
| `description` | `String?` | Transaction description |
| `amount` | `Int?` | Prefilled amount |
| `isBackToApp` | `Boolean?` | Return to your app when done |

### getBalance, getAccountInformation

```kotlin
payME.getBalance(identityToken)
payME.getAccountInformation(identityToken)
```

Results arrive through `onResponse`:

- `GET_BALANCE`: `linked` (whether the wallet is linked), `balance` (present when `linked` is `true`), `message`.
- `GET_ACCOUNT_INFO`: wallet account information, always includes `accountId`.

### setLanguage, close

- `setLanguage(Locale.vi | Locale.en)`: changes the Miniapp language for later opens.
- `close()`: closes the open Miniapp. Does nothing if no Miniapp is open.

### Events

```kotlin
payME.setUpListener(
    onResponse = { action: ActionOpenMiniApp, data: JSONObject? -> /* ... */ },
    onError = { action: ActionOpenMiniApp, error: PayMEError -> /* error.code, error.description */ }
)
```

| Event | Payload | When |
| --- | --- | --- |
| `onResponse` | `action`, `JSONObject?` | A transaction succeeds, or a `getBalance` / `getAccountInformation` result arrives |
| `onError` | `action`, `PayMEError` (`code`, `description`, `isCloseMiniApp`) | An error occurs or the user closes the Miniapp |

- The listener is shared by all `PayMEMiniApp` instances; calling it again replaces the previous listener.
- If the `onResponse` data has `"isCloseMiniApp": true`, the SDK closes the Miniapp after the callback.

### Opening and closing the Miniapp

- Only one Miniapp can be open at a time. Calling `openMiniApp` while one is open returns `MINIAPP_ALREADY_OPEN`.
- When Miniapp closes, you get its result exactly once: `onResponse`, `onError`, or `USER_CANCEL` when the user closes it. You can reopen right inside that callback.
- The back button closes a popup, then goes back a page in the Miniapp; on the first page it closes the Miniapp and reports `USER_CANCEL`.
- `close()` does not report `USER_CANCEL`.

## Error codes

Handle errors by `error.code`.

| `code` | Meaning | What to do |
| --- | --- | --- |
| `401` | `identityToken` invalid or expired | Get a new `identityToken` and call again |
| `IDENTITY_TOKEN_REQUIRED` | `identityToken` missing | Pass `identityToken` |
| `USER_CANCEL` | The user closed the Miniapp | Not an error |
| `MINIAPP_ALREADY_OPEN` | A Miniapp is already open | Wait for it to close, or call `close()` first |
| `MINIAPP_CANNOT_PRESENT` | The Miniapp cannot be shown (for example `modal` with a `context` that is not a `FragmentActivity`) | Pass a visible `FragmentActivity`, call while the app is in the foreground |
| Other codes | Network, server or business errors | Show `description`, retry; contact PayME if it keeps happening |

## Upgrading from 0.7.x

0.8.x replaces the phone number with `identityToken` and is not backward compatible.

| 0.7.x | 0.8.x |
| --- | --- |
| `OpenMiniAppOpenData(phone = ...)` (and the other classes) | `OpenMiniAppOpenData(identityToken = ...)` |
| `OpenMiniAppPaymentDirectData(phone = ..., ...)` | `OpenMiniAppPaymentDirectData(identityToken = ..., ...)` or omit `identityToken` (guest) |
| `getBalance(phone)` | `getBalance(identityToken)` |
| `getAccountInformation(phone)` | `getAccountInformation(identityToken)` |

1. Server: integrate the `token-init` API (contact PayME).
2. Change the dependency to `com.github.PayME-Miniapp:native-android:0.8.6`.
3. Replace every `phone = ...` with `identityToken = ...`.
4. Handle the `401` and `IDENTITY_TOKEN_REQUIRED` errors.

## Support

PayME Technology Corporation, email [hotro@payme.vn](mailto:hotro@payme.vn). For the key set, the `token-init` API docs and the `PAYMENT` transaction creation API, contact PayME.

Versions: [GitHub Releases](https://github.com/PayME-Miniapp/native-android/releases). Sample app: the [`app`](app) folder.
