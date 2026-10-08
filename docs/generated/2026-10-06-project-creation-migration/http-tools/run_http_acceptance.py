from http_creation_support import *
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
f=json.loads(ROOT.joinpath('http-fixtures.json').read_text(encoding='utf8')); prefix=f['prefix']; fixtures=f['fixtures']; s=login(); checks=[]
def check(name,condition,evidence=None):
 checks.append({'name':name,'passed':bool(condition),'evidence':evidence}); save('http-acceptance.json',checks)
 assert condition, name

def source(case,selected=None,session=s):
 c=fixtures[case]
 return call(session,'GET',f"/api/v1/pms/contracts/{c['contractId']}/creation-source",params={} if selected is None else {'salesOrderId':selected})

initial=json.loads(ROOT.joinpath('http-create-initial.json').read_text(encoding='utf8')); projectId=initial['response']['data']['id']; p=initial['request'];
u=requests.Session();u.headers['tenant-id']='1'; r=source('API',session=u); check('anonymous source access denied',r['code'] not in [0,500],{'code':r['code']})
r=source('API'); check('multiple orders require selection',r['code']==0 and r['data']['resolved']['salesOrderId'] is None)
r=source('RACE');check('single order auto selects linked execution',r['code']==0 and str(r['data']['resolved']['salesOrderId'])==str(fixtures['RACE']['orders'][0]['id']))
r=source('API',fixtures['RACE']['orders'][0]['id']);check('foreign contract order rejected',r['code'] not in [0,500],{'code':r['code'],'message':r.get('msg')})
r=source('FOREIGN');check('foreign company execution rejected',r['code'] not in [0,500],{'code':r['code'],'message':r.get('msg')})
r=call(s,'POST','/pms/projects',json=p,headers={'Idempotency-Key':prefix+'-API-1'});check('same key same request replays created project',r['code']==0 and r['data']['id']==projectId,r)
r=call(s,'POST','/pms/projects',json={**p,'creationReason':prefix+'-changed'},headers={'Idempotency-Key':prefix+'-API-1'});check('same key changed request conflicts',r['code'] not in [0,500],{'code':r['code'],'message':r.get('msg')})
r=source('API',fixtures['API']['orders'][1]['id']);newp={**p,'sourceFingerprint':r['data']['sourceFingerprint']}
r=call(s,'POST','/pms/projects',json=newp,headers={'Idempotency-Key':prefix+'-API-duplicate'});check('different key same contract rejected',r['code'] not in [0,500],{'code':r['code'],'message':r.get('msg')})
r=call(s,'GET',f'/pms/projects/{projectId}');save('http-project-detail.json',r);d=r.get('data') or {}
check('project name and contract come from selected chain',r['code']==0 and d['projectName']==fixtures['API']['orders'][1]['projectName'] and d['contractNo']==fixtures['API']['contractNo'])
check('CUS customer canonical name and sales type applied',d.get('customerCode')=='CUST-001' and d.get('customerName')!='Synthetic order customer' and d.get('salesType')=='02')
check('customer project detail follows selected execution',d.get('customerProjectName')==fixtures['API']['contractNo']+'-CUSTOMER-B')
r=call(s,'GET',f'/pms/projects/{projectId}/instances');save('http-project-instances.json',r);check('initialized instance endpoint readable',r['code']==0)
r=call(s,'GET',f'/api/v1/pms/contracts/project/{projectId}/overview');save('http-project-overview.json',r);check('linked commerce overview readable',r['code']==0)
# Both project entry points must use the same source validation.
c=fixtures['RACE']; d=source('RACE')['data']; base={'projectName':c['orders'][0]['projectName'],'contractId':c['contractId'],'salesOrderId':c['orders'][0]['id'],'sourceFingerprint':d['sourceFingerprint'],'signingMethod':'DIRECT_SIGN','projectCategory':'GENERAL','implementationMode':'DIRECT_SERVICE','orderOfficeCompanyId':930800,'orderOfficeDepartmentId':930801,'implementationLocation':'Synthetic local acceptance'}
for route in ['/pms/projects','/api/v1/pms/projects']:
 for label,change in [('stale fingerprint',{'sourceFingerprint':'0'*64}),('wrong customer',{'customerCode':'SYNTHETIC-NOT-SOURCE'}),('unauthorized company',{'orderOfficeCompanyId':930850})]:
  r=call(s,'GET',route+'/actions/match-templates',params={**base,**change});check(route+' rejects '+label,r['code'] not in [0,500],{'code':r['code'],'message':r.get('msg')})
r=call(s,'GET','/api/v1/pms/projects/actions/match-templates',params=base);check('selected customer preview resolves omitted client customer from contract',r['code']==0)
body={**base,'creationReason':prefix+'-RACE','templateRevisionId':993009900050,'candidateWatermark':r['data']['candidateWatermark']}
barrier=Barrier(2)
def create_race(i):
 worker=login();barrier.wait();return call(worker,'POST','/api/v1/pms/projects',json=body,headers={'Idempotency-Key':prefix+'-RACE-'+str(i)})
with ThreadPoolExecutor(2) as pool: results=list(pool.map(create_race,[1,2]))
save('http-concurrent-create.json',results);check('same contract concurrent HTTP creates exactly once',sum(x['code']==0 for x in results)==1 and all(x['code']!=500 for x in results),results)
created=[projectId]+[x['data']['id'] for x in results if x['code']==0]
c=database()
with c.cursor() as q:
 q.execute('SELECT id,contract_no,project_name,customer_id FROM proj_project WHERE contract_no LIKE %s',(prefix+'%',));projects=q.fetchall()
 q.execute('SELECT execution_no,primary_project_id FROM com_crm_execution_order WHERE creator=%s ORDER BY execution_no',(prefix,));executions=q.fetchall()
 q.execute('SELECT r.project_id,r.contract_id FROM com_project_contract_relation r JOIN com_contract c ON c.id=r.contract_id WHERE c.creator=%s',(prefix,));relations=q.fetchall()
 check('CUS customer stable identity persisted', all(str(x['customer_id'])=='1001' for x in projects))
 check('negative and concurrent requests leave exactly two projects and relations',len(projects)==2 and len(relations)==2 and len({str(x['id']) for x in projects})==2,{'projects':projects,'relations':relations})
 check('only selected executions bind to created projects',sum(x['primary_project_id'] is not None for x in executions)==2 and {str(x['primary_project_id']) for x in executions if x['primary_project_id'] is not None}==set(map(str,created)),executions)
c.close();save('http-created-projects.json',created); print(json.dumps({'passed':len(checks),'failed':0,'projectIds':created},ensure_ascii=False))