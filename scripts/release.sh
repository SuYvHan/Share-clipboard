#!/bin/bash

# 自动发布脚本
# 用法: ./scripts/release.sh [版本号] [发布说明]

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# 检查参数
if [ $# -lt 1 ]; then
    print_error "用法: $0 <版本号> [发布说明]"
    print_info "示例: $0 v1.0.0 \"修复了剪切板同步问题\""
    exit 1
fi

VERSION=$1
RELEASE_NOTES=${2:-""}

# 验证版本号格式
if [[ ! $VERSION =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    print_error "版本号格式错误，应为 vX.Y.Z 格式，如 v1.0.0"
    exit 1
fi

print_info "准备发布版本: $VERSION"

# 检查是否在git仓库中
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    print_error "当前目录不是git仓库"
    exit 1
fi

# 检查工作目录是否干净
if ! git diff-index --quiet HEAD --; then
    print_warning "工作目录有未提交的更改"
    read -p "是否继续？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "发布已取消"
        exit 1
    fi
fi

# 检查标签是否已存在
if git rev-parse "$VERSION" >/dev/null 2>&1; then
    print_error "标签 $VERSION 已存在"
    exit 1
fi

# 获取当前分支
CURRENT_BRANCH=$(git branch --show-current)
print_info "当前分支: $CURRENT_BRANCH"

# 确保在main或master分支
if [[ "$CURRENT_BRANCH" != "main" && "$CURRENT_BRANCH" != "master" ]]; then
    print_warning "建议在main或master分支进行发布"
    read -p "是否继续？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "发布已取消"
        exit 1
    fi
fi

# 拉取最新代码
print_info "拉取最新代码..."
git pull origin "$CURRENT_BRANCH"

# 运行测试
print_info "运行测试..."
if command -v ./gradlew &> /dev/null; then
    ./gradlew test
    print_success "测试通过"
else
    print_warning "未找到gradlew，跳过测试"
fi

# 更新版本号
print_info "更新版本号..."
VERSION_NAME=${VERSION#v}  # 移除v前缀
VERSION_CODE=$(date +%s)

# 更新build.gradle.kts中的版本号
if [ -f "app/build.gradle.kts" ]; then
    sed -i.bak "s/versionName = \".*\"/versionName = \"$VERSION_NAME\"/" app/build.gradle.kts
    sed -i.bak "s/versionCode = [0-9]*/versionCode = $VERSION_CODE/" app/build.gradle.kts
    rm app/build.gradle.kts.bak
    print_success "版本号已更新: $VERSION_NAME (code: $VERSION_CODE)"
else
    print_warning "未找到app/build.gradle.kts文件"
fi

# 提交版本更新
print_info "提交版本更新..."
git add .
git commit -m "chore: bump version to $VERSION" || print_warning "没有需要提交的更改"

# 创建标签
print_info "创建标签: $VERSION"
if [ -n "$RELEASE_NOTES" ]; then
    git tag -a "$VERSION" -m "$RELEASE_NOTES"
else
    git tag -a "$VERSION" -m "Release $VERSION"
fi

# 推送代码和标签
print_info "推送代码和标签..."
git push origin "$CURRENT_BRANCH"
git push origin "$VERSION"

print_success "标签 $VERSION 已创建并推送"
print_info "GitHub Actions将自动构建并创建Release"

# 显示GitHub Actions链接
REPO_URL=$(git config --get remote.origin.url | sed 's/\.git$//')
if [[ $REPO_URL == git@github.com:* ]]; then
    REPO_URL=$(echo $REPO_URL | sed 's/git@github.com:/https:\/\/github.com\//')
fi

print_info "查看构建状态: $REPO_URL/actions"
print_info "查看发布页面: $REPO_URL/releases"

print_success "发布流程已启动！🎉"
