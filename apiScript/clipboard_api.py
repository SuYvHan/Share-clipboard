#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
剪切板内容管理API接口
用于管理剪切板内容的增删改查操作
"""

import requests
from typing import Dict, Any, Optional, List, Union
import json
import base64
import os
from urllib.parse import urlencode


class ClipboardAPI:
    """剪切板内容管理API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:3001"):
        """
        初始化剪切板API客户端
        
        Args:
            base_url: API服务器基础URL
        """
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        
    def get_clipboard_items(self, 
                          page: int = 1, 
                          limit: int = 20,
                          content_type: Optional[str] = None,
                          search: Optional[str] = None,
                          filter_type: Optional[str] = None,
                          device_id: Optional[str] = None) -> Dict[str, Any]:
        """
        获取剪切板内容列表
        
        Args:
            page: 页码，默认1
            limit: 每页条目数，默认20，最大100
            content_type: 内容类型筛选 ('text', 'image', 'file')
            search: 搜索关键词
            filter_type: 特殊筛选 ('all_text', 'all_images', 'latest')
            device_id: 设备ID筛选
            
        Returns:
            Dict: API响应结果
        """
        try:
            params = {
                'page': page,
                'limit': min(100, max(1, limit))
            }
            
            if content_type:
                params['type'] = content_type
            if search:
                params['search'] = search
            if filter_type:
                params['filter'] = filter_type
            if device_id:
                params['deviceId'] = device_id
                
            response = self.session.get(
                f"{self.base_url}/api/clipboard",
                params=params,
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取剪切板内容失败: {str(e)}")
    
    def create_text_item(self, content: str, device_id: str) -> Dict[str, Any]:
        """
        创建文本类型的剪切板内容
        
        Args:
            content: 文本内容
            device_id: 设备ID
            
        Returns:
            Dict: API响应结果
        """
        try:
            data = {
                "type": "text",
                "content": content,
                "deviceId": device_id
            }
            
            response = self.session.post(
                f"{self.base_url}/api/clipboard",
                json=data,
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"创建文本内容失败: {str(e)}")
    
    def create_image_item(self, 
                         image_data: Union[str, bytes], 
                         device_id: str,
                         file_name: Optional[str] = None,
                         mime_type: Optional[str] = None) -> Dict[str, Any]:
        """
        创建图片类型的剪切板内容
        
        Args:
            image_data: 图片数据（Base64字符串或字节数据）
            device_id: 设备ID
            file_name: 文件名
            mime_type: MIME类型
            
        Returns:
            Dict: API响应结果
        """
        try:
            # 处理图片数据
            if isinstance(image_data, bytes):
                # 如果是字节数据，转换为base64
                base64_data = base64.b64encode(image_data).decode('utf-8')
                content = f"data:image/png;base64,{base64_data}"
            elif image_data.startswith('data:'):
                # 如果已经是data URL格式
                content = image_data
            else:
                # 如果是纯base64字符串
                content = f"data:image/png;base64,{image_data}"
            
            data = {
                "type": "image",
                "content": content,
                "deviceId": device_id
            }
            
            if file_name:
                data["fileName"] = file_name
            if mime_type:
                data["mimeType"] = mime_type
            if isinstance(image_data, bytes):
                data["fileSize"] = len(image_data)
                
            response = self.session.post(
                f"{self.base_url}/api/clipboard",
                json=data,
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"创建图片内容失败: {str(e)}")
    
    def create_file_item(self, 
                        file_data: Union[str, bytes], 
                        device_id: str,
                        file_name: str,
                        mime_type: Optional[str] = None) -> Dict[str, Any]:
        """
        创建文件类型的剪切板内容
        
        Args:
            file_data: 文件数据（Base64字符串或字节数据）
            device_id: 设备ID
            file_name: 文件名（必需）
            mime_type: MIME类型
            
        Returns:
            Dict: API响应结果
        """
        try:
            # 处理文件数据
            if isinstance(file_data, bytes):
                # 如果是字节数据，转换为base64
                content = base64.b64encode(file_data).decode('utf-8')
            else:
                # 如果是base64字符串
                content = file_data
            
            data = {
                "type": "file",
                "content": content,
                "deviceId": device_id,
                "fileName": file_name
            }
            
            if mime_type:
                data["mimeType"] = mime_type
            if isinstance(file_data, bytes):
                data["fileSize"] = len(file_data)
                
            response = self.session.post(
                f"{self.base_url}/api/clipboard",
                json=data,
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"创建文件内容失败: {str(e)}")
    
    def upload_file(self, 
                   file_path: str, 
                   device_id: str,
                   content_type: str = "file",
                   custom_name: Optional[str] = None) -> Dict[str, Any]:
        """
        通过multipart/form-data上传文件
        
        Args:
            file_path: 本地文件路径
            device_id: 设备ID
            content_type: 内容类型 ('file' 或 'image')
            custom_name: 自定义文件名
            
        Returns:
            Dict: API响应结果
        """
        try:
            if not os.path.exists(file_path):
                return self._error_response(f"文件不存在: {file_path}")
            
            with open(file_path, 'rb') as f:
                files = {'file': f}
                data = {
                    'type': content_type,
                    'deviceId': device_id
                }
                
                if custom_name:
                    data['fileName'] = custom_name
                
                response = self.session.post(
                    f"{self.base_url}/api/clipboard/upload",
                    files=files,
                    data=data,
                    timeout=60
                )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"上传文件失败: {str(e)}")
    
    def get_item_by_id(self, item_id: str) -> Dict[str, Any]:
        """
        根据ID获取单个剪切板内容
        
        Args:
            item_id: 剪切板项目ID
            
        Returns:
            Dict: API响应结果
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/clipboard/{item_id}",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取剪切板内容失败: {str(e)}")
    
    def update_item(self, 
                   item_id: str, 
                   content: Optional[str] = None,
                   file_name: Optional[str] = None) -> Dict[str, Any]:
        """
        更新剪切板内容
        
        Args:
            item_id: 剪切板项目ID
            content: 新的文本内容（仅限文本类型）
            file_name: 新的文件名（仅限文件和图片类型）
            
        Returns:
            Dict: API响应结果
        """
        try:
            data = {}
            if content is not None:
                data["content"] = content
            if file_name is not None:
                data["fileName"] = file_name
                
            if not data:
                return self._error_response("至少需要提供一个要更新的字段")
            
            response = self.session.put(
                f"{self.base_url}/api/clipboard/{item_id}",
                json=data,
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"更新剪切板内容失败: {str(e)}")
    
    def delete_item(self, item_id: str) -> Dict[str, Any]:
        """
        删除剪切板内容
        
        Args:
            item_id: 剪切板项目ID
            
        Returns:
            Dict: API响应结果
        """
        try:
            response = self.session.delete(
                f"{self.base_url}/api/clipboard/{item_id}",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"删除剪切板内容失败: {str(e)}")
    
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
    """主函数，用于测试剪切板API"""
    print("=== 剪切板内容管理API测试 ===")
    
    # 创建API客户端
    api = ClipboardAPI()
    
    # 测试获取剪切板内容列表
    print("\n1. 获取剪切板内容列表:")
    result = api.get_clipboard_items(limit=5)
    print(f"  结果: {result.get('success', False)}")
    if result.get('success'):
        print(f"  总数: {result.get('total', 0)}")
        print(f"  当前页数据量: {len(result.get('data', []))}")
    else:
        print(f"  错误: {result.get('message', '未知错误')}")
    
    # 测试创建文本内容
    print("\n2. 创建文本内容:")
    text_result = api.create_text_item("测试文本内容", "test-device-001")
    print(f"  结果: {text_result.get('success', False)}")
    if text_result.get('success'):
        item_id = text_result.get('data', {}).get('id')
        print(f"  创建的项目ID: {item_id}")
        
        # 测试更新内容
        if item_id:
            print("\n3. 更新文本内容:")
            update_result = api.update_item(item_id, content="更新后的文本内容")
            print(f"  结果: {update_result.get('success', False)}")
            
            # 测试删除内容
            print("\n4. 删除内容:")
            delete_result = api.delete_item(item_id)
            print(f"  结果: {delete_result.get('success', False)}")
    else:
        print(f"  错误: {text_result.get('message', '未知错误')}")


if __name__ == "__main__":
    main()
