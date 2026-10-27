import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync,rmSync} from 'node:fs';
import {createHash,randomBytes,createCipheriv,createDecipheriv,publicEncrypt,X509Certificate} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {DatabaseSync} from 'node:sqlite';

const account='02100d2235d6f7d7f893b4217b58b902', database='2a15bb15-cff5-46e5-b8e4-df38846580d1';
const configWorker='aruno-clicker-config', downloadWorker='aruno-clicker-download';
assert.equal(process.env.CLOUDFLARE_ACCOUNT_ID,account);
assert(process.env.CLOUDFLARE_API_TOKEN);
const base=`https://api.cloudflare.com/client/v4/accounts/${account}`;
const sha=value=>createHash('sha256').update(value).digest('hex');
const canonical=value=>JSON.stringify(value,(_,v)=>v && !Array.isArray(v) && typeof v==='object' ? Object.fromEntries(Object.entries(v).sort(([a],[b])=>a.localeCompare(b))) : v);
const proofDir='release-proof';mkdirSync(proofDir,{recursive:true});mkdirSync('.private',{recursive:true,mode:0o700});
const release=JSON.parse(readFileSync(process.env.TIKER_RELEASE_REQUEST || 'requests/tiker-release.json','utf8'));
const facts=release.facts || {};
if(process.argv[2]!=='inspect'){
assert.equal(facts.commit,release.sourceCommit);assert.equal(facts.sha256,release.apkSha256);
assert.equal(facts.package,'jp.aruno.clicker');assert.equal(facts.versionCode,17);
assert.equal(facts.certificateSha256,'14e1d564276b56dfbc95888ce8108aafbdaf4da938f7d5b1ed0fb593f274ca3f');
assert.equal(release.stageProof.sha256,facts.sha256);assert.equal(release.stageProof.privateR2ReadbackVerified,true);
}

