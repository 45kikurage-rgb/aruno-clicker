import assert from 'node:assert/strict';
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {RELEASE,KEY} from './code55.js';
const account=process.env.CLOUDFLARE_ACCOUNT_ID,worker='aruno-clicker-download';
assert.equal(account,'02100d2235d6f7d7f893b4217b58b902');assert(process.env.CLOUDFLARE_API_TOKEN);
const root='https://api.cloudflare.com/client/v4/accounts/'+account+'/workers/scripts/'+worker;
const hash=b=>createHash('sha256').update(b).digest('hex');
async function api(kind,init={}){const r=await fetch(root+(kind?'/'+kind:''),{...init,headers:{Authorization:'Bearer '+process.env.CLOUDFLARE_API_TOKEN,...init.headers},signal:AbortSignal.timeout(30000)});if(!r.ok)throw Error('Download Worker API '+kind+' blocked: HTTP '+r.status);return r;}
async function json(kind){const j=await (await api(kind)).json();if(!j.success)throw Error('Cloudflare '+kind+' rejected');return j.result;}
const before=await Promise.all(['settings','schedules','subdomain'].map(json)),s=before[0];
assert.equal(s.bindings.length,1);assert.equal(s.bindings[0].type,'r2_bucket');assert.equal(s.bindings[0].name,'APK_BUCKET');assert.equal(s.bindings[0].bucket_name,'aruno-clicker-download');
assert.equal(s.compatibility_date,'2026-09-25');assert.deepEqual(s.compatibility_flags,[]);assert.deepEqual(s.tail_consumers,[]);assert.deepEqual(s.placement,{});assert.equal(s.logpush,false);assert.equal(s.usage_model,'standard');
assert(Object.keys(s).every(k=>['bindings','compatibility_date','compatibility_flags','tail_consumers','placement','logpush','usage_model','tags','annotations'].includes(k)),'Unknown live setting; refusing to discard it');
const old=await api(''), bytes=Buffer.from(await old.arrayBuffer());
const form=await new Response(bytes,{headers:{'content-type':old.headers.get('content-type')}}).formData();const names=[...form.keys()];assert.deepEqual(names,['worker-code53.js']);
const oldSource=form.get(names[0]),source=typeof oldSource==='string'?Buffer.from(oldSource):Buffer.from(await oldSource.arrayBuffer());
assert.equal(hash(source),'d00c49989de91e3d7275f963469e5f5b13c22d52925eae5c1ee0d6ba2323baf1','Live source moved; inspect before deploying');
assert.equal(hash(readFileSync(new URL('./live-code53.js',import.meta.url))),hash(source));
mkdirSync('code55-download-proof',{recursive:true});writeFileSync('code55-download-proof/worker-before.bin',bytes);writeFileSync('code55-download-proof/settings-before.json',JSON.stringify(before));
async function publicRead(path,method='GET'){const r=await fetch('https://download.aruno-id.com'+path,{method,signal:AbortSignal.timeout(60000)});if(!r.ok)throw Error('Distribution HTTP '+path+' = '+r.status);return r;}
const latest=Buffer.from(await (await publicRead('/arunomatic/latest.json')).arrayBuffer());assert.equal(JSON.parse(latest).version_code,52);
writeFileSync('code55-download-proof/latest-before.json',latest);
const legacyPaths=['/','/arunomatic/code53','/apk/arunomatic-code53.apk','/apk/arunomatic-code52.apk','/apk/aruno-clicker.apk','/apk/aruno-clicker-ver-s.apk'];
const legacy=[];for(const path of legacyPaths){const r=await publicRead(path,'HEAD');legacy.push({path,status:r.status,type:r.headers.get('content-type'),size:r.headers.get('content-length')});}
const metadata={main_module:'code55.js',bindings:s.bindings,compatibility_date:s.compatibility_date,compatibility_flags:s.compatibility_flags,usage_model:s.usage_model,tags:s.tags,tail_consumers:s.tail_consumers,logpush:s.logpush};
const upload=new FormData();upload.append('metadata',new Blob([JSON.stringify(metadata)],{type:'application/json'}));for(const name of ['code55.js','live-code53.js'])upload.append(name,new Blob([readFileSync(new URL('./'+name,import.meta.url))],{type:'application/javascript+module'}),name);
const result=await (await api('',{method:'PUT',body:upload})).json();if(!result.success)throw Error('Worker upload rejected');
const after=await Promise.all(['settings','schedules','subdomain'].map(json));
function settings(x){const {annotations,...other}=x;return other;}
assert.deepEqual(settings(after[0]),settings(before[0]));assert.deepEqual(after[1],before[1]);assert.deepEqual(after[2],before[2]);
// Allow propagation without ever re-uploading or replacing the stable manifest.
let manifest;for(let i=0;i<10;i++){try{manifest=await (await publicRead('/arunomatic/test.json')).json();if(manifest.version_code===55)break;}catch{}await new Promise(r=>setTimeout(r,2000));}
assert.deepEqual(manifest,RELEASE);
const body=Buffer.from(await (await publicRead('/apk/arunomatic-code55.apk')).arrayBuffer());assert.equal(body.length,182315);assert.equal(hash(body),RELEASE.sha256);writeFileSync('code55-download-proof/ARUNOMATIC_v0.3.1-safe-task-start-code55.apk',body);
const testAlias=Buffer.from(await (await publicRead('/apk/arunomatic-test.apk')).arrayBuffer());assert.equal(hash(testAlias),RELEASE.sha256);
const head=await publicRead('/apk/arunomatic-code55.apk','HEAD');assert.equal(head.headers.get('content-length'),'182315');assert.equal(head.headers.get('x-apk-sha256'),RELEASE.sha256);
const stable=Buffer.from(await (await publicRead('/arunomatic/latest.json')).arrayBuffer());assert.deepEqual(stable,latest);writeFileSync('code55-download-proof/latest-after.json',stable);
const stableAPK=Buffer.from(await (await publicRead('/apk/arunomatic-code52.apk')).arrayBuffer());assert.equal(hash(stableAPK),JSON.parse(latest).sha256);
for(const prev of legacy){const r=await publicRead(prev.path,'HEAD');assert.deepEqual({path:prev.path,status:r.status,type:r.headers.get('content-type'),size:r.headers.get('content-length')},prev);}
const page=await (await publicRead('/arunomatic')).text();assert(page.includes('id="code55-test"'));assert(page.includes('/apk/arunomatic-code52.apk'));assert(page.includes('/arunomatic/code55'));
const testPage=await (await publicRead('/arunomatic/code55')).text();assert(testPage.includes('/apk/arunomatic-code55.apk'));assert(testPage.includes(RELEASE.sha256));
writeFileSync('code55-download-proof/test.json',JSON.stringify(manifest,null,2));writeFileSync('code55-download-proof/page.html',page);
const proof={version_code:55,source_commit:RELEASE.commit,apk_url:RELEASE.apk_url,bytes:body.length,sha256:hash(body),r2_key:KEY,stable_code:52,stable_unchanged:true,settings_preserved:true,legacy_routes:legacy,physical_test:'NOT_RUN'};writeFileSync('code55-download-proof/http-proof.json',JSON.stringify(proof,null,2));console.log('CODE55_TEST_PUBLIC_HTTP_PASS '+JSON.stringify(proof));
