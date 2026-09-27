import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const [label, scale='2.0', method, density] = process.argv.slice(2);
assert.match(label,/^[a-z0-9-]+$/); assert.ok(['1.0','2.0'].includes(scale));
if(method) assert.match(method,/^[A-Za-z]+$/);
if(density) assert.equal(density,'540');
const root=process.cwd(), folder=resolve(root,'.test-workspace/round-recovery-'+label);
await mkdir(folder);
const sdk=resolve(process.env.LOCALAPPDATA,'Android/Sdk'), adb=resolve(sdk,'platform-tools/adb.exe');
const serial='emulator-5582', reviewPackage='io.github.sbshrey.tambola.game.uireview';
function run(...args) {
  const result=spawnSync(adb,['-s',serial,...args],{encoding:'utf8',windowsHide:true,timeout:30000});
  assert.equal(result.status,0,`adb ${args.slice(0,3).join(' ')} failed`); return result.stdout.trim();
}
assert.equal(run('emu','avd','name').split(/\r?\n/)[0].trim(),'tambola_full_game_alpha23_api30');
assert.equal(run('shell','getprop','sys.boot_completed'),'1');
function installed(pkg) {
  const path=run('shell','pm','path',pkg).replace(/^package:/,'');
  assert.match(path,/^\/data\/app\/[A-Za-z0-9_+=.~\/-]+\/base\.apk$/);
  return run('shell','sha256sum',path).split(/\s/)[0];
}
const before=Object.fromEntries(['io.github.sbshrey.tambola.game','io.github.sbshrey.tambola.game.beta'].map(pkg=>[pkg,installed(pkg)]));
assert.equal(before['io.github.sbshrey.tambola.game.beta'],'e318900b61708846db7bd6010f3091f420d8e95efdcbff739d287aa6465d1583');
const buildTools=(await readdir(resolve(sdk,'build-tools'))).sort().reverse();
const aapt=resolve(sdk,'build-tools',buildTools[0],'aapt2.exe');
const apks=[['app/build/outputs/apk/debug/app-debug.apk',reviewPackage],['app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',reviewPackage+'.test']];
const hashes={};
for(const [file,pkg] of apks) {
  const path=resolve(root,file), manifest=spawnSync(aapt,['dump','badging',path],{encoding:'utf8',windowsHide:true,timeout:30000});
  assert.equal(manifest.status,0); assert.equal(manifest.stdout.match(/^package: name='([^']+)'/m)?.[1],pkg);
  hashes[pkg]=createHash('sha256').update(await readFile(path)).digest('hex'); run('install','-r',path);
}
const settings=[['system','font_scale'],...['window_animation_scale','transition_animation_scale','animator_duration_scale'].map(name=>['global',name])];
const previous=settings.map(([namespace,name])=>({namespace,name,value:run('shell','settings','get',namespace,name)}));
const originalDensity=run('shell','wm','density');
const oldOverride=originalDensity.match(/Override density: (\d+)/)?.[1];
previous.forEach(({value})=>assert.match(value,/^(null|\d+(?:\.\d+)?)$/));
let output='', exitCode, passed=false;
try {
  if(density) run('shell','wm','density',density);
  previous.forEach(({namespace,name})=>run('shell','settings','put',namespace,name,name==='font_scale'?scale:'0'));
  const className=method==='regressions' ? ['ManualTableTest','ClaimFeedbackUiTest'].map(name=>'io.github.sbshrey.tambola.game.'+name).join(',') : 'io.github.sbshrey.tambola.game.RoundRecoveryUiTest'+(method&&method!=='all'?'#'+method:'');
  const child=spawn(adb,['-s',serial,'shell','am','instrument','-w','-e','tambolaUiReview','true','-e','tambolaRecoveryScale',scale,'-e','tambolaRecoveryLabel',label,'-e','class',className,reviewPackage+'.test/androidx.test.runner.AndroidJUnitRunner'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
  const append=bytes=>{output+=bytes.toString(); process.stdout.write(bytes);};
  child.stdout.on('data',append); child.stderr.on('data',append);
  const timeout=setTimeout(()=>child.kill(),240000);
  try { exitCode=await new Promise((done,reject)=>{child.once('error',reject);child.once('close',done);}); }
  finally {clearTimeout(timeout);}
  passed=exitCode===0 && /OK \(\d+ tests?\)/.test(output) && !/FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output);
} finally {
  await writeFile(resolve(folder,'instrumentation.txt'),output);
  previous.forEach(({namespace,name,value})=>value==='null'?run('shell','settings','delete',namespace,name):run('shell','settings','put',namespace,name,value));
  if(density) run('shell','wm','density',oldOverride||'reset');
  assert.equal(run('shell','wm','density'),originalDensity);
  const restored=previous.map(setting=>({...setting,restored:run('shell','settings','get',setting.namespace,setting.name)}));
  restored.forEach(({name,value,restored})=>assert.ok(restored===value || (name==='font_scale' && value==='null' && Number(restored)===1),`Setting ${name} not restored`));
  const after=Object.fromEntries(Object.keys(before).map(pkg=>[pkg,installed(pkg)])); assert.deepEqual(after,before);
  const names=run('shell','run-as',reviewPackage,'ls','files').split(/\s+/).filter(name=>name.startsWith('recovery-'+label+'-') && /\.(png|txt)$/.test(name));
  for(const name of names) {
    assert.match(name,/^[a-z0-9-]+\.(png|txt)$/);
    const result=spawnSync(adb,['-s',serial,'exec-out','run-as',reviewPackage,'cat','files/'+name],{windowsHide:true,timeout:30000,maxBuffer:12*1024*1024});
    assert.equal(result.status,0); await writeFile(resolve(folder,name),result.stdout);
  }
  await writeFile(resolve(folder,'identity.json'),JSON.stringify({observedAt:new Date().toISOString(),serial,scale,method:method||'all',density: density||'unchanged',originalDensity,densityRestored:true,apks:hashes,installedBefore:before,installedAfter:after,settingsRestored:restored,exitCode,passed,screensAndGeometry:names},null,2)+'\n');
}
process.exitCode=passed?0:1;
