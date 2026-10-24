const stores=[
  {name:'3V Fejzo',domain:'3vfejzo.al',mark:'3V',status:'Candidate'},
  {name:'Red Store',domain:'redstore.al',mark:'RS',status:'Candidate'},
  {name:'The Smartphone Shop',domain:'smartphoneshop.al',mark:'TS',status:'Candidate'},
  {name:'BSS Buy Smart Store',domain:'bss.com.al',mark:'BSS',status:'Candidate'},
  {name:'Vodafone eShop',domain:'eshop.vodafone.al',mark:'V',status:'Candidate'},
  {name:'GoTech Electronics',domain:'gotech.al',mark:'GT',status:'Candidate'},
  {name:'Phone Shop Albania',domain:'phoneshopalbania.al',mark:'PS',status:'Candidate'},
  {name:'Redline',domain:'redline.com.al',mark:'R',status:'Candidate'},
  {name:'Smart Store',domain:'smartstore.al',mark:'SS',status:'Candidate'},
  {name:'Foleja',domain:'foleja.al',mark:'F',status:'Candidate'},
  {name:'Agorai',domain:'agorai.al',mark:'A',status:'Candidate'},
  {name:'ShopTech',domain:'shoptech.al',mark:'ST',status:'Candidate'},
  {name:'Boost Mobile',domain:'boostmobile.al',mark:'BM',status:'Candidate'},
  {name:'Molla Stores',domain:'mollastores.com',mark:'M',status:'Candidate'},
  {name:'Globe',domain:'globe.al',mark:'G',status:'Candidate'},
  {name:'Neptun',domain:'neptun.al',mark:'N',status:'Candidate'},
  {name:'Celular.al',domain:'celular.al',mark:'C',status:'Candidate'},
  {name:'Shpresa AL',domain:'shpresa.al',mark:'SH',status:'Candidate'},
  {name:'Aza Electronics',domain:'aza-electronics.com',mark:'AZ',status:'Candidate'},
  {name:'American Computers',domain:'ac.com.al',mark:'AC',status:'Candidate'},
  {name:'AlbaGame',domain:'albagame.al',mark:'AG',status:'Candidate'},
  {name:'Nova Electronics',domain:'nova-electronics.al',mark:'N',status:'Candidate'},
  {name:'Ozone',domain:'ozone.al',mark:'O',status:'Candidate'},
  {name:'Tigo Albania',domain:'tigo.al',mark:'T',status:'Candidate'},
  {name:'Xito Shop',domain:'xitoshop.com',mark:'X',status:'Candidate'},
  {name:'PC Store Albania',domain:'shop.pcstore.al',mark:'PC',status:'Candidate'},
  {name:"Geni's Computers",domain:'genispc.al',mark:'GC',status:'Candidate'}
];
const $=s=>document.querySelector(s), fmt=(n,c='ALL')=>new Intl.NumberFormat('sq-AL').format(n)+' '+(c==='ALL'?'L':c), esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));let saved=JSON.parse(localStorage.getItem('gjejma-saved')||'[]'), current='iPhone 18 Pro Max';
async function renderShops(){try{const response=await fetch('/api/shops');const {stores:shops}=await response.json();$('#shopGrid').innerHTML=shops.map(s=>`<div class="shop-item" title="${esc(s.note||'Kontrollohet drejtpërdrejt kur kërkon produkt')}\"><span class="shop-mark">${s.name.split(/\s+/).map(x=>x[0]).join('').slice(0,3).toUpperCase()}</span><span><b>${s.name}</b><small>${s.domain} · ${s.enabled?'kërkim live':'në verifikim'}</small></span></div>`).join('')}catch{$('#shopGrid').innerHTML=stores.map(s=>`<div class="shop-item"><span class="shop-mark">${s.mark}</span><span><b>${s.name}</b><small>${s.domain} · në direktori</small></span></div>`).join('')}}
async function render(){const term=current.toLowerCase().trim();let rows=[],liveData=null;$('#resultCount').textContent=term.length>=2?'Duke kërkuar live…':'Shkruaj të paktën 2 shkronja';try{if(term.length>=2){const response=await fetch('/api/search?q='+encodeURIComponent(current),{cache:'no-store'});if(!response.ok)throw new Error('search');liveData=await response.json();rows=liveData.offers||[];rows=rows.map(o=>({...o,verified:`Kontrolluar ${new Date(o.checkedAt||liveData.checkedAt).toLocaleTimeString('sq-AL')}`}))}}catch{$('#offers').innerHTML='<div class="empty"><b>Nuk u lidhëm me serverin.</b>Sigurohu që Gjejma po ekzekutohet dhe provo përsëri.</div>';return}
  rows=[...rows];const currencyOrder=c=>c==='ALL'?0:c==='EUR'?1:2;if($('#sort').value==='store')rows.sort((a,b)=>currencyOrder(a.currency)-currencyOrder(b.currency)||a.currency.localeCompare(b.currency)||a.store.localeCompare(b.store));else rows.sort((a,b)=>currencyOrder(a.currency)-currencyOrder(b.currency)||a.currency.localeCompare(b.currency)||a.price-b.price);
  $('#resultTitle').childNodes[0].textContent=current+' ';$('#resultCount').textContent=`${rows.length} oferta · ${liveData?.storesWithOffers||0}/${liveData?.storesChecked||0} dyqane u përgjigjën · live ${liveData?.checkedAt?new Date(liveData.checkedAt).toLocaleTimeString('sq-AL'):''}`;
  if(!rows.length){const issues=(liveData?.storeErrors||[]).slice(0,5).map(e=>`${esc(e.name)}: ${esc(e.error)}`).join('<br>');$('#offers').innerHTML=`<div class="empty"><b>Nuk u gjetën oferta live</b>Provo emrin e plotë ose kontrollo më vonë.${issues?`<br><br><small>${issues}</small>`:''}</div>`;$('#bestPrice').textContent='—';$('#storeCount').textContent='0';return}
  const best=rows[0],shops=new Set(rows.map(r=>r.store)).size;$('#bestPrice').textContent=fmt(best.price,best.currency);$('#storeCount').textContent=shops;
  $('#offers').innerHTML=rows.map((r,i)=>`<article class="offer"><div class="store-id"><span class="store-logo">${esc(r.mark||r.store.slice(0,2).toUpperCase())}</span><span><span class="store-name">${esc(r.store)}</span><span class="store-domain">${esc(r.domain||new URL(r.url).hostname)}</span></span></div><div class="price-block"><strong>${fmt(r.price,r.currency)}</strong><small title="${esc(r.title)}">${esc(r.title)}</small></div><div class="availability ${r.availability==='in_stock'?'':'unconfirmed'}"><span class="avail-dot"></span>${({in_stock:'Në stok',out_of_stock:'Jashtë stokut',preorder:'Paraporosi',unknown:'Stoku i panjohur'})[r.availability]||'Stoku i panjohur'}</div><a class="offer-link" href="${esc(r.url)}" target="_blank" rel="noopener">Shiko ofertën <span>↗</span></a><div class="offer-note">${esc(r.verified)} · <b>Konfirmo çmimin dhe stokun te shitësi.</b></div></article>`).join('');checkAlerts(rows);
}
function toast(t){const el=$('#toast');el.textContent=t;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2500)}
$('#searchForm').addEventListener('submit',e=>{e.preventDefault();current=$('#query').value.trim()||'iPhone 18 Pro Max';render();$('#results').scrollIntoView({behavior:'smooth',block:'start'})});$('#sort').addEventListener('change',render);$('#clear').addEventListener('click',()=>{$('#query').value='';$('#query').focus()});document.querySelectorAll('[data-query]').forEach(b=>b.addEventListener('click',()=>{$('#query').value=b.dataset.query;current=b.dataset.query;render()}));
$('#savedTop').addEventListener('click',()=>{const key=current.toLowerCase();if(saved.includes(key)){saved=saved.filter(x=>x!==key);toast('U hoq nga lista e ruajtur.')}else{saved.push(key);toast('Kërkimi u ruajt në këtë pajisje.')}localStorage.setItem('gjejma-saved',JSON.stringify(saved));$('.saved-count').textContent=saved.length});$('.saved-count').textContent=saved.length;
$('#alertBtn').addEventListener('click',()=>{const raw=prompt('Cili është çmimi yt maksimal në lekë?');if(raw===null)return;const target=Number(raw.replace(/[.,\s]/g,''));if(!Number.isFinite(target)||target<=0){toast('Shkruaj një çmim të vlefshëm.');return}const alerts=JSON.parse(localStorage.getItem('gjejma-alerts')||'[]').filter(a=>a.query!==current);alerts.push({query:current,target,lastNotified:0});localStorage.setItem('gjejma-alerts',JSON.stringify(alerts));toast(`Do ta kontrollojmë ${current} kur faqja është e hapur.`);if('Notification'in window&&Notification.permission==='default')Notification.requestPermission()});$('#suggestShop').addEventListener('click',()=>{const subject=encodeURIComponent('Sugjerim dyqani për Gjejma');location.href=`mailto:?subject=${subject}`});
function checkAlerts(rows){const alerts=JSON.parse(localStorage.getItem('gjejma-alerts')||'[]'),now=Date.now();let changed=false;for(const a of alerts){if(a.query.toLowerCase()===current.toLowerCase()&&rows[0]?.price<=a.target&&now-(a.lastNotified||0)>3600000){a.lastNotified=now;changed=true;toast(`${a.query}: çmimi ra në ${fmt(rows[0].price,rows[0].currency)}.`);if('Notification'in window&&Notification.permission==='granted')new Notification('Gjejma: u gjet ofertë',{body:`${a.query} kushton ${fmt(rows[0].price,rows[0].currency)}.`})}}if(changed)localStorage.setItem('gjejma-alerts',JSON.stringify(alerts))}
async function pollAlerts(){const alerts=JSON.parse(localStorage.getItem('gjejma-alerts')||'[]');for(const a of alerts){try{const response=await fetch('/api/search?q='+encodeURIComponent(a.query));if(!response.ok)continue;const data=await response.json(),offers=(data.products||[]).flatMap(p=>p.offers||[]).filter(o=>o.availability!=='out_of_stock'&&o.currency==='ALL').sort((x,y)=>x.price-y.price);const now=Date.now();if(offers[0]?.price<=a.target&&now-(a.lastNotified||0)>3600000){a.lastNotified=now;localStorage.setItem('gjejma-alerts',JSON.stringify(alerts));toast(`${a.query}: oferta ra nën objektivin tënd.`);if('Notification'in window&&Notification.permission==='granted')new Notification('Gjejma: çmimi ra',{body:`${a.query} kushton ${fmt(offers[0].price,offers[0].currency)}.`})}}catch{}}}
renderShops();render();setInterval(pollAlerts,5*60*1000);

