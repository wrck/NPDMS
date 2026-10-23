-- Evidence of production fillContractReferences and exact dynamic guards; no execution or DDL.
/*
<update id="fillContractReferences">
 UPDATE com_project_contract_relation r
 JOIN proj_project p ON p.tenant_id=r.tenant_id AND p.id=r.project_id AND p.deleted=FALSE
 JOIN com_contract c ON c.tenant_id=r.tenant_id AND c.company_code=p.company_code AND c.contract_no=r.contract_no
  AND CAST(c.company_code AS BINARY)=CAST(p.company_code AS BINARY)
  AND CAST(c.contract_no AS BINARY)=CAST(r.contract_no AS BINARY) AND c.deleted=FALSE
 SET r.contract_id=c.id,r.update_time=r.update_time
 WHERE r.tenant_id=#{query.tenantId} AND r.contract_id IS NULL AND r.deleted=FALSE
  AND r.source_system='DPPMS' AND r.source_table='pm_project_contract' AND r.relation_role='RELATED'
 <choose><when test="query.targetIds != null and !query.targetIds.isEmpty() and query.sourceCompanies != null and !query.sourceCompanies.isEmpty()">
  AND (<foreach collection="query.sourceCompanies" item="company" separator=" OR ">
   (p.company_code=#{company.companyCode}
    AND CAST(p.company_code AS BINARY)=CAST(#{company.companyCode} AS BINARY)
    <choose><when test="company.targetIds != null and !company.targetIds.isEmpty()">
     AND r.id IN <foreach collection="company.targetIds" item="id" open="(" separator="," close=")">#{id}</foreach>
    </when><otherwise>AND 1=0</otherwise></choose>)
  </foreach>)
 </when><otherwise>AND 1=0</otherwise></choose>
</update>


*/
