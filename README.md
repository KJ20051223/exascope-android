# ExaScope

一个原生 Android **纯搜索客户端**。填写自己的 Exa API Key，定义默认搜索范围，搜索时自动把范围描述注入查询，直接查看网页标题、摘录和来源链接。无需大模型 API。

## 下载与使用

在 [Releases](https://github.com/KJ20051223/exascope-android/releases/latest) 下载签名的 APK，支持 **Android 8.0 及以上**。

1. 安装并打开 ExaScope，点击右上角「设置」。
2. 填入 API 地址（默认为 `https://api.exa.ai`）与自己的 Exa API Key。
3. 选择「文献 / 论文」「新闻报道」「技术文档」等预设，或直接填写范围描述。
4. 可选：设置 Exa 分类、限定来源域名、查询模板和每次结果数量。
5. 保存后输入关键词并搜索，可通过「查看实际查询」确认范围如何注入。

## 查询示例

默认范围：`学术文献、研究论文、预印本与期刊文章，优先原始研究来源`

输入：`钠离子电池`

实际查询：

```text
钠离子电池

搜索范围：学术文献、研究论文、预印本与期刊文章，优先原始研究来源
```

默认模板为 `{query}\n\n搜索范围：{scope}`，可以自行编辑。缺少占位符时应用仍会补入搜索内容或范围；范围留空时仅发送原始关键词。

范围描述影响语义相关性，**不保证结果严格只属于该范围**。需要硬限制时，配合 Exa `category` 与 `includeDomains` 参数使用。论文预设使用当前 Exa 文档中的 `publication` 分类。兼容代理若使用旧版分类，应选择「不限类别」后用范围和域名筛选。

## 功能

- 自定义 HTTPS API 基础地址或 `/search` 完整地址。
- Android Keystore / AES-GCM 加密保存 Key。
- 搜索范围与查询模板持久保存；不保存搜索历史。
- Auto / Lite 搜索模式、1–100 条结果、域名限制。
- 网页摘录、作者、日期、接口返回的费用与搜索耗时。
- 取消、空结果、网络超时、Key 错误、额度与限流提示。
- 打开来源、复制链接、展开长摘要。
- 跟随系统浅色 / 深色主题，支持字体缩放与横屏。
- 无 WebView、无广告、无分析追踪、无外部字体或图片请求。

## 数据与费用

搜索词、范围与筛选参数会直接发送至设置中的 API 服务，API Key 通过 `x-api-key` 请求头发送。请只配置自己信任的兼容服务。Key 不写入源码、日志或查询正文；不跟随 HTTP 重定向。应用关闭安卓备份，密钥不可随系统备份迁移，重新安装后需要重填。

应用开启 Exa 的 `moderation` 内容筛选。API 服务可能按请求、结果或内容提取计费，具体以 [Exa 官方价格](https://exa.ai/pricing) 为准。

## 本地编译

要求：JDK 17、Android SDK Platform 35、Build Tools 35.0.0。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 使用 `gradlew.bat`。开发版位于 `app/build/outputs/apk/debug/app-debug.apk`。

发布版需要自己的私有签名文件，**不要把签名文件或密码提交到仓库**：

```bash
export EXASCOPE_KEYSTORE=/private/path/exascope-release.jks
export EXASCOPE_STORE_PASSWORD='your-store-password'
export EXASCOPE_KEY_ALIAS=exascope
export EXASCOPE_KEY_PASSWORD='your-key-password'
./gradlew assembleRelease
```

未设置签名变量时会生成未签名的发布构建。GitHub Actions 自动运行单元测试、Lint、编译和 Android 设备冒烟测试，提供开发版 APK 与未签名发布产物。正式 Release 附件由维护者使用私有签名后上传；开发版与正式版签名不同，不能互相覆盖安装。

## 验证

单元测试覆盖范围注入、模板占位符、参数校验、域名标准化、HTTPS 端点、Unicode 请求、API Key 请求头、禁跟随重定向、结果解析及取消请求。设备测试检查 Keystore 加密存储和主界面启动。不包含真实 Exa Key，真实查询取决于用户自己的账号、网络与 API 兼容性。

## 技术

Java 17 · 原生 Android View · HttpURLConnection · Android Keystore。运行时不依赖第三方 SDK。

API 文档：[Exa Search](https://exa.ai/docs/reference/search)。本项目为独立客户端，与 Exa 无隶属关系。

MIT License.
