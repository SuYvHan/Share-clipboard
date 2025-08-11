# 剪切板同步 APP

一个基于 Kotlin 开发的 Android 剪切板同步应用，支持跨设备实时同步剪切板内容。

## 🚀 主要功能

### 核心功能
- **实时同步**: 通过 WebSocket 实现剪切板内容的实时同步
- **多设备支持**: 支持多设备间的剪切板共享
- **多媒体支持**: 支持文本、图片、文件等多种类型
- **离线缓存**: 本地数据库存储，支持离线查看
- **前台服务**: 通知栏保活，确保服务持续运行

### 高级功能
- **图片处理**: Base64 编解码，支持保存到相册
- **权限管理**: 完善的权限请求和管理机制
- **配置灵活**: 支持自定义服务器配置
- **现代化 UI**: 使用 Material Design 3 和 Jetpack Compose

## 🏗️ 技术架构

### 技术栈
- **UI 框架**: Jetpack Compose + Material Design 3
- **架构模式**: MVVM + Repository Pattern
- **依赖注入**: Hilt
- **网络通信**: Retrofit + OkHttp + WebSocket
- **数据存储**: Room Database + DataStore
- **图片处理**: Coil + Base64 编解码
- **序列化**: Kotlinx Serialization
- **异步处理**: Kotlin Coroutines + Flow

### 项目结构
```
app/src/main/java/com/clipboardsync/app/
├── data/                   # 数据层
│   ├── local/             # 本地数据源
│   ├── remote/            # 远程数据源
│   └── repository/        # 仓库实现
├── domain/                # 领域层
│   ├── model/             # 数据模型
│   ├── repository/        # 仓库接口
│   └── usecase/           # 用例
├── network/               # 网络层
│   ├── api/               # API 接口
│   └── websocket/         # WebSocket 客户端
├── service/               # 服务层
│   └── clipboard/         # 剪切板服务
├── ui/                    # UI 层
│   ├── main/              # 主界面
│   ├── settings/          # 设置界面
│   ├── components/        # 通用组件
│   └── theme/             # 主题配置
├── util/                  # 工具类
└── di/                    # 依赖注入
```

## 📱 功能模块

### 1. 剪切板监听服务
- 实时监听剪切板变化
- 支持文本、图片等多种类型
- 前台服务保活机制
- 自动同步到服务器

### 2. 网络通信
- HTTP API 客户端
- WebSocket 实时通信
- 自动重连机制
- 错误处理和重试

### 3. 数据管理
- Room 数据库存储
- DataStore 配置管理
- 本地缓存机制
- 数据清理和维护

### 4. 图片处理
- Base64 编解码
- 图片保存到相册
- 图片压缩和优化
- 格式转换支持

### 5. 用户界面
- 现代化 Compose UI
- Material Design 3
- 深色模式支持
- 响应式设计

### 6. 权限管理
- 运行时权限请求
- 权限说明对话框
- 设置页面跳转
- 兼容不同 Android 版本

## 🔧 构建和安装

### 构建要求
- Android Studio Arctic Fox 或更高版本
- JDK 11 或更高版本
- Android SDK API 26+
- Kotlin 2.0+

### 构建步骤
1. 克隆项目到本地
2. 使用 Android Studio 打开项目
3. 等待 Gradle 同步完成
4. 运行构建命令：
   ```bash
   ./gradlew assembleDebug
   ```
5. APK 文件生成在 `app/build/outputs/apk/debug/`

### 安装说明
1. 将 APK 文件传输到 Android 设备
2. 在设备上启用"未知来源"安装
3. 点击 APK 文件进行安装
4. 首次运行时授予必要权限
5. 在设置中配置服务器信息

## ⚙️ 配置说明

### 服务器配置
- **服务器地址**: 默认 47.239.194.151
- **HTTP 端口**: 默认 3001
- **WebSocket 端口**: 默认 3002
- **设备 ID**: 自动生成或手动设置
- **API 密钥**: 可选配置

### 同步设置
- **自动同步**: 启用/禁用自动同步
- **同步图片**: 是否同步图片内容
- **同步文件**: 是否同步文件内容
- **安全连接**: 使用 HTTPS/WSS 协议

## 📋 API 文档

应用基于 `clipboard-sync-api-docs.md` 中定义的 API 规范开发，支持：

- 剪切板内容的 CRUD 操作
- WebSocket 实时通信
- 设备连接管理
- 配置信息同步

## 🔒 权限说明

应用需要以下权限：
- **网络权限**: 用于与服务器通信
- **存储权限**: 用于保存图片到相册
- **通知权限**: 用于显示服务状态
- **前台服务权限**: 用于保持服务运行

## 🐛 已知问题

- Kapt 不完全支持 Kotlin 2.0+（已降级到 1.9）
- 部分 Material Icons 使用了弃用的 API（已修复）
- 中文字符在某些终端中可能显示为乱码

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来改进这个项目！

---

**构建状态**: ✅ 成功  
**APK 大小**: ~21 MB  
**最低 Android 版本**: API 26 (Android 8.0)  
**目标 Android 版本**: API 36 (Android 14)
