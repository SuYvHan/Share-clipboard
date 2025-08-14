#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
剪切板同步服务综合API客户端
整合所有API接口的统一客户端
"""

from typing import Dict, Any, Optional, List
import json
import asyncio
from datetime import datetime

# 导入各个API模块
from health_api import HealthAPI
from clipboard_api import ClipboardAPI
from devices_api import DevicesAPI
from config_api import ConfigAPI
from files_api import FilesAPI
from websocket_api import WebSocketAPI, WebSocketMonitor


class ClipboardSyncClient:
    """剪切板同步服务综合客户端"""
    
    def __init__(self, 
                 base_url: str = "http://localhost:3001",
                 ws_url: str = "ws://localhost:3002/ws",
                 device_id: Optional[str] = None,
                 security_headers: Optional[Dict[str, str]] = None):
        """
        初始化综合客户端
        
        Args:
            base_url: HTTP API服务器基础URL
            ws_url: WebSocket服务器URL
            device_id: 设备ID
            security_headers: 安全请求头
        """
        self.base_url = base_url
        self.ws_url = ws_url
        self.device_id = device_id or f"client-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        self.security_headers = security_headers
        
        # 初始化各个API客户端
        self.health = HealthAPI(base_url)
        self.clipboard = ClipboardAPI(base_url)
        self.devices = DevicesAPI(base_url)
        self.config = ConfigAPI(base_url)
        self.files = FilesAPI(base_url, security_headers)
        self.websocket = WebSocketAPI(ws_url, device_id, security_headers)
    
    def check_service_status(self) -> Dict[str, Any]:
        """
        检查服务状态
        
        Returns:
            Dict: 服务状态信息
        """
        print("=== 检查服务状态 ===")
        
        # 检查HTTP API服务
        health_result = self.health.check_health()
        http_status = health_result.get('success', False)
        
        # 检查WebSocket服务
        ws_status_result = self.devices.is_websocket_server_running()
        ws_status = ws_status_result.get('running', False)
        
        # 获取连接统计
        connection_stats = None
        if ws_status:
            stats_result = self.devices.get_connection_stats()
            if stats_result.get('success'):
                connection_stats = stats_result.get('data')
        
        status = {
            'http_api': {
                'status': '✅ 正常' if http_status else '❌ 异常',
                'running': http_status,
                'response_time': health_result.get('response_time'),
                'message': health_result.get('message')
            },
            'websocket': {
                'status': '✅ 正常' if ws_status else '❌ 异常',
                'running': ws_status,
                'message': ws_status_result.get('message'),
                'stats': connection_stats
            },
            'overall': http_status and ws_status
        }
        
        return status
    
    def get_system_info(self) -> Dict[str, Any]:
        """
        获取系统信息
        
        Returns:
            Dict: 系统信息
        """
        print("=== 获取系统信息 ===")
        
        info = {}
        
        # 获取客户端配置
        client_config = self.config.get_client_config()
        if client_config.get('success'):
            info['client_config'] = client_config.get('data')
        
        # 获取用户配置
        user_config = self.config.get_user_config()
        if user_config.get('success'):
            info['user_config'] = user_config.get('data')
        
        # 获取存储统计
        storage_stats = self.config.get_storage_stats()
        if storage_stats.get('success'):
            info['storage_stats'] = storage_stats.get('data')
        
        # 获取文件统计
        file_stats = self.files.get_file_stats()
        if file_stats.get('success'):
            info['file_stats'] = file_stats.get('data')
        
        return info
    
    def get_clipboard_summary(self) -> Dict[str, Any]:
        """
        获取剪切板内容摘要
        
        Returns:
            Dict: 剪切板摘要信息
        """
        print("=== 获取剪切板摘要 ===")
        
        summary = {
            'total_items': 0,
            'text_items': 0,
            'image_items': 0,
            'file_items': 0,
            'latest_items': [],
            'devices': []
        }
        
        # 获取总数统计
        stats_result = self.config.get_storage_stats()
        if stats_result.get('success'):
            data = stats_result.get('data', {})
            summary['total_items'] = data.get('totalItems', 0)
            summary['text_items'] = data.get('textItems', 0)
            summary['image_items'] = data.get('imageItems', 0)
            summary['file_items'] = data.get('fileItems', 0)
        
        # 获取最新内容
        latest_result = self.clipboard.get_clipboard_items(limit=5)
        if latest_result.get('success'):
            summary['latest_items'] = latest_result.get('data', [])
        
        # 获取设备信息
        devices_result = self.devices.get_device_list()
        if devices_result.get('success'):
            summary['devices'] = devices_result.get('data', {}).get('devices', [])
        
        return summary
    
    def create_text_content(self, content: str) -> Dict[str, Any]:
        """
        创建文本内容（便捷方法）
        
        Args:
            content: 文本内容
            
        Returns:
            Dict: 创建结果
        """
        return self.clipboard.create_text_item(content, self.device_id)
    
    def upload_file_from_path(self, file_path: str, content_type: str = "file") -> Dict[str, Any]:
        """
        从本地路径上传文件（便捷方法）
        
        Args:
            file_path: 本地文件路径
            content_type: 内容类型
            
        Returns:
            Dict: 上传结果
        """
        return self.clipboard.upload_file(file_path, self.device_id, content_type)
    
    def download_and_save_file(self, item_id: str, save_path: str) -> Dict[str, Any]:
        """
        下载并保存文件（便捷方法）
        
        Args:
            item_id: 剪切板项目ID
            save_path: 保存路径
            
        Returns:
            Dict: 保存结果
        """
        return self.files.save_file_to_disk(item_id, save_path)
    
    async def start_realtime_sync(self, message_handler=None):
        """
        启动实时同步
        
        Args:
            message_handler: 自定义消息处理器
        """
        print(f"=== 启动实时同步 ===")
        print(f"设备ID: {self.device_id}")
        
        await self.websocket.run_client(
            auto_get_content=True,
            message_handler=message_handler
        )
    
    def cleanup_old_content(self, days: int) -> Dict[str, Any]:
        """
        清理旧内容（便捷方法）
        
        Args:
            days: 保留最近几天的内容
            
        Returns:
            Dict: 清理结果
        """
        return self.config.cleanup_by_days(days)
    
    def cleanup_by_count(self, max_count: int) -> Dict[str, Any]:
        """
        按数量清理内容（便捷方法）
        
        Args:
            max_count: 保留的最大条目数
            
        Returns:
            Dict: 清理结果
        """
        return self.config.cleanup_by_count(max_count)
    
    def print_status_report(self):
        """打印状态报告"""
        print("\n" + "="*60)
        print("           剪切板同步服务状态报告")
        print("="*60)
        
        # 服务状态
        status = self.check_service_status()
        print(f"\n📊 服务状态:")
        print(f"  HTTP API: {status['http_api']['status']}")
        if status['http_api']['response_time']:
            print(f"    响应时间: {status['http_api']['response_time']}ms")
        
        print(f"  WebSocket: {status['websocket']['status']}")
        if status['websocket']['stats']:
            stats = status['websocket']['stats']
            print(f"    总连接数: {stats.get('totalConnections', 0)}")
            print(f"    活跃连接数: {stats.get('activeConnections', 0)}")
        
        # 剪切板摘要
        summary = self.get_clipboard_summary()
        print(f"\n📋 剪切板摘要:")
        print(f"  总条目数: {summary['total_items']}")
        print(f"  文本: {summary['text_items']}, 图片: {summary['image_items']}, 文件: {summary['file_items']}")
        
        if summary['devices']:
            print(f"  已连接设备: {len(summary['devices'])} 个")
            for device in summary['devices'][:3]:  # 只显示前3个
                print(f"    - {device.get('deviceId')}: {device.get('connectionCount')} 个连接")
        
        if summary['latest_items']:
            print(f"\n📝 最新内容 (前5条):")
            for i, item in enumerate(summary['latest_items'][:5], 1):
                content_preview = item.get('content', '')[:30]
                if len(content_preview) < len(item.get('content', '')):
                    content_preview += "..."
                print(f"  {i}. [{item.get('type', '未知')}] {content_preview}")
        
        print("\n" + "="*60)


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='剪切板同步服务客户端')
    parser.add_argument('--base-url', default='http://localhost:3001', help='HTTP API基础URL')
    parser.add_argument('--ws-url', default='ws://localhost:3002/ws', help='WebSocket URL')
    parser.add_argument('--device-id', help='设备ID')
    parser.add_argument('--mode', choices=['status', 'sync', 'test'], default='status', help='运行模式')
    
    args = parser.parse_args()
    
    # 创建客户端
    client = ClipboardSyncClient(
        base_url=args.base_url,
        ws_url=args.ws_url,
        device_id=args.device_id
    )
    
    if args.mode == 'status':
        # 状态报告模式
        client.print_status_report()
        
    elif args.mode == 'sync':
        # 实时同步模式
        async def run_sync():
            await client.start_realtime_sync()
        
        asyncio.run(run_sync())
        
    elif args.mode == 'test':
        # 测试模式
        print("=== 功能测试 ===")
        
        # 测试创建文本内容
        print("\n1. 测试创建文本内容:")
        result = client.create_text_content("测试文本内容")
        print(f"  结果: {'成功' if result.get('success') else '失败'}")
        if result.get('success'):
            item_id = result.get('data', {}).get('id')
            print(f"  项目ID: {item_id}")
        
        # 显示状态报告
        client.print_status_report()


if __name__ == "__main__":
    main()
