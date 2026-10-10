# 构建与签名

## 环境

- JDK 17+（javac、jar、java 在 PATH）
- Python 3
- Android SDK `platforms;android-35` 和 `build-tools;35.0.0`
- 回归测试额外需要 Node.js 20+

```sh
sdkmanager "platforms;android-35" "build-tools;35.0.0"
python test.py
python build.py --sdk /path/to/Android/Sdk
```

Windows 可将 SDK 路径改为 `C:/Android/Sdk`。也可设置 ANDROID_SDK_ROOT / ANDROID_HOME 后直接运行 `python build.py`。构建脚本支持 `--android-jar`、`--aapt`、`--d8-jar` 显式指定工具。生成 `build/local-ledger-unsigned.apk`。

## 自行签名（示例为 POSIX shell）

```sh
keytool -genkeypair -keystore ledger-release.jks -alias ledger -keyalg RSA -keysize 3072 -validity 10000
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -p -f 4 build/local-ledger-unsigned.apk build/local-ledger-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks ledger-release.jks --ks-key-alias ledger --out build/local-ledger-signed.apk build/local-ledger-aligned.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose build/local-ledger-signed.apk
adb install -r build/local-ledger-signed.apk
```

命令交互询问密码，不要把私钥或密码提交到 Git。新密钥无法覆盖使用另一密钥签名的已安装 APK。需要先在应用内导出 JSON，卸载旧签名版本，再安装、导入。

Release v1.2.0 APK 使用本地开发证书，指纹 SHA256：`084af6e717314cd1283184807f8769e6de1144783f69b76f48f898151cb85c8a`。私钥不在仓库。构建与测试由本地执行，Release 的已签名安装包手动发布；仓库不配置 GitHub Actions CI。

## OCR 二进制来源

ppocr-rs 的 `wasm/ppocr.mjs` 和模型 WASM 源自 commit `06972734ebdaf70f39c3d550880baf2037536773`。编译模型 WASM 可参考上游仓库；本项目将校验通过的产物随源码提供。

WASM SHA256：`7bb573390bb771b357a8fd4de06e575f167b70d9ad69c1ee4f60345117727134`。构建时会检查此摘要。PDF.js 6.4.299 的两个构建模块来自 `pdfjs-dist`。
