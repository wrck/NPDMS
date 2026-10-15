SELECT SHA2(CAST(JSON_ARRAY(u.user_name,r.role_id) AS CHAR),256) AS _groupKey,
 CAST(JSON_ARRAYAGG(JSON_OBJECT('object','T_USER_ROLE','fields',JSON_OBJECT(
   'id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',u.user_name,
   'migrationCompanyCode',c.compCode))) AS CHAR) AS _sourceRows
FROM t_user_role r
LEFT JOIN t_user u ON u.user_id=r.user_id
LEFT JOIN t_company c ON c.id=r.comp_id
GROUP BY u.user_name,CAST(u.user_name AS BINARY),r.role_id