async function api(path,init={}){
  const response=await fetch(base+path,{...init,headers:{Authorization:'Bearer '+process.env.CLOUDFLARE_API_TOKEN,...init.headers},signal:AbortSignal.timeout(60000)});
  assert(response.ok,`Cloudflare operation failed: HTTP ${response.status}`);
  return response;
}
async function json(path,init){const body=await(await api(path,init)).json();assert(body.success,'Cloudflare operation rejected');return body.result;}
async function sql(text,params=[]){const result=await json('/d1/database/'+database+'/query',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sql:text,params})});assert(result.every(x=>x.success),'D1 query rejected');return result;}
async function publicRead(path,method='GET'){
  const response=await fetch('https://download.aruno-id.com'+path,{method,redirect:'error',signal:AbortSignal.timeout(60000)});
  assert(response.ok,`Public download failed: ${path} HTTP ${response.status}`);return response;
}
async function workerState(name){
  const prefix='/workers/scripts/'+name;
  const [settings,schedules,subdomain]=await Promise.all(['settings','schedules','subdomain'].map(x=>json(prefix+'/'+x)));
  const response=await api(prefix), bytes=Buffer.from(await response.arrayBuffer());
  const type=response.headers.get('content-type');assert(type?.includes('multipart/form-data'),'Inspect non-module Worker before deployment');
  const form=await new Response(bytes,{headers:{'Content-Type':type}}).formData();
  const modules={};for(const [name,value] of form){modules[name]=Buffer.from(typeof value==='string'?value:await value.arrayBuffer()).toString('base64');}
  const names=Object.keys(modules).sort();let main;
  if(name===downloadWorker){
    assert.deepEqual(names,['code55.js','live-code53.js'],'Shared download modules changed; review before publication');
    for(const filename of names)assert.equal(sha(Buffer.from(modules[filename],'base64')),sha(readFileSync('download-worker/code55/'+filename)),'Shared live source changed');
    main='code55.js';
  }else {assert.equal(names.length,1,'Review multi-module config Worker');main=names[0];}
  return {settings,schedules,subdomain,modules,main};
}
function metadata(settings,main){
  const allowed=new Set(['bindings','compatibility_date','compatibility_flags','tail_consumers','placement','logpush','usage_model','tags','annotations','observability','limits']);
  assert(Object.keys(settings).every(k=>allowed.has(k)),'Unknown live setting: review before uploading');
  const result={main_module:main,bindings:settings.bindings.filter(x=>x.type!=='secret_text'),keep_bindings:['secret_text']};
  for(const key of ['compatibility_date','compatibility_flags','tail_consumers','logpush','usage_model','tags','observability','limits'])if(settings[key]!==undefined)result[key]=settings[key];
  if(settings.placement && Object.keys(settings.placement).length)result.placement=settings.placement;
  return result;
}
async function uploadWorker(name,state,modules,main){
  const form=new FormData();form.append('metadata',new Blob([JSON.stringify(metadata(state.settings,main))],{type:'application/json'}));
  for(const [filename,encoded] of Object.entries(modules))form.append(filename,new Blob([Buffer.from(encoded,'base64')],{type:'application/javascript+module'}),filename);
  return json('/workers/scripts/'+name,{method:'PUT',body:form});
}
function comparable(settings){const {annotations,...value}=settings;return canonical(value);}
async function verifySettings(name,before){
  const prefix='/workers/scripts/'+name;
  const [settings,schedules,subdomain]=await Promise.all(['settings','schedules','subdomain'].map(x=>json(prefix+'/'+x)));
  assert.equal(comparable(settings),comparable(before.settings),'Worker settings changed');
  assert.equal(canonical(schedules),canonical(before.schedules));assert.equal(canonical(subdomain),canonical(before.subdomain));
}
async function legacyRows(){const rows={};for(const [table,order] of [['remote_config','id'],['config_history','config_version'],['admin_settings','id']])rows[table]=(await sql(`SELECT * FROM ${table} ORDER BY ${order}`))[0].results;return rows;}
const preservedPaths=['/arunomatic','/arunomatic/latest.json','/arunomatic/test.json','/arunomatic/code53','/arunomatic/code55','/apk/arunomatic-code52.apk','/apk/arunomatic-code53.apk','/apk/arunomatic-code55.apk'];

