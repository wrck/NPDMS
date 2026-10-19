SELECT r.id AS _groupKey,
 JSON_ARRAY(
  JSON_OBJECT('object','T_USER_ROLE','fields',JSON_OBJECT('id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',NULL)),
  JSON_OBJECT('object','t_user_role','fields',JSON_OBJECT('id',r.id,'user_id',r.user_id,'role_id',r.role_id,'comp_id',r.comp_id,'username',NULL,'migrationCompanyCode',NULL))
 ) AS _sourceRows
FROM t_user_role r
