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
2. Thay `sdk/src/main/assets/www/sdkWebapp3-main.zip` bằng zip MiniApp của đúng dòng (xem `RELEASE.md` của repo MiniApp).
3. Chạy unit test và quét key: `python3 -I -B scripts/check-private-keys.py` phải không báo file nào.
4. Với SDK mới thuộc dòng hiện tại, thêm entry cho version đó vào `PayMEMiniAppVersion.json` và ký bằng `script/sign-ota.cjs --sdk-version <version>` ở repo MiniApp. SDK bỏ qua entry không có chữ ký hợp lệ và dùng bundle đóng sẵn.
5. Phát hành: nâng `maven_version` trong `sdk/build.gradle` và gắn tag JitPack. Dòng 1.0.23 dùng `0.7.x`, dòng 1.0.24 trở lên dùng `0.8.x`.

## Chữ ký OTA (dòng hiện tại)

- SDK tin một entry `patch > 0` chỉ khi chữ ký ECDSA P-256 khớp với một public key trong `OtaManifest.PINNED_PUBLIC_KEYS` (`sdk/src/main/java/com/payme/sdk/ui/miniapp/source/OtaManifestVerifier.kt`).
- Đổi khoá: thêm public key mới vào danh sách và phát hành SDK trước, chuyển sang ký bằng khoá mới sau khi SDK mới đã phổ biến, rồi gỡ key cũ ở bản kế tiếp.
