#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
设备管理API接口
用于获取WebSocket连接统计信息
"""

import requests
from typing import Dict, Any
import json


class DevicesAPI:
    """设备管理API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:3001"):
        """
        初始化设备管理API客户端
        
        Args:
            base_url: API服务器基础URL
        """
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        
    def get_connection_stats(self) -> Dict[str, Any]:
        """
        获取WebSocket连接统计信息
        
        Returns:
            Dict: API响应结果，包含连接统计信息
            {
                "success": bool,
                "data": {
                    "totalConnections": int,      # 总连接数
                    "activeConnections": int,     # 活跃连接数
                    "deviceConnections": dict,    # 设备连接统计
                    "connectedDevices": list      # 已连接设备列表
                },
                "message": str,
                "status_code": int
            }
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/devices/connections",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except requests.exceptions.ConnectionError:
            return self._error_response("无法连接到服务器")
        except requests.exceptions.Timeout:
            return self._error_response("请求超时")
        except Exception as e:
            return self._error_response(f"获取连接统计失败: {str(e)}")
    
    def get_device_list(self) -> Dict[str, Any]:
        """
        获取已连接设备列表（从连接统计中提取）
        
        Returns:
            Dict: 包含设备列表的响应
        """
        try:
            stats_result = self.get_connection_stats()
            
            if not stats_result.get('success'):
                return stats_result
            
            data = stats_result.get('data', {})
            devices = data.get('connectedDevices', [])
            device_connections = data.get('deviceConnections', {})
            
            # 构建设备详细信息列表
            device_list = []
            for device_id in devices:
                device_info = {
                    'deviceId': device_id,
                    'connectionCount': device_connections.get(device_id, 0),
                    'isActive': device_id in devices
                }
                device_list.append(device_info)
            
            return {
                'success': True,
                'data': {
                    'devices': device_list,
                    'totalDevices': len(device_list),
                    'totalConnections': data.get('totalConnections', 0),
                    'activeConnections': data.get('activeConnections', 0)
                },
                'message': f'成功获取 {len(device_list)} 个设备信息',
                'status_code': stats_result.get('status_code')
            }
            
        except Exception as e:
            return self._error_response(f"获取设备列表失败: {str(e)}")
    
    def is_websocket_server_running(self) -> Dict[str, Any]:
        """
        检查WebSocket服务器是否正在运行
        
        Returns:
            Dict: 检查结果
        """
        try:
            result = self.get_connection_stats()
            
            if result.get('status_code') == 503:
                return {
                    'success': False,
                    'running': False,
                    'message': 'WebSocket服务器未启动',
                    'status_code': 503
                }
            elif result.get('success'):
                return {
                    'success': True,
                    'running': True,
                    'message': 'WebSocket服务器正在运行',
                    'status_code': result.get('status_code'),
                    'stats': result.get('data')
                }
            else:
                return {
                    'success': False,
                    'running': False,
                    'message': result.get('message', '检查失败'),
                    'status_code': result.get('status_code')
                }
                
        except Exception as e:
            return {
                'success': False,
                'running': False,
                'message': f"检查WebSocket服务器状态失败: {str(e)}",
                'status_code': None
            }
    
    def _handle_response(self, response: requests.Response) -> Dict[str, Any]:
        """处理API响应"""
        try:
            data = response.json()
            data['status_code'] = response.status_code
            return data
        except:
            return {
                "success": False,
                "message": f"响应解析失败，状态码: {response.status_code}",
                "status_code": response.status_code
            }
    
    def _error_response(self, message: str) -> Dict[str, Any]:
        """生成错误响应"""
        return {
            "success": False,
            "message": message,
            "status_code": None
        }


def main():
    """主函数，用于测试设备管理API"""
    print("=== 设备管理API测试 ===")
    
    # 创建API客户端
    api = DevicesAPI()
    
    # 检查WebSocket服务器状态
    print("\n1. 检查WebSocket服务器状态:")
    server_status = api.is_websocket_server_running()
    print(f"  服务器运行状态: {'✅ 运行中' if server_status.get('running') else '❌ 未运行'}")
    print(f"  消息: {server_status.get('message')}")
    
    if server_status.get('running'):
        # 获取连接统计
        print("\n2. 获取WebSocket连接统计:")
        stats_result = api.get_connection_stats()
        
        if stats_result.get('success'):
            data = stats_result.get('data', {})
            print(f"  总连接数: {data.get('totalConnections', 0)}")
            print(f"  活跃连接数: {data.get('activeConnections', 0)}")
            
            device_connections = data.get('deviceConnections', {})
            if device_connections:
                print(f"  设备连接统计:")
                for device_id, count in device_connections.items():
                    print(f"    - {device_id}: {count} 个连接")
            else:
                print(f"  当前无设备连接")
        else:
            print(f"  获取统计失败: {stats_result.get('message')}")
        
        # 获取设备列表
        print("\n3. 获取已连接设备列表:")
        devices_result = api.get_device_list()
        
        if devices_result.get('success'):
            data = devices_result.get('data', {})
            devices = data.get('devices', [])
            print(f"  设备总数: {data.get('totalDevices', 0)}")
            
            if devices:
                print(f"  设备详情:")
                for device in devices:
                    status = "🟢 活跃" if device.get('isActive') else "🔴 离线"
                    print(f"    - {device.get('deviceId')}: {device.get('connectionCount')} 个连接 ({status})")
            else:
                print(f"  当前无设备连接")
        else:
            print(f"  获取设备列表失败: {devices_result.get('message')}")
    
    print("\n=== 测试完成 ===")


if __name__ == "__main__":
    main()
