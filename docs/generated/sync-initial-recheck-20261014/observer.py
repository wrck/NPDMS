import concurrent.futures,csv,gzip,io,json,os,pathlib,re,shutil,signal,subprocess,threading,time,traceback
import common as c
from performance_guard import stop_test_group
LOG=pathlib.Path(str(c.PREFIX)+'-test.log')
REF=c.ROOT/'test-process.json'
DONE=c.ROOT/'test-execution.json'
STATE=c.ROOT/'observer-state.json'
BEGIN=time.monotonic();HZ=os.sysconf('SC_CLK_TCK');PAGE=os.sysconf('SC_PAGE_SIZE')
pool=concurrent.futures.ThreadPoolExecutor(max_workers=1)
phase=None;run_id=None;source_prefix=0;source_rows={};surefire=None;seen_phases=set();dump_names=set();snapshots=[];errors=[];stop=threading.Event()
summary={'samples':0,'minimum_free_bytes':shutil.disk_usage('/workspace').free,'peak_java_group_rss_bytes':0,'peak_mysql_rss_bytes':0,'peak_heap_by_pid':{},'disk_guard_triggered':False,'resource_interval_seconds':10,'cpu_measurement':'Exact cumulative /proc CPU ticks divided by CLK_TCK; interval CPU derives from successive monotonic timestamps. RSS/heap peaks are sampled maxima.'}
latest_event=None;last_resource=None;prev_cpu={};validated_pid=None;future_tasks=[];jfr_lock=threading.Lock();validation_lock=threading.RLock();validation_requested=False
def append(name,x):
 p=c.ROOT/name
 with p.open('a') as f:f.write(json.dumps(x,ensure_ascii=False)+'\n');f.flush()
def error(kind,e):
 row={'at':c.now(),'kind':kind,'error_type':type(e).__name__,'error':str(e),'traceback':traceback.format_exc()}
 errors.append(row);append('observer-errors.jsonl',row);print('OBSERVER_ERROR='+json.dumps(row,ensure_ascii=False),flush=True)
def live(pid):
 try:return not pathlib.Path('/proc/'+str(pid)+'/stat').read_text().split(') ',1)[1].startswith('Z')
 except OSError:return False
def cpu(pid):
 try:
  text=pathlib.Path('/proc/'+str(pid)+'/stat').read_text();v=text[text.rfind(')')+2:].split();ut=int(v[11]);st=int(v[12]);t=time.monotonic()
  r={'pid':pid,'pgid':int(v[2]),'user_cpu_ticks':ut,'system_cpu_ticks':st,'user_cpu_seconds':ut/HZ,'system_cpu_seconds':st/HZ,'rss_bytes':int(v[21])*PAGE,'process_start_ticks':int(v[19]),'monotonic_time':t}
  old=prev_cpu.get(pid)
  if old and old['process_start_ticks']==r['process_start_ticks'] and t>old['monotonic_time']:r['interval_cpu_percent']=(ut+st-old['user_cpu_ticks']-old['system_cpu_ticks'])/HZ/(t-old['monotonic_time'])*100
  prev_cpu[pid]=r.copy();return r
 except (OSError,ValueError):return None
def reference():return json.loads(REF.read_text()) if REF.exists() else None
def java_group():
 ref=reference();result=[]
 if not ref:return result
 raw=c.run(['ps','-eo','pid=,pgid=,comm='],timeout=5)
 for l in raw.splitlines():
  v=l.split()
  if len(v)==3 and int(v[1])==ref['maven_pid'] and v[2]=='java':
   pid=int(v[0]);r=cpu(pid)
   if r and live(pid):
    cmd=pathlib.Path('/proc/'+v[0]+'/cmdline').read_bytes()
    r['role']='surefire' if b'surefirebooter' in cmd or b'ForkedBooter' in cmd else 'maven';result.append(r)
 return result
