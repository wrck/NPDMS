# P33 新虚基类实际读写字段衔接

2026-10-19最新修订：见[原用户名及原项目ID规则](../base-sync-original-identifiers-preview-20261019.md#p33)；本页早期流程描述按该修订解释。

左侧是实际来源SELECT表达式和结果别名；右侧是实际INSERT列与运行参数。Java转换/合并/关联在两者之间执行，本表不宣称同名就是原值直写。

| 来源语句 | 原表达式 | 查询别名 |
|---|---|---|
| selectInstallation | `id` | sourceId |
| selectInstallation | `projectId` | originalProjectId |
| selectInstallation | `barcode` | barcode |
| selectInstallation | `itemCode` | itemCode |
| selectInstallation | `itemModel` | itemModel |
| selectInstallation | `itemName` | itemName |
| selectInstallation | `receiveName` | receiveName |
| selectInstallation | `emsNum` | emsNum |
| selectInstallation | `emsCompany` | emsCompany |
| selectInstallation | `packdate` | packdate |
| selectInstallation | `contractNo` | contractNo |
| selectInstallation | `installAddress` | installAddress |
| selectInstallation | `chProjectId` | chProjectId |
| selectInstallation | `chContractNo` | chContractNo |
| selectInstallation | `transferProjectId` | transferProjectId |
| selectInstallation | `transferContractNo` | transferContractNo |
| selectInstallation | `transferFlag` | transferFlag |
| selectInstallation | `createTime` | createTime |
| selectInstallation | `createBy` | source_creator |
| selectInstallation | `updateTime` | updateTime |
| selectInstallation | `updateBy` | source_updater |

| 正式表.列 | 实际写入参数 | INSERT语句 |
|---|---|---|
| imp_eng_installation.id | #{row.target.id} | insertInstallation |
| imp_eng_installation.project_id | #{row.target.projectId} | insertInstallation |
| imp_eng_installation.code | #{row.target.code} | insertInstallation |
| imp_eng_installation.equipment_id | #{row.target.equipmentId} | insertInstallation |
| imp_eng_installation.install_location | #{row.target.installLocation} | insertInstallation |
| imp_eng_installation.address_id | #{row.target.addressId} | insertInstallation |
| imp_eng_installation.address_version | #{row.target.addressVersion} | insertInstallation |
| imp_eng_installation.site_id | #{row.target.siteId} | insertInstallation |
| imp_eng_installation.site_version | #{row.target.siteVersion} | insertInstallation |
| imp_eng_installation.site_location_id | #{row.target.siteLocationId} | insertInstallation |
| imp_eng_installation.site_location_version | #{row.target.siteLocationVersion} | insertInstallation |
| imp_eng_installation.location_resolution_status | #{row.target.locationResolutionStatus} | insertInstallation |
| imp_eng_installation.address_snapshot | #{row.target.addressSnapshot} | insertInstallation |
| imp_eng_installation.location_snapshot | #{row.target.locationSnapshot} | insertInstallation |
| imp_eng_installation.effective_from | #{row.target.effectiveFrom} | insertInstallation |
| imp_eng_installation.effective_to | #{row.target.effectiveTo} | insertInstallation |
| imp_eng_installation.install_time | #{row.target.installTime} | insertInstallation |
| imp_eng_installation.installer_user_id | #{row.target.installerUserId} | insertInstallation |
| imp_eng_installation.environment_check | #{row.target.environmentCheck} | insertInstallation |
| imp_eng_installation.spec_check | #{row.target.specCheck} | insertInstallation |
| imp_eng_installation.photo_url | #{row.target.photoUrl} | insertInstallation |
| imp_eng_installation.result | #{row.target.result} | insertInstallation |
| imp_eng_installation.status | #{row.target.status} | insertInstallation |
| imp_eng_installation.remark | #{row.target.remark} | insertInstallation |
| imp_eng_installation.version | #{row.target.version} | insertInstallation |
| imp_eng_installation.creator | #{row.target.creator} | insertInstallation |
| imp_eng_installation.create_time | #{row.target.createTime} | insertInstallation |
| imp_eng_installation.updater | #{row.target.updater} | insertInstallation |
| imp_eng_installation.update_time | #{row.target.updateTime} | insertInstallation |
| imp_eng_installation.deleted | #{row.target.deleted} | insertInstallation |
| imp_eng_installation.tenant_id | #{row.target.tenantId} | insertInstallation |
