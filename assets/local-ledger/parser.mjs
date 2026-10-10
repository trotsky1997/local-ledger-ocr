// Suggestions only: multiple possible amounts remain unselected for user review.
export function localTime(date = new Date()) {const pad=n=>String(n).padStart(2,'0');return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;}
export function parseText(raw, now = new Date()) {
  const original=raw.replace(/，/g,',').replace(/：/g,':');
  // Remove non-transaction amounts, including values on the line after a label.
  const rawLines=original.split(/\n+/),kept=[];let skipNext=false;
  for(const line of rawLines){if(/可用额度|信用额度|授信额度|剩余额度|余额|本月统计|昨日支出|昨日入账|月度统计|合计支出|合计收入/.test(line)){skipNext=/[:：]\s*$/.test(line)||/^(昨日支出|昨日入账|本月统计|余额|可用额度)$/.test(line.trim());continue;}if(skipNext){skipNext=false;continue;}kept.push(line);}
  const text=kept.join('\n');
  const amounts=[];
  for(const m of text.matchAll(/(?:(?:[¥￥]|人民币|RMB|CNY)\s*(-?\d[\d,]*(?:\.\d{1,2})?)(?![\d.])|(-?\d[\d,]*(?:\.\d{1,2})?)(?![\d.])\s*(?:人民币|RMB|CNY|元))/gi)) {
    const value=Math.abs(Number((m[1]??m[2]).replace(/,/g,'')));
    if(value>0&&value<=999999999.99&&!amounts.includes(value.toFixed(2)))amounts.push(value.toFixed(2));
  }
  // Receipt amount on the next line after a known label.
  for(const m of text.matchAll(/(?:实付|实付款|交易金额|支付金额|付款金额|收款金额|金额合计|合计|实际支付)[\s:¥￥]*(-?\d[\d,]*(?:\.\d{1,2})?)(?![\d.])/g)){
    const n=Math.abs(Number(m[1].replace(/,/g,'')));if(n>0&&n<=999999999.99&&!amounts.includes(n.toFixed(2)))amounts.push(n.toFixed(2));
  }
  let happened=localTime(now);
  const d=text.match(/(20\d{2})[年/.-](\d{1,2})[月/.-](\d{1,2})日?\s*(\d{1,2}):(\d{2})(?::\d{2})?/);
  if(d){const [,y,mo,day,h,mi]=d;const date=new Date(+y,+mo-1,+day,+h,+mi);if(date.getFullYear()===+y&&date.getMonth()===+mo-1&&date.getDate()===+day&&date.getHours()===+h&&date.getMinutes()===+mi)happened=localTime(date);}
  else {const md=text.match(/(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})/);const rel=text.match(/(昨天|昨日|今天|今日)?\s*(\d{1,2}):(\d{2})/);if(md){let year=now.getFullYear();let date=new Date(year,+md[1]-1,+md[2],+md[3],+md[4]);if(date-now>86400000*30)date=new Date(year-1,+md[1]-1,+md[2],+md[3],+md[4]);happened=localTime(date);}else if(rel){const date=new Date(now);if(rel[1]==='昨天'||rel[1]==='昨日')date.setDate(date.getDate()-1);date.setHours(+rel[2],+rel[3],0,0);happened=localTime(date);}}
  let merchant='';const lines=text.split(/\n+/).map(x=>x.trim()).filter(Boolean);
  for(let i=0;i<lines.length;i++){const m=lines[i].match(/^(?:交易商户|商户全称|商户名称|收款方|收款商户|交易对方|对方名称)[\s:]+(.+)$/);if(m){merchant=m[1];if(lines[i+1]?.match(/^[（(].+[）)]$/))merchant+=lines[i+1];break;}if(/^(交易商户|商户全称|商户名称|收款方|收款商户|交易对方|对方名称):?$/.test(lines[i])){merchant=lines[i+1]??'';if(lines[i+2]?.match(/^[（(].+[）)]$/))merchant+=lines[i+2];break;}}
  if(!merchant){const paid=lines.findIndex(x=>/使用.+支付|向.+付款/.test(x));if(paid>0)merchant=lines.slice(0,paid).reverse().find(x=>!/^\d+$|昨天|今天|日报|统计|交易成功|微信支付|交易详情/.test(x))??'';}
  const income=/收款成功|收款到账|收入金额|到账金额|退款成功/.test(text);
  const category=/蜜雪|咖啡|奶茶|肯德基|餐厅|餐饮|麦当劳|瑞幸/.test(merchant)?'餐饮':/超市|便利店|商城|淘宝|京东|购物/.test(merchant)?'购物':/滴滴|地铁|公交|打车|铁路/.test(merchant)?'交通':'待分类';
  return {amount:amounts.length===1?amounts[0]:'',amounts,direction:income?'income':'expense',merchant:merchant.slice(0,200),happened,raw,category};
}
export function visualLines(ocrLines){
 const items=ocrLines.filter(l=>!(l.confidence<.6&&/^[0-9口△]$/.test(l.text.trim()))).map(l=>({text:l.text,x:Math.min(...l.box_points.map(p=>p[0])),y:l.box_points.reduce((s,p)=>s+p[1],0)/4,height:Math.max(...l.box_points.map(p=>p[1]))-Math.min(...l.box_points.map(p=>p[1]))})).sort((a,b)=>a.y-b.y||a.x-b.x),rows=[];
 for(const item of items){const row=rows.find(r=>Math.abs(r.y-item.y)<Math.max(7,Math.min(r.height,item.height)*.35));if(row)row.items.push(item);else rows.push({y:item.y,height:item.height,items:[item]});}
 return rows.sort((a,b)=>a.y-b.y).map(r=>r.items.sort((a,b)=>a.x-b.x).map(x=>x.text).join(' '));
}
export function parseTransactions(input,now=new Date()){
 const lines=Array.isArray(input)?(typeof input[0]==='string'?input:visualLines(input)):String(input).split(/\n+/),text=lines.join('\n'),cards=[];
 const markers=[];for(let i=0;i<lines.length;i++)if(/交易成功提醒|信用卡交易提醒|消费提醒|收款成功提醒/.test(lines[i]))markers.push(i);
 if(markers.length){for(let n=0;n<markers.length;n++){const card=lines.slice(markers[n],markers[n+1]??lines.length).join('\n');const e=parseText(card,now);if(e.amounts.length)cards.push(e);}return cards;}
 const paid=[];for(let i=0;i<lines.length;i++)if(/使用.+支付/.test(lines[i]))paid.push(i);
 if(paid.length){for(let n=0;n<paid.length;n++){const i=paid[n];let start=Math.max(0,i-1);while(start>Math.max(0,i-5)&&!/(?:昨天|今天|\d{1,2}月\d{1,2}日|\d{1,2}:\d{2})/.test(lines[start-1])){if(/日报设置|查看明细|统计|昨日/.test(lines[start-1]))break;start--;}if(start>0&&/昨天|今天|\d{1,2}:\d{2}/.test(lines[start-1]))start--;let end=paid[n+1]??lines.length;const detail=lines.findIndex((l,k)=>k>i&&/交易详情/.test(l));if(detail>=0)end=Math.min(end,detail+1);const e=parseText(lines.slice(start,end).join('\n'),now);if(e.amounts.length)cards.push(e);}return cards;}
 if(/记账日报|本月统计|月度账单/.test(text)&&!/交易金额|支付成功|收款成功/.test(text))return [];
 const entry=parseText(text,now);return entry.amounts.length?[entry]:[];
}