def snapshot(label,event=None):
 started=c.now();path=c.ROOT/'snapshots'/label;path.mkdir(parents=True,exist_ok=False)
 q="SELECT JSON_OBJECT('schema',SCHEMA_NAME,'digest',DIGEST,'normalized_sql',DIGEST_TEXT,'count',COUNT_STAR,'timer_ps',CAST(SUM_TIMER_WAIT AS CHAR),'lock_ps',CAST(SUM_LOCK_TIME AS CHAR),'rows_affected',SUM_ROWS_AFFECTED,'rows_sent',SUM_ROWS_SENT,'rows_examined',SUM_ROWS_EXAMINED,'tmp_tables',SUM_CREATED_TMP_TABLES,'tmp_disk_tables',SUM_CREATED_TMP_DISK_TABLES,'sort_rows',SUM_SORT_ROWS,'no_index_used',SUM_NO_INDEX_USED,'first_seen',FIRST_SEEN,'last_seen',LAST_SEEN) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME IN ('"+c.SOURCE+"','"+c.DB+"') ORDER BY SCHEMA_NAME,DIGEST;"
 (path/'digest.sql').write_text(q+'\n')
 args=['docker','exec',c.CONTAINER,'mysql','-uroot','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','-e',q]
 p=subprocess.run(args,capture_output=True,text=True,timeout=30)
 with gzip.open(path/'digest.jsonl.gz','wt') as f:f.write(p.stdout)
 (path/'digest.stderr').write_text(p.stderr)
 if p.returncode:raise RuntimeError('Digest snapshot failed: '+p.stderr)
 parsed=[json.loads(l) for l in p.stdout.splitlines() if l]
 status=c.rows('snapshots/'+label+'/innodb-status','SHOW GLOBAL STATUS WHERE Variable_name IN ('+','.join("'"+s+"'" for s in c.STATUS)+');')
 assert {r['Variable_name'] for r in status}==set(c.STATUS)
 cmp=c.rows('snapshots/'+label+'/innodb-cmp','SELECT * FROM information_schema.INNODB_CMP;')
 active=c.rows('snapshots/'+label+'/active-binlog','SHOW BINARY LOG STATUS;')
 logs=c.rows('snapshots/'+label+'/binary-logs','SHOW BINARY LOGS;')
 coverage=c.rows('snapshots/'+label+'/digest-coverage',"SHOW GLOBAL STATUS WHERE Variable_name IN ('Performance_schema_digest_lost','Uptime');")
 record={'label':label,'started_at':started,'ended_at':c.now(),'event':event,'digest_rows':len(parsed),'digest_gzip':str(path/'digest.jsonl.gz'),'innodb_status':status,'innodb_cmp':cmp,'active_binlog':active,'binary_logs':logs,'coverage':coverage,'mysql_cpu':cpu(c.MYSQL_PID),'java_group':java_group(),'non_atomic_phase_boundary':True,'scope':'Cumulative source and target schema digests. Snapshot begins after log observation and can overlap adjacent work; source streaming SELECT includes consumption backpressure.'}
 c.save('snapshots/'+label+'/receipt.json',record);snapshots.append(record)
 c.state('snapshot-index.json',{'snapshots':snapshots,'errors':errors,'counters_reset':False})
 return record
def schedule(kind,func,*args):
 def job():
  try:return func(*args)
  except Exception as e:error(kind,e)
 future_tasks.append(pool.submit(job))
def disk_guard():
 while not stop.is_set():
  free=shutil.disk_usage('/workspace').free
  summary['minimum_free_bytes']=min(summary['minimum_free_bytes'],free)
  ref=reference()
  if ref and free<c.GUARD and live(ref['maven_pid']) and not summary['disk_guard_triggered']:
   summary['disk_guard_triggered']=True
   row={'at':c.now(),'available_bytes':free,'threshold_bytes':c.GUARD,'maven_pgid':ref['maven_pid'],'latest_event':latest_event}
   c.save('disk-guard-trigger.json',row);print('DISK_GUARD_STOP='+json.dumps(row),flush=True)
   result=stop_test_group(ref,cpu(surefire) if surefire else None)
   c.save('disk-stop-result.json',result)
   return
  stop.wait(.25)
def resources():
 global surefire,last_resource,validation_requested
 next_sample=time.monotonic();n=0
 while not stop.is_set():
  if time.monotonic()<next_sample:stop.wait(min(.25,next_sample-time.monotonic()));continue
  n+=1;start=time.monotonic()
  try:
   sample={'timestamp':c.now(),'monotonic_time':start,'elapsed_seconds':start-BEGIN,'phase':phase,'free_bytes':shutil.disk_usage('/workspace').free,'mysql_cpu':cpu(c.MYSQL_PID),'java_group':java_group(),'cpu_hz':HZ,'page_size':PAGE}
   for p in sample['java_group']:
    if p['role']=='surefire':surefire=p['pid']
   sample['pressure']={}
   for kind in ('cpu','io','memory'):
    p=pathlib.Path('/proc/pressure')/kind
    if p.exists():sample['pressure'][kind]=p.read_text()
   sample['container_cgroup']={}
   for name in ('cpu.stat','cpu.max','memory.current','memory.events','io.stat'):
    p=pathlib.Path('/sys/fs/cgroup')/name
    if p.exists():sample['container_cgroup'][name]=p.read_text()
   sample['collection_duration_seconds']=time.monotonic()-start
   summary['samples']+=1;summary['peak_java_group_rss_bytes']=max(summary['peak_java_group_rss_bytes'],sum(p['rss_bytes'] for p in sample['java_group']))
   if sample['mysql_cpu']:summary['peak_mysql_rss_bytes']=max(summary['peak_mysql_rss_bytes'],sample['mysql_cpu']['rss_bytes'])
   last_resource=sample;append('resources.jsonl',sample)
  except Exception as e:error('resource-sample',e)
  next_sample+=10
