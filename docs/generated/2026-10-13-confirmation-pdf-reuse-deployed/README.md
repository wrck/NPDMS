# 完工证明、满意度PDF重复下载复用

2026-10-13需求方批准同模板重复下载不得重复登记。前端将PDF文件标识及创建元数据稳定化，文件标识包含当前版式和实际渲染内容，下载前以记录Owner、文档种类、记录版本及文件摘要匹配已有有效交付件，获取受控下载票据复用；查询权限不足时仍沿原登记入口，由后端保障幂等。同一打开窗口成功登记后再下载也直接复用。失败重试保留原文件及请求键。

后端CompletionConfirmationWorkflowService沿原项目锁和权限、版本、作废、PDF校验执行，在锁内经PlatformDeliveryMaterialApi及FileEvidenceApi查找同来源、同种类、同记录版本、同摘要且可用的有效生成文件，返回既有材料ID，不直接查询/写入平台表；其他情况沿原NativeGeneratedFileApi登记。历史及不同版本不覆盖。

真实浏览器19191：本地既有验收记录30045的两种PDF各下载三次（含关闭重开），首轮6次下载只登记两条材料，见相邻目录2026-10-13-confirmation-pdf-reuse。用户明确批准后，经scripts/worktree-backend.ps1部署冻结包6d7f8c54-e785-4aee-b9b1-6a15985c8b8a并重启59191，local,dac；原运行修复基线22条验证通过。首次部署遇到构建锁占用，未操作服务，构建结束后重试成功，未绕过锁。DAC readiness另行检查HTTP200。

部署后真实浏览器再次下载6次，新增登记请求0、交付件数量增加0，关闭重开文件字节一致；两种文件分别并发调用两次原登记API，均返回其原材料ID，数量仍不变。材料ID9100000000000000273（完工证明）、9100000000000000274（满意度）。全部既有材料保留、原签字事实未变。原已签记录30025、30031、30034、30040、30045完整详情前后相同。result.json与PDF下载样张为证据。

后端固定工具链构建成功、16项Workflow测试通过（包括锁后复用、不同种类/版本与不可用文件不复用），前端7项复用/身份变化测试及5项打印保护测试通过，ESLint通过。日志.run/confirmation-pdf-dedup-build.log与deploy.log；浏览器命令D:/常规软件/python/python.exe .run/confirmation-pdf-reuse-deployed-browser.py。未提交Git或推送。
