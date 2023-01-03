**Các bước tích hợp:**

**Update file build.gradle project **

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

**Cập nhật file build.gradle module **

TAG: version hiện tại của sdk, ví dụ 0.1.1

```java
dependencies {
...
    implementation 'com.github.PayME-Miniapp:native-android:TAG'
...
}
```

# Cách sử dụng SDK:

### Khởi tạo SDK:


| **Tham số**           | **Bắt buộc** | 
| --------------------- | ------------ | 
| **context**           | Yes          |                                                            

```kotlin
import com.payme.sdk.PayMEMiniApp
payMEMiniApp = PayMEMiniApp(context)
```

### Mở module ví PayME:

```kotlin
 fun openMiniApp()
```

Ví dụ:

```kotlin
    payMEMiniApp!!.openMiniApp()
```

