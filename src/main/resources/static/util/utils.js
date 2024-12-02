// utils.js (公共方法文件)

// 生成随机设备ID
function generateDeviceId() {
    var id = uuid.v4();
    localStorage.setItem("deviceId", id);  // 存储设备ID
    return id;
}
