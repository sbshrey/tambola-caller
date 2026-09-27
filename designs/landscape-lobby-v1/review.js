// Deliberately local design interactions; no game API, account or coin mutations.
const stage = document.querySelector('#stage');
const chooser = document.querySelector('#screen');
const called = new Set([3,12,22,44,57,69,72,82,8,13,25,40,58,62,74,85,7,18,23,37,46]);
const marks = new Set([...called].filter(n => n !== 46));
const captions = {
  welcome:'One-screen welcome. Play immediately, or optionally choose your look.',
  lobby:'One quick-play destination. Pick 1–6 tickets; the cost stays beside Play.',
  ready:'Simulated countdown. Computer players stay visibly labelled.',
  game:'Manual dabs, up to two readable tickets, ticket-level Claim and explicit page arrows.',
  game2:'A second ticket page, with marks preserved when you return.',
  claim:'Prize selection stays tied to the ticket you chose. Try Early 5 on ticket 1.',
  results:'Your settled win, then a quick route into the next round.',
  daylight:'Alternate direction: warm daylight, cream tickets, mint and coral.',
};
let screens={},cards=[],defs='',screen='welcome',quantity=3,page=1,claimTicket=1;
let history=[],timer,toastTimer,win={name:'Full house',coins:420},name='Player 07';
const pool=()=>100*(quantity+9); // One example human and two labelled computers, three tickets each.
const house=()=>Math.floor(pool()*(quantity+9<12?.5:.35));
function toast(message){
  const element=document.querySelector('#toast');element.textContent=message;element.style.display='block';
  clearTimeout(toastTimer);toastTimer=setTimeout(()=>element.style.display='none',2500);
}
function amount(selector,value){const element=stage.querySelector(selector);if(element)element.textContent=value}
function updateHand(){
  stage.querySelectorAll('[id^="ticket-"]').forEach(el=>{el.style.display=Number(el.id.slice(7))>quantity?'none':''});
  stage.querySelectorAll('[data-action^="dab-"]').forEach(el=>{
    const n=Number(el.dataset.action.slice(4)),marked=marks.has(n);
    el.querySelector('rect').setAttribute('fill',marked?'#F2B878':'#FFF9EF');
    el.querySelectorAll('circle').forEach(dot=>dot.style.display=marked?'':'none');
  });
  const start=(page-1)*2+1,end=Math.min(quantity,page*2);
  amount('#page-range',start===end?start:`${start}–${end}`);amount('#page-total',`of ${quantity}`);
  for(const [action,disabled] of [['page-up',page===1],['page-down',page*2>=quantity]]){
    const el=stage.querySelector(`[data-action="${action}"]`);if(el){el.style.opacity=disabled?'.35':'1';el.setAttribute('aria-disabled',String(disabled))}
  }
}
function economy(){
  amount('#round-pool',pool().toLocaleString());amount('#prize-count',`${quantity+9<12?6:7} prizes`);
  for(const el of stage.querySelectorAll('#prize-6,#next-house'))el.style.display=quantity+9<12?'none':'';
  stage.querySelectorAll('text').forEach(el=>{
    if(el.textContent==='3 tickets')el.textContent=`${quantity} ticket${quantity===1?'':'s'}`;
    if(el.textContent==='3 tickets in hand')el.textContent=`${quantity} ticket${quantity===1?'':'s'} in hand`;
    if(el.textContent==='120')el.textContent=pool()/10;
    if(el.textContent==='420')el.textContent=house();
    if(el.textContent==='180')el.textContent=pool()*.15;
    if(el.textContent==='120 coins')el.textContent=pool()/10+' coins';
    if(el.textContent==='420 coins')el.textContent=house()+' coins';
  });
  amount('#Wallet text',(screen==='lobby'||screen==='daylight'?1500:screen==='results'?1500-quantity*100+win.coins:1500-quantity*100).toLocaleString()+'  coins');
}
function show(next,record=true){
  clearInterval(timer);if(record&&next!==screen)history.push(screen);screen=next;chooser.value=next;
  let body=screens[next];
  if(next==='game'||next==='game2')body=screens[`play${page}`];
  if(next==='claim')body=screens[`play${page}`]+screens['claim-overlay'];
  stage.innerHTML=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 720">${defs}${body}</svg>`;
  document.querySelector('#caption').textContent=captions[next];economy();
  if(['game','game2','claim'].includes(next))updateHand();
  if(next==='claim')amount('#claim-ticket-label',`TICKET ${String(claimTicket).padStart(2,'0')}`);
  if(next==='results'){
    amount('#won-value','+'+win.coins);amount('#Won-prize text',`${win.name.toUpperCase()} · TICKET ${String(claimTicket).padStart(2,'0')}`);
    amount('[data-action="ready"] text','PLAY AGAIN  ·  '+quantity*100);
  }
  if(['lobby','daylight'].includes(next)){
    choose(quantity);for(const el of stage.querySelectorAll('text'))if(el.textContent==='Hey, Player 07')el.textContent='Hey, '+name;
  }
  if(next==='ready'){
    let seconds=9;timer=setInterval(()=>{seconds--;amount('#countdown-value',String(seconds).padStart(2,'0'));if(seconds===0){page=1;show('game')}},1000);
  }
}
function choose(n){
  quantity=n;amount('#ticket-count-label',`${n} ticket${n===1?'':'s'}`);amount('#ticket-cost-label',`${n*100} coins`);
  amount('[data-action="ready"] text','PLAY  ·  '+n*100);
  stage.querySelector('[data-action="ready"]')?.setAttribute('aria-label',`Play for ${n*100} coins`);
  stage.querySelectorAll('[data-action^="tickets-"]').forEach(el=>{
    const selected=Number(el.dataset.action.slice(8))===n;el.setAttribute('aria-pressed',String(selected));
    el.querySelector('rect').setAttribute('fill',selected?'#FF8665':screen==='daylight'?'#E6DCC6':'#383253');
    el.querySelector('text').setAttribute('fill',selected?'#24192E':screen==='daylight'?'#253E36':'#FFF9EF');
  });
}
function claim(label){
  const card=cards[claimTicket-1],rows=card.map(row=>row.filter(Boolean)),all=rows.flat();
  const eligible=label==='Early 5'?all.filter(n=>marks.has(n)).length>=5:
    label==='Corners'?[rows[0][0],rows[0].at(-1),rows[2][0],rows[2].at(-1)].every(n=>marks.has(n)):
    label==='Full house'?all.every(n=>marks.has(n)):
    rows[{'Top line':0,'Middle line':1,'Bottom line':2}[label]].every(n=>marks.has(n));
  if(!eligible)return toast('Not complete yet');
  win={name:label,coins:label==='Full house'?house():pool()/10};toast('Claim accepted · simulated result');show('results');
}
function act(el){
  const action=el.dataset.action;
  if(action.startsWith('tickets-'))choose(Number(action.slice(8)));
  else if(action.startsWith('dab-')){const n=Number(action.slice(4));if(!called.has(n))return toast('Not called yet');marks.has(n)?marks.delete(n):marks.add(n);updateHand()}
  else if(action==='claim'){claimTicket=Number(el.id.match(/\d+$/)[0]);show('claim')}
  else if(action==='claim-win')claim(el.querySelector('text').textContent);
  else if(action==='page-up'||action==='page-down'){
    const next=page+(action==='page-down'?1:-1);if(next<1||next>Math.ceil(quantity/2))return;page=next;show('game',false);
  }else if(action==='profile')document.querySelector('#profile').style.display='flex';
  else if(action==='players')toast('You · Mira · Computer · Computer');
  else if(action==='settings')toast('Sound, language and reduced motion stay in Settings');
  else if(action==='sound')toast('Sound toggle preview');
  else if(screens[action])show(action);
}
stage.addEventListener('click',event=>{const el=event.target.closest('[data-action]');if(el)act(el)});
stage.addEventListener('keydown',event=>{if(['Enter',' '].includes(event.key)){const el=event.target.closest('[data-action]');if(el){event.preventDefault();act(el)}}});
chooser.onchange=()=>{if(chooser.value==='game2'){quantity=Math.max(quantity,3);page=2}else if(chooser.value==='game')page=1;show(chooser.value)};
document.querySelector('#back').onclick=()=>show(history.pop()||'welcome',false);
document.querySelector('#motion').onclick=event=>{document.body.classList.toggle('reduce');event.target.textContent=document.body.classList.contains('reduce')?'Motion off':'Motion on'};
document.querySelector('#feedback').onclick=()=>{const el=document.querySelector('.notes');el.style.display=el.style.display==='block'?'none':'block'};
document.querySelectorAll('.avatar').forEach(el=>el.onclick=()=>{document.querySelectorAll('.avatar').forEach(x=>x.classList.remove('selected'));el.classList.add('selected')});
document.querySelector('#profile-done').onclick=()=>{name=document.querySelector('#profile input').value.trim()||'Player 07';document.querySelector('#profile').style.display='none';show(screen,false)};
document.querySelector('#profile-skip').onclick=()=>document.querySelector('#profile').style.display='none';
document.querySelector('#save-notes').onclick=()=>{
  const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify({concept:'landscape-v1',screen,notes:document.querySelector('#notes').value},null,2)],{type:'application/json'}));
  a.download='tambola-design-feedback.json';a.click();URL.revokeObjectURL(a.href);
};
Promise.all([fetch('screens.json').then(r=>r.json()),fetch('cards.json').then(r=>r.json()),fetch('frames/01-welcome.svg').then(r=>r.text())]).then(([data,deck,svg])=>{
  screens=data;cards=deck;defs=svg.match(/<defs>[\s\S]*?<\/defs>/)[0];show('welcome',false);
}).catch(()=>stage.textContent='Open this review through its local preview server.');
