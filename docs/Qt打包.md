# DocAI Qt 桌面客户端打包教程（Inno Setup）

本文档介绍如何将 DocAI Qt 桌面客户端编译为 Release 版本，使用 `windeployqt` 收集依赖，并使用 Inno Setup 制作 Windows 安装包。

---

## 一、环境准备

| 工具 | 说明 |
|------|------|
| Qt 5.x（推荐 5.15.2） | 已安装 MinGW 或 MSVC 编译器 |
| qmake | Qt 自带，需确保在 PATH 中 |
| windeployqt | Qt 自带部署工具 |
| Inno Setup 6 | [下载地址](https://jrsoftware.org/isdl.php) |

> **注意**：确保 Qt 的 `bin` 目录已加入系统 PATH，例如 `C:\Qt\5.15.2\mingw81_64\bin`。

---

## 二、编译 Release 版本

### 2.1 打开 Qt 命令行

在开始菜单中搜索并打开 **Qt 5.15.2 (MinGW 8.1.0 64-bit)** 或对应的 MSVC 命令行工具。

### 2.2 进入项目目录

```bash
cd /d F:\DocAI\DocAI
```

### 2.3 使用 qmake 生成 Makefile

```bash
mkdir build-release
cd build-release
qmake ../DocAI.pro -spec win32-g++ CONFIG+=release CONFIG+=qtquickcompiler
```

如果使用 MSVC 编译器：
```bash
qmake ../DocAI.pro -spec win32-msvc CONFIG+=release
```

### 2.4 编译

MinGW：
```bash
mingw32-make -j8
```

MSVC（需在 Visual Studio 命令行中）：
```bash
nmake
```

编译完成后，`release` 目录下会生成 `DocAI.exe`。

---

## 三、使用 windeployqt 收集依赖

### 3.1 创建打包目录

```bash
mkdir F:\DocAI\DocAI\package
copy release\DocAI.exe F:\DocAI\DocAI\package\
```

### 3.2 运行 windeployqt

```bash
windeployqt --release --no-translations --no-opengl-sw F:\DocAI\DocAI\package\DocAI.exe
```

> **关键参数说明**：
> - `--release`：仅收集 Release 版本 DLL
> - `--no-translations`：不包含翻译文件（可选）
> - `--no-opengl-sw`：不包含软件 OpenGL（可选）

### 3.3 验证收集结果

运行 windeployqt 后，`package` 目录应包含以下结构：

```
package/
├── DocAI.exe
├── Qt5Core.dll
├── Qt5Gui.dll
├── Qt5Widgets.dll
├── Qt5Network.dll              ← 网络模块（必须）
├── libgcc_s_seh-1.dll          ← MinGW 运行时（MinGW 编译时）
├── libstdc++-6.dll             ← MinGW 运行时（MinGW 编译时）
├── libwinpthread-1.dll         ← MinGW 运行时（MinGW 编译时）
├── platforms/
│   └── qwindows.dll            ← Windows 平台插件（必须）
├── styles/
│   └── qwindowsvistastyle.dll  ← 样式插件
├── imageformats/               ← 图片格式支持
│   ├── qgif.dll
│   ├── qjpeg.dll
│   ├── qico.dll
│   └── qsvg.dll
├── tls/                        ← TLS/HTTPS 支持（必须）
│   └── qschannelbackend.dll    ← 或 qopensslbackend.dll
└── networkinformation/         ← 网络信息（Qt 6 特有，Qt 5 可能无此目录）
```

### 3.4 手动检查并补充遗漏 DLL

windeployqt 可能遗漏某些 DLL。请手动检查以下关键文件是否存在：

| 文件 | 所属目录 | 说明 | 必须 |
|------|----------|------|------|
| Qt5Core.dll | 根目录 | Qt 核心 | ✅ |
| Qt5Gui.dll | 根目录 | GUI 模块 | ✅ |
| Qt5Widgets.dll | 根目录 | 控件模块 | ✅ |
| Qt5Network.dll | 根目录 | 网络模块 | ✅ |
| qwindows.dll | platforms/ | 平台插件 | ✅ |
| qwindowsvistastyle.dll | styles/ | Vista 样式 | ✅ |
| libgcc_s_seh-1.dll | 根目录 | MinGW 运行时 | ✅（MinGW） |
| libstdc++-6.dll | 根目录 | MinGW 运行时 | ✅（MinGW） |
| libwinpthread-1.dll | 根目录 | MinGW 运行时 | ✅（MinGW） |
| qschannelbackend.dll | tls/ | HTTPS 支持 | ✅ |

**如果某个 DLL 缺失**，可在 Qt 安装目录下搜索并手动复制：

```bash
# 例如查找 Qt5Network.dll
dir /s /b C:\Qt\5.15.2\mingw81_64\bin\Qt5Network.dll
# 复制到 package 目录
copy C:\Qt\5.15.2\mingw81_64\bin\Qt5Network.dll F:\DocAI\DocAI\package\

# TLS 插件
copy C:\Qt\5.15.2\mingw81_64\plugins\tls\*.dll F:\DocAI\DocAI\package\tls\
```

### 3.5 在干净环境中测试

**在没有安装 Qt 的电脑（或虚拟机）上测试运行 `DocAI.exe`**，确保不报错。如果弹出缺少 DLL 的错误，将对应的 DLL 复制到 `package` 目录即可。

可使用 [Dependencies](https://github.com/lucasg/Dependencies) 工具（开源的 DLL 依赖分析器）查看 exe 依赖树：

```bash
Dependencies.exe F:\DocAI\DocAI\package\DocAI.exe
```

---

## 四、使用 Inno Setup 制作安装包

### 4.1 安装 Inno Setup

从官网下载安装 [Inno Setup 6](https://jrsoftware.org/isdl.php)。

### 4.2 创建 Inno Setup 脚本

在项目根目录下创建 `setup.iss` 文件：

```iss
; DocAI 安装脚本
; Inno Setup 6

#define MyAppName "DocAI"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "DocAI Team"
#define MyAppURL "http://docai.sa1.tunnelfrp.com"
#define MyAppExeName "DocAI.exe"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF0123456789}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=installer_output
OutputBaseFilename=DocAI-Setup-{#MyAppVersion}
SetupIconFile=
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "quicklaunchicon"; Description: "{cm:CreateQuickLaunchIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked; OnlyBelowVersion: 6.1; Check: not IsAdminInstallMode

[Files]
; 主程序
Source: "package\DocAI.exe"; DestDir: "{app}"; Flags: ignoreversion

; Qt 核心 DLL
Source: "package\Qt5Core.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "package\Qt5Gui.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "package\Qt5Widgets.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "package\Qt5Network.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "package\Qt5Svg.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

; MinGW 运行时（MinGW 编译时需要）
Source: "package\libgcc_s_seh-1.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "package\libstdc++-6.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "package\libwinpthread-1.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

; MSVC 运行时（MSVC 编译时需要）
Source: "package\msvcp140.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "package\vcruntime140.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist
Source: "package\vcruntime140_1.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

; 平台插件（必须）
Source: "package\platforms\*"; DestDir: "{app}\platforms"; Flags: ignoreversion recursesubdirs

; 样式插件
Source: "package\styles\*"; DestDir: "{app}\styles"; Flags: ignoreversion recursesubdirs skipifsourcedoesntexist

; 图片格式插件
Source: "package\imageformats\*"; DestDir: "{app}\imageformats"; Flags: ignoreversion recursesubdirs skipifsourcedoesntexist

; TLS 插件（HTTPS 必须）
Source: "package\tls\*"; DestDir: "{app}\tls"; Flags: ignoreversion recursesubdirs skipifsourcedoesntexist

; 网络信息插件（Qt 6）
Source: "package\networkinformation\*"; DestDir: "{app}\networkinformation"; Flags: ignoreversion recursesubdirs skipifsourcedoesntexist

; 其他 windeployqt 生成的文件（通配方式兜底）
Source: "package\*.dll"; DestDir: "{app}"; Flags: ignoreversion skipifsourcedoesntexist

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
```

> **重要提示**：
> - `SetupIconFile=` 后可以填入 `.ico` 图标文件路径，如果没有图标可以留空
> - `AppId` 请为每个应用生成唯一 GUID（可通过 Inno Setup 的 `Tools > Generate GUID` 生成）
> - 最后的 `Source: "package\*.dll"` 行使用通配符兜底，确保不遗漏任何 DLL

### 4.3 编译安装包

方法一：使用 Inno Setup GUI
1. 打开 Inno Setup Compiler
2. File → Open → 选择 `setup.iss`
3. Build → Compile（或按 `Ctrl+F9`）

方法二：使用命令行
```bash
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" setup.iss
```

编译完成后，在 `installer_output` 目录下生成 `DocAI-Setup-1.0.0.exe`。

---

## 五、完整打包流程（一键脚本）

可以在项目根目录创建 `build_installer.bat` 简化打包：

```bat
@echo off
echo ============================================
echo  DocAI 一键打包脚本
echo ============================================

echo.
echo [1/4] 清理旧文件...
if exist build-release rmdir /s /q build-release
if exist package rmdir /s /q package
if exist installer_output rmdir /s /q installer_output

echo [2/4] 编译 Release 版本...
mkdir build-release
cd build-release
qmake ../DocAI.pro CONFIG+=release
mingw32-make -j8
if errorlevel 1 (
    echo 编译失败！
    pause
    exit /b 1
)
cd ..

echo [3/4] 收集依赖...
mkdir package
copy build-release\release\DocAI.exe package\
windeployqt --release --no-translations package\DocAI.exe
if errorlevel 1 (
    echo windeployqt 失败！
    pause
    exit /b 1
)

echo [4/4] 生成安装包...
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" setup.iss
if errorlevel 1 (
    echo Inno Setup 编译失败！
    pause
    exit /b 1
)

echo.
echo ============================================
echo  打包完成！安装包位于 installer_output 目录
echo ============================================
pause
```

---

## 六、常见问题排查

### Q1: 运行时提示 "无法找到入口点" 或缺少 DLL

**原因**：DLL 版本不匹配或遗漏。

**解决**：
1. 使用 Dependencies 工具查看缺失的 DLL
2. 从 Qt 安装目录手动复制对应 DLL
3. 确保 `platforms/qwindows.dll` 存在

### Q2: 运行后界面空白/样式异常

**原因**：缺少 `styles/` 或 `platforms/` 插件。

**解决**：
```bash
# 确保以下目录存在且非空
package\platforms\qwindows.dll
package\styles\qwindowsvistastyle.dll
```

### Q3: HTTPS 请求失败（网络无法连接后端）

**原因**：缺少 TLS 插件。

**解决**：
```bash
# Qt 5.15+
mkdir package\tls
copy C:\Qt\5.15.2\mingw81_64\plugins\tls\*.dll package\tls\

# 或检查 OpenSSL DLL
copy C:\Qt\5.15.2\mingw81_64\bin\libssl-1_1-x64.dll package\
copy C:\Qt\5.15.2\mingw81_64\bin\libcrypto-1_1-x64.dll package\
```

### Q4: 应用图标不显示

在 `.pro` 文件中添加：
```pro
RC_ICONS = path/to/icon.ico
```

然后重新编译。

### Q5: windeployqt 报错找不到 Qt

确保 Qt 的 `bin` 目录在 PATH 中：
```bash
set PATH=C:\Qt\5.15.2\mingw81_64\bin;%PATH%
windeployqt --release package\DocAI.exe
```

---

## 七、DLL 完整清单（速查表）

以下是 DocAI 项目所需的全部 DLL 文件列表，打包前请逐一核对：

### 核心 DLL（必须）

| DLL 文件名 | 说明 |
|------------|------|
| Qt5Core.dll | Qt 核心库 |
| Qt5Gui.dll | GUI 渲染 |
| Qt5Widgets.dll | 控件库 |
| Qt5Network.dll | HTTP/HTTPS 网络请求 |

### MinGW 运行时（MinGW 编译时必须）

| DLL 文件名 | 说明 |
|------------|------|
| libgcc_s_seh-1.dll | GCC 异常处理 |
| libstdc++-6.dll | C++ 标准库 |
| libwinpthread-1.dll | 线程支持 |

### MSVC 运行时（MSVC 编译时必须）

| DLL 文件名 | 说明 |
|------------|------|
| msvcp140.dll | MSVC 标准库 |
| vcruntime140.dll | MSVC 运行时 |
| vcruntime140_1.dll | MSVC 运行时扩展 |

### 平台及插件（必须）

| 文件路径 | 说明 |
|----------|------|
| platforms/qwindows.dll | Windows 平台集成 |
| styles/qwindowsvistastyle.dll | Vista/Win10 样式 |

### TLS/HTTPS 插件（必须）

| 文件路径 | 说明 |
|----------|------|
| tls/qschannelbackend.dll | Windows SChannel TLS |
| 或 tls/qopensslbackend.dll | OpenSSL TLS 后端 |
| 或 libssl-1_1-x64.dll + libcrypto-1_1-x64.dll | OpenSSL 运行时 |

### 图片格式插件（建议保留）

| 文件路径 | 说明 |
|----------|------|
| imageformats/qgif.dll | GIF 支持 |
| imageformats/qjpeg.dll | JPEG 支持 |
| imageformats/qico.dll | ICO 支持 |
| imageformats/qsvg.dll | SVG 支持 |

---

## 八、版本更新打包

每次更新版本时：

1. 修改 `setup.iss` 中的 `MyAppVersion`
2. 重新编译 Release 版本
3. 重新运行 windeployqt
4. 重新编译 Inno Setup 脚本
5. 发布新的安装包

建议使用语义化版本号（如 `1.0.0` → `1.0.1` → `1.1.0`）。