if(process.argv[2]==='inspect'){
  const states={};for(const name of [configWorker,downloadWorker]){const state=await workerState(name);metadata(state.settings,'inspection.mjs');states[name]={settingsKeys:Object.keys(state.settings),bindingNames:state.settings.bindings.map(x=>({name:x.name,type:x.type})),modules:Object.fromEntries(Object.entries(state.modules).map(([name,value])=>[name,sha(Buffer.from(value,'base64'))]))};}
  const rows=await legacyRows();
  writeFileSync(proofDir+'/inspect.json',JSON.stringify({workers:states,legacyCounts:Object.fromEntries(Object.entries(rows).map(([name,value])=>[name,value.length])),productionWrites:false},null,2));
  console.log('Central connection inspected fixed Workers and database; no production writes.');
}else if(process.argv[2]==='prepare'){
  const config=await workerState(configWorker), download=await workerState(downloadWorker);
  assert(config.settings.bindings.some(x=>x.type==='d1' && x.name==='DB' && x.id===database));
  assert.deepEqual(download.settings.bindings,[{type:'r2_bucket',name:'APK_BUCKET',bucket_name:'aruno-clicker-download'}]);
  metadata(config.settings,'tiker-config.mjs');metadata(download.settings,'tiker-download.mjs');
  const beforeRows=await legacyRows();
  const columns=(await sql('PRAGMA table_info(remote_config)'))[0].results.map(x=>x.name);
  for(const column of ['name1','url1','name2','url2','share_name','share_url'])assert(columns.includes(column),'Legacy schema incomplete');
  const urlTables=(await sql("SELECT name FROM sqlite_master WHERE name IN ('url_runner_config','url_runner_history')"))[0].results;
  assert.equal(urlTables.length,0,'Runner table already exists: inspect before migrating');
  let exported=await json('/d1/database/'+database+'/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({output_format:'polling'})});
  for(let attempt=0;attempt<30 && exported.status!=='complete';attempt++){
    assert.notEqual(exported.status,'error','D1 export failed');assert(exported.at_bookmark);
    await new Promise(r=>setTimeout(r,1000));
    exported=await json('/d1/database/'+database+'/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({output_format:'polling',current_bookmark:exported.at_bookmark})});
  }
  assert.equal(exported.status,'complete');assert(exported.result?.signed_url);
  const url=new URL(exported.result.signed_url);assert.equal(url.protocol,'https:');
  const exportResponse=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(60000)});assert(exportResponse.ok,'D1 export download failed');
  const exportSql=await exportResponse.text();assert(exportSql.length>0 && exportSql.length<10*1024*1024,'Unexpected export size');
  const restored=new DatabaseSync(':memory:');restored.exec(exportSql);
  for(const [table,rows] of Object.entries(beforeRows)){const order=table==='config_history'?'config_version':'id';assert.equal(canonical(restored.prepare(`SELECT * FROM ${table} ORDER BY ${order}`).all()),canonical(rows),'Export restore data mismatch');}
  restored.close();
  const publicBefore={};for(const path of preservedPaths){const response=await publicRead(path);publicBefore[path]={sha256:sha(Buffer.from(await response.arrayBuffer())),type:response.headers.get('Content-Type')};}
  const state={config,download,beforeRows,exportSql,bookmark:exported.at_bookmark,publicBefore};
  const raw=Buffer.from(JSON.stringify(state));
  writeFileSync('.private/state.json',raw,{mode:0o600});
  const key=randomBytes(32),iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',key,iv);
  const ciphertext=Buffer.concat([cipher.update(raw),cipher.final()]),tag=cipher.getAuthTag();
  const check=createDecipheriv('aes-256-gcm',key,iv);check.setAuthTag(tag);
  assert.deepEqual(Buffer.concat([check.update(ciphertext),check.final()]),raw,'Backup cipher roundtrip failed');
  const certificate=new X509Certificate(readFileSync('signing/aruno-release.crt'));
  assert.equal(certificate.fingerprint256.replaceAll(':','').toLowerCase(),facts.certificateSha256);
  const encryptedKey=publicEncrypt({key:certificate.publicKey,oaepHash:'sha256'},key);
  const envelope={schema:1,algorithm:'RSA-OAEP-SHA256+AES-256-GCM',certificateSha256:facts.certificateSha256,encryptedKey:encryptedKey.toString('base64'),iv:iv.toString('base64'),tag:tag.toString('base64'),ciphertext:ciphertext.toString('base64')};
  const backup=Buffer.from(JSON.stringify(envelope));
  writeFileSync('.private/pre-release.enc.json',backup,{mode:0o600});key.fill(0);
  writeFileSync(proofDir+'/backup-facts.json',JSON.stringify({bookmark:exported.at_bookmark,bytes:backup.length,sha256:sha(backup),exportSha256:sha(exportSql),restoreVerified:true,legacyCounts:Object.fromEntries(Object.entries(beforeRows).map(([name,rows])=>[name,rows.length])),workerHashes:{config:sha(canonical(config.modules)),download:sha(canonical(download.modules))}},null,2));
  writeFileSync('.private/apk-key.txt',`releases/tiker/0.6.0-code17/${facts.sha256}/Aru-Tiker-v0.6.0-code17.apk`);
  console.log('Backup prepared and independently restored to SQLite; production unchanged.');
}else if(process.argv[2]==='apply'){
  const state=JSON.parse(readFileSync('.private/state.json','utf8'));
  // Recheck before first mutation; do not deploy over a concurrent change.
  for(const [name,before] of [[configWorker,state.config],[downloadWorker,state.download]]){
    const current=await workerState(name);assert.equal(canonical(current.modules),canonical(before.modules));assert.equal(comparable(current.settings),comparable(before.settings));
  }
  assert.equal(canonical(await legacyRows()),canonical(state.beforeRows),'Configuration changed since backup');
  const schema=readFileSync('cloudflare-worker/migrations/0004_url_runner.sql','utf8');
  await sql(schema);
  await sql('INSERT OR IGNORE INTO d1_migrations(name) VALUES (?)',['0004_url_runner.sql']);
  let configUploaded=false,downloadUploaded=false;
  try{
    await uploadWorker(configWorker,state.config,{'tiker-config.mjs':readFileSync('cloudflare-worker/src/index.js').toString('base64')},'tiker-config.mjs');configUploaded=true;
    await verifySettings(configWorker,state.config);
    const configResponse=await fetch('https://aruno-clicker-config.45kikurage.workers.dev/v1/url-runner',{signal:AbortSignal.timeout(30000)});assert.equal(configResponse.status,200);
    const runner=await configResponse.json();assert.equal(runner.schemaVersion,3);assert.equal(runner.configVersion,0);assert.deepEqual(runner.urls,[]);
    const unauthorized=await fetch('https://aruno-clicker-config.45kikurage.workers.dev/v1/url-runner',{method:'PUT',headers:{'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(30000)});assert.equal(unauthorized.status,401);
    assert.equal(canonical(await legacyRows()),canonical(state.beforeRows),'Legacy data changed');
    const source=readFileSync('release/tiker-download.js','utf8').replaceAll('__LIVE_MAIN__',state.download.main).replaceAll('__RELEASE_FACTS__',JSON.stringify(facts));
    const modules={...state.download.modules,'tiker-download.mjs':Buffer.from(source).toString('base64')};
    await uploadWorker(downloadWorker,state.download,modules,'tiker-download.mjs');downloadUploaded=true;
    await verifySettings(downloadWorker,state.download);
    for(const path of ['/','/tiker']){const r=await publicRead(path);const text=await r.text();assert(text.includes('code17') && text.includes('/apk/aru-tiker-code17.apk'));assert(!text.includes('href="/apk/aruno-clicker-ver-s.apk"'));}
    for(const path of ['/apk/aru-tiker-code17.apk','/apk/aruno-clicker.apk']){
      const r=await publicRead(path);assert.equal(r.headers.get('Content-Type'),'application/vnd.android.package-archive');const bytes=Buffer.from(await r.arrayBuffer());assert.equal(bytes.length,facts.bytes);assert.equal(sha(bytes),facts.sha256);
    }
    for(const [path,before] of Object.entries(state.publicBefore)){const r=await publicRead(path);assert.equal(sha(Buffer.from(await r.arrayBuffer())),before.sha256,'Shared route changed: '+path);}
    const result={...facts,verifyRun:release.verifyRun,publishedAt:new Date().toISOString(),downloadPage:'https://download.aruno-id.com/tiker',apkUrl:'https://download.aruno-id.com/apk/aru-tiker-code17.apk',configApi:'https://aruno-clicker-config.45kikurage.workers.dev/v1/url-runner',legacyDataPreserved:true,arunomaticRoutesPreserved:true,workerSettingsPreserved:true,dbMigration:'0004_url_runner.sql',productionGetAndUnauthorizedPutVerified:true};
    writeFileSync(proofDir+'/publication.json',JSON.stringify(result,null,2));console.log('ARU_TIKER_PUBLISHED '+JSON.stringify(result));
  }catch(error){
    // Restore code only. The additive empty tables can remain without harming the prior API.
    if(downloadUploaded)await uploadWorker(downloadWorker,state.download,state.download.modules,state.download.main);
    if(configUploaded)await uploadWorker(configWorker,state.config,state.config.modules,state.config.main);
    writeFileSync(proofDir+'/rollback.json',JSON.stringify({configUploaded,downloadUploaded,codeRestored:true,additiveTablesRetained:true}));
    throw error;
  }
}else throw Error('Use prepare or apply');
