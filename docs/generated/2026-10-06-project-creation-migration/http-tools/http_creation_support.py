import json, os, subprocess
import pymysql, requests
from pathlib import Path
ROOT=Path(__file__).parent
BASE='http://127.0.0.1:59280/admin-api'
def database():
    container=json.loads(subprocess.check_output(['docker','inspect','npdms-domain-test-mysql-1'],text=True))[0]
    values=dict(item.split('=',1) for item in container['Config']['Env'])
    return pymysql.connect(host='127.0.0.1',port=24306,user=values['MYSQL_USER'],password=values['MYSQL_PASSWORD'],database='npdms_domain_test',cursorclass=pymysql.cursors.DictCursor,charset='utf8mb4',autocommit=False)
def login():
    session=requests.Session(); session.headers['tenant-id']='1'
    result=session.post(BASE+'/system/auth/login',json={'username':os.environ['NPDMS_TEST_USERNAME'],'password':os.environ['NPDMS_TEST_PASSWORD']},timeout=20).json()
    assert result['code']==0, {'loginCode':result.get('code'),'message':result.get('msg')}
    session.headers['Authorization']='Bearer '+result['data']['accessToken']; return session
def call(session,method,path,**kwargs):
    return session.request(method,BASE+path,timeout=45,**kwargs).json()
def save(name,value):
    ROOT.joinpath(name).write_text(json.dumps(value,ensure_ascii=False,indent=2,default=str),encoding='utf8')
if __name__=='__main__':
    c=database()
    with c.cursor() as q:
        q.execute("SELECT id,code,name,tenant_id,lifecycle_status FROM cus_customer_master WHERE code='CUST-001'"); print('seedCustomer',q.fetchall())
        q.execute("SELECT id,path,parent_id,component FROM system_menu WHERE component IN ('pms/project/projects/index','pms/project/inheritance/projects/index') AND deleted=0"); print('routes',q.fetchall())
    c.close()