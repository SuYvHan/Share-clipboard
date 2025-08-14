# GitHub Actions 工作流说明

## 🔧 修复说明

**已修复的问题：**
- ✅ 修复了 `signed-release.yml` 中的 secrets 条件语法错误
- ✅ 修复了 `pr-check.yml` 中的模板字符串语法问题
- ✅ 添加了简化版本的构建工作流 (`build-simple.yml`)
- ✅ 创建了工作流验证脚本

## 📋 工作流概览

本项目包含以下GitHub Actions工作流，用于自动化构建、测试和发布流程：

### 🚀 主要工作流

#### 1. `build-simple.yml` - 简化构建（推荐开始使用）
**触发条件：**
- 推送到 `main`/`master` 分支
- Pull Request
- 手动触发

**功能：**
- 构建Debug APK
- 运行单元测试
- 上传构建产物

#### 2. `build-and-release.yml` - 完整构建和发布
**触发条件：**
- 推送到 `main`/`master` 分支
- 创建标签 (`v*`)
- Pull Request
- 手动触发

**功能：**
- 构建Debug/Release APK
- 运行单元测试
- 自动创建GitHub Release
- 上传APK到Artifacts

#### 2. `signed-release.yml` - 签名发布
**触发条件：**
- 创建版本标签 (`v*`)
- 手动触发

**功能：**
- 构建签名的Release APK
- 创建正式版Release
- 生成详细的发布说明

#### 3. `pr-check.yml` - PR检查
**触发条件：**
- 创建、更新Pull Request

**功能：**
- 代码质量检查
- 多API级别构建测试
- 安全扫描
- APK大小检查
- 自动PR评论

#### 4. `dependency-update.yml` - 依赖更新
**触发条件：**
- 每周一定时执行
- 手动触发

**功能：**
- 检查依赖更新
- 更新Gradle Wrapper
- 安全审计
- 创建更新Issue

## 🔧 配置说明

### 必需的Secrets

为了使用签名发布功能，需要在GitHub仓库设置中添加以下Secrets：

```
KEYSTORE_BASE64      # Keystore文件的Base64编码
KEYSTORE_PASSWORD    # Keystore密码
KEY_ALIAS           # 密钥别名
KEY_PASSWORD        # 密钥密码
```

### 生成Keystore的Base64编码

```bash
# 1. 生成keystore（如果没有）
keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000

# 2. 转换为Base64
base64 -i release.keystore | pbcopy  # macOS
base64 -w 0 release.keystore         # Linux
```

## 📱 发布流程

### 自动发布（推荐）

1. **创建版本标签**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. **GitHub Actions自动执行**
   - 构建签名APK
   - 创建GitHub Release
   - 上传APK文件

### 手动发布

1. **进入Actions页面**
2. **选择"Build Signed Release"工作流**
3. **点击"Run workflow"**
4. **输入版本号并执行**

## 🔍 工作流详情

### 构建环境
- **操作系统**: Ubuntu Latest
- **Java版本**: 17 (Temurin)
- **Gradle缓存**: 自动缓存依赖

### 测试策略
- **单元测试**: 每次构建执行
- **多API测试**: API 26, 30, 34
- **代码覆盖率**: Jacoco报告

### 安全检查
- **Trivy扫描**: 漏洞检测
- **依赖审计**: 已知安全问题
- **代码质量**: Lint检查

## 📊 工作流状态

### 状态徽章

在README中添加以下徽章来显示工作流状态：

```markdown
![Build](https://github.com/your-username/your-repo/workflows/Build%20and%20Release%20Android%20App/badge.svg)
![PR Check](https://github.com/your-username/your-repo/workflows/PR%20Check/badge.svg)
![Security](https://github.com/your-username/your-repo/workflows/Dependency%20Update/badge.svg)
```

### 查看构建结果

1. **Actions页面**: 查看所有工作流执行历史
2. **Artifacts**: 下载构建产物
3. **Releases**: 查看发布版本

## 🛠️ 自定义配置

### 修改构建参数

在 `build-and-release.yml` 中修改：

```yaml
env:
  JAVA_VERSION: '17'           # Java版本
  GRADLE_OPTS: -Dorg.gradle.daemon=false  # Gradle选项
```

### 调整APK大小限制

在 `pr-check.yml` 中修改：

```yaml
# 设置大小限制 (50MB)
MAX_SIZE_MB=50
```

### 自定义发布说明

修改 `signed-release.yml` 中的 `body` 部分来自定义发布说明模板。

## 🔧 故障排除

### 常见问题

1. **构建失败**
   - 检查Java版本兼容性
   - 验证Gradle配置
   - 查看详细日志

2. **签名失败**
   - 验证Secrets配置
   - 检查Keystore格式
   - 确认密码正确

3. **测试失败**
   - 查看测试报告
   - 检查代码质量
   - 修复Lint错误

### 调试技巧

1. **启用调试日志**
   ```yaml
   - name: Debug step
     run: echo "Debug info"
     env:
       ACTIONS_STEP_DEBUG: true
   ```

2. **查看Artifacts**
   - 下载构建产物
   - 检查测试报告
   - 分析错误日志

## 📚 相关文档

- [GitHub Actions文档](https://docs.github.com/en/actions)
- [Android构建指南](https://developer.android.com/studio/build)
- [Gradle用户指南](https://docs.gradle.org/current/userguide/userguide.html)
