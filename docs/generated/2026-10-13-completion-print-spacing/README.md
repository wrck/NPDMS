# 工程完工证明打印模板间距调整

2026-10-13需求方授权：去掉设备单元格内的“设备名称、数量”表头行，并调整间距让表格适应页面宽度。

CompletionCertificatePrintV2只删除设备表头行，保留外层“设备名称和数量”、全部设备及数量。完工证明采用上下22mm、左右15mm页面内容边距；表格宽度100%、无负向偏移，页眉页脚跟随内容宽度。WordPrintFrameV2采用CSS变量且保留其他模板的原默认值；PDF导出宽度、页边距、页眉页脚及分页水印同步适配完工证明，满意度打印保持原配置。

真实Chromium在19191登录后打开既有30045完工证明打印预览：7类设备全部显示、无内嵌表头，表格及内容区均宽680.625px且左偏移0。使用现有html2pdf导出渲染链在浏览器生成隔离样张，未调用PDF登记接口或修改历史交付件；输出completion-print.pdf一页A4，Poppler渲染printed-page.png并检查无裁切、签字及页脚完整。预览截图print-preview.png，结果result.json，命令D:/常规软件/python/python.exe .run/completion-print-spacing-browser.py。

5项打印数据、签字、冻结题目及空值保护测试通过，修改文件ESLint及差异检查通过。首次使用默认Vitest配置因CSS导入失败，改用项目指定vitest.pms-file.config.ts完成验证；未降低测试。既有30025、30031、30034、30040、30045详情未变，未保存、推送或覆盖历史PDF，无服务重启，未提交Git。
