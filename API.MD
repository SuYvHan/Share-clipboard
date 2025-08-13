这是上传图片的方法
type: "image"
content: Base64编码的图片数据（包含data:image/格式前缀）
deviceId: 设备ID
fileName: 图片文件名（可选）
fileSize: 文件大小（可选）
mimeType: MIME类型，如 "image/png"（可选）
下面是上传文件的方法
type: "file"
content: Base64编码的文件数据
deviceId: 设备ID
fileName: 文件名（必需）
fileSize: 文件大小（可选）
mimeType: MIME类型，如 "text/plain", "application/pdf"（可选）
以上的http请求和文本请求头和值是一样的，同步过来的图片要通过b64编码转换为图片，文件也是b64，需要同步过来进行转码
图片返还响应为
一下为举例
{
    "type": "sync",
    "data": {
        "id": "d03c758b-fcac-4b3f-8563-95bf5f0ba9e6",
        "type": "image",
        "content": "å±å¹æªå¾ 2025-07-30 125932_77237333-1cfc-4cbf-872f-83fea32fbbf7.png",
        "deviceId": "device_1754909073348_j1bgar934",
        "fileName": "å±å¹æªå¾ 2025-07-30 125932.png",
        "fileSize": 266768,
        "mimeType": "image/png",
        "filePath": "å±å¹æªå¾ 2025-07-30 125932_77237333-1cfc-4cbf-872f-83fea32fbbf7.png",
        "createdAt": "2025-08-13T05:05:59.000Z",
        "updatedAt": "2025-08-13T05:05:59.000Z"
    }
}
使用
一下为举例
curl -X 'GET' \
  'http://47.239.194.151/api/files/preview?id=这是从响应的id的值&name=这是使用响应的fileName' \
  -H 'accept: application/octet-stream' \
  -H 'X-API-Key: Qw133133'
这个响应头
  -H 'Header Key: Header Value'还是从认证配置的请求头名称和请求头值获取
一下是py代码仅供参考预览接口
  import http.client

conn = http.client.HTTPSConnection("47.239.194.151", 3001)
payload = ''
headers = {
   'X-API-Key': 'Qw133133'
}
conn.request("GET", "/api/files/preview?id=1a3a6018-39d3-4c3a-b66a-88086dee9767&name=%C3%A5%C2%B1%C2%8F%C3%A5%C2%B9%C2%95%C3%A6%C2%88%C2%AA%C3%A5%C2%9B%C2%BE%202025-07-30%20130723.png", payload, headers)
res = conn.getresponse()
data = res.read()
print(data.decode("utf-8"))
'Header Key: Header Value'还是从认证配置的请求头名称和请求头值获取