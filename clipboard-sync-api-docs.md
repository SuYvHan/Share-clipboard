# 剪切板同步服务 API 文档

## 概述

本文档描述了剪切板同步服务的 REST API 接口，该服务提供跨设备的剪切板内容同步功能。API 基于 HTTP 协议，支持 JSON 格式的数据交换。

**API 基础地址**: `http://47.239.194.151:3001/api`

## 接口分类

### 1. Health - 健康检查接口

#### GET /health
检查服务运行状态

**请求方式**: GET

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/health' \
  -H 'accept: application/json'
```

**响应示例**:
```json
{
  "success": true,
  "message": "ok",
  "data": "string",
  "total": 0
}
```

**响应字段说明**:
- `success`: 请求是否成功
- `message`: 状态信息
- `data`: 返回数据
- `total`: 总数量

---

### 2. Clipboard - 剪切板内容管理接口

#### GET /clipboard
获取剪切板内容列表

**请求方式**: GET

**请求参数**:
- `page`: 页码，默认为1
- `limit`: 每页数量，默认为20

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/clipboard?page=1&limit=20' \
  -H 'accept: application/json'
```

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "id": "021c22d1-d233-4427-a05e-00e94c442457",
      "type": "text",
      "content": "我喜欢你",
      "deviceId": "device_1754890509661_3mvc8qfk0",
      "fileName": null,
      "fileSize": null,
      "mimeType": null,
      "createdAt": "2025-08-11T06:14:11.000Z",
      "updatedAt": "2025-08-11T06:14:11.000Z"
    }
  ],
  "total": 1
}
```

**响应字段说明**:
- `id`: 剪切板内容唯一标识
- `type`: 内容类型（text/image/file）
- `content`: 剪切板内容
- `deviceId`: 设备ID
- `fileName`: 文件名（文件类型时使用）
- `fileSize`: 文件大小（文件类型时使用）
- `mimeType`: MIME类型
- `createdAt`: 创建时间
- `updatedAt`: 更新时间

#### POST /clipboard
创建新的剪切板内容

**请求方式**: POST

**请求头**:
```
Content-Type: application/json
accept: application/json
```

**请求参数**:
- `type`: 内容类型（必填）
- `content`: 剪切板内容（必填）
- `deviceId`: 设备ID（必填）
- `fileName`: 文件名（可选）
- `fileSize`: 文件大小（可选）
- `mimeType`: MIME类型（可选）

**请求示例**:
```bash
curl -X 'POST' \
  'http://47.239.194.151:3001/api/clipboard' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "type": "text",
  "content": "string",
  "deviceId": "string",
  "fileName": "string",
  "fileSize": 0,
  "mimeType": "string"
}'
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "id": "f7a87112-1468-40bf-b93e-0d709b694f34",
    "type": "text",
    "content": "string",
    "deviceId": "string",
    "fileName": null,
    "fileSize": null,
    "mimeType": null,
    "createdAt": "2025-08-11T06:47:15.000Z",
    "updatedAt": "2025-08-11T06:47:15.000Z"
  },
  "message": "内容上传成功"
}
```

#### DELETE /clipboard/{id}
删除指定的剪切板内容

**请求方式**: DELETE

**路径参数**:
- `id`: 剪切板内容ID（必填）

**请求示例**:
```bash
curl -X 'DELETE' \
  'http://47.239.194.151:3001/api/clipboard/id' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "message": "string",
  "data": "string",
  "total": 0
}
```

**请求错误 (400)**:
```json
{
  "success": false,
  "message": "string"
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

#### GET /clipboard/{id}
获取指定的剪切板内容

**请求方式**: GET

**路径参数**:
- `id`: 剪切板内容ID（必填）

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/clipboard/id' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "id": "string",
    "type": "text",
    "content": "string",
    "deviceId": "string",
    "fileName": "string",
    "fileSize": 0,
    "mimeType": "string",
    "createdAt": "2025-08-11T06:57:51.719Z",
    "updatedAt": "2025-08-11T06:57:51.719Z"
  },
  "total": 0
}
```

**资源不存在 (404)**:
```json
{
  "success": false,
  "message": "string"
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

---

### 3. Devices - 设备管理接口

#### GET /devices/connections
获取WebSocket连接统计信息

**请求方式**: GET

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/devices/connections' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "totalConnections": 0,
    "activeConnections": 0,
    "deviceConnections": {}
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

**WebSocket服务未启动 (503)**:
```json
{
  "success": false,
  "message": "string"
}
```

**响应字段说明**:
- `totalConnections`: 总连接数
- `activeConnections`: 活跃连接数
- `deviceConnections`: 设备连接详情

---

### 4. Config - 配置管理接口

#### GET /config/client
获取客户端配置信息

**功能说明**: 获取前端应用需要的配置信息，如WebSocket端口等

**请求方式**: GET

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/config/client' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "websocket": {
      "port": 0
    }
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

