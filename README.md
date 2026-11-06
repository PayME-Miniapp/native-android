**Các bước tích hợp:**

**Thêm maven jitpack.io**

Nếu project khai báo repository trong `build.gradle` của project:

```groovy
allprojects {
  repositories {
    ...
    maven { url "https://jitpack.io" }
  }
}
```

Nếu project dùng `dependencyResolutionManagement` trong `settings.gradle`:

```groovy
dependencyResolutionManagement {
  repositories {
    ...
    maven { url "https://jitpack.io" }
  }
}
```

**Thêm dependency thư viện PayMEMiniapp**

Cập nhật file `build.gradle` của module, thay `TAG` bằng phiên bản SDK, ví dụ `0.8.0`:

```groovy
dependencies {
  ...
  implementation 'com.github.PayME-Miniapp:native-android:TAG'
}
```

# Cách sử dụng Miniapp:

### Khởi tạo Miniapp:

| **Tham số**    | **Bắt buộc** | **Kiểu dữ liệu**       |
| -------------- | ------------ | ---------------------- |
| **context**    | Có           | Context                |
| **appId**      | Có           | String                 |
| **publicKey**  | Có           | String                 |
| **privateKey** | Có           | String                 |
| **env**        | Không        | ENV (`ENV.PRODUCTION`, `ENV.SANDBOX`), mặc định `ENV.PRODUCTION` |
| **locale**     | Không        | Locale (`Locale.vi`, `Locale.en`), mặc định `Locale.vi` |

Chú thích:
- appId: mỗi đối tác tích hợp PayME Miniapp sẽ được cấp 1 appId riêng biệt
- publicKey, privateKey: cặp key được gen khi đăng ký đối tác với PayME
- env: môi trường miniapp mà đối tác muốn sử dụng
- locale: ngôn ngữ hiển thị của Miniapp. Có thể đổi sau bằng `setLanguage`

```kotlin
import com.payme.sdk.PayMEMiniApp
payMEMiniApp = PayMEMiniApp(
    this,
    "your appId here",
    "your publicKey here",
    "your privateKey here",
    ENV.PRODUCTION,
    Locale.vi
)
```

### Set up các listeners:

Sử dụng hàm này để thiết lập việc hứng các events onResponse hoặc onError được bắn ra trong quá trình thao tác với Miniapp

```kotlin
 fun setUpListener(
    onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit)?,
    onError: ((ActionOpenMiniApp, PayMEError) -> Unit)?
)
```

| **Tham số**    | **Bắt buộc** | **Kiểu dữ liệu**                         |
| -------------- | ------------ | ---------------------------------------- |
| **onResponse** | Không        | (ActionOpenMiniApp, JSONObject?) -> Unit |
| **onError**    | Không        | (ActionOpenMiniApp, PayMEError?) -> Unit |

Chú thích:
- onResponse: event onResponse được bắn khi kết thúc 1 action thao tác Miniapp (ví dụ: thanh toán thành công), event này được bắn kèm action tạo ra event này và 1 JSONObject chứa các dữ liệu thêm
- onError: event onError được bắn khi có lỗi xảy ra trong quá trình thao tác với Miniapp, event này được bắn kèm action đang thao tác và 1 PayMEError chứa thông tin thêm về lỗi

Chi tiết các kiểu dữ liệu

**ActionOpenMiniApp:** (action thao tác Miniapp)
| **Giá trị** | **Giải thích** |
|-----------------|-----------------------------------------------------------------------------------------------------------|
| **PAYME** | Dùng riêng cho app ví PayME |  
| **OPEN** | Nếu chưa kích hoạt tài khoản ví PayME thì kích hoạt, nếu đã kích hoạt thì mở giao diện trang chủ ví PayME |
| **PAY** | Mở giao diện thanh toán đơn hàng |
| **GET_BALANCE** | Lấy số dư ví PayME |
| **SERVICE** | Mở giao diện thanh toán dịch vụ |
| **DEPOSIT** | Mở giao diện nạp tiền |
| **WITHDRAW** | Mở giao diện rút tiền |
| **TRANSFER** | Mở giao diện chuyển tiền |
| **KYC** | Mở giao diện kyc |
| **PAYMENT** | Mở giao diện thanh toán giao dịch do đối tác tạo sẵn |
| **TRANSFER_QR** | Mở giao diện chuyển tiền từ ví PayME đến tài khoản đích do đối tác truyền vào |
| **GET_ACCOUNT_INFO** | Lấy thông tin tài khoản PayME |

