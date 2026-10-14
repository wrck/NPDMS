# 满意度调查表打印页面间距

2026-10-13需求方要求满意度打印页面间距与完工证明一致，表格适应页面宽度。两种Word V2打印共用上下22mm、左右15mm页面内容边距和12mm页脚间距；满意度表格宽度100%且无负向偏移。PDF导出同样180mm内容宽度，页眉页脚、水印及分页高度保持一致。

真实Chromium从19191项目992203060198打开既有30045满意度打印预览，实际内容区/表格宽度均680.625px、左偏移0，左右15mm、上下22mm；10项冻结评分全部正确回显。使用现有html2pdf链生成隔离样张satisfaction-print.pdf，一页A4，Poppler渲染printed-page.png核对评分矩阵、意见、签字及页脚完整无裁切；未调用PDF登记接口、未修改历史交付件。预览print-preview.png，结果result.json。命令D:/常规软件/python/python.exe .run/satisfaction-print-spacing-browser.py。

5项打印数据与历史保护测试、修改文件ESLint通过，既有30025、30031、30034、30040、30045完整详情未变。无后端重启、Git提交或推送。
