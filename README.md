# Folder Only Manager Pro

<p align="center">
  <strong>Android 文件夹管理器 · Folder-first file manager</strong><br>
  Kotlin · Android Storage Access Framework · Min SDK 26
</p>

[简体中文](#简体中文) | [English](#english)

## 简体中文

Folder Only Manager Pro 是一个 Android 文件夹管理器示例项目。它通过 Android Storage Access Framework (SAF) 让用户选择并授权目录，并在应用内部提供文件夹优先的管理操作。

### 功能

- 使用系统目录选择器授权文件夹（`ACTION_OPEN_DOCUMENT_TREE`）
- 持久化已授予的目录访问权限
- 浏览授权目录和子目录
- 新建、重命名、删除文件夹
- 将当前目录递归压缩为 ZIP 并保存到用户选择的位置
- 深色简洁界面
- 不提供单文件选择/上传入口

### 重要限制

**本项目不能接管或强制修改所有第三方应用的上传窗口。** 普通 Android APK 无法把其他应用的 `ACTION_GET_CONTENT` / `ACTION_OPEN_DOCUMENT` 请求全局改造成文件夹选择。第三方应用只有在自身支持 `ACTION_OPEN_DOCUMENT_TREE` 或提供文件夹选择能力时，才能使用目录授权。若要全局强制限制，需要定制 Android 系统/ROM 或系统级集成。

### 环境要求

- Android Studio（建议较新稳定版）
- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

### 构建与运行

1. 克隆仓库：

   ```bash
   git clone https://github.com/YOUR_USERNAME/FolderOnlyManager.git
   cd FolderOnlyManager
   ```

2. 使用 Android Studio 打开项目根目录，等待 Gradle 同步。
3. 连接 Android 手机或启动模拟器。
4. 点击 **Run**，或执行：

   ```bash
   ./gradlew assembleDebug
   ```

5. Debug APK 输出位置：

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

### 项目结构

```text
FolderOnlyManager/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/folderonlymanager/MainActivity.kt
├── .github/workflows/android.yml
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── LICENSE
└── README.md
```

### 权限与隐私

应用通过用户主动选择目录来获得 SAF 授权，不申请广泛的存储管理权限。用户应只授权需要管理的目录。删除目录会同时删除其内部内容；压缩大型目录可能耗时较长并占用存储空间。

### 贡献

欢迎提交 Issue 和 Pull Request。提交前请确认没有上传签名密钥、密码或个人文件。

## English

Folder Only Manager Pro is an Android folder manager sample built with Kotlin and Android's Storage Access Framework (SAF).

Features include persisted folder access, folder browsing, create/rename/delete operations, and recursive ZIP export.

**Platform limitation:** A regular Android app cannot globally replace or force the upload/file picker UI used by other apps. Other apps must explicitly support `ACTION_OPEN_DOCUMENT_TREE` to request folder access. System-wide enforcement requires an OS/ROM-level integration.

See the Chinese section above for setup, build instructions, privacy notes, and project structure.


<!-- CI rebuild trigger -->


## Android 文件选择器集成（v3.0）

本版本新增 Android Storage Access Framework（SAF）DocumentsProvider。首次使用时，先打开 Folder Only Manager 并授权一个管理目录；之后，支持系统 SAF 的应用可在系统文件选择器的“浏览/位置”中访问该目录。该入口由 Android DocumentsUI 提供，不是普通应用列表中的强制接管。某些应用使用自定义上传器或不支持 SAF 时不会显示此来源。

文件通过只读 URI 提供给调用应用；目录结构可浏览。是否能选择整个文件夹取决于调用应用是否发起 `ACTION_OPEN_DOCUMENT_TREE`。
