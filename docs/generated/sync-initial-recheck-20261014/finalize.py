"""Finalize exactly one initial migration; regression skips all correctness scans."""
import datetime, json, pathlib, shutil, xml.etree.ElementTree as ET
import common as c

def main():
    assert not (c.ROOT/'finalize-intent.json').exists(), 'No automatic finalization retry'
    c.save('finalize-intent.json', {'at':c.now(), 'scope':'INITIAL_MIGRATION_ONLY'})
    execution=json.loads((c.ROOT/'test-execution.json').read_text())
    pre=json.loads((c.ROOT/'preflight.json').read_text())
    events={}
    for line in pathlib.Path(str(c.PREFIX)+'-test.log').read_text(errors='replace').splitlines():
        if line.startswith('FULL_CHAIN_') and '=' in line:
            tag, value=line.split('=',1)
            events.setdefault(tag,[]).append(json.loads(value))
    c.save('native-event-index.json',events)
    report=c.REPO/'.run/mysql-business-harness/target/surefire-reports/TEST-cn.iocoder.yudao.server.sync.ShipmentFullChainMySqlTest.xml'
    junit=None
    if report.exists() and report.stat().st_mtime>=datetime.datetime.fromisoformat(execution['started_at']).timestamp():
        shutil.copyfile(report,c.ROOT/'surefire.xml')
        suite=ET.parse(report).getroot()
        junit={k:int(suite.attrib[k]) for k in ('tests','failures','errors','skipped')}
        properties={p.attrib['name']:p.attrib['value'] for p in suite.findall('./properties/property')}
        assert properties['pms.sync.mysql.fullChain.initialOnly']=='true'
        assert properties['pms.sync.mysql.fullChain.database']==c.DB
    c.save('junit-result.json',{'counts':junit,'report_present':junit is not None})
    jfr=c.ROOT/'initial.jfr'
    jfr_result={'exists':jfr.exists()}
    if jfr.exists():jfr_result.update(bytes=jfr.stat().st_size,sha256=c.sha(jfr))
    c.save('jfr-result.json',jfr_result)
    assert all(x['phase']=='INITIAL_MIGRATION' for tag in ('FULL_CHAIN_TASK','FULL_CHAIN_PROGRESS','FULL_CHAIN_SOURCE') for x in events.get(tag,[])), 'Unexpected later phase'
    required=json.loads((c.ROOT/'required-source-hashes.json').read_text())
    mismatches=[p for p,h in required.items() if not (c.REPO/p).exists() or c.sha(c.REPO/p)!=h]
    configuration=c.config('after/configuration')
    mysql=c.identity(c.CONTAINER,c.CID)
    immutable=['Id','RestartCount','HostConfig','Mounts','Image']
    identity_ok=all(mysql[k]==pre['mysql'][k] for k in immutable) and all(mysql['State'][k]==pre['mysql']['State'][k] for k in ['Pid','StartedAt','Running','OOMKilled'])
    observer=json.loads((c.ROOT/'observer-result.json').read_text())
    result=events.get('FULL_CHAIN_INITIAL_ONLY_RESULT',[None])[-1]
    regression=bool(events.get('FULL_CHAIN_PERFORMANCE_STOP')) or (c.ROOT/'performance-regression.json').exists()
    passed=(not regression and execution['exit_code']==0 and not execution['disk_guard_triggered']
            and junit=={'tests':1,'failures':0,'errors':0,'skipped':0}
            and result is not None and result['status']=='PASS_INITIAL_ONLY'
            and not mismatches and configuration==pre['configuration'] and identity_ok
            and observer['status']=='PASS' and jfr.exists() and jfr.stat().st_size>0)
    # A stopped or failed run is terminal before any large verification query.
    if not passed:
        c.save('verification-receipt.json',{'status':'FAIL_PERFORMANCE' if regression else 'FAIL',
               'at':c.now(),'execution':execution,'junit':junit,'performance_stop':events.get('FULL_CHAIN_PERFORMANCE_STOP',[]),
               'source_timings':events.get('FULL_CHAIN_SOURCE',[]),'correctness_scans_executed':False,
               'later_phases_executed':False,'source_content_reverification':'NOT_RUN_AFTER_FAILURE',
               'required_source_mismatches':mismatches,'mysql_identity_unchanged':identity_ok,
               'configuration_unchanged':configuration==pre['configuration'],'jfr':jfr_result,'observer_status':observer['status']})
        print('INITIAL_RECHECK_FAILED: no remaining correctness scans or later phases',flush=True)
        return 1
    existing=json.loads((c.ROOT/'source-preexisting-content-before.json').read_text())
    tables=[r['table_name'] for r in existing['counts']]
    counts=c.rows('after/source-counts','START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY; '+' UNION ALL '.join("SELECT '"+c.SOURCE+"' AS schema_name,'"+t+"' AS table_name,COUNT(*) AS exact_rows FROM `"+c.SOURCE+'`.`'+t+'`' for t in tables)+'; ROLLBACK;',timeout=900)
    checks=c.rows('after/source-checksums','CHECKSUM TABLE '+', '.join('`'+c.SOURCE+'`.`'+t+'`' for t in tables)+' EXTENDED;',timeout=900)
    assert counts==existing['counts'] and checks==existing['checksums'], 'Protected source changed'
    definitions=[]
    for i,r in enumerate(existing['definitions']):
        definitions.append({'schema':c.SOURCE,'name':r['name'],'type':r['type'],'show_create':c.rows('after/source-definition-'+str(i),'SHOW CREATE '+('VIEW' if r['type']=='VIEW' else 'TABLE')+' `'+c.SOURCE+'`.`'+r['name']+'`;')})
    assert definitions==existing['definitions']
    meta=c.rows('after/source-metadata',"SELECT TABLE_NAME,TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+c.SOURCE+"' ORDER BY TABLE_NAME;")
    oldmeta=json.loads((c.ROOT/'source-metadata-before.json').read_text())
    assert all(r in meta for r in oldmeta) and {r['TABLE_NAME'] for r in meta}-{r['TABLE_NAME'] for r in oldmeta}==set(pre['new_reuse_helpers'])
    c.save('source-after-evidence.json',{'counts':counts,'checksums':checks,'definitions':definitions,'only_expected_helpers_added':True})
    assert len(events['FULL_CHAIN_TASK'])==1 and len(events['FULL_CHAIN_SOURCE'])==5
    assert result['laterPhasesExecuted'] is False
    c.save('verification-receipt.json',{'status':'PASS_INITIAL_ONLY','at':c.now(),'execution':execution,'junit':junit,
           'native':result,'source_timings':events['FULL_CHAIN_SOURCE'],'protected_source_unchanged':True,
           'required_source_mismatches':[],'mysql_identity_unchanged':True,'configuration_unchanged':True,
           'jfr':jfr_result,'observer_status':observer['status'],'later_phases_executed':False,
           'remaining_scope':'No reconciliation, stable replay, changed-source or CDC acceptance claimed'})
    print('INITIAL_RECHECK_PASS',flush=True)
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as e:
        c.save('finalize-error.json',{'at':c.now(),'type':type(e).__name__,'error':str(e),'no_retry':True})
        raise