def main():
 global phase,run_id,source_prefix,latest_event
 c.save('observer-process.json',{'pid':os.getpid(),'started_at':c.now(),'resource_interval':10,'disk_guard_poll_seconds':.25,'guard_bytes':c.GUARD,'periodic_heap_attach':False,'jfr_policy':'JVM startup recording; dumponexit=true; maxsize64m; no jcmd or heap attach during run'})
 snapshot('before-test')
 c.save('observer-ready.json',{'at':c.now(),'pid':os.getpid(),'before_test_snapshot_complete':True})
 guard_thread=threading.Thread(target=disk_guard,daemon=True,name='approved-disk-guard');guard_thread.start()
 resource_thread=threading.Thread(target=resources,daemon=True,name='ten-second-resources');resource_thread.start()
 tail=None;last_poll=0;last_beat=0;last_read=None;last_state=0
 while True:
  if tail is None and LOG.exists():tail=LOG.open(errors='replace')
  if tail:
   while True:
    line=tail.readline()
    if not line:break
    if not line.startswith('FULL_CHAIN_'):continue
    tag,v=line.rstrip().split('=',1)
    try:event=json.loads(v)
    except Exception as e:error('native-event-parse',e);continue
    latest_event={'at':c.now(),'tag':tag,'event':event};append('native-events.jsonl',latest_event)
    if tag=='FULL_CHAIN_PERFORMANCE_STOP':
     if not (c.ROOT/'performance-regression.json').exists():c.save('performance-regression.json',latest_event)
     print('PERFORMANCE_STOP_OBSERVED='+json.dumps(event),flush=True)
    if tag=='FULL_CHAIN_PROGRESS':
     phase=event['phase'];run_id=event['runId']
     if phase not in seen_phases:
      seen_phases.add(phase);source_rows={};source_prefix=0;schedule('phase-start-snapshot',snapshot,phase.lower()+'-start',latest_event)
    elif tag=='FULL_CHAIN_SOURCE':
     schedule('source-end-snapshot',snapshot,'source-'+str(event['sourceIndex'])+'-end',latest_event)
    elif tag=='FULL_CHAIN_TASK':
     phase=event['phase'];schedule('phase-end-snapshot',snapshot,phase.lower()+'-end',latest_event)

     c.state('phase-'+phase.lower()+'-complete.json',latest_event)
     print('PHASE_COMPLETE='+json.dumps(latest_event),flush=True)
    elif tag=='FULL_CHAIN_INITIAL_ONLY_RESULT':c.save('native-initial-result.json',event);print('INITIAL_RESULT='+json.dumps(event),flush=True)
  if time.monotonic()-last_state>=1:
   c.state('observer-state.json',{'at':c.now(),'pid':os.getpid(),'maven':reference(),'surefire_pid':surefire,'phase':phase,'latest_event':latest_event,'latest_barcode_read':last_read,'last_resource':last_resource,'resource_summary':summary,'snapshots_completed':len(snapshots),'observer_errors':len(errors)});last_state=time.monotonic()
  if time.monotonic()-last_beat>=30:
   print('OBSERVER_HEARTBEAT='+json.dumps({'at':c.now(),'phase':phase,'surefire_pid':surefire,'free_bytes':shutil.disk_usage('/workspace').free,'latest_barcode_read':last_read,'samples':summary['samples'],'errors':len(errors)}),flush=True);last_beat=time.monotonic()
  if DONE.exists():break
  time.sleep(.25)
 # Drain any end events written before the Maven result appears; main loop has
 # read native task/result lines during the Maven JVM shutdown window.
 stop.set();guard_thread.join(timeout=12);resource_thread.join(timeout=15);pool.shutdown(wait=True)
 snapshot('after-test')
 c.save('resource-summary.json',summary)
 c.save('observer-result.json',{'completed_at':c.now(),'status':'COMPLETE_WITH_ERRORS' if errors else 'PASS','snapshots':len(snapshots),'errors':errors,'jfr_dumps':sorted(dump_names),'resource_summary':summary,'native_log_path':str(LOG),'phase_boundary_limitation':'Snapshots follow observed progress/task logs and may include adjacent verification/setup; digest deltas are not atomic native phase counts. Source SELECT timers include consumer backpressure. No RESET performed.'})
 print('OBSERVER_COMPLETE',flush=True)
if __name__=='__main__':
 try:main()
 except Exception as e:error('observer-main',e);c.state('observer-failure.json',{'at':c.now(),'pid':os.getpid(),'error':str(e),'maven_must_not_be_restarted':True});raise
