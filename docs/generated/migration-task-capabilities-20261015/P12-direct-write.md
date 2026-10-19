# P12 产品目录直写实施记录

新增独立DPPMS_PRODUCT_CATALOG_DIRECT及GET /api/v1/pms/integration/templates/dppms-products-direct，保留原子集模板和Owner实现。完整批准fb_items来源使用原id读取，仅缺失产品补齐；已有CRM名称、价格、分类和平台字段保持。来源原id只作INT追溯，正式产品新ID独立生成，正式source_key仍承接原产品编码。

实源只读核对34976条，原产品码及TRIM后编码均34976种，无缺码，无名称/型号超128。表为InnoDB基础表但未声明主键或自增，不能描述为自增主键表。按原id游标读取，无额外业务码或时间排序。

targeted-034552.log实际XML事务5项通过：预览零写入及实际事务要求、既有CRM字段保留、同码多来源与标签冲突、后置第二条约束失败全回滚、租户/失效运行/删除及编码身份保护。此增量未打包部署、未保存真实任务、未执行真实预览或业务迁入；前端新增选择尚待本轮验证。

后续前端验证：TaskEditor运行测试16项通过，覆盖P12/P13独立选择不保存。真实Chromium组件选择通过，证据p13-direct-20261016/browser/browser-result.json，使用API fixture，不能替代正式业务预览。
