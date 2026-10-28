import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import worker from '../src/index.js';

class D1 {
  constructor() {
    this.db = new DatabaseSync(':memory:');
    for (const name of ['0001_remote_config.sql','0002_admin_key.sql','0003_three_destinations.sql','0004_url_runner.sql']) {
      this.db.exec(readFileSync(new URL('../migrations/'+name, import.meta.url),'utf8'));
    }
  }
  prepare(sql) {
    const statement=this.db.prepare(sql);
    return {params: [], bind(...params){this.params=params;return this;},
      async first(){return statement.get(...this.params) ?? null;},
      async all(){return {results:statement.all(...this.params)};},
      async run(){const result=statement.run(...this.params);return {success:true,meta:{changes:Number(result.changes)}};}};
  }
  async exec() {throw Error('HTTP handler attempted DDL');}
  async batch(statements) {
    this.db.exec('BEGIN IMMEDIATE');
    try {const result=[];for(const statement of statements)result.push(await statement.run());this.db.exec('COMMIT');return result;}
    catch(error){this.db.exec('ROLLBACK');throw error;}
  }
}
const headers={'Content-Type':'application/json',Authorization:'Bearer test-admin-token'};
function payload(n=6){return {urls:Array.from({length:n},(_,i)=>`https://lite.tiktok.com/t/Case-${i}/?x=wow!?`),individualSeconds:[1,2,3,4,5],commonSeconds:7,schemaVersion:3,loopMode:'LOOP',waitFromForeground:false,startIndex:1};}
function put(env,body,h=headers){return worker.fetch(new Request('https://worker.example/v1/url-runner',{method:'PUT',headers:h,body:JSON.stringify(body)}),env);}

test('real SQLite: explicit migrations, GET/PUT, exact URLs, optimistic lock, history retention, old API unchanged',async()=>{
  const env={DB:new D1(),ADMIN_TOKEN:'test-admin-token'};
  const legacyBefore=await (await worker.fetch(new Request('https://worker.example/v1/config'),env)).json();
  assert.equal((await put(env,payload(120),{})).status,401);
  assert.equal((await put(env,payload(201))).status,400);
  const first=await put(env,{...payload(120),expectedConfigVersion:0});
  assert.equal(first.status,200);
  assert.deepEqual((await first.json()).urls,payload(120).urls);
  assert.equal((await put(env,{...payload(),expectedConfigVersion:0})).status,409);
  for(let version=1;version<=22;version++){
    const response=await put(env,{...payload(),expectedConfigVersion:version});
    assert.equal(response.status,200);
  }
  assert.equal(env.DB.db.prepare('SELECT count(*) AS n FROM url_runner_history').get().n,20);
  const saved=await (await worker.fetch(new Request('https://worker.example/v1/url-runner'),env)).json();
  assert.deepEqual(saved.urls,payload().urls);
  assert.equal(saved.loopMode,'LOOP');assert.equal(saved.waitFromForeground,false);assert.equal(saved.startIndex,1);
  assert.deepEqual(await (await worker.fetch(new Request('https://worker.example/v1/config'),env)).json(),legacyBefore);
  env.DB.db.close();
});
