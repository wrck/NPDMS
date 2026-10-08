from http_creation_support import *
from playwright.sync_api import sync_playwright
import re
out=ROOT/'real-browser';result=json.loads((out/'results.json').read_text(encoding='utf8'));fixture=json.loads(ROOT.joinpath('http-fixtures.json').read_text(encoding='utf8'))['fixtures']['UI2'];errors=[]
with sync_playwright() as p:
 b=p.chromium.launch(headless=True);page=b.new_page(viewport={'width':1500,'height':1100});page.on('pageerror',lambda e:errors.append(str(e)))
 try:
  page.goto('http://127.0.0.1:19081/login',wait_until='domcontentloaded');page.get_by_placeholder('请输入用户名',exact=True).fill(os.environ['NPDMS_TEST_USERNAME']);page.get_by_placeholder('请输入密码',exact=True).filter(visible=True).fill(os.environ['NPDMS_TEST_PASSWORD'])
  if page.get_by_role('checkbox',name='记住我').is_checked():page.get_by_text('记住我',exact=True).click()
  page.get_by_role('button',name='登录',exact=True).click();page.wait_for_url(lambda u:'/login' not in u,timeout=40000);print('UI login complete',flush=True)
  page.goto('http://127.0.0.1:19081/pms-inheritance/project-detail?projectId=992203060185',wait_until='domcontentloaded');page.get_by_text(fixture['orders'][0]['executionNo'],exact=True).first.wait_for(timeout=30000)
  body=page.inner_text('body');assert fixture['orders'][0]['projectName'] in body and fixture['contractNo']+'-CUSTOMER-A' in body
  zero=page.locator('.fact').filter(has=page.locator('dt',has_text=re.compile('^项目金额$'))).first.locator('dd').inner_text().strip();assert zero in ['0','0.00'],zero
  assert '编码命名空间' in body and '项目主联系人' in body and '992203060185' in body;assert not errors,errors
  for name in ['UI2 detail shows linked execution and customer project after shared facts integration','UI2 authorized zero amount displays as zero','UI2 existing code namespace and primary contact presentation retained','real detail render has no uncaught browser errors']:result['checks'].append({'name':name,'passed':True})
  page.screenshot(path=str(out/'UI2-detail.png'),full_page=True);result['amountRestrictedIdentity']={'status':'NOT_RUN','reason':'No existing tenant-1 amount-restricted contract reader; no permission changes'};(out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8');print({'passed':len(result['checks']),'created':len(result['created'])})
 except Exception as ex:
  print({'failure':str(ex),'url':page.url,'pageErrors':errors});page.screenshot(path=str(out/'inheritance-after-fix-diagnostic.png'),full_page=True);raise
 finally:b.close()