-- P05 / T_ROLE | 原任务 2108554583850524673 v0
-- EXACT_SAVED_SQL；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
SELECT r.*, 100-r.priority AS migrationSort, COALESCE(r.update_time,r.create_time) AS migrationVersion, (SELECT CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END FROM t_user u WHERE u.user_name=r.create_by) AS creatorSourceKey, (SELECT CASE WHEN COUNT(*)=1 THEN MAX(u.user_id) ELSE NULL END FROM t_user u WHERE u.user_name=r.update_by) AS updaterSourceKey FROM t_role r WHERE r.status<>0 OR r.status IS NULL;
