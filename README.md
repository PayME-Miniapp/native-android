**Tiếng Việt** | [English](README.en.md)

# PayME Miniapp Android SDK

Thư viện Android để mở PayME Miniapp (ví, thanh toán, nạp/rút/chuyển tiền, dịch vụ, eKYC) ngay trong ứng dụng của đối tác.

**Phiên bản hiện tại:** `0.8.4` (SDK 1.0.24, mini-app patch 0502). Xem [lịch sử phiên bản](https://github.com/PayME-Miniapp/native-android/releases).

## Mục lục

- [Yêu cầu](#yêu-cầu)
- [Cài đặt](#cài-đặt)
- [Cấu hình ứng dụng](#cấu-hình-ứng-dụng)
- [Bắt đầu nhanh](#bắt-đầu-nhanh)
- [identityToken](#identitytoken)
- [Tham chiếu API](#tham-chiếu-api)
  - [Khởi tạo PayMEMiniApp](#khởi-tạo-paymeminiapp)
  - [setUpListener](#setuplistener)
  - [openMiniApp](#openminiapp)
  - [Các lớp OpenMiniApp*Data](#các-lớp-openminiappdata)
  - [getBalance](#getbalance)
  - [getAccountInformation](#getaccountinformation)
  - [setLanguage](#setlanguage)
  - [setMode](#setmode)
  - [close](#close)
- [Mã lỗi](#mã-lỗi)
- [Nâng cấp từ 0.7.x](#nâng-cấp-từ-07x)
- [Cập nhật OTA mini-app](#cập-nhật-ota-mini-app)
- [Lịch sử phiên bản và hỗ trợ](#lịch-sử-phiên-bản-và-hỗ-trợ)

## Yêu cầu

| Hạng mục | Yêu cầu |
| --- | --- |
| `minSdk` | 26 (Android 8.0) trở lên |
| `compileSdk` | 37 trở lên (thư viện phụ thuộc `androidx.core` 1.19.1 yêu cầu) |
| Android Gradle Plugin | 9.1.0 trở lên (cùng lý do trên) |
| JDK để build | 17 trở lên (SDK biên dịch ra bytecode Java 17) |
| Kotlin | 2.1 trở lên nếu app viết bằng Kotlin (SDK biên dịch với Kotlin 2.2, kéo theo `kotlin-stdlib` 2.2.10) |
| Activity mở dạng `modal` | `FragmentActivity` (ví dụ `AppCompatActivity`) |
| Android System WebView | Phải hỗ trợ `WEB_MESSAGE_LISTENER`. Nếu không, mini-app trả lỗi `UNSUPPORTED_WEBVIEW` |

Thông tin đối tác do PayME cấp: `appId`, `publicKey`, `privateKey`, và API phía server để lấy [`identityToken`](#identitytoken).

## Cài đặt

**1. Thêm kho JitPack.** Các thư viện phụ thuộc còn lại nằm trên `google()` và `mavenCentral()`.

Nếu project khai báo repository trong `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Nếu project khai báo repository trong `build.gradle` gốc:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```

**2. Thêm dependency** vào `build.gradle` của module app:

```groovy
dependencies {
    implementation 'com.github.PayME-Miniapp:native-android:0.8.4'
}
```

> [!WARNING]
> Không dùng `0.8.0`: bản này trên JitPack được build từ commit cũ. `0.8.1`, `0.8.2`, `0.8.3` có cùng API với `0.8.4`; nên dùng `0.8.4` (bản mới nhất, gồm các bản sửa lỗi vòng đời mini-app).

## Cấu hình ứng dụng

### Quyền

SDK đã khai báo sẵn các quyền dưới đây trong manifest của thư viện, chúng được gộp vào app khi build. Quyền runtime (camera, danh bạ, bộ nhớ) do SDK tự xin khi người dùng dùng tới tính năng tương ứng; app không cần xin trước.

| Quyền | Mục đích |
| --- | --- |
| `INTERNET` | Gọi API PayME, tải bản cập nhật mini-app |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Kiểm tra trạng thái kết nối |
| `ACCESS_LOCAL_NETWORK` | SDK chạy một máy chủ web cục bộ (`http://localhost`) để WebView tải mini-app |
| `CAMERA` | eKYC: chụp giấy tờ, nhận diện và xác thực khuôn mặt |
| `NFC` | eKYC: đọc chip căn cước |
| `READ_CONTACTS` | Chọn số điện thoại từ danh bạ |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | Chọn ảnh và lưu ảnh mã QR (Android cũ) |

SDK khai báo `<uses-feature android:name="android.hardware.camera" />` (bắt buộc theo mặc định), nên Google Play sẽ ẩn app trên thiết bị không có camera. Nếu app cần hỗ trợ các thiết bị này, ghi đè trong manifest của app:

```xml
<uses-feature
    android:name="android.hardware.camera"
    android:required="false"
    tools:replace="android:required" />
```

### Gộp manifest

Thẻ `<application>` của SDK đặt các thuộc tính sau: `android:networkSecurityConfig`, `android:dataExtractionRules`, `android:fullBackupContent`, `android:requestLegacyExternalStorage="true"`, `android:supportsRtl="true"`. Nếu app cũng đặt các thuộc tính này với giá trị khác, manifest merger sẽ báo lỗi. Khi đó thêm `tools:replace` cho thuộc tính tương ứng.

Nếu app dùng `networkSecurityConfig` riêng, file đó phải cho phép cleartext tới `localhost`, vì mini-app được phục vụ qua `http://localhost`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
    <!-- cấu hình riêng của app -->
</network-security-config>
```

### ProGuard / R8

SDK không kèm consumer rules và không dùng reflection trên các lớp public của mình; cầu nối JS dùng `WebViewCompat.addWebMessageListener`, không dùng `@JavascriptInterface`. Hiện không cần thêm rule riêng. Nếu gặp lỗi khi bật R8, vui lòng báo PayME kèm log build.

### Khác

- Màn hình mini-app (dạng `screen`) và các màn eKYC khoá hướng dọc.
- SDK gửi sự kiện telemetry đã lọc dữ liệu nhạy cảm lên Mixpanel. Lưu ý khi khai báo mục Data safety trên Google Play.

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

    // identityToken lấy từ server của đối tác ngay trước khi mở
    private fun openWallet(identityToken: String) {
        payME.openMiniApp(OpenMiniAppType.screen, OpenMiniAppOpenData(identityToken = identityToken))
    }
}
```

Không ghi log `identityToken`, `publicKey`, `privateKey`. Không đặt khoá thật trong mã nguồn mẫu hay repository.

## identityToken

Từ 0.8.0 (SDK 1.0.24), SDK không nhận số điện thoại nữa. App truyền `identityToken`: chuỗi do BE PayME ký cho người dùng của đối tác, gắn với `appId`.

### Luồng tích hợp

1. **Server đối tác** gọi API `identityToken/init` của BE PayME (server tới server) với thông tin người dùng để nhận `identityToken`. Không gọi API này từ app. Liên hệ PayME để nhận tài liệu API.
2. **Server đối tác** trả `identityToken` cho app qua kênh đã xác thực của đối tác.
3. **App** truyền `identityToken` vào `OpenMiniApp*Data`, `getBalance` hoặc `getAccountInformation`.

### Thời hạn và xử lý lỗi

- `identityToken` có hiệu lực 15 phút và dùng lại được nhiều lần trong thời gian đó. Hết hạn thì server đối tác gọi lại API để lấy chuỗi mới.
- Chuỗi sai hoặc hết hạn: mini-app đóng và `onError` trả `code = "401"`. `getBalance` và `getAccountInformation` cũng trả `"401"`.
- Chuỗi rỗng ở action bắt buộc, `getBalance` hoặc `getAccountInformation`: SDK không mở mini-app và trả `onError` với `code = "IDENTITY_TOKEN_REQUIRED"`.
- Nếu hệ thống huỷ process của app khi mini-app đang mở, khi khôi phục mini-app sẽ tự đóng (không crash, không gọi callback) vì phiên và `identityToken` chỉ nằm trong bộ nhớ. Mở lại với `identityToken` còn hạn.

### Action nào cần identityToken

| Lớp dữ liệu | identityToken |
| --- | --- |
| `OpenMiniAppOpenData`, `OpenMiniAppKYCData`, `OpenMiniAppServiceData`, `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData`, `OpenMiniAppPaymentData`, `OpenMiniAppTransferQRData` | Bắt buộc (`String`) |
| `OpenMiniAppPaymentDirectData` (action `PAYMENT`) | Tuỳ chọn (`String? = null`). Có: người dùng trả được bằng ví PayME; BE PayME kiểm tra người dùng trong chuỗi khớp với giao dịch. Không có: chỉ thanh toán như khách vãng lai (thẻ, QR) |
| `OpenMiniAppPayMEData` (action `PAYME`) | Không có tham số. Người dùng tự nhập số điện thoại và đăng nhập bằng PIN |
| `getBalance`, `getAccountInformation` | Bắt buộc (`String`) |

### Quy tắc bảo mật

- Chỉ giữ `identityToken` trong bộ nhớ. Không ghi log, không lưu (SharedPreferences, `Bundle`/saved state, file, database), không đưa lên URL hay deep link.
- SDK không lưu chuỗi này, không ghi nó ra log, và xoá tham chiếu khi đóng mini-app. `toString()` của các lớp `OpenMiniApp*Data` chỉ in tên lớp và action.

## Tham chiếu API

Lớp chính: `com.payme.sdk.PayMEMiniApp`. Các model và enum nằm trong `com.payme.sdk.models`.

### Khởi tạo PayMEMiniApp

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

| Tham số | Kiểu | Bắt buộc | Mô tả |
| --- | --- | --- | --- |
| `context` | `Context` | Có | Nên truyền `Activity`. Mở dạng `modal` cần `FragmentActivity` |
| `appId` | `String` | Có | Mã đối tác do PayME cấp |
| `publicKey` | `String` | Có | Khoá công khai do PayME cấp khi đăng ký đối tác |
| `privateKey` | `String` | Có | Khoá bí mật của đối tác, đăng ký với PayME |
| `env` | `ENV` | Không, mặc định `ENV.PRODUCTION` | Môi trường. Đối tác dùng `ENV.PRODUCTION` hoặc `ENV.SANDBOX`; `STAGING`, `DEV`, `LOCAL` dành cho PayME |
| `locale` | `Locale` | Không, mặc định `Locale.vi` | Ngôn ngữ hiển thị: `Locale.vi` hoặc `Locale.en` (`com.payme.sdk.models.Locale`, không phải `java.util.Locale`) |

### setUpListener

```kotlin
fun setUpListener(
    onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit)? = null,
    onError: ((ActionOpenMiniApp, PayMEError) -> Unit)? = null
)
```

- `onResponse`: gọi khi một action kết thúc thành công, kèm action và dữ liệu `JSONObject` do mini-app hoặc API trả về.
- `onError`: gọi khi có lỗi hoặc người dùng đóng mini-app, kèm action và `PayMEError`.
- Listener dùng chung cho mọi instance `PayMEMiniApp`; lần gọi sau ghi đè lần trước. Truyền `null` thì giữ listener cũ.
- Callback được gọi trên main thread, riêng `IDENTITY_TOKEN_REQUIRED`, `MINIAPP_ALREADY_OPEN`, `MINIAPP_CANNOT_PRESENT` và `USER_CANCEL` của phiên kẹt (xem [Mở, đóng và nút back](#mở-đóng-và-nút-back)) được gọi đồng bộ trên luồng đã gọi `openMiniApp`.

Nếu dữ liệu `onResponse` có `"isCloseMiniApp": true` thì SDK đóng mini-app sau khi gọi callback. Nội dung `JSONObject` của các action mở mini-app do mini-app quy định; dữ liệu của `getBalance` và `getAccountInformation` được mô tả ở mục tương ứng.

**`ActionOpenMiniApp`**

| Giá trị | Ý nghĩa |
| --- | --- |
| `PAYME` | Mở ví PayME đầy đủ (người dùng tự đăng nhập) |
| `OPEN` | Mở trang chủ ví; kích hoạt ví nếu người dùng chưa có |
| `PAY` | Thanh toán đơn hàng (`OpenMiniAppPaymentData`) |
| `PAYMENT` | Thanh toán giao dịch đối tác đã tạo sẵn (`OpenMiniAppPaymentDirectData`) |
| `TRANSFER_QR` | Chuyển tiền tới tài khoản đích do đối tác truyền vào |
| `SERVICE` | Thanh toán dịch vụ |
| `DEPOSIT` | Nạp tiền vào ví |
| `WITHDRAW` | Rút tiền từ ví |
| `TRANSFER` | Chuyển tiền |
| `KYC` | Định danh tài khoản (eKYC) |
| `GET_BALANCE` | Kết quả của `getBalance` |
| `GET_ACCOUNT_INFO` | Kết quả của `getAccountInformation` |

**`PayMEError`**

| Thuộc tính | Kiểu | Mô tả |
| --- | --- | --- |
| `type` | `PayMEErrorType` | `MiniApp`, `UserCancel` hoặc `Network` |
| `code` | `String` | Mã lỗi, xem [Mã lỗi](#mã-lỗi) |
| `description` | `String` | Mô tả lỗi theo ngôn ngữ hiện tại, có thể hiển thị cho người dùng |
| `isCloseMiniApp` | `Boolean` | `true` nếu mini-app đã đóng do lỗi này |

### openMiniApp

```kotlin
fun openMiniApp(
    openType: OpenMiniAppType = OpenMiniAppType.screen,
    openMiniAppData: OpenMiniAppDataInterface,
)
```

| Tham số | Kiểu | Bắt buộc | Mô tả |
| --- | --- | --- | --- |
| `openType` | `OpenMiniAppType` | Không, mặc định `screen` | `OpenMiniAppType.screen`: mở toàn màn hình trong Activity riêng của SDK. `OpenMiniAppType.modal`: bottom sheet trên Activity hiện tại, yêu cầu `context` là `FragmentActivity` (nếu không, mini-app không mở và `onError` trả `MINIAPP_CANNOT_PRESENT`) |
| `openMiniAppData` | `OpenMiniAppDataInterface` | Có | Một trong các lớp `OpenMiniApp*Data` bên dưới; lớp quyết định action |

Ví dụ thanh toán giao dịch tạo sẵn, không có `identityToken` (dùng tham số đặt tên vì `identityToken` đứng trước):

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

Ví dụ nạp tiền:

```kotlin
payME.openMiniApp(
    OpenMiniAppType.modal,
    OpenMiniAppDepositData(
        identityToken = identityToken,
        additionalData = DepositWithdrawTransferData(
            description = "Nạp tiền",
            amount = 100000,
            extraData = mapOf("orderId" to "<ORDER_ID>"),
            isBackToApp = true,
            isShowResult = true
        )
    )
)
```

#### Mở, đóng và nút back

- **Mỗi lúc chỉ một mini-app.** Gọi `openMiniApp` khi đang có mini-app mở (kể cả gọi từ một Activity khác trong lúc modal đang hiện) bị từ chối với `MINIAPP_ALREADY_OPEN`; mini-app đang mở giữ nguyên.
- **Mở lại ngay trong callback đóng được.** Khi mini-app đang đóng (`USER_CANCEL`, `isCloseMiniApp = true` trong `onResponse`/`onError`, hoặc sau `close()`), gọi `openMiniApp` ngay trong callback hay ngay sau `close()` mở được mini-app mới.
- **Nút back của hệ thống** (cả `screen` và `modal`): trước tiên mini-app web tự xử lý (đóng popup trên cùng hoặc chặn back ở màn đang thanh toán); nếu không, lùi về trang trước trong mini-app; hết trang thì đóng mini-app và `onError` trả `USER_CANCEL`.
- **Đóng modal** bằng cách chạm ngoài hoặc nút đóng trong mini-app trả `USER_CANCEL` với `type = UserCancel`.
- **App đóng Activity đang chứa mini-app** (`finish()` Activity có modal, xoá task...) khi mini-app còn mở: `onError` trả `USER_CANCEL` một lần. Xoay màn hình, đổi dark mode/ngôn ngữ hay hệ thống tạm huỷ Activity không đóng mini-app.
- **Thời gian khởi động 15 giây:** nếu sau 15 giây mini-app vừa mở vẫn chưa hiển thị được (Activity/modal không được tạo, ví dụ app bị chuyển nền ngay), lần `openMiniApp` tiếp theo coi phiên đó là đã mất: báo `USER_CANCEL` cho phiên cũ rồi mở phiên mới thay vì trả `MINIAPP_ALREADY_OPEN`. `USER_CANCEL` này được gọi đồng bộ ngay trong `openMiniApp`, trước khi mini-app mới mở.

### Các lớp OpenMiniApp*Data

| Lớp | Action | Tham số |
| --- | --- | --- |
| `OpenMiniAppPayMEData()` | `PAYME` | Không có |
| `OpenMiniAppOpenData` | `OPEN` | `identityToken: String` |
| `OpenMiniAppKYCData` | `KYC` | `identityToken: String` |
| `OpenMiniAppPaymentData` | `PAY` | `identityToken: String`, `paymentData: PaymentData` |
| `OpenMiniAppPaymentDirectData` | `PAYMENT` | `identityToken: String? = null`, `paymentDirectData: PaymentDirectData` |
| `OpenMiniAppServiceData` | `SERVICE` | `identityToken: String`, `additionalData: ServiceData` |
| `OpenMiniAppDepositData` | `DEPOSIT` | `identityToken: String`, `additionalData: DepositWithdrawTransferData` |
| `OpenMiniAppWithdrawData` | `WITHDRAW` | `identityToken: String`, `additionalData: DepositWithdrawTransferData` |
| `OpenMiniAppTransferData` | `TRANSFER` | `identityToken: String`, `additionalData: DepositWithdrawTransferData` |
| `OpenMiniAppTransferQRData` | `TRANSFER_QR` | `identityToken: String`, `transferQRData: TransferQRData` |

Các data class lồng bên dưới không có giá trị mặc định: mọi tham số đều phải truyền. Tham số kiểu nullable (`?`) có thể truyền `null`; khi đó mini-app dùng giá trị mặc định của nó. `extraData` là dữ liệu tuỳ ý của đối tác (giá trị `String`, `Int`, `Boolean` hoặc `Map` lồng nhau; kiểu khác được chuyển thành chuỗi).

**`PaymentData`** (cho `OpenMiniAppPaymentData`)

| Thuộc tính | Kiểu | Mô tả |
| --- | --- | --- |
| `transactionId` | `String` | Mã giao dịch của đối tác |
| `amount` | `Int` | Số tiền (VND) |
| `note` | `String?` | Ghi chú giao dịch |
| `ipnUrl` | `String?` | URL để server PayME gọi IPN khi trạng thái giao dịch thay đổi |
| `extraData` | `Map<String, Any>?` | Dữ liệu bổ sung, trả lại cho đối tác khi thanh toán thành công |
| `isShowResult` | `Boolean?` | Có hiển thị màn kết quả của PayME không |

**`PaymentDirectData`** (cho `OpenMiniAppPaymentDirectData`)

| Thuộc tính | Kiểu | Mô tả |
| --- | --- | --- |
| `transaction` | `String` | Mã giao dịch đã tạo sẵn trên hệ thống PayME |
| `extraData` | `Map<String, Any>?` | Dữ liệu bổ sung, trả lại cho đối tác khi thanh toán thành công |
| `isShowResult` | `Boolean?` | Có hiển thị màn kết quả của PayME không |

**`ServiceData`** (cho `OpenMiniAppServiceData`)

| Thuộc tính | Kiểu | Mô tả |
| --- | --- | --- |
| `service` | `String` | Mã dịch vụ do PayME cung cấp, ví dụ `POWE` (tiền điện) |
| `extraData` | `Map<String, Any>?` | Dữ liệu bổ sung, trả lại cho đối tác khi thanh toán thành công |
| `isBackToApp` | `Boolean?` | Quay về app đối tác sau khi hoàn tất |
| `isShowResult` | `Boolean?` | Có hiển thị màn kết quả của PayME không |

**`DepositWithdrawTransferData`** (cho `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData`)

| Thuộc tính | Kiểu | Mô tả |
| --- | --- | --- |
| `description` | `String?` | Mô tả giao dịch |
| `amount` | `Int?` | Số tiền (VND) |
| `extraData` | `Map<String, Any>?` | Dữ liệu bổ sung, trả lại cho đối tác khi giao dịch thành công |
| `isBackToApp` | `Boolean?` | Quay về app đối tác sau khi hoàn tất |
| `isShowResult` | `Boolean?` | Có hiển thị màn kết quả của PayME không |

**`TransferQRData`** (cho `OpenMiniAppTransferQRData`)

| Thuộc tính | Kiểu | Mô tả |
| --- | --- | --- |
| `amount` | `Int` | Số tiền (VND); truyền `0` nếu để người dùng tự nhập |
| `bankNumber` | `String` | Số thẻ hoặc số tài khoản ngân hàng nhận |
| `swiftCode` | `String` | Mã SWIFT của ngân hàng nhận |
| `cardHolder` | `String` | Tên chủ tài khoản nhận |
| `note` | `String?` | Ghi chú giao dịch |
| `partnerTransaction` | `String?` | Mã giao dịch của đối tác |
| `extraData` | `Map<String, Any>?` | Dữ liệu bổ sung, trả lại cho đối tác khi giao dịch thành công |
| `isShowResult` | `Boolean?` | Có hiển thị màn kết quả của PayME không |

### getBalance

```kotlin
fun getBalance(identityToken: String)
```

Lấy số dư ví của người dùng mà không mở giao diện. Kết quả trả qua `onResponse` với action `GET_BALANCE`:

```json
{ "linked": true, "balance": 150000, "message": "<thông điệp từ PayME>" }
```

Nếu `identityToken` hợp lệ nhưng chưa lấy được phiên ví (ví dụ người dùng chưa liên kết ví với đối tác):

```json
{ "linked": false, "message": "Tài khoản chưa được liên kết" }
```

Lỗi trả qua `onError` với action `GET_BALANCE`: `IDENTITY_TOKEN_REQUIRED`, `401` hoặc lỗi mạng.

### getAccountInformation

```kotlin
fun getAccountInformation(identityToken: String)
```

Lấy thông tin tài khoản PayME của người dùng. Kết quả trả qua `onResponse` với action `GET_ACCOUNT_INFO`: object `accountInfo` do BE PayME trả về, luôn có `accountId` (số nguyên); các trường còn lại do BE PayME quy định.

```json
{ "accountId": 123456, "...": "..." }
```

Lỗi trả qua `onError` với action `GET_ACCOUNT_INFO`: `IDENTITY_TOKEN_REQUIRED`, `401`, `OTHER` (không có thông tin tài khoản) hoặc lỗi mạng.

### setLanguage

```kotlin
fun setLanguage(lang: Locale)
```

Đổi ngôn ngữ mini-app (`Locale.vi` hoặc `Locale.en`) cho các lần mở sau. Ngôn ngữ chỉ áp dụng cho giao diện và chuỗi của SDK: SDK không đổi `java.util.Locale` mặc định hay resources của app, và hàm này không gọi `onChangeLocale` (callback đó chỉ được gọi khi người dùng đổi ngôn ngữ bên trong mini-app).

### setMode

```kotlin
fun setMode(mode: String)
```

Chọn kênh cập nhật OTA của mini-app (xem [Cập nhật OTA](#cập-nhật-ota-mini-app)). Mặc định `"miniapp_product"`. Với `ENV.SANDBOX`, dùng `"miniapp_sandbox"` như app demo. Các giá trị khác (`miniapp_staging`, `pm_*`, `bank`) dành cho PayME.

### close

```kotlin
fun close()
```

Đóng mini-app đang mở (cả `screen` và `modal`). Đóng chủ động bằng `close()` không gọi `onError`. `close()` không bao giờ ném exception (kể cả khi Activity chứa modal đang bị hệ thống huỷ tạm) và gọi khi không có mini-app nào mở thì không làm gì; có thể gọi `openMiniApp` ngay sau đó.

> Các hàm `setChangeEnvFunction`, `setChangeLocaleFunction`, `setOneSignalFunctions` chỉ dành cho app ví PayME; đối tác không cần gọi.

## Mã lỗi

Nên xử lý theo `code`, không dựa vào `type`.

| `code` | `type` | Khi nào | Xử lý gợi ý |
| --- | --- | --- | --- |
| `IDENTITY_TOKEN_REQUIRED` | `MiniApp` | `identityToken` rỗng ở action bắt buộc, `getBalance` hoặc `getAccountInformation`. Mini-app không được mở | Lấy `identityToken` từ server trước khi gọi |
| `401` | `MiniApp` | `identityToken` sai hoặc hết hạn (hoặc phiên hết hạn khi đang dùng). Mini-app đóng (`isCloseMiniApp = true`); `getBalance`/`getAccountInformation` cũng trả mã này | Server lấy `identityToken` mới rồi mở lại |
| `USER_CANCEL` | `UserCancel` | Người dùng đóng mini-app (nút trong mini-app, nút back của hệ thống khi hết trang, chạm ngoài modal), hoặc app đóng Activity đang chứa mini-app. Mỗi lần đóng chỉ báo một lần | Không coi là lỗi; quay về luồng của app |
| `MINIAPP_ALREADY_OPEN` | `MiniApp` | Gọi `openMiniApp` khi đang có mini-app mở (kể cả từ Activity khác). Mini-app mới không được mở, mini-app đang mở giữ nguyên | Đợi mini-app hiện tại đóng, hoặc gọi `close()` rồi mở lại |
| `MINIAPP_CANNOT_PRESENT` | `MiniApp` | Không hiển thị được mini-app: mở `modal` mà `context` không phải `FragmentActivity` hoặc Activity đang đóng/đã huỷ, hoặc không mở được Activity/modal (ví dụ Activity đã lưu trạng thái) | Truyền `FragmentActivity` đang hiển thị; thử lại khi Activity ở foreground |
| `CONNECTION_LOST` | `Network` | Mất kết nối, đăng ký thiết bị thất bại hoặc không giải mã được phản hồi | Báo người dùng kiểm tra mạng, cho thử lại |
| `TIMED_OUT` | `Network` | Quá thời gian chờ máy chủ | Cho thử lại |
| `SERVER_ERROR` | `Network` | Máy chủ trả lỗi HTTP | Thử lại sau |
| `DECODE_FAILED` | `Network` | Phản hồi không phải JSON hợp lệ | Thử lại; báo PayME nếu lặp lại |
| `OTHER` | `Network` hoặc `MiniApp` | `Network`: lỗi mạng khác. `MiniApp`: `getAccountInformation` không có thông tin tài khoản | Hiển thị `description` |
| `UNSUPPORTED_WEBVIEW` | `MiniApp` | Android System WebView quá cũ. Mini-app đóng | Hướng dẫn người dùng cập nhật Android System WebView |
| `RESOURCE_NOT_FOUND` | `MiniApp` | Không tìm thấy tài nguyên của mini-app (404). Mini-app đóng | Báo PayME |
| `UNZIP_FAILED` | `MiniApp` | Không giải nén được gói mini-app. Mini-app đóng | Thử lại; báo PayME nếu lặp lại |
| `INSUFFICIENT_STORAGE` | `MiniApp` | Thiết bị không đủ dung lượng cho gói mini-app. Mini-app đóng | Hướng dẫn người dùng giải phóng bộ nhớ |
| Mã khác | `MiniApp` | Lỗi nghiệp vụ do mini-app trả về | Hiển thị `description`; kiểm tra `isCloseMiniApp` |

`PayMENetworkErrorCode` còn khai báo `ENCODE_FAILED` và `NO_RESPONSE`, nhưng phiên bản hiện tại không trả hai mã này.

## Nâng cấp từ 0.7.x

0.8.x (SDK 1.0.24) không tương thích ngược với 0.7.x (SDK 1.0.23): tham số `phone` được thay bằng `identityToken`.

| 0.7.x | 0.8.x |
| --- | --- |
| `implementation '...:native-android:0.7.x'` | `implementation 'com.github.PayME-Miniapp:native-android:0.8.4'` |
| `OpenMiniAppOpenData(phone = ...)` và tương tự cho `KYC`, `Service`, `Deposit`, `Withdraw`, `Transfer`, `Payment`, `TransferQR` | `OpenMiniAppOpenData(identityToken = ...)` và tương tự |
| `OpenMiniAppPaymentDirectData(phone = ..., paymentDirectData = ...)` | `OpenMiniAppPaymentDirectData(identityToken = ..., paymentDirectData = ...)` hoặc bỏ `identityToken` (khách vãng lai) |
| `OpenMiniAppPayMEData()` | Không đổi |
| `getBalance(phone)` | `getBalance(identityToken)` |
| `getAccountInformation(phone)` | `getAccountInformation(identityToken)` |
| Số điện thoại không hợp lệ: `getBalance` trả `linked: false` | `identityToken` sai hoặc hết hạn: `onError` mã `401` |
| Không có | `onError` mã `IDENTITY_TOKEN_REQUIRED` khi chuỗi rỗng |

Việc cần làm phía đối tác: thêm API phía server để lấy `identityToken`, đổi mọi chỗ gọi `phone = ...` thành `identityToken = ...`, xử lý mã `401` và `IDENTITY_TOKEN_REQUIRED`. Dòng 0.7.x vẫn dùng `phone` và chỉ nhận bản vá lỗi.

## Cập nhật OTA mini-app

SDK đóng gói sẵn một bản mini-app (0.8.4: patch 0502) và có thể nhận bản vá web qua OTA mà không cần phát hành lại app. Đối tác không cần làm gì thêm ngoài việc chọn đúng [`setMode`](#setmode). Khi nâng cấp SDK, bản web đóng gói mới được cài thay cho bản đang có trên máy nếu bản đó cũ hơn (kể cả bản OTA cũ); bản OTA mới hơn bản đóng gói được giữ nguyên.

- SDK đọc manifest tại `https://static.payme.vn/frontend/miniapp-store/PayMEMiniAppVersion.json` và lấy mục theo mode hiện tại.
- Bản vá chỉ được cài khi mục trong manifest có chữ ký ECDSA P-256 hợp lệ của PayME, URL tải nằm trên `https://static.payme.vn`, kích thước không quá 128 MB, và file tải về đúng `size` và `sha256`. Nếu không, SDK giữ bản đang chạy.
- SDK không cài bản vá có số patch thấp hơn bản đang cài của cùng mode.

<details>
<summary>Dành cho PayME: định dạng mục manifest (SDK 1.0.24 trở lên)</summary>

Mỗi mode trên manifest (`miniapp_*`, `pm_*`, `bank`, …) có dạng:

```json
{
  "patch": 501,
  "latestMandatoryPatch": 501,
  "url": "https://static.payme.vn/.../p0501.zip",
  "sha256": "<64 ký tự hex thường: SHA-256 của file zip>",
  "size": 4287341,
  "sig": "<base64 của chữ ký ECDSA P-256/SHA-256 dạng DER>"
}
```

- Chuỗi được ký (UTF-8, không có xuống dòng cuối): `payme-ota-v1|<sdkVersion>|<mode>|<patch>|<latestMandatoryPatch>|<url>|<sha256>|<size>`. Số nguyên ghi dạng thập phân.
- `patch: 0` và `latestMandatoryPatch: 0` là kill switch: quay về bản đóng gói sẵn, không cần `sig`.
- Mục có `patch > 0` bị bỏ qua nếu: thiếu hoặc sai chữ ký; URL không phải `https://static.payme.vn`; `sha256` hoặc `size` sai định dạng; `size` vượt 128 MB; `latestMandatoryPatch < 1` hoặc `latestMandatoryPatch > patch`.
- Mỗi phiên bản SDK cần một mục ký riêng vì `sdkVersion` nằm trong chuỗi được ký. Ký bằng script trong repo mini-app web:

```sh
node script/sign-ota.cjs --zip sdkWebapp3-main.zip --sdk-version 1.0.24 --mode miniapp_product \
  --patch 501 --latest-mandatory 501 --url https://static.payme.vn/.../p0501.zip --key <khoá-ký.pem>
```

Quy trình phát hành xem [RELEASE.md](RELEASE.md).

</details>

## Lịch sử phiên bản và hỗ trợ

- Ghi chú phát hành: [GitHub Releases](https://github.com/PayME-Miniapp/native-android/releases).
- App demo: thư mục [`app`](app) (`app/src/main/java/com/example/app/MainActivity.kt`). Thông tin đối tác dùng cho demo đặt trong `local.properties` (xem `local.properties.example`).
- Báo lỗi: tạo [issue trên GitHub](https://github.com/PayME-Miniapp/native-android/issues) (không đính kèm khoá, `identityToken` hay dữ liệu người dùng), hoặc liên hệ đầu mối tích hợp PayME của bạn.
