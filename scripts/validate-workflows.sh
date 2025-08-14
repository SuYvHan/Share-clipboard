#!/bin/bash

# GitHub Actions工作流验证脚本

echo "🔍 验证GitHub Actions工作流文件..."

# 检查工作流文件是否存在
WORKFLOW_DIR=".github/workflows"
if [ ! -d "$WORKFLOW_DIR" ]; then
    echo "❌ 工作流目录不存在: $WORKFLOW_DIR"
    exit 1
fi

# 验证YAML语法
echo "📋 检查YAML语法..."
for file in "$WORKFLOW_DIR"/*.yml "$WORKFLOW_DIR"/*.yaml; do
    if [ -f "$file" ]; then
        echo "  检查: $(basename "$file")"
        
        # 使用Python验证YAML语法
        python3 -c "
import yaml
import sys
try:
    with open('$file', 'r', encoding='utf-8') as f:
        yaml.safe_load(f)
    print('    ✅ YAML语法正确')
except yaml.YAMLError as e:
    print(f'    ❌ YAML语法错误: {e}')
    sys.exit(1)
except Exception as e:
    print(f'    ❌ 文件读取错误: {e}')
    sys.exit(1)
"
        if [ $? -ne 0 ]; then
            echo "❌ 工作流验证失败"
            exit 1
        fi
    fi
done

echo "✅ 所有工作流文件验证通过！"

# 检查必需的文件
echo "📁 检查必需文件..."
REQUIRED_FILES=(
    "gradlew"
    "app/build.gradle.kts"
    "settings.gradle.kts"
)

for file in "${REQUIRED_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✅ $file"
    else
        echo "  ❌ 缺少文件: $file"
    fi
done

# 检查权限
echo "🔐 检查文件权限..."
if [ -f "gradlew" ]; then
    if [ -x "gradlew" ]; then
        echo "  ✅ gradlew 可执行"
    else
        echo "  ⚠️  gradlew 不可执行，正在修复..."
        chmod +x gradlew
        echo "  ✅ gradlew 权限已修复"
    fi
fi

echo "🎉 工作流验证完成！"
