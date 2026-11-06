'use strict';
const test=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs/promises');const os=require('node:os');const path=require('node:path');const {EventEmitter}=require('node:events');const {PassThrough}=require('node:stream');
const {createReadinessProbe,parseMetadata}=require('../src/dac/probe.cjs');
const publicKey='a'.repeat(64),instanceId='12345678-1234-4123-8123-123456789abc',nonce='b'.repeat(64);
const metadata=`port=8280\npid=123\npublicKey=${publicKey}\ninstanceId=${instanceId}\n`;
const status={product:'sdp-dac',protocolVersion:3,status:'READY',serverUrl:'https://server',webOrigin:'https://web',port:8280,publicKey,instanceId,signature:Buffer.alloc(64).toString('base64')};
async function fixture(t){const dir=await fs.mkdtemp(path.join(os.tmpdir(),'dac-probe-'));await fs.chmod(dir,0o700);const file=path.join(dir,'endpoint.properties');await fs.writeFile(file,metadata,{mode:0o600});t.after(()=>fs.rm(dir,{recursive:true,force:true}));return {runtimeFile:file,protectedDirectory:dir};}
function fakeRequest({code=200,body=JSON.stringify(status),headers={'content-type':'application/json'},hang=false,onOptions=()=>{}}={}){
 return (options,callback)=>{onOptions(options);const req=new EventEmitter();req.destroy=()=>{req.destroyed=true;};req.end=()=>{if(hang)return;setImmediate(()=>{if(req.destroyed)return;const res=new PassThrough();res.statusCode=code;res.headers=headers;callback(res);if(!res.destroyed)res.end(body);});};return req;};
}
const input={child:{pid:123},port:8280,nonce};
test('reads exact baseline metadata and uses only fixed loopback status request',async t=>{const config=await fixture(t);let called=0;const probe=createReadinessProbe({...config,request:fakeRequest({onOptions:o=>{called++;assert.equal(o.hostname,'127.0.0.1');assert.equal(o.path,`/api/v1/desktop/status?nonce=${nonce}`);assert.equal(o.port,8280);assert.equal(o.agent,false);}})});assert.deepEqual(await probe(input),{pid:123,instanceId,status});assert.equal(called,1);});
test('rejects malformed metadata, duplicate properties and invalid UUID',()=>{for(const value of [metadata+'x=1\n',metadata.replace('pid=123','pid=9007199254740993'),metadata.replace('4123','1123'),metadata.replace('8280','9000'),metadata+'\n',metadata.replace('pid=123','pid=0123')])assert.throws(()=>parseMetadata(value));});
test('refuses stale PID/port and symlink or writable metadata before HTTP',async t=>{const config=await fixture(t);let calls=0;const probe=createReadinessProbe({...config,request:()=>{calls++;throw Error('unexpected network');}});
 await assert.rejects(probe({...input,child:{pid:124}}),/Stale/);await assert.rejects(probe({...input,port:8281}),/Stale/);
 await fs.chmod(config.runtimeFile,0o666);await assert.rejects(probe(input),/Untrusted/);await fs.chmod(config.runtimeFile,0o600);
 const other=path.join(config.protectedDirectory,'other');await fs.rename(config.runtimeFile,other);await fs.symlink(other,config.runtimeFile);await assert.rejects(probe(input));assert.equal(calls,0);
});
test('refuses nonprivate directory and oversized runtime metadata',async t=>{const config=await fixture(t);const probe=createReadinessProbe({...config,request:()=>assert.fail('must not request')});await fs.chmod(config.protectedDirectory,0o755);await assert.rejects(probe(input),/private/);await fs.chmod(config.protectedDirectory,0o700);await fs.writeFile(config.runtimeFile,'x'.repeat(4097));await assert.rejects(probe(input),/Untrusted/);});
test('rejects redirects, oversized responses, mismatched identity and unexpected fields',async t=>{const config=await fixture(t);for(const opts of [{code:302},{body:'x'.repeat(8193)},{body:JSON.stringify({...status,instanceId:'12345678-1234-4123-8123-000000000000'})},{body:JSON.stringify({...status,extra:true})},{body:'[]'},{headers:{'content-type':'text/html'}},{headers:{'content-type':'application/json','content-length':'99999'}}]){await assert.rejects(createReadinessProbe({...config,request:fakeRequest(opts)})(input));}});
test('honors pre-abort, in-flight abort and hard timeout',async t=>{const config=await fixture(t);const early=new AbortController();early.abort();await assert.rejects(createReadinessProbe({...config,request:()=>assert.fail('network')})({...input,signal:early.signal}),{name:'AbortError'});
 const controller=new AbortController();const probe=createReadinessProbe({...config,request:fakeRequest({hang:true,onOptions:()=>setImmediate(()=>controller.abort())})});await assert.rejects(probe({...input,signal:controller.signal}),{name:'AbortError'});
 await assert.rejects(createReadinessProbe({...config,timeoutMs:5,request:fakeRequest({hang:true})})(input),/timed out/);
});