#### GET /config
获取用户配置

**请求方式**: GET

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/config' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "data": {
    "maxItems": 2,
    "autoCleanupDays": 30
  }
}
```

或者:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "maxItems": 0,
    "autoCleanupDays": 0
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

**响应字段说明**:
- `maxItems`: 最大保存项目数
- `autoCleanupDays`: 自动清理天数

#### PUT /config
更新用户配置

**请求方式**: PUT

**请求参数**:
- `maxItems`: 最大保存项目数
- `autoCleanupDays`: 自动清理天数

**请求示例**:
```bash
curl -X 'PUT' \
  'http://47.239.194.151:3001/api/config' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "maxItems": 0,
  "autoCleanupDays": 0
}'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "data": {
    "maxItems": 0,
    "autoCleanupDays": 0
  },
  "message": "配置更新成功"
}
```

或者:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "maxItems": 0,
    "autoCleanupDays": 0
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

#### POST /config/cleanup
清理过期内容

**请求方式**: POST

**请求参数**:
- `maxCount`: 最大保留数量
- `beforeDate`: 清理此日期之前的内容

**请求示例**:
```bash
curl -X 'POST' \
  'http://47.239.194.151:3001/api/config/cleanup' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "maxCount": 0,
  "beforeDate": "2025-08-11"
}'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "data": {
    "deletedCount": 0,
    "remainingCount": 2
  },
  "message": "清理完成，删除了 0 个项目"
}
```

或者:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "deletedCount": 0,
    "remainingCount": 0
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

**响应字段说明**:
- `deletedCount`: 删除的项目数量
- `remainingCount`: 剩余的项目数量

#### DELETE /config/clear-all
清理所有内容

**请求方式**: DELETE

**请求示例**:
```bash
curl -X 'DELETE' \
  'http://47.239.194.151:3001/api/config/clear-all' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "data": {
    "deletedCount": 2
  },
  "message": "已清空所有内容，删除了 2 个项目"
}
```

或者:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "deletedCount": 0
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

#### GET /config/stats
获取存储统计信息

**请求方式**: GET

**请求示例**:
```bash
curl -X 'GET' \
  'http://47.239.194.151:3001/api/config/stats' \
  -H 'accept: application/json'
```

**响应示例**:

**成功响应 (200)**:
```json
{
  "success": true,
  "data": {
    "totalItems": 0,
    "textItems": null,
    "imageItems": null,
    "totalSize": "NaN undefined"
  }
}
```

或者:
```json
{
  "success": true,
  "message": "string",
  "data": {
    "totalItems": 0,
    "textItems": 0,
    "imageItems": 0,
    "totalSize": "string"
  },
  "total": 0
}
```

**服务器错误 (500)**:
```json
{
  "success": false,
  "message": "string"
}
```

**响应字段说明**:
- `totalItems`: 总项目数
- `textItems`: 文本项目数
- `imageItems`: 图片项目数
- `totalSize`: 总存储大小
---

### 5. WebSocket - 实时通信接口

#### WebSocket 连接端点
建立 WebSocket 连接以实时同步剪切板内容

**连接地址**: `ws://localhost:3002/ws?deviceId=your-device-id`

**连接参数**:
- `deviceId`: 设备唯一标识符（可选，用于设备管理）

#### 客户端发送的消息类型

##### 1. 获取所有剪切板内容 (get_all_content)
```json
{
  "type": "get_all_content",
  "data": {
    "limit": 1000,
    "type": "text",
    "search": "关键词",
    "deviceId": "设备ID"
  }
}
```

##### 2. 获取所有文本内容 (get_all_text)
```json
{
  "type": "get_all_text"
}
```

##### 3. 获取所有图片内容 (get_all_images)
```json
{
  "type": "get_all_images"
}
```

##### 4. 获取最新内容 (get_latest)
```json
{
  "type": "get_latest",
  "count": 10
}
```

##### 5. 同步剪切板内容 (sync)
```json
{
  "type": "sync",
  "data": {
    "id": "uuid",
    "type": "text",
    "content": "剪切板内容",
    "deviceId": "设备ID"
  }
}
```

##### 6. 删除剪切板内容 (delete)
```json
{
  "type": "delete",
  "id": "要删除的项目ID"
}
```
#### 服务器发送的消息类型

##### 1. 所有剪切板内容 (all_content)
```json
{
  "type": "all_content",
  "data": [剪切板项数组],
  "message": "成功获取 N 条剪切板内容",
  "count": 总数量
}
```

