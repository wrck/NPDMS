"""Initial-only diagnostic preflight. No benchmark or cleanup; creates only a fresh target after all gates."""
import csv,hashlib,io,json,pathlib,shutil,subprocess
import common as c
HEAD=(c.ROOT/'candidate-head.txt').read_text().strip()
BRANCH='fix/visio-template-followup'
def longrows(name,q):return c.rows(name,q,timeout=900)
def main():
    assert not (c.ROOT/'preflight-intent.json').exists(),'No automatic preflight/creation retry'
    assert not (c.ROOT/'initial-review.txt').exists(),'No stale review decision'
    assert not (c.ROOT/'performance-regression.json').exists(),'No stale stop decision'
    c.save('preflight-intent.json',{'at':c.now(),'database':c.DB,'source':c.SOURCE,'budget_bytes':c.BUDGET})
    assert shutil.disk_usage('/workspace').free>=c.BUDGET,'Insufficient budget before source checks'
    assert c.run(['git','-C',str(c.REPO),'rev-parse','HEAD']).strip()==HEAD
    assert c.run(['git','-C',str(c.REPO),'branch','--show-current']).strip()==BRANCH
    c.run(['git','-C',str(c.REPO),'diff','--exit-code','HEAD','--'])
    required=json.loads((c.ROOT/'required-source-hashes.json').read_text())
    assert all(c.sha(c.REPO/n)==h for n,h in required.items())
    assert not list(pathlib.Path('/workspace').glob('npdms-sync-initial-v15-20261014*')),'Existing artifact: no duplicate launch'
    paths=set(subprocess.check_output(['rg','--files','--hidden','-g','!.git'],cwd=c.REPO,text=True).splitlines());paths.add('.run/mysql-business-harness/pom.xml')
    index=[{'relative_path':n,'bytes':(c.REPO/n).stat().st_size,'sha256':c.sha(c.REPO/n)} for n in sorted(paths) if (c.REPO/n).is_file()]
    c.save('candidate-file-index-before.json',{'files':index,'all_visible_repository_files_plus_harness_pom':True})
    c.save('candidate-source-confirmation.json',{'status':'PASS_SOURCE_LOCK','head':HEAD,'required_hashes':required,'build_semantics':'Normal offline Maven lifecycle builds the locked sources; this receipt does not claim a separate fresh compilation.'})
    git={'head':HEAD,'status':c.run(['git','-C',str(c.REPO),'status','--porcelain']),'diff_sha256':hashlib.sha256(c.run(['git','-C',str(c.REPO),'diff','--binary']).encode()).hexdigest(),'staged_diff':c.run(['git','-C',str(c.REPO),'diff','--cached','--stat'])}
    assert not git['staged_diff']
    epoch=json.loads((c.ROOT/'runtime-epoch.json').read_text())
    mysql=c.identity(c.CONTAINER,c.CID);redis=c.identity(c.REDIS,'2ef2d9d23b49fc6d5bece75f26f5f00765172b32b8c793278792ba8a7be27639')
    for current,key in [(mysql,'mysql'),(redis,'redis')]:
        for field in ['Id','RestartCount','HostConfig','Mounts','Image']:assert current[field]==epoch[key][field]
        for field in ['Pid','StartedAt','Running','OOMKilled']:assert current['State'][field]==epoch[key]['State'][field]
        assert current['State']['Running'] and not current['State']['OOMKilled']
    assert mysql['State']['Pid']==c.MYSQL_PID and mysql['HostConfig']['Memory']==2147483648 and mysql['HostConfig']['NanoCpus']==2000000000
    assert mysql['HostConfig']['PortBindings']=={'3306/tcp':[{'HostIp':'127.0.0.1','HostPort':'23316'}]}
    assert redis['HostConfig']['PortBindings']=={'6379/tcp':[{'HostIp':'127.0.0.1','HostPort':'23379'}]}
    c.save('container-before.json',mysql);c.save('redis-before.json',redis)
    configuration=c.config('configuration-before');v={r['Variable_name']:r['Value'] for r in configuration}
    assert v['log_bin']=='ON' and v['binlog_format']=='ROW' and v['binlog_row_image']=='FULL' and v['sync_binlog']=='1' and v['innodb_flush_log_at_trx_commit']=='1' and v['innodb_buffer_pool_size']=='134217728'
    original_configuration=json.loads(pathlib.Path('/workspace/npdms-sync-reusev74m-verification-20261010/preflight.json').read_text())['configuration']
    assert configuration==original_configuration,'Global configuration differs from accepted V7'
    assert c.rows('session-binlog-before','SELECT @@SESSION.sql_log_bin AS sql_log_bin;')==[{'sql_log_bin':'1'}]
    c.save('configuration-before.json',configuration)
    ps=c.run(['ps','-eo','pid,ppid,stat,comm'])
    assert all(l.split()[2].startswith('Z') for l in ps.splitlines()[1:] if len(l.split())>=4 and l.split()[3] in ['java','jshell','mvn'])
    c.save('java-before.json',{'raw':ps})
    others=c.rows('connections-before','SELECT ID,USER,DB,COMMAND,STATE FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID();')
    assert all(r['USER']=='event_scheduler' and r['STATE']=='Waiting on empty queue' for r in others)
    assert not c.rows('transactions-before','SELECT trx_mysql_thread_id,trx_rows_modified FROM information_schema.INNODB_TRX WHERE trx_mysql_thread_id<>CONNECTION_ID();')
    schemas=c.rows('schemas-before','SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME;');c.save('schemas-before.json',schemas)
    assert c.DB not in {r['SCHEMA_NAME'] for r in schemas}
    helpers=['reuse_initialv15r20261014_'+n for n in ['fb_contract','phase','fb_shipment','fb_shipment_barcode']]
    assert not c.rows('helper-absence',"SELECT TABLE_SCHEMA,TABLE_NAME FROM information_schema.TABLES WHERE TABLE_NAME LIKE 'reuse_initialv15r20261014%';")
    source=c.rows('source-metadata-before',"SELECT TABLE_NAME,TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+c.SOURCE+"' ORDER BY TABLE_NAME;");c.save('source-metadata-before.json',source)
    tables=[r['TABLE_NAME'] for r in source if r['TABLE_TYPE']=='BASE TABLE'];assert len(tables)>=15 and len(source)>=19
    assert all(r['ENGINE']=='InnoDB' for r in source if r['TABLE_TYPE']=='BASE TABLE')
    counts=longrows('source-preexisting-counts','START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY; '+' UNION ALL '.join("SELECT '"+c.SOURCE+"' AS schema_name,'"+t+"' AS table_name,COUNT(*) AS exact_rows FROM `"+c.SOURCE+'`.`'+t+'`' for t in tables)+'; ROLLBACK;')
    checks=longrows('source-preexisting-checksums','CHECKSUM TABLE '+', '.join('`'+c.SOURCE+'`.`'+t+'`' for t in tables)+' EXTENDED;')
    definitions=[]
    for i,r in enumerate(source):
        defs=c.rows('source-preexisting-def-'+str(i),'SHOW CREATE '+('VIEW' if r['TABLE_TYPE']=='VIEW' else 'TABLE')+' `'+c.SOURCE+'`.`'+r['TABLE_NAME']+'`;')
        definitions.append({'schema':c.SOURCE,'name':r['TABLE_NAME'],'type':r['TABLE_TYPE'],'show_create':defs})
    source_content={'counts':counts,'checksums':checks,'definitions':definitions}
    baseline_path=pathlib.Path('/workspace/npdms-sync-approved-target-reset-20261012/after/source/evidence.json')
    assert c.sha(baseline_path)=='f303f9248ec0c55cc06f8f3ba2ada84bc0d98f0a9a1945150e580c5cd45e9102'
    original_baseline=json.loads(baseline_path.read_text())
    baseline={'counts':[{'schema_name':c.SOURCE,**r} for r in original_baseline['counts']],'checksums':original_baseline['checksums'],'definitions':[{'schema':c.SOURCE,**r} for r in original_baseline['definitions']]}
    c.save('protected-baseline-reference.json',{'path':str(baseline_path),'sha256':c.sha(baseline_path),'normalization':'Add explicit source schema keys only; preserve counts, checksums and SHOW CREATE values.'})
    for field,key in [('counts','table_name'),('checksums','Table'),('definitions','name')]:
        names={r[key] for r in baseline[field]};assert sorted([r for r in source_content[field] if r[key] in names],key=lambda r:r[key])==sorted(baseline[field],key=lambda r:r[key]),field
    c.save('source-preexisting-content-before.json',source_content)
    originals={r['table_name'] for r in json.loads(pathlib.Path('/workspace/npdms-sync-reusev64m-verification-20261010/source-11-start-evidence.json').read_text())['source_content']['counts']}
    assert len(originals)==11
    start={'counts':[r for r in counts if r['table_name'] in originals],'checksums':[r for r in checks if r['Table'].split('.')[-1] in originals],'definitions':[r for r in definitions if r['name'] in originals]}
    assert all(len(start[k])==11 for k in start)
    c.save('source-11-start-evidence.json',{'basis':'Fresh SELECT/CHECKSUM/SHOW compared with the protected source baseline; original 11 tables retained','source_content':start})
    c.save('binary-logs-before.json',{'active':c.rows('active-binlog-before','SHOW BINARY LOG STATUS;'),'logs':c.rows('binary-logs-before','SHOW BINARY LOGS;')})
    available=shutil.disk_usage('/workspace').free
    assert available>=c.BUDGET,('Insufficient exact budget; stop without further cleanup',available,c.BUDGET)
    data={'checked_at':c.now(),'status':'PASS','database':c.DB,'source':c.SOURCE,'required_free_bytes':c.BUDGET,'available_bytes':available,'guard_bytes':c.GUARD,'candidate_file_count':len(index),'required_hashes':required,'git':git,'mysql':mysql,'redis':redis,'configuration':configuration,'one_maven_run_only':True,'rows':4000000,'contracts':81522,'shipmentContracts':120639,'packages':400000,'chunk':500,'fetch':2000,'source_existing_objects':len(source),'new_reuse_helpers':helpers,'parser_baseline_difference':'V7 and this V15 run both initialize the existing production SQL parser cache. Container epoch differs after environment recovery; record it without claiming an identical run epoch.','jfr_policy':{'profile':'profile','stackdepth':128,'recording_maxsize_bytes':67108864,'single_dump_maxsize_bytes':67108864,'dumponexit':True,'dump_only':'JVM exit (success or stopped diagnostic)' ,'readiness_wait_seconds':30},'no_cleanup_or_binlog_rotation_or_dependency_permission_change_during_test':True,'command':c.command(),'capacity_approval':{'approved_on':'2026-10-14','scope':'This isolated acceptance run only','historical_peak_growth_bytes':17300082688,'guard_bytes':2147483648,'extra_margin_bytes':536870912,'required_bytes':19984437248}}
    c.save('preflight-before-creation.json',data)
    c.save('create-target-intent.json',{'at':c.now(),'database':c.DB,'source_not_regenerated':True,'no_retry':True})
    c.sql('create-target-database','CREATE DATABASE `'+c.DB+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    assert c.rows('empty-target',"SELECT COUNT(*) AS table_count FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+c.DB+"';")==[{'table_count':'0'}]
    data['created_at']=c.now();data['available_immediately_after_creation_bytes']=shutil.disk_usage('/workspace').free
    assert data['available_immediately_after_creation_bytes']>=c.BUDGET
    c.save('preflight.json',data)
    print(json.dumps({'status':'PREFLIGHT_PASS','available_bytes':data['available_immediately_after_creation_bytes'],'budget_bytes':c.BUDGET,'candidate_files':len(index),'database':c.DB,'source_originals':11,'source_existing_objects':len(source)}),flush=True)
if __name__=='__main__':
    try:main()
    except Exception as e:
        c.save('preflight-error.json',{'at':c.now(),'type':type(e).__name__,'error':str(e),'benchmark_started':False,'no_retry_no_cleanup':True});raise
