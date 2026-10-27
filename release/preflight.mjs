import assert from 'node:assert/strict';
import {mkdirSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const account='02100d2235d6f7d7f893b4217b58b902';
assert.equal(process.env.CLOUDFLARE_ACCOUNT_ID,account);
assert(process.env.CLOUDFLARE_API_TOKEN,'Existing Cloudflare credential is unavailable');
const base=`https://api.cloudflare.com/client/v4/accounts/${account}`;
const hash=value=>createHash('sha256').update(value).digest('hex');
async function api(path,init={}){
  const r=await fetch(base+path,{...init,headers:{Authorization:'Bearer '+process.env.CLOUDFLARE_API_TOKEN,...init.headers},signal:AbortSignal.timeout(30000)});
  assert(r.ok,`Cloudflare preflight HTTP ${r.status}`);return r;
}
async function json(path,init){const j=await(await api(path,init)).json();assert(j.success,'Cloudflare rejected preflight');return j.result;}
const facts={workers:{},database:{}};
for(const worker of ['aruno-clicker-config','aruno-clicker-download']){
  const settings=await json('/workers/scripts/'+worker+'/settings');
  const response=await api('/workers/scripts/'+worker);
  const bytes=Buffer.from(await response.arrayBuffer());
  const modules={};
  if(response.headers.get('content-type')?.includes('multipart/form-data')){
    const form=await new Response(bytes,{headers:{'Content-Type':response.headers.get('content-type')}}).formData();
    for(const [name,value] of form){modules[name]=hash(typeof value==='string'?value:Buffer.from(await value.arrayBuffer()));}
  }else modules['raw-worker']=hash(bytes);
  facts.workers[worker]={settingsSha256:hash(JSON.stringify(settings)),bindings:settings.bindings.map(({name,type,id,bucket_name})=>({name,type,id,bucket_name})),modules};
}
const query=await json('/d1/database/2a15bb15-cff5-46e5-b8e4-df38846580d1/query',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sql:"SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",params:[]})});
assert(query[0].success);
facts.database.tables=query[0].results.map(x=>x.name);
for(const name of ['remote_config','config_history','admin_settings'])assert(facts.database.tables.includes(name),'Missing legacy config table');
mkdirSync('dist',{recursive:true});writeFileSync('dist/preflight.json',JSON.stringify(facts,null,2));
console.log('Live preflight succeeded; current module hashes recorded, no deployment or DB writes.');
