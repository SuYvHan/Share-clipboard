# 剪切板同步服务 Python API 客户端

这个目录包含了剪切板同步服务的完整 Python API 客户端实现，为每个 Swagger API 接口提供了对应的 Python 函数方法。

## 📁 文件结构

```
apiScript/
├── README.md                 # 本文档
├── health_api.py            # 健康检查API
├── clipboard_api.py         # 剪切板内容管理API
├── devices_api.py           # 设备管理API
├── config_api.py            # 配置管理API
├── files_api.py             # 文件管理API
├── websocket_api.py         # WebSocket实时通信API
├── clipboard_client.py      # 综合API客户端
├── examples.py              # 使用示例
└── ws_monitor.py            # WebSocket监控工具（已存在）
```

## 🚀 快速开始

### 1. 安装依赖

```bash
pip install requests websockets
```

### 2. 启动服务

确保剪切板同步服务正在运行：

```bash
# 在项目根目录
npm run dev
```

### 3. 运行示例

```bash
# 运行所有API使用示例
python examples.py

# 查看服务状态
python clipboard_client.py --mode status

# 启动实时同步监控
python clipboard_client.py --mode sync

# 运行WebSocket监控
python websocket_api.py --mode monitor
```

## 📚 API 模块说明

### 1. 健康检查 API (`health_api.py`)

检查服务器运行状态：

```python
from health_api import HealthAPI

api = HealthAPI()
result = api.check_health()
print(f"服务器状态: {'正常' if result['success'] else '异常'}")
```

### 2. 剪切板内容管理 API (`clipboard_api.py`)

管理剪切板内容的增删改查：

```python
from clipboard_api import ClipboardAPI

api = ClipboardAPI()

# 创建文本内容
result = api.create_text_item("测试文本", "device-001")

# 获取内容列表
items = api.get_clipboard_items(limit=10)

# 上传文件
result = api.upload_file("/path/to/file.txt", "device-001")

# 更新内容
result = api.update_item(item_id, content="新内容")

# 删除内容
result = api.delete_item(item_id)
```

### 3. 设备管理 API (`devices_api.py`)

管理WebSocket连接和设备信息：

```python
from devices_api import DevicesAPI

api = DevicesAPI()

# 获取连接统计
stats = api.get_connection_stats()

# 获取设备列表
devices = api.get_device_list()

# 检查WebSocket服务器状态
status = api.is_websocket_server_running()
```

### 4. 配置管理 API (`config_api.py`)

管理系统配置和数据清理：

```python
from config_api import ConfigAPI

api = ConfigAPI()

# 获取配置
config = api.get_user_config()

# 更新配置
result = api.update_max_items(1000)

# 清理过期内容
result = api.cleanup_by_days(30)  # 保留30天内的内容
result = api.cleanup_by_count(500)  # 保留最新500条

# 获取存储统计
stats = api.get_storage_stats()
```

### 5. 文件管理 API (`files_api.py`)

管理文件的预览、下载和存储：

```python
from files_api import FilesAPI

api = FilesAPI()

# 下载文件
result = api.download_file(item_id, "custom_name.txt")

# 预览文件
result = api.preview_file(item_id)

# 保存文件到本地
result = api.save_file_to_disk(item_id, "/save/path/")

# 获取文件统计
stats = api.get_file_stats()

# 手动清理文件
result = api.cleanup_files()
```

### 6. WebSocket 实时通信 API (`websocket_api.py`)

实时同步剪切板内容：

```python
import asyncio
from websocket_api import WebSocketAPI, WebSocketMonitor

# 方式1：使用监控器
monitor = WebSocketMonitor(device_id="my-device")
await monitor.start_monitoring()

# 方式2：使用API客户端
api = WebSocketAPI(device_id="my-device")
await api.connect()

# 获取所有内容
await api.get_all_content(limit=100)

# 获取最新内容
await api.get_latest(10)

# 同步新内容
await api.sync_content({
    "id": "uuid",
    "type": "text",
    "content": "新内容",
    "deviceId": "my-device"
})

# 删除内容
await api.delete_content(item_id)
```

### 7. 综合客户端 (`clipboard_client.py`)

整合所有API的统一客户端：

