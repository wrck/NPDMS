# HTTP地址PDF下载摘要错误修复

2026-10-13需求方报告下载Cannot read properties of undefined (reading digest)。此前摘要使用crypto.subtle，localhost属于浏览器可信环境，但局域网HTTP不属于安全上下文，crypto.subtle不可用。

仅改confirmationPdfDelivery.ts使用项目既有crypto-js库计算SHA-256，保持标准摘要与稳定PDF标识兼容，去重、授权、受控下载及历史契约不变。新增无Web Crypto时标准abc摘要验证，与既有保护共8项测试通过；ESLint通过。

真实Chromium以http://10.210.0.11:19191登录，确认isSecureContext=false且crypto.subtle不存在。两种PDF各下载3次（含关闭重开），6次下载成功，文件与原版字节一致，新增登记请求0、材料增加0；各并发2次原登记也返回原材料ID。30025、30031、30034、30040、30045签字详情未变，无页面异常。浏览器命令D:/常规软件/python/python.exe .run/confirmation-pdf-http-browser.py，证据result.json及下载文件。

本次只改前端，无后端重启，未提交或推送。此证据补足此前仅验证localhost的环境遗漏。
