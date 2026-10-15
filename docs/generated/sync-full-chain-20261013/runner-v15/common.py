import datetime, hashlib, json, pathlib, subprocess, csv, io, os
ROOT=pathlib.Path(__file__).resolve().parent
REPO=pathlib.Path('/workspace/NPDMS-sync-acceptance')
PREFIX=pathlib.Path('/workspace/npdms-sync-reusev154m-20261013')
DB='npdms_sync_full_chain_20261009_reusev154m'
SOURCE='npdms_sync_full_chain_20261009_full4mdensity'
CONTAINER='npdms-sync-business-20261009'
CID='493a79e85127e1633da411ded12b500a0f2ac57ff5b7b9e9ce51dacb16b5179b'
MYSQL_PID=int(json.loads((ROOT/'runtime-epoch.json').read_text())['mysql']['State']['Pid'])
REDIS='npdms-sync-full-chain-redis-20261009'
JDK=pathlib.Path('/workspace/toolchains/jdk-25.0.4.1/bin')
MAVEN='/workspace/toolchains/apache-maven-3.9.11/bin/mvn'
GUARD=2147483648
BUDGET=19984437248
PHASES=['INITIAL_MIGRATION','POST_LINK_RECONCILIATION_REPLAY','UNCHANGED_REPLAY','CHANGED_SOURCE_FULL_SCAN']
STATUS=['Innodb_os_log_written','Innodb_data_written','Innodb_buffer_pool_pages_flushed','Innodb_buffer_pool_reads','Innodb_buffer_pool_read_requests','Innodb_log_waits']
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def sha(p):
 h=hashlib.sha256()
 with pathlib.Path(p).open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def save(name,x):
 p=ROOT/name;p.parent.mkdir(parents=True,exist_ok=True);assert not p.exists(),str(p)
 p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n')
def state(name,x):
 p=ROOT/name;tmp=p.with_suffix('.json.tmp');tmp.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n');tmp.replace(p)
def run(args,timeout=30):
 p=subprocess.run(args,text=True,capture_output=True,timeout=timeout)
 if p.returncode:raise RuntimeError(json.dumps({'command':args,'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr},ensure_ascii=False))
 return p.stdout
def sql(name,q,raw=False,timeout=30):
 p=ROOT/(name+'.sql');p.parent.mkdir(parents=True,exist_ok=True);assert not p.exists();p.write_text(q+'\n')
 start=now();args=['docker','exec',CONTAINER,'mysql','-uroot','--default-character-set=utf8mb4','--batch']+(['--raw'] if raw else [])+['-e',q]
 r=subprocess.run(args,text=True,capture_output=True,timeout=timeout)
 (ROOT/(name+'.stdout')).write_text(r.stdout);(ROOT/(name+'.stderr')).write_text(r.stderr)
 save(name+'.execution.json',{'started_at':start,'ended_at':now(),'command':args,'exit_code':r.returncode,'read_only':not q.strip().startswith('CREATE DATABASE')})
 if r.returncode:raise RuntimeError('SQL failed, no automatic retry: '+name+'\n'+r.stderr)
 return r.stdout
def rows(name,q,timeout=30):return list(csv.DictReader(io.StringIO(sql(name,q,timeout=timeout)),delimiter='\t'))
def identity(name,expected=None):
 x=json.loads(run(['docker','inspect',name]))[0]
 if expected:assert x['Id']==expected and x['State']['Running']
 return {k:x[k] for k in ['Id','State','RestartCount','HostConfig','Mounts','Image']}
def config(label):
 return rows(label,"SHOW GLOBAL VARIABLES WHERE Variable_name IN ('log_bin','binlog_format','binlog_row_image','binlog_transaction_compression','binlog_transaction_compression_level_zstd','innodb_buffer_pool_size','innodb_compression_level','innodb_file_per_table','innodb_flush_log_at_trx_commit','innodb_page_size','sync_binlog');")
def command():
 return [MAVEN,'-o','-s','/workspace/npdms-sync-verification-settings.xml','-Dmaven.repo.local=/workspace/toolchains/m2','-f','pom.sync-business-verification.xml','-pl','.run/mysql-business-harness','-am','test','-Dtest=ShipmentFullChainMySqlTest','-Dpms.sync.mysql.fullChain=true','-Dpms.sync.mysql.fullChain.productionSqlParserCache=true','-Dpms.sync.mysql.fullChain.compressBinlog=true','-Dpms.sync.mysql.fullChain.compressEvidenceTables=true','-Dpms.sync.mysql.fullChain.compressBindingTable=true','-Dpms.sync.mysql.fullChain.compressedPageKiB=4','-Dpms.sync.mysql.fullChain.rows=4000000','-Dpms.sync.mysql.fullChain.contracts=81522','-Dpms.sync.mysql.fullChain.shipmentContracts=120639','-Dpms.sync.mysql.fullChain.packages=400000','-Dpms.sync.mysql.fullChain.database='+DB,'-Dpms.sync.mysql.fullChain.reuseSourceDatabase='+SOURCE,'-DargLine=-Xms512m -Xmx1536m -XX:FlightRecorderOptions=stackdepth=128 -XX:StartFlightRecording=name=fullchain,settings=profile,dumponexit=false,maxsize=64m','-Dsurefire.failIfNoSpecifiedTests=false','-DfailIfNoTests=false']
