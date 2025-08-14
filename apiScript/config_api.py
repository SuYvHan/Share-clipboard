#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
配置管理API接口
用于管理系统配置和数据清理操作
"""

import requests
from typing import Dict, Any, Optional
import json
from datetime import datetime, timedelta


class ConfigAPI:
    """配置管理API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:3001"):
        """
        初始化配置管理API客户端
        
        Args:
            base_url: API服务器基础URL
        """
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        
    def get_client_config(self) -> Dict[str, Any]:
        """
        获取客户端配置信息
        
        Returns:
            Dict: 客户端配置信息，包含WebSocket端口等
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/config/client",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取客户端配置失败: {str(e)}")
    
    def get_user_config(self) -> Dict[str, Any]:
        """
        获取用户配置信息
        
        Returns:
            Dict: 用户配置信息
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/config",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取用户配置失败: {str(e)}")
    
    def update_user_config(self, config: Dict[str, Any]) -> Dict[str, Any]:
        """
        更新用户配置
        
        Args:
            config: 要更新的配置项
            {
                "maxItems": int,                    # 最大条目数
                "autoCleanupDays": int,            # 自动清理天数
                "fileCleanup": {                   # 文件清理配置
                    "enabled": bool,
                    "maxFileCount": int,
                    "strategy": str                # 'oldest_first' 或 'largest_first'
                },
                "websocketSecurity": {             # WebSocket安全配置
                    "enabled": bool,
                    "key": str,
                    "value": str
                }
            }
            
        Returns:
            Dict: API响应结果
        """
        try:
            response = self.session.put(
                f"{self.base_url}/api/config",
                json=config,
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"更新用户配置失败: {str(e)}")
    
    def cleanup_expired_content(self, 
                              max_count: Optional[int] = None,
                              before_date: Optional[str] = None,
                              max_file_count: Optional[int] = None,
                              file_cleanup_strategy: Optional[str] = None) -> Dict[str, Any]:
        """
        清理过期内容
        
        Args:
            max_count: 保留的最大条目数
            before_date: 删除此日期之前的内容 (YYYY-MM-DD格式)
            max_file_count: 保留的最大文件数量
            file_cleanup_strategy: 文件清理策略 ('oldest_first' 或 'largest_first')
            
        Returns:
            Dict: API响应结果
        """
        try:
            data = {}
            
            if max_count is not None:
                data["maxCount"] = max_count
            if before_date is not None:
                data["beforeDate"] = before_date
            if max_file_count is not None:
                data["maxFileCount"] = max_file_count
            if file_cleanup_strategy is not None:
                data["fileCleanupStrategy"] = file_cleanup_strategy
            
            response = self.session.post(
                f"{self.base_url}/api/config/cleanup",
                json=data if data else None,
                timeout=60
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"清理过期内容失败: {str(e)}")
    
    def clear_all_content(self) -> Dict[str, Any]:
        """
        清空所有剪切板内容
        
        Returns:
            Dict: API响应结果
        """
        try:
            response = self.session.delete(
                f"{self.base_url}/api/config/clear-all",
                timeout=60
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"清空所有内容失败: {str(e)}")
    
    def get_storage_stats(self) -> Dict[str, Any]:
        """
        获取存储统计信息
        
        Returns:
            Dict: 存储统计信息
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/config/stats",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取存储统计失败: {str(e)}")
    
    def cleanup_by_days(self, days: int) -> Dict[str, Any]:
        """
        按天数清理过期内容（便捷方法）
        
        Args:
            days: 保留最近几天的内容
            
        Returns:
            Dict: API响应结果
        """
        try:
            # 计算日期
            cutoff_date = datetime.now() - timedelta(days=days)
            before_date = cutoff_date.strftime('%Y-%m-%d')
            
            return self.cleanup_expired_content(before_date=before_date)
            
        except Exception as e:
            return self._error_response(f"按天数清理失败: {str(e)}")
    
    def cleanup_by_count(self, max_count: int) -> Dict[str, Any]:
        """
        按数量清理内容（便捷方法）
        
        Args:
            max_count: 保留的最大条目数
            
        Returns:
            Dict: API响应结果
        """
        return self.cleanup_expired_content(max_count=max_count)
    
    def cleanup_files_by_count(self, max_file_count: int, strategy: str = 'oldest_first') -> Dict[str, Any]:
        """
        按文件数量清理（便捷方法）
        
        Args:
            max_file_count: 保留的最大文件数量
            strategy: 清理策略 ('oldest_first' 或 'largest_first')
            
        Returns:
            Dict: API响应结果
        """
        return self.cleanup_expired_content(
            max_file_count=max_file_count,
            file_cleanup_strategy=strategy
        )
    
    def update_max_items(self, max_items: int) -> Dict[str, Any]:
        """
        更新最大条目数配置（便捷方法）
        
        Args:
            max_items: 最大条目数
            
        Returns:
            Dict: API响应结果
        """
        return self.update_user_config({"maxItems": max_items})
    
    def update_auto_cleanup_days(self, days: int) -> Dict[str, Any]:
        """
        更新自动清理天数配置（便捷方法）
        
        Args:
            days: 自动清理天数
            
        Returns:
            Dict: API响应结果
        """
        return self.update_user_config({"autoCleanupDays": days})
    
    def enable_file_cleanup(self, max_file_count: int, strategy: str = 'oldest_first') -> Dict[str, Any]:
        """
        启用文件清理功能（便捷方法）
        
        Args:
            max_file_count: 最大文件数量
            strategy: 清理策略
            
        Returns:
            Dict: API响应结果
        """
        return self.update_user_config({
            "fileCleanup": {
                "enabled": True,
                "maxFileCount": max_file_count,
                "strategy": strategy
            }
        })
    
    def disable_file_cleanup(self) -> Dict[str, Any]:
        """
        禁用文件清理功能（便捷方法）
        
        Returns:
            Dict: API响应结果
        """
        return self.update_user_config({
            "fileCleanup": {
                "enabled": False
            }
        })
    
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
    """主函数，用于测试配置管理API"""
    print("=== 配置管理API测试 ===")
    
    # 创建API客户端
    api = ConfigAPI()
    
    # 获取客户端配置
    print("\n1. 获取客户端配置:")
    client_config = api.get_client_config()
    if client_config.get('success'):
        data = client_config.get('data', {})
        ws_config = data.get('websocket', {})
        print(f"  WebSocket端口: {ws_config.get('port', '未知')}")
    else:
        print(f"  获取失败: {client_config.get('message')}")
    
    # 获取用户配置
    print("\n2. 获取用户配置:")
    user_config = api.get_user_config()
    if user_config.get('success'):
        data = user_config.get('data', {})
        print(f"  最大条目数: {data.get('maxItems', '未知')}")
        print(f"  自动清理天数: {data.get('autoCleanupDays', '未知')}")
        
        file_cleanup = data.get('fileCleanup', {})
        print(f"  文件清理: {'启用' if file_cleanup.get('enabled') else '禁用'}")
        if file_cleanup.get('enabled'):
            print(f"    最大文件数: {file_cleanup.get('maxFileCount', '未知')}")
            print(f"    清理策略: {file_cleanup.get('strategy', '未知')}")
    else:
        print(f"  获取失败: {user_config.get('message')}")
    
    # 获取存储统计
    print("\n3. 获取存储统计:")
    stats = api.get_storage_stats()
    if stats.get('success'):
        data = stats.get('data', {})
        print(f"  总条目数: {data.get('totalItems', 0)}")
        print(f"  文本条目: {data.get('textItems', 0)}")
        print(f"  图片条目: {data.get('imageItems', 0)}")
        print(f"  文件条目: {data.get('fileItems', 0)}")
        print(f"  总存储大小: {data.get('totalSize', '未知')}")
    else:
        print(f"  获取失败: {stats.get('message')}")
    
    print("\n=== 测试完成 ===")


if __name__ == "__main__":
    main()
