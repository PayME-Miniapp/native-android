**Các bước tích hợp:**

**Thêm maven jitpack.io**

Update file build.gradle project

```kotlin
allprojects {
  repositories {
    ...
    maven {
        url "https://jitpack.io"
    }
 }
}
```

**Thêm dependency thư viện PayMEMiniapp**

Cập nhật file build.gradle module
TAG: version hiện tại của sdk, ví dụ 0.1.1

```java
dependencies {
...
    implementation 'com.github.PayME-Miniapp:native-android:TAG'
...
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
| **env**        | Có           | "PRODUCTION","SANDBOX" |

Chú thích:
- appId: mỗi đối tác tích hợp PayME Miniapp sẽ được cấp 1 appId riêng biệt
- publicKey, privateKey: cặp key được gen khi đăng ký đối tác với PayME
- env: môi trường miniapp mà đối tác muốn sử dụng

```kotlin
import com.payme.sdk.PayMEMiniApp
payMEMiniApp = PayMEMiniApp(
    this,
    "your appId here",
    "your publicKey here",
    "your privateKey here",
    ENV.PRODUCTION
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

**ActionOpenMiniApp:** (action thao tác Miniapp) enum "PAYME" | "OPEN" | "PAY" | "GET_BALANCE"
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
| **phone** | Có | String | Số điện thoại của tài khoản |

**OpenMiniAppPaymentData:** đối tác dùng action này khi muốn mở giao diện thanh toán của Miniapp
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------|
| **phone** | Có | String | Số điện thoại của tài khoản |
| **paymentData** | Có | PaymentData | Thông tin thêm để phục vụ việc thanh toán |

Chi tiết PaymentData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|--------------------------------------------------------------------------------------|
| **transactionId** | Có | String | Mã giao dịch |  
| **amount** | Có | Int | Tổng số tiền giao dịch |  
| **note** | Không | String | Ghi chú của giao dịch |
| **ipnUrl** | Không | String | Đường dẫn để server PayME ipn đến khi giao dịch có tiến triển (thành công/thất bại) |

**OpenMiniAppDepositData:** đối tác dùng action này khi muốn mở giao diện nạp tiền vào ví PayME
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **phone** | Có | String | Số điện thoại của tài khoản |
| **additionalData** | Có | DepositWithdrawTransferData | Thông tin thêm để phục vụ việc |

**OpenMiniAppWithDrawData:** đối tác dùng action này khi muốn mở giao diện rút tiền ra ví PayME
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **phone** | Có | String | Số điện thoại của tài khoản |
| **additionalData** | Có | DepositWithdrawTransferData | Thông tin thêm để phục vụ việc |

**OpenMiniAppTransferData:** đối tác dùng action này khi muốn mở giao diện chuyển tiền
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **phone** | Có | String | Số điện thoại của tài khoản |
| **additionalData** | Có | DepositWithdrawTransferData | Thông tin thêm để phục vụ việc |

Chi tiết DepositWithdrawTransferData:
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|------------------|-------------------------------------------------------|
| **description** | Không | String | Miêu tả giao dịch |  
| **amount** | Không | Int | Tổng số tiền giao dịch |

**OpenMiniAppKYCData:** đối tác dùng action này khi muốn mở giao diện kyc
| **Thuộc tính** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích** |
|---------------------|--------------|--------------------------------------|----------------------------------|
| **phone** | Có | String | Số điện thoại của tài khoản |

### Hàm getBalance

Đối tác dùng hàm này để lấy thông tin số dư ví PayME của tài khoản, kết quả sẽ được trả về ở event onResponse, action GET_BALANCE

```kotlin
 fun getBalance(
    phone: String,
)
```

| **Tham số** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích**                                                      |
| ----------- | ------------ | ---------------- | ------------------------------------------------------------------- |
| **phone**   | Có           | String           | Số điện thoại của tài khoản cần lấy số dư ví (không cần format +84) |

### Hàm getAccountInfo

Đối tác dùng hàm này để lấy thông tin tài khoản PayME, kết quả sẽ được trả về ở event onResponse, action GET_ACCOUNT_INFO

```kotlin
 fun getAccountInfo(
    phone: String,
)
```

| **Tham số** | **Bắt buộc** | **Kiểu dữ liệu** | **Giải thích**                                                      |
| ----------- | ------------ | ---------------- | ------------------------------------------------------------------- |
| **phone**   | Có           | String           | Số điện thoại của tài khoản cần lấy số dư ví (không cần format +84) |

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
