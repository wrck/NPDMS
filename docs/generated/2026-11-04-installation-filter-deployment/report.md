# 硬件安装免填筛选正式部署回执

2026-11-04（Asia/Shanghai），根据用户授权发布前后端，未执行数据库迁移。

- 后端和两台前端激活版本 `SDP20261104_INSTALLATION_FILTER`；后端 PID 166621、readiness UP、NRestarts 0。独立 DAC 保持 PID 112439、active。两台前端 index SHA-256 均为 `a45560d3246ae34c6a8a88f6b7687d7bbf16756079534a9ad910aec6dfa97171`，Nginx 配置校验成功。
- 前一正式版本 `SDP20261103_PROJECT_STATUS_MULTI` 保留。后端产物 SHA-256 `582e60b79710bfb6970438dd6d2a61da73e36487eb6eacdf5fedd0b403f7ea23`；原已验证修复基线 22 项通过。制品包含本次硬件安装筛选、可配置产品描述条件及此前项目列表办事处选项改动。
- 通过正式参数配置页面，将 `pms.installation.address.product.filters` 从 `{"productFilters":[{"productTypeNotIn":["光模块"]}]}` 更新为 `{"productFilters":[{"productTypeNotIn":["光模块"],"productDescNotLike":"%光模块%"}]}`；保留正式环境原有类型条件。数据库只读复核确认值生效，Flyway 最新仍为 V534。
- 测试环境原配置另包含“外购件”，更新时保留该条件；项目 84460 的测试接口由必填 8、免填 32 变为必填 4、免填 36。定向测试 49 项及前端测试 10 项通过。
- 使用正式环境管理员浏览器，从项目列表搜索项目 `16200426072101N-0` 并进入硬件安装。默认必填 4 台；切换免填显示 36 台，列表包含产品描述含“光模块”的设备。正式数据为地址已填写 0/4，与测试环境不同；未保存设备或安装记录。见 [页面截图](production.png)。
- 本次同时发布的办事处选项此前在测试环境以服务经理/项目经理账号验证；工程管理部角色和正式环境该角色页面未在本次单独验收。
