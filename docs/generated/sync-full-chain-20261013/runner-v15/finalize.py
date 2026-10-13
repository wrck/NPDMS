import csv,hashlib,io,json,pathlib,shutil,subprocess,xml.etree.ElementTree as ET
import common as c
def main():
 assert not (c.ROOT/'verification-receipt.json').exists()
 assert not (c.ROOT/'finalize-intent.json').exists(),'No automatic finalize retry'
 c.save('finalize-intent.json',{'at':c.now(),'no_test_restart':True,'after_quick_native_and_junit_report':True})
 execution=json.loads((c.ROOT/'test-execution.json').read_text())
 pre=json.loads((c.ROOT/'preflight.json').read_text())
 report=c.REPO/'.run/mysql-business-harness/target/surefire-reports/TEST-cn.iocoder.yudao.server.sync.ShipmentFullChainMySqlTest.xml'
 assert report.exists() and report.stat().st_mtime>=__import__('datetime').datetime.fromisoformat(execution['started_at']).timestamp();saved=c.ROOT/'surefire.xml';assert not saved.exists();shutil.copyfile(report,saved)
 suite=ET.parse(saved).getroot();junit={k:int(suite.attrib[k]) for k in ['tests','failures','errors','skipped']}
 properties={p.attrib['name']:p.attrib['value'] for p in suite.findall('./properties/property')}
 assert properties['pms.sync.mysql.fullChain.database']==c.DB
 for key,value in {'rows':'4000000','contracts':'81522','shipmentContracts':'120639','packages':'400000','reuseSourceDatabase':c.SOURCE,'productionSqlParserCache':'true','compressBinlog':'true','compressEvidenceTables':'true','compressBindingTable':'true','compressedPageKiB':'4'}.items():assert properties['pms.sync.mysql.fullChain.'+key]==value
 c.save('junit-result.json',{'counts':junit,'properties':properties,'failures':[ET.tostring(x,encoding='unicode') for x in suite.findall('.//failure')+suite.findall('.//error')]})
 events={}
 with pathlib.Path(str(c.PREFIX)+'-test.log').open(errors='replace') as f:
  for line in f:
   if line.startswith('FULL_CHAIN_') and '=' in line:
    tag,data=line.strip().split('=',1);events.setdefault(tag,[]).append(json.loads(data))
 c.save('native-event-index.json',events)
 result=events.get('FULL_CHAIN_RESULT',[None])[-1]
 # Report native status and timings before slower archived-content verification.
 quick={'at':c.now(),'maven_exit_code':execution['exit_code'],'junit':junit,'full_chain_result':result,'native_phases':events.get('FULL_CHAIN_TASK',[]),'verification_in_progress':True}
 c.save('four-phase-native-result.json',quick);print('FOUR_PHASE_NATIVE_RESULT='+json.dumps(quick,ensure_ascii=False),flush=True)
 start=json.loads((c.ROOT/'source-11-start-evidence.json').read_text())['source_content']
 tables=[r['table_name'] for r in start['counts']]
 query='START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY; '+' UNION ALL '.join("SELECT '"+c.SOURCE+"' AS schema_name,'"+t+"' AS table_name,COUNT(*) AS exact_rows FROM `"+c.SOURCE+'`.`'+t+'`' for t in tables)+'; ROLLBACK;'
 counts=c.rows('after/source-11-counts',query,timeout=900)
 checks=c.rows('after/source-11-checksums','CHECKSUM TABLE '+', '.join('`'+c.SOURCE+'`.`'+t+'`' for t in tables)+' EXTENDED;',timeout=900)
 assert counts==start['counts'] and checks==start['checksums'],'Original source content changed'
 definitions=[]
 for i,r in enumerate(start['definitions']):
  defs=c.rows('after/source-11-def-'+str(i),'SHOW CREATE TABLE `'+c.SOURCE+'`.`'+r['name']+'`;')
  definitions.append({'schema':c.SOURCE,'name':r['name'],'type':'BASE TABLE','show_create':defs})
 assert definitions==start['definitions'],'Original source definitions changed'
 c.save('source-11-after-evidence.json',{'checked_at':c.now(),'counts':counts,'checksums':checks,'definitions':definitions,'counts_checksums_definitions_identical':True})
 existing=json.loads((c.ROOT/'source-preexisting-content-before.json').read_text())
 oldtables=[r['table_name'] for r in existing['counts']]
 oldcounts=c.rows('after/source-preexisting-counts','START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY; '+' UNION ALL '.join("SELECT '"+c.SOURCE+"' AS schema_name,'"+t+"' AS table_name,COUNT(*) AS exact_rows FROM `"+c.SOURCE+'`.`'+t+'`' for t in oldtables)+'; ROLLBACK;',timeout=900)
 oldchecks=c.rows('after/source-preexisting-checksums','CHECKSUM TABLE '+', '.join('`'+c.SOURCE+'`.`'+t+'`' for t in oldtables)+' EXTENDED;',timeout=900)
 assert oldcounts==existing['counts'] and oldchecks==existing['checksums']
 olddefs=[]
 for i,r in enumerate(existing['definitions']):
  defs=c.rows('after/source-preexisting-def-'+str(i),'SHOW CREATE '+('VIEW' if r['type']=='VIEW' else 'TABLE')+' `'+c.SOURCE+'`.`'+r['name']+'`;')
  olddefs.append({'schema':c.SOURCE,'name':r['name'],'type':r['type'],'show_create':defs})
 assert olddefs==existing['definitions']
 meta=c.rows('after/source-metadata',"SELECT TABLE_NAME,TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+c.SOURCE+"' ORDER BY TABLE_NAME;")
 oldmeta=json.loads((c.ROOT/'source-metadata-before.json').read_text())
 assert all(r in meta for r in oldmeta) and {r['TABLE_NAME'] for r in meta}-{r['TABLE_NAME'] for r in oldmeta}==set(pre['new_reuse_helpers'])
 schemas=c.rows('after/schemas','SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME;')
 assert {r['SCHEMA_NAME'] for r in schemas}=={r['SCHEMA_NAME'] for r in json.loads((c.ROOT/'schemas-before.json').read_text())}|{c.DB}
 c.save('source-preexisting-after-evidence.json',{'checked_at':c.now(),'counts':oldcounts,'checksums':oldchecks,'definitions':olddefs,'metadata':meta,'all_preexisting_objects_preserved':True,'only_four_authorized_reuse_helpers_added':True})
 residual=c.rows('after/jdbc-fixture-residual',"START TRANSACTION READ ONLY; SELECT (SELECT COUNT(*) FROM `"+c.DB+"`.plt_migration_source_record WHERE id<0) AS negative_sources,(SELECT COUNT(*) FROM `"+c.DB+"`.plt_external_key_mapping WHERE id<0) AS negative_mappings,(SELECT COUNT(*) FROM `"+c.DB+"`.plt_migration_source_record WHERE tenant_id<>1) AS other_tenant_sources,(SELECT COUNT(*) FROM `"+c.DB+"`.plt_external_key_mapping WHERE tenant_id<>1) AS other_tenant_mappings; COMMIT;")
 assert residual==[{'negative_sources':'0','negative_mappings':'0','other_tenant_sources':'0','other_tenant_mappings':'0'}]
 compressed=c.rows('after/compression',"SELECT TABLE_NAME,ROW_FORMAT,CREATE_OPTIONS FROM information_schema.TABLES WHERE TABLE_SCHEMA='"+c.DB+"' AND TABLE_NAME IN ('plt_migration_source_record','plt_external_key_mapping','int_sync_binding') ORDER BY TABLE_NAME;")
 assert len(compressed)==3 and all(r['ROW_FORMAT']=='Compressed' and 'KEY_BLOCK_SIZE=4' in r['CREATE_OPTIONS'].upper() for r in compressed)

 after_config=c.config('after/configuration');assert after_config==pre['configuration']
 mysql=c.identity(c.CONTAINER,c.CID);redis=c.identity(c.REDIS,pre['redis']['Id'])
 for key in ['Id','RestartCount','HostConfig','Mounts','Image']:assert mysql[key]==pre['mysql'][key] and redis[key]==pre['redis'][key]
 for key in ['Pid','StartedAt','Running','OOMKilled']:assert mysql['State'][key]==pre['mysql']['State'][key] and redis['State'][key]==pre['redis']['State'][key]
 before=json.loads((c.ROOT/'candidate-file-index-before.json').read_text())['files'];bad=[]
 for r in before:
  p=c.REPO/r['relative_path']
  if not p.exists() or p.stat().st_size!=r['bytes'] or c.sha(p)!=r['sha256']:bad.append(r['relative_path'])
 assert not bad,bad
 git={'head':c.run(['git','-C',str(c.REPO),'rev-parse','HEAD']).strip(),'status':c.run(['git','-C',str(c.REPO),'status','--porcelain']),'diff_sha256':hashlib.sha256(c.run(['git','-C',str(c.REPO),'diff','--binary']).encode()).hexdigest(),'staged_diff':c.run(['git','-C',str(c.REPO),'diff','--cached','--stat'])};assert git==pre['git']
 c.save('candidate-and-git-after.json',{'checked_files':len(before),'mismatches':bad,'git':git,'all_identical':True})
 final_counts=c.rows('after/final-counts','START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY; '+' UNION ALL '.join("SELECT '"+t+"' AS table_name,COUNT(*) AS exact_rows FROM `"+c.DB+'`.`'+t+'` WHERE tenant_id=1' for t in ['com_contract','com_shipment_contract_reference','com_shipment_package','ast_device','ast_device_shipment','int_sync_binding','plt_migration_source_record','plt_external_key_mapping','plt_migration_issue','plt_outbox_event'])+'; ROLLBACK;',timeout=900)
 actual={r['table_name']:int(r['exact_rows']) for r in final_counts}
 if result:assert actual==result['counts']
 logs=c.rows('after/binary-logs','SHOW BINARY LOGS;');active=c.rows('after/active-binlog','SHOW BINARY LOG STATUS;')
 physical=c.rows('after/table-physical',"SELECT NAME,FILE_SIZE,ALLOCATED_SIZE,ROW_FORMAT,ZIP_PAGE_SIZE FROM information_schema.INNODB_TABLESPACES WHERE NAME LIKE '"+c.DB+"/%' ORDER BY NAME;")
 c.save('final-counts.json',{'checked_at':c.now(),'counts':actual,'native_counts_identical':result is not None,'table_physical':physical,'binary_logs':logs,'active_binlog':active})
 ok=execution['exit_code']==0 and not execution['disk_guard_triggered'] and junit=={'tests':1,'failures':0,'errors':0,'skipped':0} and result is not None and result['status']=='PASS'
 if ok:
  assert len(events['FULL_CHAIN_TASK'])==4 and [r['phase'] for r in events['FULL_CHAIN_TASK']]==c.PHASES
  assert len(events['FULL_CHAIN_SOURCE_SCHEMA'])==2 and all(r['columnsVerified']==102 for r in events['FULL_CHAIN_SOURCE_SCHEMA'])
  assert events['FULL_CHAIN_ORGANIZATION_BATCH_SQL']==[{'status':'PASS','checks':'MIXED_FACTS_NULL_CLEAR_TENANT_DELETED_EMPTY_MISSING_ROLLBACK'}]
  assert events['FULL_CHAIN_JDBC_EVIDENCE_SQL']==[{'status':'PASS','checks':'MAPPER_EQUIVALENCE_JSON_UNICODE_NULL_TIME_AUDIT_MANDATORY_TX_DUPLICATE_ROLLBACK_TENANT'}]
  assert events['FULL_CHAIN_MAPPING_PAGE_SQL']==[{'status':'PASS','checks':'TENANT_BATCH_DELETED_EMPTY_NULL_DUPLICATE_MISSING_ROLLBACK'}]
  assert events['FULL_CHAIN_REUSED_SOURCE'][0]['generatedRows']==0 and result['deltaShipments']==1000
  assert all(r['productionInitializerRequested'] and r['after']=='com.baomidou.mybatisplus.extension.parser.cache.JdkSerialCaffeineJsqlParseCache' for r in events['FULL_CHAIN_SQL_PARSER_PROFILE'])
  assert all(r['transactionCompression'] and r['binlogEnabled'] and r['compressedPageKiB']==4 and r['bindingTableCompression'] for r in events['FULL_CHAIN_STORAGE_PROFILE'])
 observer=json.loads((c.ROOT/'observer-result.json').read_text())
 assert observer['status']=='PASS' and observer['jfr_dumps']==['initial-end']
 dump=json.loads((c.ROOT/'jfr/initial-end-receipt.json').read_text())
 assert dump['bytes']<=67108864 and c.sha(dump['path'])==dump['sha256']
 receipt={'status':'PASS' if ok else 'FAIL','completed_at':c.now(),'maven_invocations':1,'execution':execution,'junit':junit,'timing':result['timing'] if result else None,'source_timings':events.get('FULL_CHAIN_SOURCE',[]),'counts':actual,'source_11_counts_checksums_definitions_unchanged':True,'candidate_files_unchanged':len(before),'git_state_unchanged':True,'mysql_redis_resources_globals_unchanged':True,'source102_organization_mapping_business_association_stability_assertions_passed':ok,'binary_logs_after':logs,'active_binlog_after':active,'target_allocated_bytes':sum(int(r['ALLOCATED_SIZE']) for r in physical),'filesystem_available_bytes':shutil.disk_usage('/workspace').free,'parser_baseline_difference':pre['parser_baseline_difference'],'no_cleanup_or_binlog_rotation_or_reset_during_test':True,'no_other_test_commit_push_permission_dependency_change':True,'jfr_analysis_pending':True,'jfr_policy':pre['jfr_policy'],'single_jfr_dump':dump,'observer_pass':True,'preexisting_source_objects_preserved':len(existing['definitions']),'jdbc_full_column_equivalence_and_no_residual':True}
 c.save('verification-receipt.json',receipt);print('VERIFICATION_RESULT='+json.dumps(receipt,ensure_ascii=False),flush=True)
if __name__=='__main__':
 try:main()
 except Exception as e:c.save('finalize-error.json',{'at':c.now(),'type':type(e).__name__,'error':str(e),'test_not_restarted':True});raise
