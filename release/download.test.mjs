import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const facts={sha256:'a'.repeat(64),bytes:3,versionCode:17,package:'jp.aruno.clicker'};
const source=readFileSync(new URL('tiker-download.js',import.meta.url),'utf8')
  .replace("import legacy from './__LIVE_MAIN__';", "const legacy = {fetch:async()=>new Response('existing ARUNOMATIC')};")
  .replaceAll('__RELEASE_FACTS__',JSON.stringify(facts));
const {default:worker,page}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
const env={APK_BUCKET:{get:async()=>({size:3,body:'APK',customMetadata:{sha256:facts.sha256}})}};
test('single-edition mobile download page, HEAD, exact immutable APK and unchanged legacy routes',async()=>{
  const response=await worker.fetch(new Request('https://download.aruno-id.com/'),env);
  assert.equal(response.status,200);
  const html=await response.text();
  assert(html.includes('name="viewport"'));assert(html.includes('Aru-Tiker'));assert(html.includes('code17'));
  assert(!html.includes('href="/apk/aruno-clicker-ver-s.apk"'));assert(!html.includes('href="/apk/aruno-clicker-share'));
  const apk=await worker.fetch(new Request('https://download.aruno-id.com/apk/aru-tiker-code17.apk'),env);
  assert.equal(apk.headers.get('Content-Type'),'application/vnd.android.package-archive');assert.equal(await apk.text(),'APK');
  assert.equal(await (await worker.fetch(new Request('https://download.aruno-id.com/tiker',{method:'HEAD'}),env)).text(),'');
  assert.equal(await (await worker.fetch(new Request('https://download.aruno-id.com/arunomatic'),env)).text(),'existing ARUNOMATIC');
  assert.equal((await worker.fetch(new Request('https://download.aruno-id.com/tiker',{method:'POST'}),env)).status,405);
});
test('incorrect APK metadata never serves unverified bytes',async()=>{
  const bad={APK_BUCKET:{get:async()=>({size:4,body:'BAD!',customMetadata:{sha256:'b'.repeat(64)}})}};
  assert.equal((await worker.fetch(new Request('https://download.aruno-id.com/apk/aruno-clicker.apk'),bad)).status,503);
});