**PayMEError:** (lỗi trong quá trình thao tác Miniapp)
| **Thuộc tính** | **Kiểu dữ liệu** | **Giải thích** |
|-------------------|-----------------------------------------|-------------------------------------------------------------------------------|
| **type** | enum "MiniApp", "UserCancel", "Network" | Nhóm lỗi: lỗi trong Miniapp, người dùng đóng Miniapp hoặc lỗi do kết nối mạng |  
| **code** | String | Mã lỗi |
| **description** | String | Miêu tả lỗi |

Ví dụ sử dụng:

```kotlin
    payMEMiniApp!!.setUpListener(
    onResponse = { actionOpenMiniApp: ActionOpenMiniApp, json: JSONObject? ->
        Log.d(PayMEMiniApp.TAG, "onSuccess action: $actionOpenMiniApp ${json?.toString()}")
    },
    onError = { actionOpenMiniApp: ActionOpenMiniApp, payMEError: PayMEError ->
        Toast.makeText(this, payMEError.description, Toast.LENGTH_LONG).show()
    }
)
```

### Tích hợp identityToken (SDK 0.8.0 — thay đổi không tương thích với 0.7.x)

Từ SDK 0.8.0 (SDK 1.0.24), SDK **không còn nhận số điện thoại** (`phone`). App đối tác truyền `identityToken`: chuỗi do BE PayME ký cho người dùng, gắn với `appId` và có hạn dùng.

1. BE đối tác gọi API cấp chuỗi ký của BE PayME (server tới server) với số điện thoại của người dùng. Không gọi API này từ app.
2. App đối tác truyền `identityToken` vào các `OpenMiniAppData`, `getBalance(identityToken)` và `getAccountInformation(identityToken)`.
3. Chuỗi sai hoặc hết hạn: Miniapp đóng và trả lỗi `401` qua `onError`.
   `getBalance` và `getAccountInformation` cũng trả `onError` với code `401` (không còn trả `linked: false`).
4. Thiếu chuỗi (rỗng) ở action bắt buộc, `getBalance` hoặc `getAccountInformation`: SDK không mở Miniapp, trả `onError` với code `IDENTITY_TOKEN_REQUIRED`.

| Action | identityToken |
| --- | --- |
| `OpenMiniAppOpenData`, `OpenMiniAppKYCData`, `OpenMiniAppServiceData`, `OpenMiniAppDepositData`, `OpenMiniAppWithdrawData`, `OpenMiniAppTransferData`, `OpenMiniAppPaymentData`, `OpenMiniAppTransferQRData` | Bắt buộc (`String`) |
| `OpenMiniAppPaymentDirectData` (PAYMENT) | Tuỳ chọn (`String? = null`). Có: trả được bằng ví PayME. Không có: chỉ thanh toán như khách vãng lai (thẻ, QR) |
| `OpenMiniAppPayMEData` (PAYME) | Không có tham số; người dùng tự nhập số điện thoại và đăng nhập bằng PIN như cũ |

Nâng cấp từ 0.7.x: đổi `phone = ...` thành `identityToken = ...` ở mọi chỗ gọi. Dòng 0.7.x (SDK 1.0.23) vẫn dùng `phone`.

**Bảo mật:** `identityToken` chỉ giữ trong bộ nhớ. Không ghi log, không lưu (SharedPreferences, Bundle, file, database) và không đưa lên URL. SDK không lưu chuỗi này và xoá tham chiếu khi đóng Miniapp.

### Hàm openMiniApp:

Đối tác dùng hàm này để mở giao diện PayME Miniapp sau khi đã khởi tạo

```kotlin
 fun openMiniApp(
    openType: OpenMiniAppType = OpenMiniAppType.screen,
    openMiniAppData: OpenMiniAppDataInterface,
)
```

| **Tham số**         | **Bắt buộc** | **Kiểu dữ liệu**         | **Giải thích**                                                       |
| ------------------- | ------------ | ------------------------ | -------------------------------------------------------------------- |
| **openType**        | Có           | enum "screen", "modal"   | Mở Miniapp theo giao diện toàn màn hình hoặc modal trươt từ dưới lên |
| **openMiniAppData** | Có           | OpenMiniAppDataInterface | Thông tin thêm tùy vào loại action                                   |

Chi tiết các OpenMiniAppData:

