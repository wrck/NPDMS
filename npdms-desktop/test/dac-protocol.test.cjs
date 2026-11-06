'use strict';
const test = require('node:test'); const assert = require('node:assert/strict'); const crypto = require('node:crypto');
const { environment, parseConnectUri, nonce, verifyStatus } = require('../src/dac/protocol.cjs');
const { loadMasterKey } = require('../src/dac/key-vault.cjs');
function fixture() {
  const keys = crypto.generateKeyPairSync('ed25519');
  const expected = { publicKey: keys.publicKey.export({format:'der',type:'spki'}).subarray(-32).toString('hex'), nonce: nonce(), port:8280, instanceId:'instance-1', serverUrl:'https://server/api', webOrigin:'https://web' };
  const status = { ...expected, product:'sdp-dac',protocolVersion:3,status:'READY' }; delete status.nonce;
  status.signature = crypto.sign(null, Buffer.from(['sdp-dac-discovery-v1',expected.nonce,8280,'instance-1',3,'READY',expected.serverUrl,expected.webOrigin].join('\n')), keys.privateKey).toString('base64');
  return {expected,status};
}
test('strict connect URI and precision-preserving tenant',()=>{
  const uri=`sdp-dac://connect/${'a'.repeat(32)}?challenge=${'b'.repeat(64)}&tenant=999999999999999999`;
  assert.equal(parseConnectUri(uri).tenantId,'999999999999999999');
  for(const bad of [uri+'\n',uri+'&server=http://evil',uri+'#x',uri.replace('tenant=9','tenant=09'),uri.replace('connect','start')]) assert.throws(()=>parseConnectUri(bad));
});
test('environment exact spelling and rejection',()=>{
  assert.deepEqual(environment('https://server/api/','https://web/'),{serverUrl:'https://server/api',webOrigin:'https://web'});
  for(const u of ['file:///x','https://u:p@server','https://server?x','https://server#x','https://server:0',' https://server','https://server\\evil']) assert.throws(()=>environment(u,'https://web'));
  assert.throws(()=>environment('https://server','https://web/path'));
});
test('signed status verifies every bound field and rejects replay',()=>{
  const {expected,status}=fixture(); assert.equal(verifyStatus(status,expected).status,'READY');
  for(const [field,value] of Object.entries({product:'other',protocolVersion:2,status:'STARTING',port:8281,instanceId:'other',publicKey:'a'.repeat(64),serverUrl:'https://evil',webOrigin:'https://evil',signature:'invalid'})) assert.throws(()=>verifyStatus({...status,[field]:value},expected));
  assert.throws(()=>verifyStatus(status,{...expected,nonce:nonce()}));
  assert.throws(()=>verifyStatus(status,{...expected,publicKey:null}));
});
test('OS vault fail closed, no automatic missing-key regeneration',async()=>{
  await assert.rejects(loadMasterKey(null,'dac'));
  await assert.rejects(loadMasterKey({backend:'basic_text'},'dac'));
  const vault={backend:'linux-os-key-vault',isAvailable:async()=>true,read:async()=>null};
  await assert.rejects(loadMasterKey(vault,'dac'));
  let stored; vault.createIfAbsent=async(id,key)=>{stored=Buffer.from(key);}; vault.read=async()=>stored;
  assert.equal((await loadMasterKey(vault,'dac',{create:true})).length,32);
});
module.exports.fixture=fixture;
