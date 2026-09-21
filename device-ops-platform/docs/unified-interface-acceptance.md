# 界面统一与紧凑交互验收

2026-09-08

## 实现

Element Plus根级zhCn；统一浅色控制区与青绿设计变量、正文14px与辅助12–13px、中文无衬线/命令等宽。PanelHeader紧凑且眉题可选，移除英文眉题、重复脚本摘要与主状态；凭据说明去重但保留Protocol安全警告。管理显示标签中文化，状态映射与未知fallback共用，本地时间格式统一，历史缺失不伪造。原始枚举、API、脚本、输出、快照script.content保持不变。

## 测试

最终106单测、类型、构建通过；全量7 E2E通过，包含1280×720、1440×900、1024×768。窄屏曾出现nearest滚动将49px按钮底部定位到768.34375px，居中滚动后全可见；未放宽ratio1、祖先裁切或点击断言，未为此修改CSS。全量lint0错误552格式警告，构建保留依赖注释及chunk体积提示。

## 产物与部署

打包预检发现历史静态资源残留，未部署该包。仅删除server target/classes/static生成目录重建，最终JAR和dist均27文件，逐文件hash一致；启动后HTTP静态校验亦全部一致。

48181连续空闲检查后，原H2/DPAPI独占备份与hash确认，使用原key和新独立runtime启动。最终Java32700、healthUP，V20无迁移，total192维护前后不变，历史1.2/active1.3与evidence31字节摘要未变。runtime在target/deployed-runtime/20260908T131759550Z-unified，不可清理运行目录。

## 真实浏览器边界

主代理刷新48181既有历史工作台，DOM确认中文控件、标题去眉题、重复输出状态移除、四页签与输入下载仍在。最终IAB截图两次失败（browser screenshot activity capture failed for guest），因此不把完整真实视觉验收标为通过。三视口实际点击、滚动和裁切回归由独立浏览器mock测试覆盖，不替代目标环境截图。

未执行设备命令、连接测试、重解析、发布、数据库编辑或Git提交/推送。
