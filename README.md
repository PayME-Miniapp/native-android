**Tiếng Việt** | [English](README.en.md)

# PayME Miniapp Android SDK

Thư viện Android để mở PayME Miniapp (ví, thanh toán, nạp, rút, chuyển tiền, dịch vụ, eKYC) trong ứng dụng của bạn.

Phiên bản hiện tại: **0.8.4** (SDK 1.0.24).

## Mục lục

1. [Yêu cầu](#yêu-cầu)
2. [Cài đặt](#cài-đặt)
3. [Cấu hình ứng dụng](#cấu-hình-ứng-dụng)
4. [Bắt đầu nhanh](#bắt-đầu-nhanh)
5. [identityToken](#identitytoken)
6. [API](#api)
7. [Mã lỗi](#mã-lỗi)
8. [Nâng cấp từ 0.7.x](#nâng-cấp-từ-07x)
9. [Hỗ trợ](#hỗ-trợ)

## Yêu cầu

- `minSdk` 26, `compileSdk` 37 trở lên.
- Android Gradle Plugin 9.1.0 trở lên, build bằng JDK 17 trở lên.
- Kotlin 2.1 trở lên (nếu ứng dụng viết bằng Kotlin).
- Mở dạng `modal` cần Activity là `FragmentActivity` (ví dụ `AppCompatActivity`).
- Android System WebView hỗ trợ `WEB_MESSAGE_LISTENER`. WebView quá cũ thì Miniapp trả lỗi `UNSUPPORTED_WEBVIEW`.
- Bộ key `appId`, `publicKey`, `privateKey` do PayME cấp.
- Server của bạn gọi được API cấp `identityToken` của PayME.

## Cài đặt

1. **JitPack.** Thêm `maven { url = uri("https://jitpack.io") }` vào danh sách repository, cạnh `google()` và `mavenCentral()` (trong `settings.gradle` hoặc `allprojects` của `build.gradle` gốc):

   ```groovy
   dependencyResolutionManagement {
       repositories {
           google()
           mavenCentral()
           maven { url = uri("https://jitpack.io") }
       }
   }
   ```

2. **Dependency** trong `build.gradle` của module app:

   ```groovy
   dependencies {
       implementation 'com.github.PayME-Miniapp:native-android:0.8.4'
   }
   ```

3. **ProGuard / R8.** Không cần thêm rule. Nếu gặp lỗi khi bật R8, gửi log build cho PayME.

## Cấu hình ứng dụng

Không cần khai báo quyền. Các quyền cần thiết đã có sẵn trong SDK, Miniapp tự xin quyền khi cần.

- SDK khai báo `android.hardware.camera` là bắt buộc, nên Google Play ẩn ứng dụng trên máy không có camera. Muốn hỗ trợ các máy này, thêm vào manifest của ứng dụng:

  ```xml
  <uses-feature
      android:name="android.hardware.camera"
      android:required="false"
      tools:replace="android:required" />
  ```

- Nếu ứng dụng có `networkSecurityConfig` riêng, file đó phải cho phép cleartext tới `localhost` (Miniapp chạy trên `http://localhost`):

  ```xml
  <domain-config cleartextTrafficPermitted="true">
      <domain includeSubdomains="true">localhost</domain>
  </domain-config>
  ```

- Manifest merger báo xung đột thuộc tính `<application>` (`networkSecurityConfig`, `dataExtractionRules`, `fullBackupContent`, `requestLegacyExternalStorage`, `supportsRtl`): thêm `tools:replace` cho thuộc tính đó.
- SDK gửi sự kiện telemetry (đã lọc dữ liệu nhạy cảm) lên Mixpanel. Lưu ý khi khai báo Data safety trên Google Play.

## Bắt đầu nhanh

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
        payME.setMode("miniapp_sandbox") // chỉ khi dùng ENV.SANDBOX
        payME.setUpListener(
            onResponse = { action, data -> Log.d("PayME", "onResponse $action") },
            onError = { action, error -> Log.w("PayME", "onError $action ${error.code}") }
        )
    }

    // identityToken do server của bạn lấy từ PayME
    private fun openWallet(identityToken: String) {
        payME.openMiniApp(OpenMiniAppType.screen, OpenMiniAppOpenData(identityToken = identityToken))
    }
}
```

> Không đưa key thật vào mã nguồn công khai. Không ghi log `identityToken`, `publicKey`, `privateKey`.

## identityToken

`identityToken` là chuỗi PayME cấp cho từng người dùng của bạn. Ứng dụng truyền chuỗi này mỗi lần mở Miniapp.

1. Ứng dụng gọi server của bạn.
2. Server của bạn gọi API `identityToken/init` của PayME với thông tin người dùng (liên hệ PayME để nhận tài liệu API). Không gọi API này từ ứng dụng.
3. Ứng dụng truyền `identityToken` nhận được vào `OpenMiniApp*Data`, `getBalance` hoặc `getAccountInformation`.

| Lớp dữ liệu / hàm | `identityToken` |
| --- | --- |
| `OpenMiniAppPaymentDirectData` (`PAYMENT`) | Tuỳ chọn. Có thì người dùng trả được bằng ví PayME; không có thì thanh toán như khách vãng lai (thẻ, QR) |
| Các lớp còn lại, `getBalance`, `getAccountInformation` | Bắt buộc |

- Chuỗi có hiệu lực 15 phút, dùng lại được trong thời gian đó.
- Sai hoặc hết hạn: `onError` trả `401` (Miniapp đóng nếu đang mở). Lấy chuỗi mới rồi gọi lại.
- Chỉ giữ chuỗi trong bộ nhớ: không lưu (SharedPreferences, saved state, file), không ghi log.

## API

Lớp chính `com.payme.sdk.PayMEMiniApp`; model và enum nằm trong `com.payme.sdk.models`. Mọi hàm trả `Unit`. Kết quả và lỗi trả về qua `onResponse` và `onError`.

### Khởi tạo

```kotlin
PayMEMiniApp(context, appId, publicKey, privateKey, env = ENV.PRODUCTION, locale = Locale.vi)
```

| Tham số | Mô tả |
| --- | --- |
| `context` | Nên truyền `Activity`. Mở dạng `modal` cần `FragmentActivity` |
| `appId` | Mã ứng dụng do PayME cấp |
| `publicKey` | Public key của PayME |
| `privateKey` | Private key của ứng dụng |
| `env` | `ENV.PRODUCTION` (mặc định) hoặc `ENV.SANDBOX` |
| `locale` | `Locale.vi` (mặc định) hoặc `Locale.en` (`com.payme.sdk.models.Locale`, không phải `java.util.Locale`) |

Với `ENV.SANDBOX`, gọi thêm `setMode("miniapp_sandbox")`. Môi trường production không cần gọi.

### openMiniApp

```kotlin
payME.openMiniApp(openType, openMiniAppData)
```

`openType`: `OpenMiniAppType.screen` (toàn màn hình, mặc định) hoặc `OpenMiniAppType.modal` (bottom sheet trên Activity hiện tại). Lớp `openMiniAppData` quyết định action:

| Lớp | Action | Chức năng | Dữ liệu |
| --- | --- | --- | --- |
| `OpenMiniAppOpenData` | `OPEN` | Trang chủ ví (chưa kích hoạt thì mở luồng kích hoạt) | Không có |
| `OpenMiniAppKYCData` | `KYC` | Định danh (eKYC) | Không có |
| `OpenMiniAppPaymentData` | `PAY` | Thanh toán đơn hàng của bạn | `paymentData`: [`PaymentData`](#paymentdata-pay) |
| `OpenMiniAppPaymentDirectData` | `PAYMENT` | Thanh toán giao dịch đã tạo qua API PayME | `paymentDirectData`: [`PaymentDirectData`](#paymentdirectdata-payment) |
| `OpenMiniAppTransferQRData` | `TRANSFER_QR` | Chuyển tiền tới tài khoản ngân hàng | `transferQRData`: [`TransferQRData`](#transferqrdata-transfer_qr) |
| `OpenMiniAppServiceData` | `SERVICE` | Thanh toán dịch vụ (điện, nước, thẻ…) | `additionalData`: [`ServiceData`](#servicedata-service) |
| `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData` | `DEPOSIT`, `WITHDRAW`, `TRANSFER` | Nạp, rút, chuyển tiền | `additionalData`: [`DepositWithdrawTransferData`](#depositwithdrawtransferdata-deposit-withdraw-transfer) |

Mọi lớp nhận `identityToken` làm tham số đầu tiên.

```kotlin
payME.openMiniApp(
    OpenMiniAppType.screen,
    OpenMiniAppPaymentData(
        identityToken = identityToken,
        paymentData = PaymentData(
            transactionId = "ORDER_123", amount = 10000, note = "Đơn hàng 123",
            ipnUrl = null, extraData = null, isShowResult = true
        )
    )
)

// Khách vãng lai: bỏ identityToken, dùng tham số đặt tên
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
            description = "Nạp tiền", amount = 50000, extraData = null, isBackToApp = true, isShowResult = true
        )
    )
)
```

Các data class dưới đây không có giá trị mặc định: phải truyền mọi tham số. Tham số nullable (`?`) truyền `null` thì Miniapp dùng giá trị mặc định của nó. `amount` là số nguyên VND.

Mọi data class đều có thêm hai trường: `extraData: Map<String, Any>?` (dữ liệu riêng của bạn, trả lại khi giao dịch thành công) và `isShowResult: Boolean?` (hiện màn hình kết quả của PayME).

#### PaymentData (PAY)

| Trường | Kiểu | Mô tả |
| --- | --- | --- |
| `transactionId` | `String` | Mã giao dịch của bạn |
| `amount` | `Int` | Số tiền |
| `note` | `String?` | Ghi chú |
| `ipnUrl` | `String?` | URL nhận thông báo trạng thái giao dịch |

#### PaymentDirectData (PAYMENT)

| Trường | Kiểu | Mô tả |
| --- | --- | --- |
| `transaction` | `String` | Mã giao dịch PayME trả về khi bạn tạo giao dịch |

#### TransferQRData (TRANSFER_QR)

| Trường | Kiểu | Mô tả |
| --- | --- | --- |
| `amount` | `Int` | Số tiền (`0` để người dùng tự nhập) |
| `bankNumber` | `String` | Số tài khoản hoặc số thẻ nhận |
| `swiftCode` | `String` | Mã SWIFT của ngân hàng nhận |
| `cardHolder` | `String` | Tên chủ tài khoản |
| `note` | `String?` | Ghi chú |
| `partnerTransaction` | `String?` | Mã giao dịch của bạn |

#### ServiceData (SERVICE)

| Trường | Kiểu | Mô tả |
| --- | --- | --- |
| `service` | `String` | Mã dịch vụ do PayME cung cấp, ví dụ `POWE` (điện) |
| `isBackToApp` | `Boolean?` | Quay về ứng dụng của bạn sau khi xong |

#### DepositWithdrawTransferData (DEPOSIT, WITHDRAW, TRANSFER)

| Trường | Kiểu | Mô tả |
| --- | --- | --- |
| `description` | `String?` | Mô tả giao dịch |
| `amount` | `Int?` | Số tiền điền sẵn |
| `isBackToApp` | `Boolean?` | Quay về ứng dụng của bạn sau khi xong |

### getBalance, getAccountInformation

```kotlin
payME.getBalance(identityToken)
payME.getAccountInformation(identityToken)
```

Kết quả trả qua `onResponse`:

- `GET_BALANCE`: `linked` (đã liên kết ví chưa), `balance` (có khi `linked` = `true`), `message`.
- `GET_ACCOUNT_INFO`: thông tin tài khoản ví, luôn có `accountId`.

### setLanguage, close

- `setLanguage(Locale.vi | Locale.en)`: đổi ngôn ngữ Miniapp cho các lần mở sau.
- `close()`: đóng Miniapp đang mở. Không có Miniapp nào mở thì không làm gì.

### Sự kiện

```kotlin
payME.setUpListener(
    onResponse = { action: ActionOpenMiniApp, data: JSONObject? -> /* ... */ },
    onError = { action: ActionOpenMiniApp, error: PayMEError -> /* error.code, error.description */ }
)
```

| Sự kiện | Nội dung | Khi nào |
| --- | --- | --- |
| `onResponse` | `action`, `JSONObject?` | Giao dịch thành công, hoặc có kết quả `getBalance` / `getAccountInformation` |
| `onError` | `action`, `PayMEError` (`code`, `description`, `isCloseMiniApp`) | Có lỗi hoặc người dùng đóng Miniapp |

- Listener dùng chung cho mọi instance `PayMEMiniApp`; gọi lại thì ghi đè listener cũ.
- Dữ liệu `onResponse` có `"isCloseMiniApp": true` thì SDK đóng Miniapp sau callback.

### Mở và đóng Miniapp

- Mỗi lúc chỉ mở một Miniapp. Gọi `openMiniApp` khi đang có Miniapp mở sẽ nhận `MINIAPP_ALREADY_OPEN`.
- Khi Miniapp đóng, bạn nhận kết quả đúng một lần: `onResponse`, `onError`, hoặc `USER_CANCEL` khi người dùng đóng. Có thể mở lại ngay trong callback này.
- Nút back đóng popup, rồi lùi trang trong Miniapp; hết trang thì đóng Miniapp và báo `USER_CANCEL`.
- `close()` không báo `USER_CANCEL`.

## Mã lỗi

Xử lý theo `error.code`.

| `code` | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| `401` | `identityToken` sai hoặc hết hạn | Lấy `identityToken` mới rồi gọi lại |
| `IDENTITY_TOKEN_REQUIRED` | Thiếu `identityToken` | Truyền `identityToken` |
| `USER_CANCEL` | Người dùng đóng Miniapp | Không phải lỗi |
| `MINIAPP_ALREADY_OPEN` | Đang có Miniapp mở | Đợi Miniapp đóng, hoặc gọi `close()` trước |
| `MINIAPP_CANNOT_PRESENT` | Không hiển thị được Miniapp (ví dụ mở `modal` mà `context` không phải `FragmentActivity`) | Truyền `FragmentActivity` đang hiển thị, gọi khi ứng dụng ở foreground |
| Mã khác | Lỗi mạng, máy chủ hoặc nghiệp vụ | Hiển thị `description`, thử lại; liên hệ PayME nếu lặp lại |

## Nâng cấp từ 0.7.x

Bản 0.8.x thay số điện thoại bằng `identityToken` và không tương thích ngược.

| 0.7.x | 0.8.x |
| --- | --- |
| `OpenMiniAppOpenData(phone = ...)` (và các lớp khác) | `OpenMiniAppOpenData(identityToken = ...)` |
| `OpenMiniAppPaymentDirectData(phone = ..., ...)` | `OpenMiniAppPaymentDirectData(identityToken = ..., ...)` hoặc bỏ `identityToken` (khách vãng lai) |
| `getBalance(phone)` | `getBalance(identityToken)` |
| `getAccountInformation(phone)` | `getAccountInformation(identityToken)` |

1. Server: tích hợp API `identityToken/init` (liên hệ PayME).
2. Đổi dependency thành `com.github.PayME-Miniapp:native-android:0.8.4`.
3. Thay mọi `phone = ...` bằng `identityToken = ...`.
4. Xử lý lỗi `401` và `IDENTITY_TOKEN_REQUIRED`.

## Hỗ trợ

PayME Technology Corporation, email [hotro@payme.vn](mailto:hotro@payme.vn). Bộ key, tài liệu API `identityToken/init` và API tạo giao dịch `PAYMENT`: liên hệ PayME.

Danh sách phiên bản: [GitHub Releases](https://github.com/PayME-Miniapp/native-android/releases). Ứng dụng mẫu: thư mục [`app`](app).
