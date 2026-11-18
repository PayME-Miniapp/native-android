# Phát hành SDK

## Hai dòng phát hành

| Dòng | Branch | SDK | Bundle web đóng sẵn | Kiểm tra chữ ký OTA |
| --- | --- | --- | --- | --- |
| Hiện tại | `dev` | 1.0.24 trở lên | MiniApp patch 0500 trở lên | Có |
| Duy trì | `support/sdk-1.0.23` | 1.0.23 | MiniApp patch 0418–0499 | Không |

- Tính năng mới chỉ làm trên `dev`. `support/sdk-1.0.23` chỉ nhận sửa lỗi và vá bảo mật.
- Không merge `dev` vào `support/sdk-1.0.23`. SDK 1.0.23 chỉ chạy được bundle 04xx, SDK 1.0.24 trở lên chỉ chạy bundle 05xx trở lên.
- Lỗi có ở cả hai dòng: sửa trên `support/sdk-1.0.23` trước, sau đó cherry-pick lên `dev`.

## Các bước phát hành

1. Nâng version SDK ở `sdk/build.gradle` (`sdk_version`) khi ra SDK mới.
2. Thay `sdk/src/main/assets/www/sdkWebapp3-main.zip` bằng zip MiniApp của đúng dòng (xem `RELEASE.md` của repo MiniApp). Đồng thời nâng `MiniAppSourceConstants.BUNDLED_PATCH` lên đúng số patch của zip (zip không chứa số này; OTA có patch ≤ số này bị bỏ qua).
3. Chạy unit test và quét key: `python3 -I -B scripts/check-private-keys.py` phải không báo file nào.
4. Với SDK mới thuộc dòng hiện tại, thêm entry cho version đó vào `PayMEMiniAppVersion.json` và ký bằng `script/sign-ota.cjs --sdk-version <version>` ở repo MiniApp. SDK bỏ qua entry không có chữ ký hợp lệ và dùng bundle đóng sẵn.
5. Phát hành: nâng `maven_version` trong `sdk/build.gradle` và gắn tag JitPack. Dòng 1.0.23 dùng `0.7.x`, dòng 1.0.24 trở lên dùng `0.8.x` trở lên (từ 0.9.0 SDK có sẵn key "app"; 0.10.x hiện tại).
   1. `./gradlew :sdk:testDebugUnitTest :sdk:assembleRelease`.
   2. Sửa dòng "Phiên bản hiện tại" và các dòng `native-android:<version>` trong `README.md`.
   3. Push `dev`, tag `<version>`, tạo GitHub Release.
   4. Kiểm tra JitPack build xong: `curl -s https://jitpack.io/api/builds/com.github.PayME-Miniapp/native-android/<version>` có `"status" : "ok"` (lần đầu có thể mất vài phút; mở `https://jitpack.io/com/github/PayME-Miniapp/native-android/<version>/build.log` nếu lỗi).
   5. Sau đó nâng wrapper RN/Flutter (xem `RELEASE.md` của từng repo).

## Thứ tự phát hành chung

Web patch (OTA/zip) → native-android → iOS (Release trên `native-ios`, repo spec `PayME-Miniapp/Specs`, package `payme-miniapp-spm`; xem `RELEASE.md` của repo SDK iOS) → RN + Flutter → repo tài liệu (`payme-mini-app`, `payme-miniapp-flutter`) → ví PayME, demo-dpp.

## Chữ ký OTA (dòng hiện tại)

- SDK tin một entry `patch > 0` chỉ khi chữ ký ECDSA P-256 khớp với một public key trong `OtaManifest.PINNED_PUBLIC_KEYS` (`sdk/src/main/java/com/payme/sdk/ui/miniapp/source/OtaManifestVerifier.kt`).
- Đổi khoá: thêm public key mới vào danh sách và phát hành SDK trước, chuyển sang ký bằng khoá mới sau khi SDK mới đã phổ biến, rồi gỡ key cũ ở bản kế tiếp.
