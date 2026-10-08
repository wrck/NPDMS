from http_creation_support import *
import uuid
prefix='PCREATE-20261006-710A-HTTP-'+uuid.uuid4().hex[:6]
c=database(); fixtures={}
try:
 with c.cursor() as q:
  q.execute("SELECT COUNT(*) n FROM cus_customer_master WHERE id=1001 AND code='CUST-001' AND tenant_id=1 AND lifecycle_status='ENABLED' AND deleted=0")
  assert q.fetchone()['n']==1
  for case in ['API','RACE','UI1','UI2','FOREIGN']:
   ct=prefix+'-'+case
   q.execute("INSERT INTO com_contract (tenant_id,company_id,company_code,contract_no,contract_name,master_source_system,master_source_record_key,customer_code,status,creator,updater) VALUES (1,930800,'DPTECH-DEMO',%s,%s,'SYNTHETIC',%s,'CUST-001','ENABLED',%s,%s)",(ct,ct,ct,prefix,prefix))
   fixture={'contractId':q.lastrowid,'contractNo':ct,'orders':[]}
   for label in (['A','B'] if case in ['API','UI1'] else ['A']):
    so=ct+'-SO-'+label; ex=ct+'-EX-'+label; company='OTHER-SYNTHETIC' if case=='FOREIGN' else 'DPTECH-DEMO'
    q.execute("INSERT INTO com_sales_order (tenant_id,source_system,source_record_key,source_version,company_id,company_code,order_type,order_no,sales_type,contract_no,execution_no,customer_id,customer_code,customer_name,source_project_name,order_create_time,status,creator,updater) VALUES (1,'SYNTHETIC',%s,'v1',930800,'DPTECH-DEMO','0',%s,%s,%s,%s,1001,'CUST-001','Synthetic order customer',%s,'2026-10-01 12:00:00','ENABLED',%s,%s)",(so,so,'01' if label=='A' else '02',ct,ex,ct+'-ORDER-'+label,prefix,prefix))
    orderId=q.lastrowid
    q.execute("INSERT INTO com_crm_execution_order (tenant_id,source_system,company_code,department_code,execution_no,project_name,customer_project_name,final_customer_name,agent_name,sales_rep_name,project_amount,required_in_date,status,creator,updater) VALUES (1,'SYNTHETIC',%s,'OFFICE-HZ-DEMO',%s,%s,%s,'Synthetic final customer','Synthetic agent','Synthetic sales',0,'2026-11-01','ACTIVE',%s,%s)",(company,ex,ct+'-EXECUTION-'+label,ct+'-CUSTOMER-'+label,prefix,prefix))
    fixture['orders'].append({'id':orderId,'orderNo':so,'executionNo':ex,'executionId':q.lastrowid,'projectName':ct+('-ORDER-' if label=='A' else '-EXECUTION-')+label})
   fixtures[case]=fixture
 c.commit(); save('http-fixtures.json',{'prefix':prefix,'fixtures':fixtures}); print(json.dumps({'prefix':prefix,'contracts':len(fixtures),'orders':sum(len(x['orders']) for x in fixtures.values()),'executions':sum(len(x['orders']) for x in fixtures.values())}))
finally:c.close()