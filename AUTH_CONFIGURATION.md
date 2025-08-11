# 认证配置说明

## 🔐 新的认证参数格式

根据您的要求，应用已更新为支持灵活的请求头认证方式，WebSocket连接URL格式如下：

```
ws://47.239.194.151:3002/?deviceId=ID&authKey=key&authValue=value
```

## 📋 配置字段说明

### 必填字段
- **服务器地址**: WebSocket服务器的IP地址或域名
- **WebSocket端口**: 服务器WebSocket服务端口（默认3002）

### 可选字段
- **设备ID**: 设备唯一标识符（留空自动生成）
- **请求头名称 (Header Key)**: 认证请求头的名称
- **请求头值 (Header Value)**: 认证请求头的值

## 🔧 配置示例

### 示例1: 基本连接（无认证）
```
服务器地址: 47.239.194.151
WebSocket端口: 3002
设备ID: (留空自动生成)
请求头名称: (留空)
请求头值: (留空)
```
**生成的URL**: `ws://47.239.194.151:3002/?deviceId=auto-generated-id`

### 示例2: Bearer Token认证
```
服务器地址: 47.239.194.151
WebSocket端口: 3002
设备ID: my-device-001
请求头名称: Authorization
请求头值: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```
**生成的URL**: `ws://47.239.194.151:3002/?deviceId=my-device-001&authKey=Authorization&authValue=Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

### 示例3: API Key认证
```
服务器地址: 47.239.194.151
WebSocket端口: 3002
设备ID: mobile-client
请求头名称: X-API-Key
请求头值: sk-1234567890abcdef
```
**生成的URL**: `ws://47.239.194.151:3002/?deviceId=mobile-client&authKey=X-API-Key&authValue=sk-1234567890abcdef`

### 示例4: 自定义Token认证
```
服务器地址: 47.239.194.151
WebSocket端口: 3002
设备ID: android-app
请求头名称: X-Auth-Token
请求头值: custom-token-12345
```
**生成的URL**: `ws://47.239.194.151:3002/?deviceId=android-app&authKey=X-Auth-Token&authValue=custom-token-12345`

## 🎯 常用认证头名称

| 认证类型 | Header Key | Header Value 示例 |
|----------|------------|-------------------|
| Bearer Token | `Authorization` | `Bearer your-jwt-token` |
| API Key | `X-API-Key` | `your-api-key` |
| Custom Token | `X-Auth-Token` | `your-custom-token` |
| Basic Auth | `Authorization` | `Basic base64(username:password)` |
| Session Token | `X-Session-Token` | `session-id-12345` |
| Access Token | `X-Access-Token` | `access-token-67890` |

## 🔄 URL生成逻辑

应用会根据配置自动生成WebSocket连接URL：

```kotlin
val websocketUrlWithAuth: String
    get() {
        val baseUrl = websocketUrl  // ws://host:port/
        val params = mutableListOf<String>()
        
        // 添加设备ID
        if (deviceId.isNotEmpty()) {
            params.add("deviceId=$deviceId")
        }
        
        // 添加认证参数
        if (authKey.isNotEmpty() && authValue.isNotEmpty()) {
            params.add("authKey=$authKey")
            params.add("authValue=$authValue")
        }
        
        return if (params.isNotEmpty()) {
            "$baseUrl?${params.joinToString("&")}"
        } else {
            baseUrl
        }
    }
```

## 📱 应用界面配置

### 配置界面布局
```
┌─────────────────────────────────────┐
│ 服务器配置                          │
├─────────────────────────────────────┤
│ 服务器地址 *                        │
│ [47.239.194.151              ]     │
├─────────────────────────────────────┤
│ WebSocket端口 *                     │
│ [3002                        ]     │
├─────────────────────────────────────┤
│ 设备ID（可选）                      │
│ [留空自动生成                ]     │
├─────────────────────────────────────┤
│ 认证配置                            │
├─────────────────────────────────────┤
│ 请求头名称 (Header Key)             │
│ [例如: Authorization         ]     │
├─────────────────────────────────────┤
│ 请求头值 (Header Value)             │
│ [例如: Bearer your-token     ]     │
├─────────────────────────────────────┤
│ [保存配置] [测试连接]               │
└─────────────────────────────────────┘
```

### 字段验证规则
- **服务器地址**: 必填，不能为空
- **WebSocket端口**: 必填，1-65535之间的数字
- **设备ID**: 可选，留空自动生成UUID
- **请求头名称**: 可选，如果填写则请求头值也必须填写
- **请求头值**: 可选，如果填写则请求头名称也必须填写

## 🔒 安全注意事项

### 1. 敏感信息保护
- 认证信息本地加密存储
- 避免在日志中输出完整的认证值
- 支持安全连接（WSS协议）

### 2. 认证最佳实践
- 使用强密码或复杂Token
- 定期更换认证凭据
- 避免在不安全网络中传输认证信息
- 考虑使用JWT等有时效性的Token

### 3. 服务器端配置
服务器需要解析URL参数中的认证信息：
```javascript
// Node.js 示例
const url = require('url');
const querystring = require('querystring');

wss.on('connection', (ws, req) => {
    const query = querystring.parse(url.parse(req.url).query);
    const deviceId = query.deviceId;
    const authKey = query.authKey;
    const authValue = query.authValue;
    
    // 验证认证信息
    if (authKey && authValue) {
        // 根据authKey类型验证authValue
        if (!validateAuth(authKey, authValue)) {
            ws.close(1008, 'Authentication failed');
            return;
        }
    }
    
    // 连接成功，处理后续逻辑
});
```

## 🚀 使用流程

### 1. 基本配置
1. 打开应用，点击右上角设置按钮
2. 填写服务器地址和WebSocket端口
3. 点击"保存配置"

### 2. 添加认证（可选）
1. 在"认证配置"部分填写请求头名称
2. 填写对应的请求头值
3. 点击"保存配置"

### 3. 测试连接
1. 点击"测试连接"按钮
2. 查看连接状态提示
3. 如果连接失败，检查配置是否正确

### 4. 开始使用
1. 配置成功后返回主界面
2. 应用会自动连接到WebSocket服务器
3. 开始享受实时剪切板同步功能

## 📊 连接状态说明

| 状态 | 图标 | 说明 |
|------|------|------|
| 已连接 | 🟢 | WebSocket连接正常，可以同步 |
| 未连接 | 🔴 | WebSocket连接断开 |
| 重连中 | 🟡 | 正在尝试重新连接 |
| 认证失败 | ❌ | 认证信息错误，请检查配置 |

## 🔧 故障排除

### 连接失败
1. 检查服务器地址和端口是否正确
2. 确认服务器WebSocket服务正在运行
3. 检查网络连接是否正常
4. 验证认证信息是否正确

### 认证失败
1. 确认请求头名称格式正确
2. 检查请求头值是否有效
3. 联系服务器管理员确认认证方式
4. 尝试重新获取认证凭据

这个新的认证配置方式提供了最大的灵活性，支持各种常见的认证方案！🔐
