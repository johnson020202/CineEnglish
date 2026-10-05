# CineEnglish Android 客户端构建与发布签名指南

## 1. 使用 Android Studio 打开与构建

1. 打开 Android Studio (推荐 Hedgehog / Iguana / Jellyfish 或更新版本)。
2. 选择 **Open**，导航并选择目录：
   `/Users/xindao/.gemini/antigravity-ide/scratch/cine_english/android`
3. 等待 Gradle 同步完成（工程已配置 Gradle 8.7 与 AGP 8.5.1，兼容 JDK 17）。
4. 连接 Android 手机或启动模拟器，点击绿色 **Run** 按钮即可一键运行。

---

## 2. 命令行构建 APK

在终端中执行以下命令进行快速编译：

```bash
cd android

# 设置 JDK 17 环境变量
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH=$JAVA_HOME/bin:$PATH

# 构建调试版 APK
./gradlew assembleDebug
```

构建成功后，输出的 APK 路径位于：
`android/app/build/outputs/apk/debug/app-debug.apk`

### 安装到连接的设备
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. 调试签名 (Debug Keystore) 说明

- `app-debug.apk` 使用 Android SDK 默认生成的通用调试密钥（Debug Keystore）签名。
- 密钥库路径：`~/.android/debug.keystore`
- 别名：`androiddebugkey`，密码：`android`
- 调试签名仅用于个人安装测试，不可用于正式应用商店发布。

---

## 4. 正式发布签名模板 (Release Signing)

为保证私有密钥的绝对安全，**严禁将正式发布证书与密码提交至版本库**。

### 步骤 A：生成正式密钥库（如果尚未拥有）
```bash
keytool -genkeypair -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias cineenglish
```

### 步骤 B：创建本地密钥属性文件 `android/keystore.properties`（已加入 .gitignore）
```properties
storeFile=/path/to/my-release-key.jks
storePassword=your_keystore_password
keyAlias=cineenglish
keyPassword=your_key_password
```

### 步骤 C：在 `android/app/build.gradle.kts` 中配置发布签名
```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

### 步骤 D：构建正式包
```bash
./gradlew assembleRelease
```
输出路径：`android/app/build/outputs/apk/release/app-release.apk`