##### 2. 所有文本内容 (all_text)
```json
{
  "type": "all_text",
  "data": [文本剪切板项数组]
}
```

##### 3. 所有图片内容 (all_images)
```json
{
  "type": "all_images",
  "data": [图片剪切板项数组]
}
```

##### 4. 最新内容 (latest)
```json
{
  "type": "latest",
  "data": [最新剪切板项数组],
  "count": 请求的数量
}
```

##### 5. 新增内容通知 (sync)
```json
{
  "type": "sync",
  "data": 新的剪切板项
}
```

##### 6. 删除内容通知 (delete)
```json
{
  "type": "delete",
  "id": "被删除的项目ID"
}
```

##### 7. 连接统计 (connection_stats)
```json
{
  "type": "connection_stats",
  "data": {
    "totalConnections": 总连接数,
    "activeConnections": 活跃连接数,
    "connectedDevices": [设备列表]
  }
}
```

##### 8. 错误消息 (sync with error)
```json
{
  "type": "sync",
  "data": {
    "error": "错误信息"
  }
}
```
#### 心跳机制
- 服务器每30秒发送ping帧
- 客户端应响应pong帧
- 60秒无响应将断开连接

#### 使用示例
```javascript
const ws = new WebSocket('ws://localhost:3002/ws?deviceId=my-device');

ws.onopen = () => {
  // 连接成功后获取所有剪切板内容
  ws.send(JSON.stringify({
    type: 'get_all_content',
    data: { limit: 100 }
  }));
};

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log('收到消息:', message);
};
```

#### 连接状态码

| 状态码 | 描述 |
|--------|------|
| 101 | WebSocket 连接升级成功 |
| 400 | 请求参数错误 |
| 500 | 服务器错误 |

---

## 错误处理

### 通用错误响应格式

```json
{
  "success": false,
  "message": "错误描述信息"
}
```

### 常见HTTP状态码

| 状态码 | 描述 | 说明 |
|--------|------|------|
| 200 | OK | 请求成功 |
| 400 | Bad Request | 请求参数错误 |
| 404 | Not Found | 资源不存在 |
| 500 | Internal Server Error | 服务器内部错误 |
| 503 | Service Unavailable | 服务不可用（如WebSocket服务未启动） |

---

## 使用示例

### JavaScript 示例

```javascript
// 获取剪切板内容列表
fetch('http://47.239.194.151:3001/api/clipboard?page=1&limit=20', {
  method: 'GET',
  headers: {
    'accept': 'application/json'
  }
})
.then(response => response.json())
.then(data => console.log(data));

// 创建剪切板内容
fetch('http://47.239.194.151:3001/api/clipboard', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'accept': 'application/json'
  },
  body: JSON.stringify({
    type: 'text',
    content: 'Hello World',
    deviceId: 'my-device-001'
  })
})
.then(response => response.json())
.then(data => console.log(data));
```

### Python 示例

```python
import requests
import json

# API 基础配置
BASE_URL = 'http://47.239.194.151:3001/api'
HEADERS = {
    'accept': 'application/json',
    'Content-Type': 'application/json'
}

# 获取剪切板内容列表
response = requests.get(f'{BASE_URL}/clipboard?page=1&limit=20', headers=HEADERS)
print(response.json())

# 创建剪切板内容
data = {
    'type': 'text',
    'content': 'Hello World',
    'deviceId': 'my-device-001'
}
response = requests.post(f'{BASE_URL}/clipboard', 
                        headers=HEADERS, 
                        data=json.dumps(data))
print(response.json())
```

# 可选项
1. 设置webbsocket安全
curl -X 'POST' \
  'http://47.239.194.151/api/config/websocket-security' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "key": "X-API-Key",
  "value": "your-secret-key-here"
}'
---
密钥和value都是可选项都是可配置的

## 注意事项

1. **设备ID**: 建议使用唯一的设备标识符，便于设备管理和数据同步
2. **内容类型**: 支持 text、image、file 等多种类型的剪切板内容
3. **分页查询**: 获取列表时建议使用分页参数，避免一次性加载过多数据
4. **WebSocket连接**: 建议实现重连机制，确保连接稳定性
5. **错误处理**: 请根据返回的状态码和错误信息进行相应的错误处理

---

## 更新日志

### v1.0.0 (2025-01-01)
- 初始版本发布
- 支持剪切板内容的增删改查
- 提供 WebSocket 实时同步功能
- 支持设备管理和配置管理
- 提供存储统计和清理功能

---

**文档版本**: v1.0.0  
**最后更新**: 2025-01-11  
**API 基础地址**: http://47.239.194.151:3001/api
