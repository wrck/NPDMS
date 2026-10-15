import json,os,pathlib,shutil,subprocess,time,sys
import common as c
def process_start_ticks(pid):
 text=pathlib.Path("/proc/"+str(pid)+"/stat").read_text();return int(text[text.rfind(")")+2:].split()[19])
def free():return shutil.disk_usage("/workspace").free
def main():
 pre=json.loads((c.ROOT/'preflight.json').read_text());assert pre['status']=='PASS'
 assert not (c.ROOT/'launch-intent.json').exists(),'Exactly one actual Maven invocation allowed'
 assert free()>=c.BUDGET
 env=os.environ.copy();env['JAVA_HOME']='/workspace/toolchains/jdk-25.0.4.1';env['PATH']=str(c.JDK)+':/workspace/toolchains/apache-maven-3.9.11/bin:'+env['PATH'];env['MAVEN_OPTS']=env.get('MAVEN_OPTS','')+' -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
 log=pathlib.Path(str(c.PREFIX)+'-test.log');assert not log.exists()
 c.save('launch-intent.json',{'at':c.now(),'command':c.command(),'one_maven_run_only':True,'launcher_pid':os.getpid()})
 with (c.ROOT/'launch-intent.json').open('rb') as intent:os.fsync(intent.fileno())
 with (c.ROOT/'observer.log').open('xb') as obslog:
  observer=subprocess.Popen([sys.executable,str(c.ROOT/'observer.py')],cwd=c.ROOT,env=env,stdout=obslog,stderr=subprocess.STDOUT)
  c.save('observer-launched-process.json',{'at':c.now(),'pid':observer.pid,'proc_start_ticks':process_start_ticks(observer.pid),'launcher_pid':os.getpid()})
  began=time.monotonic()
  while not (c.ROOT/'observer-ready.json').exists():
   assert observer.poll() is None,'Observer startup failed; Maven not started'
   assert time.monotonic()-began<45,'Observer not ready; Maven not started'
   time.sleep(.1)
  assert free()>=c.BUDGET
  started=c.now();tick=time.monotonic()
  with log.open('xb') as out:
   proc=subprocess.Popen(c.command(),cwd=c.REPO,env=env,stdout=out,stderr=subprocess.STDOUT,start_new_session=True)
   ref={'started_at':started,'maven_pid':proc.pid,'maven_pgid':proc.pid,'launcher_pid':os.getpid(),'observer_pid':observer.pid,'working_directory':str(c.REPO),'command':c.command(),'log':str(log),'stop_threshold_bytes':c.GUARD,'budget_bytes':c.BUDGET}
   ref.update(maven_proc_start_ticks=process_start_ticks(proc.pid),observer_proc_start_ticks=process_start_ticks(observer.pid),launcher_proc_start_ticks=process_start_ticks(os.getpid()),available_at_launch_bytes=free(),candidate_file_index=str(c.ROOT/'candidate-file-index-before.json'),candidate_file_index_sha256=c.sha(c.ROOT/'candidate-file-index-before.json'),candidate_source_confirmation=str(c.ROOT/'candidate-source-confirmation.json'),jfr_policy=pre['jfr_policy'])
   c.save('test-process.json',ref);print('TEST_STARTED='+json.dumps(ref),flush=True)
   rc=proc.wait()
  result={**ref,'ended_at':c.now(),'exit_code':rc,'elapsed_seconds':time.monotonic()-tick,'log_sha256':c.sha(log),'maven_invocations':1,'disk_guard_triggered':(c.ROOT/'disk-guard-trigger.json').exists()}
  c.save('test-execution.json',result);print('MAVEN_FINISHED='+json.dumps(result),flush=True)
  # Observer is independent of the test process group. Its failures never restart Maven.
  while observer.poll() is None:time.sleep(.25)
  c.save('launcher-result.json',{'at':c.now(),'maven_exit_code':rc,'observer_exit_code':observer.returncode,'maven_invocations':1})
  print('LAUNCHER_COMPLETE',flush=True)
if __name__=='__main__':
 try:main()
 except Exception as e:
  c.save('launch-error.json',{'at':c.now(),'type':type(e).__name__,'error':str(e),'no_automatic_maven_restart':True});raise
