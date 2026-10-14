-- P06 / T_USER_ROLE | 原任务 2108568095503093761 v0
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT r.id,r.user_id,r.role_id,r.comp_id,u.user_name AS username FROM t_user_role r LEFT JOIN t_user u ON u.user_id=r.user_id;
