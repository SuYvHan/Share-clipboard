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
    "success": true,
    "data": [
        {
            "id": "961aea94-16e4-4407-bf6c-af81cbba23b2",
            "type": "image",
            "content": "å±å¹æªå¾ 2025-08-12 231803_56b17446-a327-490d-9577-7d3490967ea9.png",
            "deviceId": "device_1754909073348_j1bgar934",
            "fileName": "å±å¹æªå¾ 2025-08-12 231803.png",
            "fileSize": 78024,
            "mimeType": "image/png",
            "filePath": "å±å¹æªå¾ 2025-08-12 231803_56b17446-a327-490d-9577-7d3490967ea9.png",
            "createdAt": "2025-08-13T04:49:26.000Z",
            "updatedAt": "2025-08-13T04:49:26.000Z"
        }
    ],
    "total": 1,
    "filteredTotal": 1,
    "allTotal": 2
}
使用
一下为举例
curl -X 'GET' \
  'http://47.239.194.151/api/files/preview?id=这是从响应的id的值&name=这是使用响应的filePath' \
  -H 'accept: application/octet-stream' \
  -H 'X-API-Key: Qw133133'
这个响应头
  -H 'Header Key: Header Value'还是从认证配置的请求头名称和请求头值获取