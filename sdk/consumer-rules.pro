# Rule R8/ProGuard áp dụng cho app tích hợp SDK (consumerProguardFiles).
# App đối tác bật minify không cần tự thêm các rule dưới đây.

# eKYC Kalapa và thư viện NFC/OCR (FIS) mà Kalapa gói kèm.
-keep class vn.kalapa.ekyc.** { *; }
-keep class com.fis.ekyc.** { *; }
-keep class com.fis.nfc.** { *; }

# Model Gson: giữ tên trường có @SerializedName.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Class tuỳ chọn mà thư viện eKYC/NFC tham chiếu nhưng không đi kèm (R8 báo "Missing class").
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.googlecode.tesseract.android.TessBaseAPI
-dontwarn com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
-dontwarn io.reactivex.Observable
-dontwarn io.reactivex.Scheduler
-dontwarn io.reactivex.android.schedulers.AndroidSchedulers
-dontwarn io.reactivex.disposables.Disposable
-dontwarn io.reactivex.functions.Consumer
-dontwarn io.reactivex.schedulers.Schedulers
-dontwarn net.sf.scuba.tlv.TLVInputStream
-dontwarn org.apache.commons.io.IOUtil
-dontwarn org.jmrtd.lds.DataGroup
-dontwarn org.jnbis.Bitmap
-dontwarn org.jnbis.WsqDecoder
-dontwarn vn.kalapa.nfc.R$layout

# ML Kit (nhận diện khuôn mặt, đồ vật khi chụp CCCD) tạo registrar bằng reflection qua constructor rỗng.
# R8 full mode (AGP 9 mặc định) bỏ constructor nếu chỉ -keep class → "NoSuchMethodException ...Registrar.<init>".
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
