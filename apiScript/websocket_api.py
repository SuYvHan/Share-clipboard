#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
WebSocket API接口
用于实时剪切板内容同步
"""

import asyncio
import websockets
import json
from typing import Dict, Any, Optional, Callable, List
import logging
from datetime import datetime


class WebSocketAPI:
    """WebSocket API客户端"""
    
    def __init__(self, 
                 ws_url: str = "ws://localhost:3002/ws",
                 device_id: Optional[str] = None,
                 security_headers: Optional[Dict[str, str]] = None):
        """
        初始化WebSocket API客户端
        
        Args:
            ws_url: WebSocket服务器URL
            device_id: 设备ID
            security_headers: 安全请求头
        """
        self.ws_url = ws_url
        self.device_id = device_id or f"python-client-{datetime.now().strftime('%Y%m%d%H%M%S')}"
        self.security_headers = security_headers or {}
        self.websocket = None
        self.is_connected = False
        self.message_handlers = {}
        self.logger = logging.getLogger(__name__)
        
        # 添加设备ID到URL
        if '?' in self.ws_url:
            self.ws_url += f"&deviceId={self.device_id}"
        else:
            self.ws_url += f"?deviceId={self.device_id}"
    
    async def connect(self) -> bool:
        """
        连接到WebSocket服务器
        
        Returns:
            bool: 连接是否成功
        """
        try:
            # 构建连接参数
            extra_headers = self.security_headers if self.security_headers else None
            
            self.websocket = await websockets.connect(
                self.ws_url,
                extra_headers=extra_headers,
                ping_interval=30,
                ping_timeout=10
            )
            
            self.is_connected = True
            self.logger.info(f"WebSocket连接成功: {self.ws_url}")
            return True
            
        except Exception as e:
            self.logger.error(f"WebSocket连接失败: {str(e)}")
            self.is_connected = False
            return False
    
    async def disconnect(self):
        """断开WebSocket连接"""
        if self.websocket:
            await self.websocket.close()
            self.is_connected = False
            self.logger.info("WebSocket连接已断开")
    
    async def send_message(self, message: Dict[str, Any]) -> bool:
        """
        发送消息到服务器
        
        Args:
            message: 要发送的消息
            
        Returns:
            bool: 发送是否成功
        """
        if not self.is_connected or not self.websocket:
            self.logger.error("WebSocket未连接")
            return False
        
        try:
            await self.websocket.send(json.dumps(message, ensure_ascii=False))
            return True
        except Exception as e:
            self.logger.error(f"发送消息失败: {str(e)}")
            return False
    
    async def listen_messages(self, message_handler: Optional[Callable] = None):
        """
        监听服务器消息
        
        Args:
            message_handler: 消息处理函数
        """
        if not self.is_connected or not self.websocket:
            self.logger.error("WebSocket未连接")
            return
        
        try:
            async for message in self.websocket:
                try:
                    data = json.loads(message)
                    
                    # 调用消息处理器
                    if message_handler:
                        await message_handler(data)
                    
                    # 调用注册的处理器
                    message_type = data.get('type')
                    if message_type in self.message_handlers:
                        await self.message_handlers[message_type](data)
                        
                except json.JSONDecodeError:
                    self.logger.error(f"无法解析消息: {message}")
                except Exception as e:
                    self.logger.error(f"处理消息失败: {str(e)}")
                    
        except websockets.exceptions.ConnectionClosed:
            self.logger.info("WebSocket连接已关闭")
            self.is_connected = False
        except Exception as e:
            self.logger.error(f"监听消息失败: {str(e)}")
            self.is_connected = False
    
    def register_message_handler(self, message_type: str, handler: Callable):
        """
        注册消息处理器
        
        Args:
            message_type: 消息类型
            handler: 处理函数
        """
        self.message_handlers[message_type] = handler
    
    async def get_all_content(self, 
                            limit: int = 1000,
                            content_type: Optional[str] = None,
                            search: Optional[str] = None,
                            device_id: Optional[str] = None) -> bool:
        """
        获取所有剪切板内容
        
        Args:
            limit: 限制数量
            content_type: 内容类型筛选
            search: 搜索关键词
            device_id: 设备ID筛选
            
        Returns:
            bool: 发送是否成功
        """
        message = {
            "type": "get_all_content",
            "data": {
                "limit": limit
            }
        }
        
        if content_type:
            message["data"]["type"] = content_type
        if search:
            message["data"]["search"] = search
        if device_id:
            message["data"]["deviceId"] = device_id
        
        return await self.send_message(message)
    
    async def get_all_text(self) -> bool:
        """获取所有文本内容"""
        return await self.send_message({"type": "get_all_text"})
    
    async def get_all_images(self) -> bool:
        """获取所有图片内容"""
        return await self.send_message({"type": "get_all_images"})
    
    async def get_latest(self, count: int = 10) -> bool:
        """
        获取最新内容
        
        Args:
            count: 获取数量
            
        Returns:
            bool: 发送是否成功
        """
        return await self.send_message({
            "type": "get_latest",
            "count": count
        })
    
    async def sync_content(self, clipboard_item: Dict[str, Any]) -> bool:
        """
        同步剪切板内容
        
        Args:
            clipboard_item: 剪切板项目数据
            
        Returns:
            bool: 发送是否成功
        """
        return await self.send_message({
            "type": "sync",
            "data": clipboard_item
        })
    
    async def delete_content(self, item_id: str) -> bool:
        """
        删除剪切板内容
        
        Args:
            item_id: 项目ID
            
        Returns:
            bool: 发送是否成功
        """
        return await self.send_message({
            "type": "delete",
            "id": item_id
        })
    
    async def run_client(self, 
                        auto_get_content: bool = True,
                        message_handler: Optional[Callable] = None):
        """
        运行WebSocket客户端
        
        Args:
            auto_get_content: 连接后是否自动获取内容
            message_handler: 消息处理函数
        """
        # 连接到服务器
        if not await self.connect():
            return
        
        try:
            # 自动获取内容
            if auto_get_content:
                await self.get_all_content(limit=50)
            
            # 开始监听消息
            await self.listen_messages(message_handler)
            
        except KeyboardInterrupt:
            self.logger.info("用户中断连接")
        except Exception as e:
            self.logger.error(f"客户端运行错误: {str(e)}")
        finally:
            await self.disconnect()


class WebSocketMonitor:
    """WebSocket监控器，用于监控剪切板变化"""
    
    def __init__(self, ws_url: str = "ws://localhost:3002/ws", device_id: Optional[str] = None):
        self.api = WebSocketAPI(ws_url, device_id)
        self.received_messages = []
        
    async def default_message_handler(self, message: Dict[str, Any]):
        """默认消息处理器"""
        message_type = message.get('type')
        timestamp = datetime.now().strftime('%H:%M:%S')
        
        print(f"\n[{timestamp}] 收到消息类型: {message_type}")
        
        if message_type == 'sync':
            data = message.get('data', {})
            if 'error' in data:
                print(f"  ❌ 错误: {data['error']}")
            else:
                print(f"  🆕 新内容: {data.get('type', '未知')} - {data.get('content', '')[:50]}...")
                
        elif message_type == 'delete':
            item_id = message.get('id')
            print(f"  🗑️ 删除内容: {item_id}")
            
        elif message_type == 'all_content':
            data = message.get('data', [])
            count = message.get('count', len(data))
            print(f"  📋 获取到 {len(data)} 条内容 (总计: {count})")
            
        elif message_type == 'all_text':
            data = message.get('data', [])
            print(f"  📝 获取到 {len(data)} 条文本内容")
            
        elif message_type == 'all_images':
            data = message.get('data', [])
            print(f"  🖼️ 获取到 {len(data)} 条图片内容")
            
        elif message_type == 'latest':
            data = message.get('data', [])
            count = message.get('count', 0)
            print(f"  ⏰ 获取到最新 {len(data)} 条内容 (请求: {count})")
            
        elif message_type == 'connection_stats':
            data = message.get('data', {})
            print(f"  📊 连接统计:")
            print(f"    总连接数: {data.get('totalConnections', 0)}")
            print(f"    活跃连接数: {data.get('activeConnections', 0)}")
            devices = data.get('connectedDevices', [])
            if devices:
                print(f"    已连接设备: {', '.join(devices)}")
        
        # 保存消息
        self.received_messages.append({
            'timestamp': datetime.now(),
            'message': message
        })
    
    async def start_monitoring(self):
        """开始监控"""
        print(f"=== WebSocket监控器启动 ===")
        print(f"连接地址: {self.api.ws_url}")
        print(f"设备ID: {self.api.device_id}")
        print("按 Ctrl+C 停止监控\n")
        
        await self.api.run_client(
            auto_get_content=True,
            message_handler=self.default_message_handler
        )
    
    def get_message_history(self) -> List[Dict[str, Any]]:
        """获取消息历史"""
        return self.received_messages


async def main():
    """主函数，用于测试WebSocket API"""
    import argparse
    
    parser = argparse.ArgumentParser(description='WebSocket API测试工具')
    parser.add_argument('--url', default='ws://localhost:3002/ws', help='WebSocket服务器URL')
    parser.add_argument('--device-id', help='设备ID')
    parser.add_argument('--mode', choices=['monitor', 'test'], default='monitor', help='运行模式')
    
    args = parser.parse_args()
    
    if args.mode == 'monitor':
        # 监控模式
        monitor = WebSocketMonitor(args.url, args.device_id)
        await monitor.start_monitoring()
    else:
        # 测试模式
        api = WebSocketAPI(args.url, args.device_id)
        
        if await api.connect():
            print("WebSocket连接成功！")
            
            # 发送测试消息
            await api.get_all_content(limit=10)
            await asyncio.sleep(1)
            
            await api.get_latest(5)
            await asyncio.sleep(1)
            
            await api.disconnect()
        else:
            print("WebSocket连接失败！")


if __name__ == "__main__":
    # 设置日志
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    
    asyncio.run(main())
