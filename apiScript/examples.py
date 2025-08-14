#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
剪切板同步服务API使用示例
展示各种API接口的使用方法
"""

import asyncio
import os
import base64
from datetime import datetime
from typing import Dict, Any

# 导入API客户端
from clipboard_client import ClipboardSyncClient
from health_api import HealthAPI
from clipboard_api import ClipboardAPI
from devices_api import DevicesAPI
from config_api import ConfigAPI
from files_api import FilesAPI
from websocket_api import WebSocketMonitor


def example_health_check():
    """示例：健康检查"""
    print("=== 健康检查示例 ===")
    
    api = HealthAPI()
    result = api.check_health()
    
    print(f"服务器状态: {'正常' if result['success'] else '异常'}")
    print(f"响应消息: {result['message']}")
    if result['response_time']:
        print(f"响应时间: {result['response_time']}ms")
    
    return result['success']


def example_clipboard_operations():
    """示例：剪切板操作"""
    print("\n=== 剪切板操作示例 ===")
    
    api = ClipboardAPI()
    device_id = "example-device-001"
    
    # 1. 创建文本内容
    print("\n1. 创建文本内容:")
    text_result = api.create_text_item("这是一个测试文本内容", device_id)
    print(f"  创建结果: {'成功' if text_result.get('success') else '失败'}")
    
    created_item_id = None
    if text_result.get('success'):
        created_item_id = text_result.get('data', {}).get('id')
        print(f"  项目ID: {created_item_id}")
    
    # 2. 获取剪切板内容列表
    print("\n2. 获取剪切板内容列表:")
    list_result = api.get_clipboard_items(limit=5)
    print(f"  获取结果: {'成功' if list_result.get('success') else '失败'}")
    if list_result.get('success'):
        items = list_result.get('data', [])
        print(f"  获取到 {len(items)} 条内容")
        for i, item in enumerate(items[:3], 1):
            content_preview = item.get('content', '')[:30]
            print(f"    {i}. [{item.get('type')}] {content_preview}...")
    
    # 3. 更新内容（如果创建成功）
    if created_item_id:
        print("\n3. 更新文本内容:")
        update_result = api.update_item(created_item_id, content="更新后的文本内容")
        print(f"  更新结果: {'成功' if update_result.get('success') else '失败'}")
        
        # 4. 删除内容
        print("\n4. 删除内容:")
        delete_result = api.delete_item(created_item_id)
        print(f"  删除结果: {'成功' if delete_result.get('success') else '失败'}")
    
    # 5. 创建图片内容（Base64示例）
    print("\n5. 创建图片内容:")
    # 创建一个简单的1x1像素PNG图片的Base64数据
    simple_png_base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    image_result = api.create_image_item(
        image_data=simple_png_base64,
        device_id=device_id,
        file_name="test_image.png",
        mime_type="image/png"
    )
    print(f"  创建图片结果: {'成功' if image_result.get('success') else '失败'}")
    
    return True


def example_device_management():
    """示例：设备管理"""
    print("\n=== 设备管理示例 ===")
    
    api = DevicesAPI()
    
    # 1. 检查WebSocket服务器状态
    print("\n1. 检查WebSocket服务器状态:")
    status_result = api.is_websocket_server_running()
    print(f"  服务器状态: {'运行中' if status_result.get('running') else '未运行'}")
    print(f"  消息: {status_result.get('message')}")
    
    if status_result.get('running'):
        # 2. 获取连接统计
        print("\n2. 获取连接统计:")
        stats_result = api.get_connection_stats()
        if stats_result.get('success'):
            data = stats_result.get('data', {})
            print(f"  总连接数: {data.get('totalConnections', 0)}")
            print(f"  活跃连接数: {data.get('activeConnections', 0)}")
            
            device_connections = data.get('deviceConnections', {})
            if device_connections:
                print(f"  设备连接:")
                for device_id, count in device_connections.items():
                    print(f"    - {device_id}: {count} 个连接")
        
        # 3. 获取设备列表
        print("\n3. 获取设备列表:")
        devices_result = api.get_device_list()
        if devices_result.get('success'):
            data = devices_result.get('data', {})
            devices = data.get('devices', [])
            print(f"  设备总数: {len(devices)}")
            for device in devices:
                status = "活跃" if device.get('isActive') else "离线"
                print(f"    - {device.get('deviceId')}: {status}")
    
    return True


def example_config_management():
    """示例：配置管理"""
    print("\n=== 配置管理示例 ===")
    
    api = ConfigAPI()
    
    # 1. 获取用户配置
    print("\n1. 获取用户配置:")
    config_result = api.get_user_config()
    if config_result.get('success'):
        data = config_result.get('data', {})
        print(f"  最大条目数: {data.get('maxItems')}")
        print(f"  自动清理天数: {data.get('autoCleanupDays')}")
        
        file_cleanup = data.get('fileCleanup', {})
        print(f"  文件清理: {'启用' if file_cleanup.get('enabled') else '禁用'}")
    
    # 2. 获取存储统计
    print("\n2. 获取存储统计:")
    stats_result = api.get_storage_stats()
    if stats_result.get('success'):
        data = stats_result.get('data', {})
        print(f"  总条目数: {data.get('totalItems', 0)}")
        print(f"  文本: {data.get('textItems', 0)}")
        print(f"  图片: {data.get('imageItems', 0)}")
        print(f"  文件: {data.get('fileItems', 0)}")
        print(f"  总大小: {data.get('totalSize', '未知')}")
    
    # 3. 更新配置示例（注释掉以避免意外修改）
    print("\n3. 配置更新示例（仅演示，不实际执行）:")
    print("  # 更新最大条目数为500")
    print("  # api.update_max_items(500)")
    print("  # 启用文件清理，最多保留50个文件")
    print("  # api.enable_file_cleanup(50, 'oldest_first')")
    
    return True


def example_file_operations():
    """示例：文件操作"""
    print("\n=== 文件操作示例 ===")
    
    api = FilesAPI()
    
    # 1. 获取文件统计
    print("\n1. 获取文件统计:")
    stats_result = api.get_file_stats()
    if stats_result.get('success'):
        data = stats_result.get('data', {})
        print(f"  总文件数: {data.get('totalFiles', 0)}")
        print(f"  总大小: {data.get('totalSize', 0)} 字节")
        print(f"  目录大小: {data.get('directorySize', 0)} 字节")
    
    # 2. 获取清理状态
    print("\n2. 获取文件清理状态:")
    cleanup_status = api.get_cleanup_status()
    if cleanup_status.get('success'):
        data = cleanup_status.get('data', {})
        print(f"  清理任务已调度: {data.get('isScheduled', False)}")
        print(f"  清理任务运行中: {data.get('isRunning', False)}")
    
    print("\n注意: 文件下载和预览需要有效的剪切板项目ID")
    
    return True


async def example_websocket_operations():
    """示例：WebSocket操作"""
    print("\n=== WebSocket操作示例 ===")
    
    # 创建WebSocket监控器
    monitor = WebSocketMonitor(device_id="example-websocket-client")
    
    print("启动WebSocket监控（10秒后自动停止）...")
    
    try:
        # 连接并监听5秒
        if await monitor.api.connect():
            print("WebSocket连接成功！")
            
            # 发送一些测试消息
            await monitor.api.get_all_content(limit=5)
            await asyncio.sleep(1)
            
            await monitor.api.get_latest(3)
            await asyncio.sleep(1)
            
            # 监听消息
            async def test_handler(message):
                message_type = message.get('type')
                print(f"收到消息: {message_type}")
                if message_type == 'all_content':
                    data = message.get('data', [])
                    print(f"  获取到 {len(data)} 条内容")
            
            # 设置超时
            try:
                await asyncio.wait_for(
                    monitor.api.listen_messages(test_handler),
                    timeout=5.0
                )
            except asyncio.TimeoutError:
                print("监听超时，停止测试")
            
            await monitor.api.disconnect()
        else:
            print("WebSocket连接失败！")
            
    except Exception as e:
        print(f"WebSocket测试出错: {str(e)}")
    
    return True


def example_comprehensive_client():
    """示例：综合客户端使用"""
    print("\n=== 综合客户端示例 ===")
    
    # 创建综合客户端
    client = ClipboardSyncClient(device_id="example-comprehensive-client")
    
    # 1. 打印状态报告
    print("\n1. 服务状态报告:")
    client.print_status_report()
    
    # 2. 创建内容
    print("\n2. 创建测试内容:")
    result = client.create_text_content("综合客户端测试内容")
    print(f"  创建结果: {'成功' if result.get('success') else '失败'}")
    
    # 3. 获取系统信息
    print("\n3. 系统信息:")
    info = client.get_system_info()
    if 'storage_stats' in info:
        stats = info['storage_stats']
        print(f"  存储统计: 总计 {stats.get('totalItems', 0)} 条内容")
    
    return True


def main():
    """主函数：运行所有示例"""
    print("🚀 剪切板同步服务API使用示例")
    print("=" * 50)
    
    examples = [
        ("健康检查", example_health_check),
        ("剪切板操作", example_clipboard_operations),
        ("设备管理", example_device_management),
        ("配置管理", example_config_management),
        ("文件操作", example_file_operations),
        ("综合客户端", example_comprehensive_client),
    ]
    
    results = {}
    
    # 运行同步示例
    for name, func in examples:
        try:
            print(f"\n{'='*20} {name} {'='*20}")
            results[name] = func()
        except Exception as e:
            print(f"❌ {name} 示例执行失败: {str(e)}")
            results[name] = False
    
    # 运行异步示例
    try:
        print(f"\n{'='*20} WebSocket操作 {'='*20}")
        results["WebSocket操作"] = asyncio.run(example_websocket_operations())
    except Exception as e:
        print(f"❌ WebSocket操作示例执行失败: {str(e)}")
        results["WebSocket操作"] = False
    
    # 打印总结
    print(f"\n{'='*50}")
    print("📊 示例执行总结:")
    for name, success in results.items():
        status = "✅ 成功" if success else "❌ 失败"
        print(f"  {name}: {status}")
    
    success_count = sum(1 for success in results.values() if success)
    total_count = len(results)
    print(f"\n总计: {success_count}/{total_count} 个示例执行成功")
    
    if success_count == total_count:
        print("🎉 所有示例都执行成功！")
    else:
        print("⚠️  部分示例执行失败，请检查服务器状态")


if __name__ == "__main__":
    main()