**OpenMiniAppOpenData:** đối tác dùng action này khi muốn mở giao diện trang chủ ví PayME để sử dụng các dịch vụ tiện ích của PayME. Nếu chưa kích hoạt tài khoản ví PayME thì kích hoạt, nếu đã kích hoạt thì mở giao diện trang chủ ví PayME
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |

**OpenMiniAppPaymentData:** đối tác dùng action này khi muốn mở giao diện thanh toán của Miniapp
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |
| **paymentData** | Có | PaymentData | Thông tin thêm để phục vụ việc thanh toán |

Chi tiết PaymentData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------------------------------------------------|
| **transactionId** | Có | String | Mã giao dịch |  
| **amount** | Có | Int | Tổng số tiền giao dịch |  
| **note** | Không | String | Ghi chú của giao dịch |
| **ipnUrl** | Không | String | Đường dẫn để server PayME ipn đến khi giao dịch có tiến triển (thành công/thất bại) |
| **extraData** | Không | Map<String, Any> | Dữ liệu bổ sung do đối tác quy định, được trả về đối tác khi thanh toán thành công |
| **isShowResult** | Không | Boolean | Có hiển thị màn hình kết quả của PayME không (mặc định: true) |

**OpenMiniAppDepositData:** đối tác dùng action này khi muốn mở giao diện nạp tiền vào ví PayME
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |
| **additionalData** | Có | DepositWithdrawTransferData | Thông tin thêm để phục vụ việc |

**OpenMiniAppWithdrawData:** đối tác dùng action này khi muốn mở giao diện rút tiền ra ví PayME
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |
| **additionalData** | Có | DepositWithdrawTransferData | Thông tin thêm để phục vụ việc |

**OpenMiniAppTransferData:** đối tác dùng action này khi muốn mở giao diện chuyển tiền
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |
| **additionalData** | Có | DepositWithdrawTransferData | Thông tin thêm để phục vụ việc |

Chi tiết DepositWithdrawTransferData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|-------------------------------------------------------|
| **description** | Không | String | Miêu tả giao dịch |  
| **amount** | Không | Int | Tổng số tiền giao dịch |
| **extraData** | Không | Map<String, Any> | Dữ liệu bổ sung do đối tác quy định, được trả về đối tác khi giao dịch thành công |
| **isBackToApp** | Không | Boolean | Quay về app đối tác sau khi hoàn tất |
| **isShowResult** | Không | Boolean | Có hiển thị màn hình kết quả của PayME không (mặc định: true) |

**OpenMiniAppKYCData:** đối tác dùng action này khi muốn mở giao diện kyc
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |

**OpenMiniAppServiceData:** đối tác dùng action này khi muốn mở giao diện thanh toán dịch vụ
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|----------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |
| **additionalData** | Có | ServiceData | Thông tin dịch vụ cần thanh toán |

Chi tiết ServiceData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|-------------------------------------------------------|
| **service** | Có | String | Mã dịch vụ, ví dụ `POWE` (điện), `WATE` (nước), `ADSL` (internet), `TIVI` (truyền hình), `PPMB` (điện thoại trả sau), `MOBILE_CARD`, `MOBILE_TOPUP`, `GAME_CARD` |
| **extraData** | Không | Map<String, Any> | Dữ liệu bổ sung do đối tác quy định, được trả về đối tác khi thanh toán thành công |
| **isBackToApp** | Không | Boolean | Quay về app đối tác sau khi hoàn tất |
| **isShowResult** | Không | Boolean | Có hiển thị màn hình kết quả của PayME không (mặc định: true) |

**OpenMiniAppPaymentDirectData:** (action PAYMENT) đối tác dùng action này khi đã tạo sẵn giao dịch ở hệ thống của mình và muốn mở giao diện thanh toán giao dịch đó
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------|
| **identityToken** | Không | String? | Có: trả được bằng ví PayME. Không có: thanh toán như khách vãng lai (thẻ, QR) |
| **paymentDirectData** | Có | PaymentDirectData | Thông tin giao dịch |

Chi tiết PaymentDirectData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------------------------------------------------|
| **transaction** | Có | String | Mã giao dịch |
| **extraData** | Không | Map<String, Any> | Dữ liệu bổ sung do đối tác quy định, được trả về đối tác khi thanh toán thành công |
| **isShowResult** | Không | Boolean | Có hiển thị màn hình kết quả của PayME không (mặc định: true) |

**OpenMiniAppTransferQRData:** đối tác dùng action này khi muốn chuyển tiền từ ví PayME đến tài khoản đích do đối tác truyền vào
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------|
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |
| **transferQRData** | Có | TransferQRData | Thông tin chuyển tiền |

