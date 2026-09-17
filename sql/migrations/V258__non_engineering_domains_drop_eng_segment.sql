-- V258: 非工程实施领域表去 eng_ 段（需求方2026-09-17指示）
-- PLT、KNO、RES 不属于工程实施相关领域，不保留 eng_ 段，直接用领域前缀+实体名。
-- {领域}_eng_{实体} 双段前缀仅适用于工程实施领域（SOL/IMP）的表。

RENAME TABLE
  -- PLT 域（动态表单旧链与设备授权旧链）
  `plt_eng_form_template` TO `plt_form_template`,
  `plt_eng_form_instance` TO `plt_form_instance`,
  `plt_eng_authorization` TO `plt_authorization`,
  -- KNO 域（公告）
  `kno_eng_announcement` TO `kno_announcement`,
  `kno_eng_announcement_check` TO `kno_announcement_check`,
  -- RES 域（外协申请）
  `res_eng_outsource_request` TO `res_outsource_request`;