```python
from clipboard_client import ClipboardSyncClient

client = ClipboardSyncClient(device_id="my-device")

# 检查服务状态
client.print_status_report()

# 创建内容
result = client.create_text_content("测试内容")

# 上传文件
result = client.upload_file_from_path("/path/to/file.txt")

# 下载并保存文件
result = client.download_and_save_file(item_id, "/save/path/")

# 启动实时同步
await client.start_realtime_sync()

# 清理旧内容
result = client.cleanup_old_content(days=30)
```

## 🔧 配置选项

### 服务器地址配置

```python
# 自定义服务器地址
api = ClipboardAPI(base_url="http://your-server:3001")
websocket_api = WebSocketAPI(ws_url="ws://your-server:3002/ws")
```

### 安全请求头配置

```python
# 配置安全请求头
security_headers = {"X-API-Key": "your-api-key"}
files_api = FilesAPI(security_headers=security_headers)
websocket_api = WebSocketAPI(security_headers=security_headers)
```

### 设备ID配置

```python
# 自定义设备ID
client = ClipboardSyncClient(device_id="my-custom-device-id")
```

## 📖 使用示例

### 完整的剪切板操作流程

```python
from clipboard_client import ClipboardSyncClient
import asyncio

async def main():
    # 创建客户端
    client = ClipboardSyncClient(device_id="example-device")
    
    # 1. 检查服务状态
    client.print_status_report()
    
    # 2. 创建文本内容
    result = client.create_text_content("Hello, World!")
    if result.get('success'):
        item_id = result['data']['id']
        print(f"创建成功，ID: {item_id}")
    
    # 3. 获取内容列表
    items = client.clipboard.get_clipboard_items(limit=5)
    print(f"获取到 {len(items.get('data', []))} 条内容")
    
    # 4. 启动实时监控（可选）
    # await client.start_realtime_sync()

if __name__ == "__main__":
    asyncio.run(main())
```

### 文件操作示例

```python
from files_api import FilesAPI
import os

api = FilesAPI()

# 假设有一个文件类型的剪切板项目
item_id = "your-file-item-id"

# 下载文件
result = api.download_file(item_id, "downloaded_file.txt")
if result.get('success'):
    # 保存到本地
    with open("local_file.txt", "wb") as f:
        f.write(result['content'])
    print("文件下载并保存成功")

# 或者直接使用便捷方法
result = api.save_file_to_disk(item_id, "./downloads/")
print(f"文件保存到: {result.get('file_path')}")
```

## 🛠️ 命令行工具

### 1. 综合客户端命令行

```bash
# 查看服务状态
python clipboard_client.py --mode status

# 启动实时同步
python clipboard_client.py --mode sync --device-id my-device

# 运行功能测试
python clipboard_client.py --mode test
```

### 2. WebSocket监控命令行

```bash
# 启动监控
python websocket_api.py --mode monitor

# 指定设备ID
python websocket_api.py --mode monitor --device-id my-device

# 指定服务器地址
python websocket_api.py --url ws://localhost:3002/ws --mode monitor
```

### 3. 运行所有示例

```bash
# 运行完整的API使用示例
python examples.py
```

## 🔍 错误处理

所有API方法都返回统一的响应格式：

```python
{
    "success": bool,        # 操作是否成功
    "message": str,         # 响应消息
    "data": any,           # 响应数据（成功时）
    "status_code": int     # HTTP状态码
}
```

示例错误处理：

```python
result = api.create_text_item("test", "device-001")
if result.get('success'):
    print("操作成功")
    data = result.get('data')
else:
    print(f"操作失败: {result.get('message')}")
    if result.get('status_code'):
        print(f"状态码: {result['status_code']}")
```

## 📝 注意事项

1. **服务器状态**：使用前请确保剪切板同步服务正在运行
2. **网络连接**：确保能够访问API服务器地址
3. **WebSocket连接**：WebSocket功能需要服务器的WebSocket服务正常运行
4. **文件操作**：文件下载和预览需要有效的剪切板项目ID
5. **安全配置**：如果启用了安全请求头，需要正确配置
6. **异步操作**：WebSocket相关操作需要在异步环境中运行

## 🤝 贡献

欢迎提交问题和改进建议！