Chi tiết TransferQRData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------------------------------------------------|
| **amount** | Có | Int | Số tiền giao dịch (nếu không có số tiền thì truyền 0) |
| **bankNumber** | Có | String | Số thẻ hoặc số tài khoản ngân hàng |
| **swiftCode** | Có | String | Mã swiftCode của ngân hàng |
| **cardHolder** | Có | String | Tên chủ thẻ |
| **note** | Không | String | Ghi chú của giao dịch |
| **partnerTransaction** | Không | String | Mã giao dịch của đối tác |
| **extraData** | Không | Map<String, Any> | Dữ liệu bổ sung do đối tác quy định, được trả về đối tác khi thanh toán thành công |
| **isShowResult** | Không | Boolean | Có hiển thị màn hình kết quả của PayME không (mặc định: true) |

**OpenMiniAppPayMEData:** (action PAYME) mở ví PayME. Không có tham số; người dùng tự nhập số điện thoại và đăng nhập bằng PIN

### Hàm getBalance

Đối tác dùng hàm này để lấy thông tin số dư ví PayME của tài khoản, kết quả sẽ được trả về ở event onResponse, action GET_BALANCE

```kotlin
 fun getBalance(
    identityToken: String,
)
```

| **Tham số** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích**                                                      |
| ----------- | ------------ | ---------------- | ------------------------------------------------------------------- |
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |

### Hàm getAccountInformation

Đối tác dùng hàm này để lấy thông tin tài khoản PayME, kết quả sẽ được trả về ở event onResponse, action GET_ACCOUNT_INFO

```kotlin
 fun getAccountInformation(
    identityToken: String,
)
```

| **Tham số** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích**                                                      |
| ----------- | ------------ | ---------------- | ------------------------------------------------------------------- |
| **identityToken** | Có | String | Chuỗi do BE PayME ký cho người dùng (xem mục Tích hợp identityToken) |

### Hàm setLanguage

Đối tác dùng hàm này để đổi ngôn ngữ hiển thị của Miniapp

```kotlin
fun setLanguage(lang: Locale)
```

| **Tham số** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
| ----------- | ------------ | ---------------- | -------------- |
| **lang** | Có | Locale | `Locale.vi` hoặc `Locale.en` |

### Hàm close

Đối tác dùng hàm này để đóng Miniapp đang mở

```kotlin
fun close()
```

# Cập nhật OTA của miniapp (SDK 1.0.24+)

SDK chỉ tải bản vá web khi mục tương ứng trong manifest có chữ ký hợp lệ. Mỗi mode trên manifest (`miniapp_*`, `pm_*`, `bank`, …) có dạng:

```json
{
  "patch": 500,
  "latestMandatoryPatch": 500,
  "url": "https://static.payme.vn/.../p0500.zip",
  "sha256": "<64 ký tự hex thường: SHA-256 của file zip>",
  "size": 4287341,
  "sig": "<base64 của chữ ký ECDSA P-256/SHA-256 dạng DER>"
}
```

- Chuỗi được ký (UTF-8, không có xuống dòng cuối): `payme-ota-v1|<sdkVersion>|<mode>|<patch>|<latestMandatoryPatch>|<url>|<sha256>|<size>`. Số nguyên ghi dạng thập phân.
- `patch: 0` và `latestMandatoryPatch: 0` là kill switch, quay về bản web đóng gói sẵn; không cần `sig`.
- Mục có `patch > 0` bị bỏ qua (giữ bản đang chạy) nếu: thiếu hoặc sai chữ ký, URL không phải `https://static.payme.vn`, `sha256`/`size` sai định dạng, `size` vượt giới hạn, hoặc `latestMandatoryPatch > patch`.
- Sau khi tải, file phải đúng `size` byte và đúng `sha256`; nếu không sẽ bị xoá. SDK không cài bản vá thấp hơn bản đang cài của cùng mode.
- Ký một mục bằng script trong repo web:

```sh
node script/sign-ota.cjs --zip sdkWebapp3-main.zip --sdk-version 1.0.24 --mode miniapp_product \
  --patch 500 --latest-mandatory 500 --url https://static.payme.vn/.../p0500.zip --key <khoá-ký.pem>
```

Script in ra object của mode để dán vào manifest. Mỗi phiên bản SDK cần một mục ký riêng vì `sdkVersion` nằm trong chuỗi được ký.
