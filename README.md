# 本地记账 OCR · Local Ledger OCR

[![Android build and regression tests](https://github.com/trotsky1997/local-ledger-ocr/actions/workflows/build.yml/badge.svg)](https://github.com/trotsky1997/local-ledger-ocr/actions/workflows/build.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

一个免登录、在本机保存账本的独立 Android 应用。使用 [ppocr-rs](https://github.com/trotsky1997/ppocr-rs) 离线识别账单截图，支持微信、支付宝、银行通知和新短信记账，以及流水文件导入。

**[下载 APK](https://github.com/trotsky1997/local-ledger-ocr/releases/latest)** · [使用说明](docs/USAGE.md) · [构建与签名](docs/BUILD.md) · [测试](#测试)

## 功能

- **本地截图 OCR**：模型随 APK 提供，截图不上传；提取金额、商户、时间、收支方向和分类建议。
- **多笔核对**：按交易卡片切分，一张截图可识别多笔。排除余额、可用额度、日报、月度统计；核对后入账，也可保存为待确认。
- **消息记账**：微信支付/收款助手、支付宝、常见银行 App 的收支通知；新银行短信。四个开关默认开启，仍需授予相应系统权限。
- **微信可见卡片读取（可选）**：系统无障碍权限开启后，处理微信金融助手页面上的可见交易卡片。
- **流水导入**：微信、支付宝 CSV/XLSX；银行 CSV/XLSX；招商银行文本型“交易流水”PDF。
- **小米记账兼容**：六列 XLSX 导入与导出，保留分类子类、备注、秒级时间和原文件零金额记录。
- **本地 SQLite + JSON 备份**：金额以整数分存储；待确认不计入汇总；相同来源重复导入会去重。

## 要求与边界

Android 8.0+，支持 ES modules、Web Workers 与 WebAssembly SIMD 的 Android System WebView。包名 `local.codex.ledger`，当前版本 `1.2.0`（versionCode 3）。应用不依赖小米钱包。

- 通知监听只能获得系统实际交付的通知内容。微信可能不发送完整金额；此时可使用截图 OCR。
- 无障碍通道只处理当前可见卡片，不读取未打开的历史聊天。这条通道已实现，当前验证记录尚不包含真实微信页面采集。
- 银行 App 与短信的解析规则已做回归测试；真实消息投递仍依赖系统权限、通知内容和后台运行状态。
- 当前银行 PDF 模板针对招商银行“交易流水”。其他银行 PDF、扫描型 PDF 不保证支持；可优先使用 CSV/XLSX 或截图 OCR。
- 不同渠道可能描述同一笔交易。微信、支付宝、银行、小米之间缺少共同交易号，跨来源重叠流水需人工核对。
- 卸载会删除本地数据。迁移前请导出 JSON 完整备份。

## 构建

需要 JDK 17+、Python 3、Android SDK platform android-35 与 build-tools 35.0.0。

```sh
python build.py --sdk /path/to/Android/Sdk
```

生成 `build/local-ledger-unsigned.apk`。需使用自己的密钥签名后安装，步骤见 [BUILD.md](docs/BUILD.md)。仓库不包含发布私钥。自行换签名不能直接覆盖 Release APK，重装前应备份账本。

## 测试

需要 JDK 17+、Python 3 和 Node.js 20+，不需要 Android SDK：

```sh
python test.py
```

公开测试仅使用合成交易文本和生成的表格，不包含任何用户账单或截图。覆盖金融消息过滤、余额/额度排除、CSV 引号与换行、GB18030、退款、稳定 ID、银行借贷方向、小米六列往返和多卡片解析。CI 同时构建未签名 APK。

## 结构

| 路径 | 内容 |
|---|---|
| `src/LocalLedgerActivity.java` | SQLite、文件选择、原生桥接、受限本地资源服务 |
| `src/BankRules.java`、`TransactionCapture.java` | 金额、商户、时间与交易过滤，保存待确认 |
| `src/LedgerNotifications.java`、`LedgerSms.java` | 通知与新短信接收 |
| `src/LedgerAccessibility.java` | 可选的微信可见金融卡片读取 |
| `src/StatementImporter.java`、`MiExport.java` | CSV/XLSX 导入、小米格式导出与去重 |
| `assets/local-ledger/` | WebView 界面、OCR Worker、WASM 模型、PDF.js |
| `tests/` | 合成数据回归测试 |

## 隐私

OCR 和流水解析在本机执行，账本保存在应用私有 SQLite。没有登录、云同步、遥测或远程 OCR 接口。界面资源由仅绑定 `127.0.0.1` 的本地服务提供；Android `INTERNET` 权限用于该回环连接。详见 [PRIVACY.md](docs/PRIVACY.md)。

## 许可证与第三方来源

应用新增源码采用 [Apache-2.0](LICENSE)。

- **ppocr-rs 0.11.4**：[仓库](https://github.com/trotsky1997/ppocr-rs)，commit `06972734ebdaf70f39c3d550880baf2037536773`；WASM 摘要与随附 `ARTIFACT.json` 一致，保留上游 NOTICE、模型来源与依赖许可证。
- **PDF.js 6.4.299**：[仓库](https://github.com/mozilla/pdf.js)，Apache-2.0，许可证见 `assets/local-ledger/PDFJS-LICENSE`。

微信、支付宝、小米及银行名称仅用于说明兼容的交易格式，本项目与这些机构无隶属关系。
