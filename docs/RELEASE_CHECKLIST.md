# 📋 发布检查清单

## 🚀 发布前准备

### ✅ 代码准备
- [ ] 所有功能开发完成
- [ ] 代码已合并到main/master分支
- [ ] 所有测试通过
- [ ] 代码已经过Code Review
- [ ] 没有已知的严重Bug

### ✅ 版本管理
- [ ] 确定版本号（遵循语义化版本）
- [ ] 更新CHANGELOG.md（如果有）
- [ ] 更新README.md中的版本信息
- [ ] 检查依赖版本是否需要更新

### ✅ 构建测试
- [ ] 本地构建成功
- [ ] Debug版本测试通过
- [ ] Release版本测试通过
- [ ] 在不同Android版本上测试

## 🔧 发布配置

### ✅ GitHub Secrets配置
- [ ] `KEYSTORE_BASE64` - Keystore文件的Base64编码
- [ ] `KEYSTORE_PASSWORD` - Keystore密码
- [ ] `KEY_ALIAS` - 密钥别名
- [ ] `KEY_PASSWORD` - 密钥密码

### ✅ 权限检查
- [ ] 仓库有Actions权限
- [ ] 有创建Release的权限
- [ ] 有推送标签的权限

## 📱 发布流程

### 🎯 选择发布方式

#### 方式1：脚本发布（推荐）
```bash
# Linux/macOS
./scripts/release.sh v1.0.0 "发布说明"

# Windows
.\scripts\release.ps1 -Version "v1.0.0" -ReleaseNotes "发布说明"
```

#### 方式2：手动标签
```bash
git tag v1.0.0
git push origin v1.0.0
```

#### 方式3：GitHub Actions
1. 进入Actions页面
2. 选择"Auto Build and Release"
3. 点击"Run workflow"
4. 填写版本信息

### ✅ 发布后检查
- [ ] GitHub Actions构建成功
- [ ] Release页面创建成功
- [ ] APK文件上传成功
- [ ] 下载链接可用
- [ ] APK可以正常安装
- [ ] 应用功能正常

## 🔍 版本号规范

### 语义化版本 (SemVer)
- **主版本号**：不兼容的API修改
- **次版本号**：向下兼容的功能性新增
- **修订号**：向下兼容的问题修正

### 示例
- `v1.0.0` - 首个正式版本
- `v1.1.0` - 新增功能
- `v1.1.1` - Bug修复
- `v2.0.0` - 重大更新

## 📝 发布说明模板

```markdown
## 🎉 新功能
- 新增剪切板历史记录功能
- 支持图片同步

## 🔧 改进
- 优化同步性能
- 改进用户界面

## 🐛 修复
- 修复Android 12+权限问题
- 修复网络连接异常

## ⚠️ 注意事项
- 需要重新授权通知权限
- 建议清除应用数据后重新配置
```

## 🚨 紧急发布流程

### 热修复发布
1. **创建hotfix分支**
   ```bash
   git checkout -b hotfix/v1.0.1
   ```

2. **修复问题并测试**
3. **合并到main分支**
   ```bash
   git checkout main
   git merge hotfix/v1.0.1
   ```

4. **快速发布**
   ```bash
   ./scripts/release.sh v1.0.1 "紧急修复：修复严重Bug"
   ```

## 📊 发布后监控

### ✅ 监控指标
- [ ] 下载量统计
- [ ] 崩溃率监控
- [ ] 用户反馈收集
- [ ] 性能指标检查

### ✅ 用户支持
- [ ] 监控Issues页面
- [ ] 回复用户反馈
- [ ] 更新FAQ文档
- [ ] 准备下一版本计划

## 🔗 相关链接

- [GitHub Actions工作流](../github/workflows/)
- [构建配置](../app/build.gradle.kts)
- [发布脚本](../scripts/)
- [使用文档](../README.md)
