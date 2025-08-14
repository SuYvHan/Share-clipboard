#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
剪切板同步服务 Python API 客户端安装和测试脚本
"""

import subprocess
import sys
import os
import importlib.util
from typing import List, Tuple


def check_python_version() -> bool:
    """检查Python版本"""
    if sys.version_info < (3, 7):
        print("❌ 错误: 需要Python 3.7或更高版本")
        print(f"   当前版本: {sys.version}")
        return False
    
    print(f"✅ Python版本检查通过: {sys.version.split()[0]}")
    return True


def install_package(package: str) -> bool:
    """安装Python包"""
    try:
        print(f"📦 安装 {package}...")
        result = subprocess.run(
            [sys.executable, "-m", "pip", "install", package],
            capture_output=True,
            text=True,
            check=True
        )
        print(f"✅ {package} 安装成功")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ {package} 安装失败:")
        print(f"   错误: {e.stderr}")
        return False


def check_package_installed(package: str) -> bool:
    """检查包是否已安装"""
    try:
        spec = importlib.util.find_spec(package)
        return spec is not None
    except ImportError:
        return False


def install_dependencies() -> bool:
    """安装依赖包"""
    print("\n=== 安装依赖包 ===")
    
    required_packages = [
        "requests",
        "websockets"
    ]
    
    all_success = True
    
    for package in required_packages:
        if check_package_installed(package):
            print(f"✅ {package} 已安装")
        else:
            if not install_package(package):
                all_success = False
    
    return all_success


def test_imports() -> bool:
    """测试导入所有API模块"""
    print("\n=== 测试模块导入 ===")
    
    modules = [
        "health_api",
        "clipboard_api", 
        "devices_api",
        "config_api",
        "files_api",
        "websocket_api",
        "clipboard_client"
    ]
    
    all_success = True
    
    for module in modules:
        try:
            __import__(module)
            print(f"✅ {module} 导入成功")
        except ImportError as e:
            print(f"❌ {module} 导入失败: {e}")
            all_success = False
        except Exception as e:
            print(f"⚠️  {module} 导入警告: {e}")
    
    return all_success


def test_basic_functionality() -> bool:
    """测试基本功能"""
    print("\n=== 测试基本功能 ===")
    
    try:
        # 测试健康检查API
        from health_api import HealthAPI
        health_api = HealthAPI()
        print("✅ 健康检查API初始化成功")
        
        # 测试剪切板API
        from clipboard_api import ClipboardAPI
        clipboard_api = ClipboardAPI()
        print("✅ 剪切板API初始化成功")
        
        # 测试综合客户端
        from clipboard_client import ClipboardSyncClient
        client = ClipboardSyncClient()
        print("✅ 综合客户端初始化成功")
        
        return True
        
    except Exception as e:
        print(f"❌ 基本功能测试失败: {e}")
        return False


def run_connectivity_test() -> bool:
    """运行连接测试"""
    print("\n=== 连接测试 ===")
    
    try:
        from health_api import HealthAPI
        
        print("🔍 测试服务器连接...")
        api = HealthAPI()
        result = api.check_health()
        
        if result.get('success'):
            print("✅ 服务器连接成功")
            print(f"   响应时间: {result.get('response_time', 'N/A')}ms")
            return True
        else:
            print("❌ 服务器连接失败")
            print(f"   错误: {result.get('message', '未知错误')}")
            print("\n💡 提示:")
            print("   1. 确保剪切板同步服务正在运行")
            print("   2. 检查服务器地址是否正确 (默认: http://localhost:3001)")
            print("   3. 运行 'npm run dev' 启动服务")
            return False
            
    except Exception as e:
        print(f"❌ 连接测试失败: {e}")
        return False


def create_example_script() -> bool:
    """创建示例脚本"""
    print("\n=== 创建示例脚本 ===")
    
    example_content = '''#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速开始示例
"""

from clipboard_client import ClipboardSyncClient

def main():
    print("=== 剪切板同步服务快速测试 ===")
    
    # 创建客户端
    client = ClipboardSyncClient(device_id="quick-test-device")
    
    # 显示状态报告
    client.print_status_report()
    
    # 创建测试内容
    print("\\n📝 创建测试文本内容...")
    result = client.create_text_content("快速测试内容")
    
    if result.get('success'):
        print("✅ 内容创建成功!")
        item_id = result.get('data', {}).get('id')
        print(f"   项目ID: {item_id}")
    else:
        print(f"❌ 内容创建失败: {result.get('message')}")

if __name__ == "__main__":
    main()
'''
    
    try:
        with open("quick_start.py", "w", encoding="utf-8") as f:
            f.write(example_content)
        print("✅ 创建 quick_start.py 示例脚本")
        return True
    except Exception as e:
        print(f"❌ 创建示例脚本失败: {e}")
        return False


def print_usage_instructions():
    """打印使用说明"""
    print("\n" + "="*60)
    print("🎉 安装完成！")
    print("="*60)
    
    print("\n📚 快速开始:")
    print("   1. 启动剪切板同步服务:")
    print("      npm run dev")
    print()
    print("   2. 运行示例脚本:")
    print("      python examples.py")
    print()
    print("   3. 查看服务状态:")
    print("      python clipboard_client.py --mode status")
    print()
    print("   4. 启动实时监控:")
    print("      python websocket_api.py --mode monitor")
    print()
    print("   5. 运行快速测试:")
    print("      python quick_start.py")
    
    print("\n📖 更多信息:")
    print("   - 查看 README.md 了解详细使用方法")
    print("   - 查看 examples.py 了解完整示例")
    print("   - 访问 http://localhost:3001/api/docs 查看API文档")
    
    print("\n🔧 故障排除:")
    print("   - 确保服务器正在运行 (npm run dev)")
    print("   - 检查端口是否被占用 (3001, 3002)")
    print("   - 查看服务器日志排查问题")


def main():
    """主函数"""
    print("🚀 剪切板同步服务 Python API 客户端安装程序")
    print("="*60)
    
    # 检查Python版本
    if not check_python_version():
        sys.exit(1)
    
    # 安装依赖
    if not install_dependencies():
        print("\n❌ 依赖安装失败，请手动安装:")
        print("   pip install requests websockets")
        sys.exit(1)
    
    # 测试导入
    if not test_imports():
        print("\n❌ 模块导入测试失败")
        sys.exit(1)
    
    # 测试基本功能
    if not test_basic_functionality():
        print("\n❌ 基本功能测试失败")
        sys.exit(1)
    
    # 创建示例脚本
    create_example_script()
    
    # 运行连接测试
    connectivity_ok = run_connectivity_test()
    
    # 打印使用说明
    print_usage_instructions()
    
    if not connectivity_ok:
        print("\n⚠️  注意: 服务器连接测试失败")
        print("   请确保剪切板同步服务正在运行后再使用API客户端")
    
    print("\n✅ 安装程序完成!")


if __name__ == "__main__":
    main()
