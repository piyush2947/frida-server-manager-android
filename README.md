# Frida Server Manager

A modern Android application for managing Frida server installations on rooted devices. This app simplifies the process of downloading, installing, and running Frida server for dynamic analysis and reverse engineering.

## Features

- 🚀 **Automatic Installation**: Download and install Frida server directly from GitHub releases
- 📁 **Manual Installation**: Install from local Frida server binaries
- 🔄 **Version Management**: Switch between different Frida server versions
- 📱 **Architecture Detection**: Automatically detects device architecture (ARM64, ARM, x86, x86_64)
- 🖥️ **Server Management**: Start, stop, and monitor Frida server
- 📊 **Real-time Logs**: View server output and installation progress
- 🎨 **Modern UI**: Clean Material Design 3 interface built with Jetpack Compose

## Screenshots

*Add screenshots of your app here*

## Requirements

- Android 7.0 (API level 24) or higher
- Rooted Android device
- Superuser permissions

## Installation

### Option 1: Download APK
1. Go to [Releases](../../releases)
2. Download the latest APK file
3. Install on your rooted Android device
4. Grant superuser permissions when prompted

### Option 2: Build from Source
1. Clone this repository
2. Open in Android Studio
3. Build and install on your device

```bash
git clone https://github.com/piyush2947/frida-server-manager-android.git
cd frida-server-manager-android
./gradlew assembleDebug
./gradlew installDebug
```

## Usage

1. **Launch the app** on your rooted device
2. **Grant root permissions** when prompted
3. **Choose installation method**:
   - **Download**: Automatically fetch from GitHub releases
   - **Select File**: Install from local binary
4. **Select Frida version** from available releases
5. **Monitor installation** progress in real-time
6. **Start server** once installation completes

## Architecture Support

- ARM64 (arm64-v8a) - Most modern Android devices
- ARM (armeabi-v7a) - Older Android devices
- x86 - Android emulators and x86 devices
- x86_64 - 64-bit Android emulators

## Permissions

The app requires the following permissions:
- `INTERNET` - Download Frida server from GitHub
- `WRITE_EXTERNAL_STORAGE` - Save downloaded files
- `READ_EXTERNAL_STORAGE` - Read local Frida binaries
- Superuser access - Install and run Frida server

## Building

### Prerequisites
- Android Studio Arctic Fox or newer
- Android SDK 34
- JDK 17 or newer

### Release Build
To build a signed release APK:
1. Create a `keystore.properties` file in the root directory:
   ```
   storePassword=your_store_password
   keyPassword=your_key_password
   keyAlias=your_key_alias
   storeFile=path/to/your/keystore.keystore
   ```
2. Run: `./gradlew assembleRelease`
3. Find the signed APK in `app/build/outputs/apk/release/`

### Dependencies
- Jetpack Compose - Modern UI toolkit
- OkHttp - Network requests
- Gson - JSON parsing
- XZ for Java - Archive extraction

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Security

This app is designed for legitimate security research and educational purposes. Please use responsibly and only on devices you own or have explicit permission to test.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Disclaimer

This tool is for educational and research purposes only. Users are responsible for ensuring they comply with all applicable laws and regulations when using this software.

## Acknowledgments

- [Frida](https://frida.re/) - Dynamic instrumentation toolkit
- Android development community
- Contributors and users

## Support

If you encounter any issues or have questions:
1. Check existing [Issues](../../issues)
2. Create a new issue with detailed information
3. Include device information and logs if applicable

---

**⭐ Star this repository if you find it useful!**