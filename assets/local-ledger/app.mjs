import {parseText,parseTransactions,localTime} from './parser.mjs';
const $=id=>document.getElementById(id), native=window.Ledger;
let state={entries:[],summary:{}},filter='all',editing={},busy=false,engineReady=false,serial=0,currentId,ocrQueue=[];
const money=cents=>(Number(cents??0)/100).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});
function possibleDuplicate(e){const normalize=s=>String(s||'').replace(/^(?:财付通|美团支付|微信支付|支付宝)[-一:：]?/,'').replace(/[\s（）()]/g,'');const merchant=normalize(e.merchant);return merchant&&state.entries.some(other=>other.id!==e.id&&other.cents===e.cents&&other.direction===e.direction&&Math.abs(new Date(other.happened)-new Date(e.happened))<=120000&&(normalize(other.merchant).includes(merchant)||merchant.includes(normalize(other.merchant)))&&normalize(other.merchant));}
function notice(message){$('notice').textContent=message;$('notice').hidden=false;}
window.notice=notice;window.nativeError=message=>{busy=false;$('imageButton').disabled=!engineReady;notice(message);};
function call(name,...args){if(!native)throw Error('此页面需要在本地记账安装包中打开');const s=native[name](...args);if(typeof s==='string'){const result=JSON.parse(s);if(result.error)throw Error(result.error);return result;}}
function render(){
 $('month').textContent=new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'long'});
 $('expense').textContent='¥'+money(state.summary.expense);$('income').textContent='¥'+money(state.summary.income);
 $('count').textContent=`共 ${state.summary.total??0} 笔 · 待确认 ${state.summary.pending??0} 笔`;
 $('wechat').checked=state.wechat;$('alipay').checked=state.alipay;
 $('bank').checked=state.bank;$('sms').checked=state.sms;$('bankPackages').value=state.bankPackages||'';
 $('smsPermission').textContent=state.smsPermission?'接收短信权限已开启':'接收短信权限未开启';
 $('accessibilityPermission').textContent=state.accessibility?'微信支付卡片读取已开启':'微信支付卡片读取未开启';
 $('permission').textContent=state.notificationAccess?'通知使用权已开启':'通知使用权未开启';
 const box=$('entries');box.replaceChildren();
 const rows=state.entries.filter(e=>filter==='all'||e.status===filter);
 if(!rows.length){const empty=document.createElement('div');empty.className='empty';empty.textContent=filter==='pending'?'暂无待确认记录':'从一张账单截图开始，或手动记一笔。';box.append(empty);}
 for(const e of rows){const row=document.createElement('button');row.className='entry';const left=document.createElement('div'),name=document.createElement('div'),meta=document.createElement('div'),right=document.createElement('div');name.className='name';name.textContent=e.merchant||'未填写商户';if(e.status==='pending'){const badge=document.createElement('span');badge.className='badge';badge.textContent='待确认';name.append(badge);}meta.className='meta';meta.textContent=`${e.happened.replace('T',' ')} · ${e.category} · ${e.source}`;right.className='money';right.textContent=(e.direction==='income'?'+':'−')+'¥'+money(e.cents);left.append(name,meta);row.append(left,right);row.onclick=()=>openEditor({...e,amount:(e.cents/100).toFixed(2)});box.append(row);}
}
function refresh(){try{state=call('snapshot');render();}catch(e){notice(e.message);}}
window.refresh=refresh;
function openEditor(e){editing={...e};for(const key of ['direction','amount','merchant','happened','raw','subcategory','note'])$(key).value=e[key]??'';
 $('duplicateHint').textContent=possibleDuplicate(e)?'账本中存在金额、商户和时间接近的记录，可能来自另一支付渠道，请核对后再入账。':'';
 const categories=Array.from($('category').options).map(x=>x.value);if(e.category&&!categories.includes(e.category)){const opt=document.createElement('option');opt.textContent=e.category;$('category').append(opt);}$('category').value=e.category||'待分类';
 $('source').textContent='来源：'+(e.source||'手动');$('formError').textContent='';$('deleteButton').hidden=!e.id;$('candidates').replaceChildren();
 if(e.amounts?.length>1){const label=document.createElement('span');label.textContent='检测到多个金额，请选择：';label.className='hint';$('candidates').append(label);for(const a of e.amounts){const b=document.createElement('button');b.type='button';b.textContent='¥'+a;b.onclick=()=>{$('amount').value=a;};$('candidates').append(b);}}
 if(!$('editor').open)$('editor').showModal();
}
$('manualButton').onclick=()=>openEditor({direction:'expense',happened:localTime(),source:'手动'});
function showOcrBatch(){const box=$('ocrItems');box.replaceChildren();for(const e of ocrQueue){const b=document.createElement('button');b.className='entry';b.textContent=`${e.happened.replace('T',' ')}　${e.merchant||'未识别商户'}　¥${e.amount||'待选择金额'}`;b.onclick=()=>{$('ocrBatch').close();openEditor({...e,_ocr:true});};box.append(b);}$('ocrCount').textContent=`识别到 ${ocrQueue.length} 笔独立交易，请逐笔核对。`;if(!$('ocrBatch').open)$('ocrBatch').showModal();}
$('saveOcrDrafts').onclick=()=>{try{const r=call('saveBatch',JSON.stringify(ocrQueue.map(e=>({...e,status:'pending',source:'截图 OCR',amount:e.amount||'0'}))));ocrQueue=[];$('ocrBatch').close();refresh();notice(`已保存 ${r.count} 笔待确认记录`);}catch(e){notice(e.message);}};
window.receivePdf=async base64=>{try{notice('正在本地读取银行 PDF…');const {bankPdfCsv}=await import('./bank-pdf.mjs');const bytes=Uint8Array.from(atob(base64),c=>c.charCodeAt(0));const csv=await bankPdfCsv(bytes,(p,n)=>notice(`正在本地读取 PDF：${p}/${n} 页`));call('previewBankCsv',csv);}catch(e){notice('PDF 导入失败：'+e.message);}};
$('statementButton').onclick=()=>{try{call('importStatement');notice('请选择微信或支付宝导出的 CSV / XLSX 流水文件');}catch(e){notice(e.message);}};
window.receiveStatementPreview=p=>{
 $('statementSummary').textContent=`${p.provider}流水：有效 ${p.valid} 条，已存在 ${p.duplicates} 条，本次新增 ${p.newCount} 条。`;
 $('statementTotals').textContent=`本次收入 ¥${money(p.income)} · 支出 ¥${money(p.expense)}`;
 $('statementSkipped').replaceChildren();for(const [reason,count] of Object.entries(p.skipped)){const line=document.createElement('div');line.textContent=`跳过 ${count} 条：${reason}`;$('statementSkipped').append(line);}
 $('statementSample').replaceChildren();for(const e of p.sample){const line=document.createElement('p');line.className='meta';line.textContent=`${e.happened.replace('T',' ')}　${e.merchant||'未填写对方'}　${e.direction==='income'?'+':'−'}¥${money(e.cents)}`;$('statementSample').append(line);}
 $('statementError').textContent='';$('confirmStatement').disabled=p.newCount===0;if(!$('statement').open)$('statement').showModal();
};
$('confirmStatement').onclick=()=>{try{const r=call('confirmStatement');$('statement').close();refresh();notice(`已导入 ${r.count} 条，跳过 ${r.duplicates} 条已有记录`);}catch(e){$('statementError').textContent=e.message;}};
$('entryForm').onsubmit=e=>{e.preventDefault();try{const entry={...editing,status:'confirmed',source:editing.source||'手动'};delete entry.cents;delete entry.amounts;for(const key of ['direction','amount','merchant','category','happened','raw','subcategory','note'])entry[key]=$(key).value;call('save',JSON.stringify(entry));$('editor').close();refresh();notice('已保存到账本');if(editing._ocr){ocrQueue=ocrQueue.filter(x=>x.id!==editing.id);if(ocrQueue.length)showOcrBatch();}}catch(err){$('formError').textContent=err.message;}};
$('deleteButton').onclick=()=>{if(confirm('删除这笔账单？此操作无法撤销。')){try{call('remove',editing.id);$('editor').close();refresh();}catch(e){$('formError').textContent=e.message;}}};
document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>{$(b.dataset.close).close();if(b.dataset.close==='editor'&&editing._ocr&&ocrQueue.length)showOcrBatch();});
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('selected',x===b));render();});
$('settingsButton').onclick=()=>{refresh();$('settings').showModal();};
for(const name of ['wechat','alipay','bank','sms'])$(name).onchange=()=>{try{call('setSwitch',name,$(name).checked);refresh();}catch(e){notice(e.message);}};
$('smsPermissionButton').onclick=()=>call('smsPermission');
$('accessibilityButton').onclick=()=>call('accessibilitySettings');
$('saveBankPackages').onclick=()=>{try{call('setBankPackages',$('bankPackages').value);notice('银行包名已保存');refresh();}catch(e){notice(e.message);}};
$('permissionButton').onclick=()=>call('notificationSettings');$('exportButton').onclick=()=>call('exportData');$('importButton').onclick=()=>call('importData');
$('exportMi').onclick=()=>call('exportMi');
window.imageCancelled=()=>{busy=false;$('imageButton').disabled=!engineReady;};
$('imageButton').disabled=true;
const worker=new Worker(new URL('./ocr-worker.mjs',import.meta.url),{type:'module'});
worker.onmessage=async ({data})=>{
 if(data.type==='ready'){engineReady=true;$('engine').textContent='本地 OCR 已就绪 · 截图不上传';$('imageButton').disabled=busy;return;}
 if(data.type==='fatal'){window.nativeError('本地 OCR 初始化失败：'+data.error+'。可继续手动记账。');$('engine').textContent='OCR 不可用';return;}
 if(data.id!==currentId)return;busy=false;$('imageButton').disabled=!engineReady;
 if(data.error){notice('识别失败：'+data.error);return;}
 const raw=data.result.lines.map(x=>x.text).join('\n');$('engine').textContent=`本地 OCR 已就绪 · 本次识别 ${data.result.lines.length} 行`;
 if(!raw.trim()){notice('未识别到文字，请选择清晰的账单截图。');return;}
 const entries=parseTransactions(data.result.lines);if(!entries.length){notice('未找到独立交易金额；日报、统计、余额和可用额度不会作为账单。可手动记账。');return;}
 for(const e of entries){const bytes=new TextEncoder().encode(e.raw);const digest=await crypto.subtle.digest('SHA-256',bytes);e.id='ocr-'+Array.from(new Uint8Array(digest),n=>n.toString(16).padStart(2,'0')).join('');e.source='截图 OCR';}
 if(entries.length===1)openEditor(entries[0]);else{ocrQueue=entries;showOcrBatch();}
};
worker.onerror=e=>{engineReady=false;window.nativeError('本地 OCR 无法运行，请检查系统 WebView 是否支持 WebAssembly SIMD。手动记账仍可用。');$('engine').textContent='OCR 不可用';};
window.receiveImage=base64=>{try{busy=true;$('imageButton').disabled=true;$('engine').textContent='正在手机上识别，请稍候…';const str=atob(base64),bytes=Uint8Array.from(str,c=>c.charCodeAt(0));currentId=++serial;worker.postMessage({id:currentId,bytes},[bytes.buffer]);}catch(e){window.nativeError(e.message);}};
$('imageButton').onclick=()=>{try{busy=true;$('imageButton').disabled=true;call('pickImage');}catch(e){window.nativeError(e.message);}};
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});
refresh();
