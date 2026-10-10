import {getDocument,GlobalWorkerOptions} from './pdf.min.mjs';
GlobalWorkerOptions.workerSrc=new URL('./pdf.worker.min.mjs',import.meta.url).href;
const csvCell=v=>'"'+String(v??'').replaceAll('"','""')+'"';
export async function bankPdfCsv(bytes,onProgress=()=>{}){
 const task=getDocument({data:bytes,useSystemFonts:true,disableFontFace:true,isEvalSupported:false});
 task.onPassword=(update,reason)=>{const password=prompt(reason===2?'PDF 密码错误，请重新输入':'请输入银行流水 PDF 的打开密码');if(password===null){task.destroy();return;}update(password);};
 const doc=await task.promise;if(doc.numPages>100){await task.destroy();throw Error('PDF 超过 100 页，请分段导出');}
 const records=[];let account='',bank='';
 try{for(let pageNo=1;pageNo<=doc.numPages;pageNo++){
  onProgress(pageNo,doc.numPages);const page=await doc.getPage(pageNo),content=await page.getTextContent();
  const items=content.items.filter(x=>typeof x.str==='string'&&x.str.trim()).map(x=>({text:x.str,x:x.transform[4],y:x.transform[5]})).sort((a,b)=>b.y-a.y||a.x-b.x);
  const groups=[];for(const item of items){let line=groups.find(g=>Math.abs(g.y-item.y)<2.5);if(!line){line={y:item.y,items:[]};groups.push(line);}line.items.push(item);}groups.sort((a,b)=>b.y-a.y);for(const g of groups)g.items.sort((a,b)=>a.x-b.x);
  const text=groups.map(g=>g.items.map(x=>x.text).join(' ')).join('\n');if(pageNo===1){if(!text.includes('招商银行交易流水'))throw Error('当前 PDF 模板支持招商银行“交易流水”。其他银行请导出 CSV / XLSX；扫描件可用截图 OCR。');bank='招商银行';account=text.match(/账号[：:]\s*([^\s]+)/)?.[1]??'';}
  const header=groups.find(g=>g.items.map(x=>x.text).join('').includes('记账日期')&&g.items.map(x=>x.text).join('').includes('交易金额'));
  if(!header)throw Error(`第 ${pageNo} 页未找到流水表头，已停止导入`);
  const anchor=label=>header.items.find(x=>x.text.replace(/\s/g,'').includes(label))?.x;
  const amountX=anchor('交易金额'),balanceX=anchor('联机余额'),summaryX=anchor('交易摘要'),partyX=anchor('对手信息');
  if([amountX,balanceX,summaryX,partyX].some(x=>x===undefined))throw Error(`第 ${pageNo} 页表格列无法定位`);
  const cut1=(amountX+balanceX)/2,cut2=(balanceX+summaryX)/2,cut3=(summaryX+partyX)/2;
  let current;
  for(const g of groups.filter(g=>g.y<header.y-12)){
   const dateItem=g.items.find(x=>/^20\d{2}-\d{2}-\d{2}$/.test(x.text.trim()));
   if(dateItem){const currency=g.items.find(x=>/^(CNY|RMB|人民币|USD|HKD|EUR)$/.test(x.text.trim()));const numeric=g.items.filter(x=>/^[+−-]?[\d,]+\.\d{2}$/.test(x.text.trim()));const amount=numeric.find(x=>x.x<cut1&&x.x>dateItem.x+65);const balance=numeric.find(x=>x.x>=cut1&&x.x<cut2);
    if(!currency||!amount||!balance)throw Error(`第 ${pageNo} 页存在无法解析的金额行，已停止导入`);
    current={date:dateItem.text.trim(),currency:currency.text.trim(),amount:amount.text.trim().replace('−','-'),summary:g.items.filter(x=>x.x>=cut2&&x.x<cut3).map(x=>x.text).join(''),party:g.items.filter(x=>x.x>=cut3).map(x=>x.text).join('')};records.push(current);
   }else if(current){const continuation=g.items.filter(x=>x.x>=cut3&&g.y>28).map(x=>x.text).join('');if(continuation&&!/^\d+\/\d+$/.test(continuation))current.party+=continuation;}
  }
  page.cleanup();
 }}finally{await task.destroy();}
 if(!records.length)throw Error('没有可读取的交易行；扫描型 PDF 请先使用截图 OCR。');
 const rows=[['记账日期','币种','交易金额','交易摘要','对手信息','银行来源','账户标识'],...records.map(r=>[r.date,r.currency,r.amount,r.summary,r.party,bank,account])];return rows.map(row=>row.map(csvCell).join(',')).join('\n');
}
