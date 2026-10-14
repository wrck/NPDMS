-- P07 / t_user_role | 原任务 2108569376783273985 v0
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT r.id,r.user_id,r.role_id,r.comp_id,u.user_name AS username,c.compCode AS migrationCompanyCode FROM t_user_role r LEFT JOIN t_user u ON u.user_id=r.user_id LEFT JOIN t_company c ON c.id=r.comp_id;
