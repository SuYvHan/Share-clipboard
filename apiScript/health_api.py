#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
健康检查API接口
用于检查剪切板同步服务器是否正常运行
"""

import requests
from typing import Dict, Any, Optional
import json


class HealthAPI:
    """健康检查API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:3001"):
        """
        初始化健康检查API客户端
        
        Args:
            base_url: API服务器基础URL
        """
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        
    def check_health(self) -> Dict[str, Any]:
        """
        检查服务器健康状态
        
        Returns:
            Dict: 包含健康检查结果的字典
            {
                "success": bool,
                "message": str,
                "status_code": int,
                "response_time": float
            }
        """
        try:
            import time
            start_time = time.time()
            
            response = self.session.get(
                f"{self.base_url}/api/health",
                timeout=10
            )
            
            response_time = time.time() - start_time
            
            if response.status_code == 200:
                data = response.json()
                return {
                    "success": True,
                    "message": data.get("message", "ok"),
                    "status_code": response.status_code,
                    "response_time": round(response_time * 1000, 2),  # 毫秒
                    "data": data
                }
            else:
                return {
                    "success": False,
                    "message": f"服务器返回错误状态码: {response.status_code}",
                    "status_code": response.status_code,
                    "response_time": round(response_time * 1000, 2),
                    "data": None
                }
                
        except requests.exceptions.ConnectionError:
            return {
                "success": False,
                "message": "无法连接到服务器",
                "status_code": None,
                "response_time": None,
                "data": None
            }
        except requests.exceptions.Timeout:
            return {
                "success": False,
                "message": "请求超时",
                "status_code": None,
                "response_time": None,
                "data": None
            }
        except Exception as e:
            return {
                "success": False,
                "message": f"请求失败: {str(e)}",
                "status_code": None,
                "response_time": None,
                "data": None
            }


def main():
    """主函数，用于测试健康检查API"""
    print("=== 剪切板同步服务健康检查 ===")
    
    # 创建API客户端
    api = HealthAPI()
    
    # 执行健康检查
    result = api.check_health()
    
    # 打印结果
    print(f"健康检查结果:")
    print(f"  状态: {'✅ 正常' if result['success'] else '❌ 异常'}")
    print(f"  消息: {result['message']}")
    print(f"  状态码: {result['status_code']}")
    if result['response_time'] is not None:
        print(f"  响应时间: {result['response_time']}ms")
    
    if result['data']:
        print(f"  详细信息: {json.dumps(result['data'], ensure_ascii=False, indent=2)}")
    
    return result['success']


if __name__ == "__main__":
    import sys
    success = main()
    sys.exit(0 if success else 1)
