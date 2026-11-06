'use strict';
const test=require('node:test'); const assert=require('node:assert/strict'); const {EventEmitter}=require('node:events');
const {candidatePorts,launchSpec,DacSupervisor}=require('../src/dac/supervisor.cjs');
const config={publicKey:'0'.repeat(64),javaPath:'/bundle/java',jarPath:'/bundle/dac.jar',parserDirectory:'/bundle/parser',runtimeFile:'/data/runtime',dataDirectory:'/data/dac',serverUrl:'https://server',webOrigin:'https://web'};
test('launch pins paths, profile, loopback and scrubs inherited injection',()=>{
 const s=launchSpec(config,Buffer.alloc(32),8280,{PATH:'/bin',SPRING_CONFIG_LOCATION:'evil',JAVA_TOOL_OPTIONS:'evil',LD_PRELOAD:'evil'});
 assert.equal(s.options.shell,false); assert.equal(s.options.env.PATH,'/bin'); assert.equal(s.options.env.JAVA_TOOL_OPTIONS,undefined); assert.equal(s.options.env.LD_PRELOAD,undefined); assert.ok(s.args.includes('--server.address=127.0.0.1'));
 assert.deepEqual(candidatePorts(8289),[8289,8280,8281,8282,8283,8284,8285,8286,8287,8288]); assert.throws(()=>launchSpec(config,Buffer.alloc(32),9000));
});
test('only exit 98 retries and exclusive lock spans children',async()=>{
 let starts=0,releases=0; const supervisor=new DacSupervisor({acquireLock:async()=>()=>releases++,spawn:()=>{const c=new EventEmitter();c.pid=++starts;c.kill=()=>assert.fail('must not kill exited process');setImmediate(()=>c.emit('exit',starts===1?98:1));return c;},probe:async()=>null,pollMs:1,timeoutMs:50});
 await assert.rejects(supervisor.start(config,Buffer.alloc(32)),/failed/); assert.equal(starts,2);assert.equal(releases,1);
});
test('timeout stops owned child only; retains lock until exit and rejects concurrent start',async()=>{
 let release=0,kills=0;const child=new EventEmitter();child.pid=42;child.kill=signal=>{assert.equal(signal,'SIGTERM');kills++;};
 const supervisor=new DacSupervisor({acquireLock:async()=>()=>release++,spawn:()=>child,probe:async()=>new Promise(()=>{}),timeoutMs:10,pollMs:1});
 await assert.rejects(supervisor.start(config,Buffer.alloc(32)),/timed out/);assert.equal(kills,1);assert.equal(release,0);
 await assert.rejects(supervisor.start(config,Buffer.alloc(32)),/already/);child.emit('exit',0);await new Promise(setImmediate);assert.equal(release,1);
});
test('READY requires signature bound to owned PID and retains lock through lifetime',async()=>{
 const crypto=require('node:crypto');const pair=crypto.generateKeyPairSync('ed25519');const publicKey=pair.publicKey.export({format:'der',type:'spki'}).subarray(-32).toString('hex');
 let releases=0;const child=new EventEmitter();child.pid=123;child.kill=()=>child.emit('exit',0);
 const supervisor=new DacSupervisor({acquireLock:async()=>()=>releases++,spawn:()=>child,probe:async({nonce,port})=>{
   const status={product:'sdp-dac',protocolVersion:3,status:'READY',port,publicKey,instanceId:'instance',serverUrl:config.serverUrl,webOrigin:config.webOrigin};
   status.signature=crypto.sign(null,Buffer.from(['sdp-dac-discovery-v1',nonce,port,'instance',3,'READY',config.serverUrl,config.webOrigin].join('\n')),pair.privateKey).toString('base64');
   return {pid:123,instanceId:'instance',status};
 }});
 assert.equal((await supervisor.start({...config,publicKey},Buffer.alloc(32))).status,'READY');assert.equal(releases,0);supervisor.stop();await new Promise(setImmediate);assert.equal(releases,1);
});
test('post-READY process errors and failed termination retain exclusive ownership until exit',async()=>{
 const crypto=require('node:crypto');const pair=crypto.generateKeyPairSync('ed25519');const publicKey=pair.publicKey.export({format:'der',type:'spki'}).subarray(-32).toString('hex');
 let releases=0;const child=new EventEmitter();child.pid=456;child.kill=()=>{child.emit('error',new Error('EPERM'));return false;};
 const supervisor=new DacSupervisor({acquireLock:async()=>()=>releases++,spawn:()=>child,probe:async({nonce,port})=>{
   const status={product:'sdp-dac',protocolVersion:3,status:'READY',port,publicKey,instanceId:'instance',serverUrl:config.serverUrl,webOrigin:config.webOrigin};
   status.signature=crypto.sign(null,Buffer.from(['sdp-dac-discovery-v1',nonce,port,'instance',3,'READY',config.serverUrl,config.webOrigin].join('\n')),pair.privateKey).toString('base64');
   return {pid:456,instanceId:'instance',status};
 }});
 await supervisor.start({...config,publicKey},Buffer.alloc(32));child.emit('error',new Error('IPC failure'));supervisor.stop();supervisor.stop();await new Promise(setImmediate);
 assert.equal(releases,0);assert.equal(supervisor.child,child);await assert.rejects(supervisor.start({...config,publicKey},Buffer.alloc(32)),/already/);
 child.emit('exit',0);await new Promise(setImmediate);assert.equal(releases,1);assert.equal(supervisor.child,null);
});
test('spawn throw removes secret environment and releases nonrunning lock',async()=>{
 let captured,releases=0;const supervisor=new DacSupervisor({acquireLock:async()=>()=>releases++,spawn:(cmd,args,options)=>{captured=options.env;throw new Error('spawn refused');},probe:async()=>null});
 await assert.rejects(supervisor.start(config,Buffer.alloc(32)),/spawn refused/);assert.equal(captured.DEVICE_OPS_CREDENTIAL_MASTER_KEY,undefined);assert.equal(releases,1);
});
test('spawn error without PID releases lock without retrying',async()=>{
 let releases=0,starts=0;const supervisor=new DacSupervisor({acquireLock:async()=>()=>releases++,spawn:()=>{starts++;const child=new EventEmitter();setImmediate(()=>child.emit('error',new Error('ENOENT')));return child;},probe:async()=>null,pollMs:1});
 await assert.rejects(supervisor.start(config,Buffer.alloc(32)),/failed/);assert.equal(releases,1);assert.equal(starts,1);
});
test('all inherited SERVER_ bindings are excluded except controlled SERVER_URL',()=>{
 const spec=launchSpec(config,Buffer.alloc(32),8280,{SERVER_SSL_ENABLED:'true',server_servlet_context_path:'/evil',SERVER_URL:'https://evil',SERVER_TOMCAT_ACCESSLOG_ENABLED:'true'});
 assert.deepEqual(Object.keys(spec.options.env).filter(k=>/^server_/i.test(k)),['SERVER_URL']);assert.equal(spec.options.env.SERVER_URL,config.serverUrl);
});
