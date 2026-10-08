import json,os,re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
ROOT=Path(__file__).parent
f=json.loads(ROOT.joinpath('http-fixtures.json').read_text(encoding='utf8')); checks=[]; results=[]
out=ROOT/'real-browser';out.mkdir(exist_ok=True)
def save(): (out/'results.json').write_text(json.dumps({'checks':checks,'created':results},ensure_ascii=False,indent=2),encoding='utf8')
def check(name,condition):
 checks.append({'name':name,'passed':bool(condition)});save();assert condition,name
with sync_playwright() as pw:
 browser=pw.chromium.launch(headless=True)
 page=browser.new_page(viewport={'width':1500,'height':1100});page.set_default_timeout(20000)
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 try:
  page.goto('http://127.0.0.1:19081/login',wait_until='domcontentloaded')
  page.get_by_placeholder('请输入用户名',exact=True).fill(os.environ['NPDMS_TEST_USERNAME'])
  page.get_by_placeholder('请输入密码',exact=True).filter(visible=True).fill(os.environ['NPDMS_TEST_PASSWORD'])
  for checkbox in page.locator('input[type=checkbox]:visible').all():
   if checkbox.is_checked():checkbox.uncheck(force=True)
  page.get_by_role('button',name='登录',exact=True).click();page.wait_for_url(lambda u:'/login' not in u,timeout=40000)
  check('real UI login without mock authentication',True)
  for case,route in [('UI1','/pms/project-management/projects'),('UI2','/pms-inheritance/projects')]:
   fixture=f['fixtures'][case]; chosen=fixture['orders'][-1]
   page.goto('http://127.0.0.1:19081'+route,wait_until='domcontentloaded');page.get_by_role('button',name='创建项目',exact=True).wait_for(timeout=40000)
   # Filter before capturing anything, so screenshots contain only this activity's rows.
   query=page.locator('.el-form-item').filter(has=page.locator('label',has_text=re.compile('^项目名称$'))).first
   query.locator('input').fill(f['prefix']);page.get_by_role('button',name='查询',exact=True).click()
   page.get_by_role('button',name='创建项目',exact=True).click();dialog=page.locator('.el-dialog:visible').last
   def field(label):return dialog.locator('.el-form-item').filter(has=page.locator('label',has_text=re.compile('^'+re.escape(label)+'$'))).first
   field('合同主档').locator('input').fill(fixture['contractNo'])
   page.get_by_role('option',name=re.compile(re.escape(fixture['contractNo']))).click()
   if case=='UI1':
    field('关联销售订单').wait_for();check(case+' multiple orders leave project name empty until chosen',field('项目名称').locator('input').input_value()=='')
    field('关联销售订单').locator('.el-select').click();page.get_by_role('option',name=re.compile(re.escape(chosen['orderNo']))).click()
   expect(field('项目名称').locator('input')).to_have_value(chosen['projectName'])
   check(case+' project name is readonly and execution follows selected header',field('项目名称').locator('input').get_attribute('readonly') is not None and chosen['executionNo'] in dialog.inner_text())
   expect(field('客户编码').locator('input')).to_have_value('CUST-001')
   check(case+' organization populated from source', 'DPTECH-DEMO' in field('下单公司').inner_text() and 'OFFICE-HZ-DEMO' in field('下单办事处').inner_text())
   page.screenshot(path=str(out/(case+'-source.png')),full_page=True)
   dialog.get_by_text('站点未维护',exact=True).click();field('兼容实施地点').locator('input').fill('Synthetic local browser acceptance')
   for label,option in [('签约方式','直签'),('项目类别','普通类'),('实施方式','原厂直服')]:
    control=field(label).locator('.el-select');control.click()
    option_list=control.locator('input').get_attribute('aria-controls')
    options=page.locator('[id="'+option_list+'"]').get_by_role('option')
    options.first.wait_for(state='visible');names=options.all_text_contents()
    # Match repository dictionary labels; report actual candidates when labels differ.
    matching=[name for name in names if option == name]
    assert len(matching)==1,{'field':label,'options':names}
    page.get_by_role('option',name=matching[0],exact=True).click()
   field('创建原因').locator('textarea').fill(f['prefix']+'-'+case+'-browser')
   dialog.get_by_role('button',name='下一步：匹配模板',exact=True).click()
   dialog.get_by_text('项目交付全流程V2',exact=True).click()
   dialog.get_by_role('button',name='下一步：确认',exact=True).click()
   with page.expect_response(lambda r:r.request.method=='POST' and r.url.endswith('/pms/projects'),timeout=45000) as response:
    dialog.get_by_role('button',name='提交创建',exact=True).click()
   data=response.value.json();check(case+' real browser project transaction succeeds',data.get('code')==0)
   results.append({'case':case,'route':route,'requestUrl':response.value.url,'response':data});save()
   projectId=data['data']['id']
   detailRoute='/pms/project-management/project-master-detail' if case=='UI1' else '/pms-inheritance/project-detail'
   page.goto('http://127.0.0.1:19081'+detailRoute+'?projectId='+str(projectId),wait_until='domcontentloaded')
   page.get_by_text(chosen['executionNo'],exact=True).first.wait_for(timeout=40000)
   check(case+' detail shows linked execution and customer project',chosen['projectName'] in page.inner_text('body') and fixture['contractNo']+'-CUSTOMER-'+('B' if case=='UI1' else 'A') in page.inner_text('body'))
   zero=page.locator('.fact').filter(has=page.locator('dt',has_text=re.compile('^项目金额$')))
   check(case+' authorized zero amount displays as zero',zero.count()>0 and zero.first.locator('dd').inner_text().strip() in ['0','0.00'])
   page.screenshot(path=str(out/(case+'-detail.png')),full_page=True)
  check('no uncaught browser errors',not errors)
 except Exception as error:
  (out/'failure.json').write_text(json.dumps({'type':type(error).__name__,'message':str(error),'pageErrors':errors,'url':page.url},ensure_ascii=False,indent=2),encoding='utf8')
  if '/login' not in page.url:page.screenshot(path=str(out/'failure.png'),full_page=True)
  raise
 finally:browser.close()
print(json.dumps({'passed':len(checks),'created':len(results)}))