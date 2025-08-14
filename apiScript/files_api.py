#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
文件管理API接口
用于文件的预览、下载和管理操作
"""

import requests
from typing import Dict, Any, Optional
import json
import os
from urllib.parse import urlencode


class FilesAPI:
    """文件管理API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:3001", security_headers: Optional[Dict[str, str]] = None):
        """
        初始化文件管理API客户端
        
        Args:
            base_url: API服务器基础URL
            security_headers: 安全请求头，如 {'X-API-Key': 'your-key'}
        """
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        
        # 设置安全请求头
        if security_headers:
            self.session.headers.update(security_headers)
    
    def preview_file(self, item_id: str, file_name: Optional[str] = None) -> Dict[str, Any]:
        """
        预览文件（内联显示）
        
        Args:
            item_id: 剪切板项目ID
            file_name: 自定义文件名（可选）
            
        Returns:
            Dict: 包含文件内容的响应
            {
                "success": bool,
                "content": bytes,        # 文件二进制内容
                "content_type": str,     # MIME类型
                "file_name": str,        # 文件名
                "file_size": int,        # 文件大小
                "status_code": int
            }
        """
        try:
            params = {'id': item_id}
            if file_name:
                params['name'] = file_name
            
            response = self.session.get(
                f"{self.base_url}/api/files/preview",
                params=params,
                timeout=60
            )
            
            if response.status_code == 200:
                return {
                    "success": True,
                    "content": response.content,
                    "content_type": response.headers.get('Content-Type', 'application/octet-stream'),
                    "file_name": self._extract_filename_from_headers(response.headers),
                    "file_size": len(response.content),
                    "status_code": response.status_code
                }
            else:
                # 尝试解析JSON错误响应
                try:
                    error_data = response.json()
                    return {
                        "success": False,
                        "message": error_data.get('message', '预览文件失败'),
                        "status_code": response.status_code
                    }
                except:
                    return {
                        "success": False,
                        "message": f"预览文件失败，状态码: {response.status_code}",
                        "status_code": response.status_code
                    }
                    
        except Exception as e:
            return self._error_response(f"预览文件失败: {str(e)}")
    
    def download_file(self, item_id: str, file_name: Optional[str] = None) -> Dict[str, Any]:
        """
        下载文件
        
        Args:
            item_id: 剪切板项目ID
            file_name: 自定义文件名（可选）
            
        Returns:
            Dict: 包含文件内容的响应
        """
        try:
            params = {'id': item_id}
            if file_name:
                params['name'] = file_name
            
            response = self.session.get(
                f"{self.base_url}/api/files/download",
                params=params,
                timeout=60
            )
            
            if response.status_code == 200:
                return {
                    "success": True,
                    "content": response.content,
                    "content_type": response.headers.get('Content-Type', 'application/octet-stream'),
                    "file_name": self._extract_filename_from_headers(response.headers),
                    "file_size": len(response.content),
                    "status_code": response.status_code
                }
            else:
                # 尝试解析JSON错误响应
                try:
                    error_data = response.json()
                    return {
                        "success": False,
                        "message": error_data.get('message', '下载文件失败'),
                        "status_code": response.status_code
                    }
                except:
                    return {
                        "success": False,
                        "message": f"下载文件失败，状态码: {response.status_code}",
                        "status_code": response.status_code
                    }
                    
        except Exception as e:
            return self._error_response(f"下载文件失败: {str(e)}")
    
    def download_file_legacy(self, item_id: str) -> Dict[str, Any]:
        """
        下载文件（传统路径参数版本）
        
        Args:
            item_id: 剪切板项目ID
            
        Returns:
            Dict: 包含文件内容的响应
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/files/{item_id}",
                timeout=60
            )
            
            if response.status_code == 200:
                return {
                    "success": True,
                    "content": response.content,
                    "content_type": response.headers.get('Content-Type', 'application/octet-stream'),
                    "file_name": self._extract_filename_from_headers(response.headers),
                    "file_size": len(response.content),
                    "status_code": response.status_code
                }
            else:
                try:
                    error_data = response.json()
                    return {
                        "success": False,
                        "message": error_data.get('message', '下载文件失败'),
                        "status_code": response.status_code
                    }
                except:
                    return {
                        "success": False,
                        "message": f"下载文件失败，状态码: {response.status_code}",
                        "status_code": response.status_code
                    }
                    
        except Exception as e:
            return self._error_response(f"下载文件失败: {str(e)}")
    
    def preview_file_legacy(self, item_id: str) -> Dict[str, Any]:
        """
        预览文件（传统路径参数版本）
        
        Args:
            item_id: 剪切板项目ID
            
        Returns:
            Dict: 包含文件内容的响应
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/files/{item_id}/preview",
                timeout=60
            )
            
            if response.status_code == 200:
                return {
                    "success": True,
                    "content": response.content,
                    "content_type": response.headers.get('Content-Type', 'application/octet-stream'),
                    "file_name": self._extract_filename_from_headers(response.headers),
                    "file_size": len(response.content),
                    "status_code": response.status_code
                }
            else:
                try:
                    error_data = response.json()
                    return {
                        "success": False,
                        "message": error_data.get('message', '预览文件失败'),
                        "status_code": response.status_code
                    }
                except:
                    return {
                        "success": False,
                        "message": f"预览文件失败，状态码: {response.status_code}",
                        "status_code": response.status_code
                    }
                    
        except Exception as e:
            return self._error_response(f"预览文件失败: {str(e)}")
    
    def get_file_stats(self) -> Dict[str, Any]:
        """
        获取文件存储统计信息
        
        Returns:
            Dict: 文件统计信息
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/files/stats",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取文件统计失败: {str(e)}")
    
    def cleanup_files(self) -> Dict[str, Any]:
        """
        手动触发文件清理
        
        Returns:
            Dict: 清理结果
        """
        try:
            response = self.session.post(
                f"{self.base_url}/api/files/cleanup",
                timeout=120
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"文件清理失败: {str(e)}")
    
    def get_cleanup_status(self) -> Dict[str, Any]:
        """
        获取文件清理任务状态
        
        Returns:
            Dict: 清理任务状态
        """
        try:
            response = self.session.get(
                f"{self.base_url}/api/files/cleanup/status",
                timeout=30
            )
            
            return self._handle_response(response)
            
        except Exception as e:
            return self._error_response(f"获取清理状态失败: {str(e)}")
    
    def save_file_to_disk(self, item_id: str, save_path: str, file_name: Optional[str] = None) -> Dict[str, Any]:
        """
        下载文件并保存到本地磁盘
        
        Args:
            item_id: 剪切板项目ID
            save_path: 保存路径（目录）
            file_name: 自定义文件名（可选）
            
        Returns:
            Dict: 保存结果
        """
        try:
            # 下载文件
            download_result = self.download_file(item_id, file_name)
            
            if not download_result.get('success'):
                return download_result
            
            # 确保保存目录存在
            os.makedirs(save_path, exist_ok=True)
            
            # 确定文件名
            final_file_name = file_name or download_result.get('file_name', f'file_{item_id}')
            file_path = os.path.join(save_path, final_file_name)
            
            # 保存文件
            with open(file_path, 'wb') as f:
                f.write(download_result['content'])
            
            return {
                "success": True,
                "message": "文件保存成功",
                "file_path": file_path,
                "file_name": final_file_name,
                "file_size": download_result.get('file_size', 0),
                "content_type": download_result.get('content_type')
            }
            
        except Exception as e:
            return self._error_response(f"保存文件失败: {str(e)}")
    
    def _extract_filename_from_headers(self, headers: Dict[str, str]) -> str:
        """从响应头中提取文件名"""
        content_disposition = headers.get('Content-Disposition', '')
        if 'filename=' in content_disposition:
            # 提取filename参数
            parts = content_disposition.split('filename=')
            if len(parts) > 1:
                filename = parts[1].strip('"').strip("'")
                # URL解码
                try:
                    from urllib.parse import unquote
                    return unquote(filename)
                except:
                    return filename
        return 'unknown_file'
    
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
    """主函数，用于测试文件管理API"""
    print("=== 文件管理API测试 ===")
    
    # 创建API客户端
    api = FilesAPI()
    
    # 获取文件统计
    print("\n1. 获取文件存储统计:")
    stats = api.get_file_stats()
    if stats.get('success'):
        data = stats.get('data', {})
        print(f"  总文件数: {data.get('totalFiles', 0)}")
        print(f"  总大小: {data.get('totalSize', 0)} 字节")
        print(f"  目录大小: {data.get('directorySize', 0)} 字节")
        print(f"  文件计数: {data.get('fileCount', 0)}")
    else:
        print(f"  获取失败: {stats.get('message')}")
    
    # 获取清理状态
    print("\n2. 获取文件清理状态:")
    cleanup_status = api.get_cleanup_status()
    if cleanup_status.get('success'):
        data = cleanup_status.get('data', {})
        print(f"  是否已调度: {data.get('isScheduled', False)}")
        print(f"  是否正在运行: {data.get('isRunning', False)}")
    else:
        print(f"  获取失败: {cleanup_status.get('message')}")
    
    print("\n=== 测试完成 ===")
    print("\n注意: 文件预览和下载功能需要有效的剪切板项目ID才能测试")


if __name__ == "__main__":
    main()
