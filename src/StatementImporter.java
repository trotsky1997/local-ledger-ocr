package local.codex.ledger;
import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.zip.*;
import java.math.*;
import java.security.*;
import java.text.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Offline import of exported personal WeChat/Alipay statements. No formulas are executed. */
public final class StatementImporter {
 public static class Entry {public String id,direction,merchant,category,happened,raw,source;public String subcategory="",note="";public long cents;}
 public static class Result {public List<Entry> entries=new ArrayList<>();public Map<String,Integer> skipped=new LinkedHashMap<>();public String provider;public int scanned;public long income,expense;void skip(String reason){skipped.put(reason,skipped.containsKey(reason)?skipped.get(reason)+1:1);}}
 static String clean(String s){return s==null?"":s.replace("\uFEFF","").trim().replaceFirst("^`","").trim();}
 static String header(String s){return clean(s).replaceAll("[\\s()（）]","");}
 static int find(List<String> row,String... aliases){for(int i=0;i<row.size();i++)for(String a:aliases)if(header(row.get(i)).equals(header(a)))return i;return -1;}
 static String get(List<String> row,int index){return index>=0&&index<row.size()?clean(row.get(index)):"";}
 static String decode(byte[] bytes)throws Exception {try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){return Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}}
 public static List<List<String>> csv(String text)throws Exception{
  List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
  // TSV is accepted when its table header contains tabs and no commas.
  char sep=',';for(String line:text.split("\\r?\\n")){if(line.contains("交易时间")||line.contains("交易创建时间")){if(line.indexOf('\t')>=0&&line.indexOf(',')<0)sep='\t';break;}}
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else if(quoted||cell.length()==0)quoted=!quoted;else cell.append(c);}
   else if(c==sep&&!quoted){row.add(cell.toString());cell.setLength(0);}
   else if((c=='\r'||c=='\n')&&!quoted){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString());rows.add(row);row=new ArrayList<>();cell.setLength(0);if(rows.size()>50050)throw new Exception("流水超过 50000 条，请按时间分段导出");}
   else cell.append(c);if(cell.length()>100000)throw new Exception("流水单元格内容过长");
  }if(quoted)throw new Exception("CSV 引号不完整，请重新导出");if(cell.length()>0||!row.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;
 }
 static void xml(byte[] bytes,DefaultHandler handler)throws Exception{
  String prefix=new String(bytes,StandardCharsets.UTF_8);if(prefix.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||prefix.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new Exception("不支持含外部实体的表格");
  SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(false);
  XMLReader r=f.newSAXParser().getXMLReader();try{r.setFeature("http://xml.org/sax/features/external-general-entities",false);r.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(SAXException ignored){}
  r.setEntityResolver((publicId,systemId)->{throw new SAXException("External entities are disabled");});r.setContentHandler(handler);r.parse(new InputSource(new ByteArrayInputStream(bytes)));
 }
 static Map<String,byte[]> unzip(byte[] bytes)throws Exception{
  Map<String,byte[]> files=new TreeMap<>();int total=0,count=0;
  try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=z.getNextEntry())!=null){if(++count>300)throw new Exception("压缩包文件数超限");if(e.isDirectory())continue;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[32768];int n;while((n=z.read(b))!=-1){total+=n;if(total>40*1024*1024)throw new Exception("解压后文件超过 40 MB，请分段导出");out.write(b,0,n);}files.put(e.getName(),out.toByteArray());}}
  catch(ZipException e){throw new Exception("无法读取压缩包。如有密码，请先在手机上解压，再选择 CSV / XLSX 文件。");}return files;
 }
 static List<List<String>> xlsx(Map<String,byte[]> files)throws Exception{
  final List<String> shared=new ArrayList<>();byte[] ss=files.get("xl/sharedStrings.xml");if(ss!=null)xml(ss,new DefaultHandler(){StringBuilder value;boolean inText;public void startElement(String u,String l,String q,Attributes a){if(q.equals("si"))value=new StringBuilder();if(q.equals("t"))inText=true;}public void characters(char[] ch,int s,int n){if(inText&&value!=null)value.append(ch,s,n);}public void endElement(String u,String l,String q){if(q.equals("t"))inText=false;if(q.equals("si")){shared.add(value.toString());value=null;}}});
  final List<List<String>> rows=new ArrayList<>();
  for(Map.Entry<String,byte[]> f:files.entrySet())if(f.getKey().matches("xl/worksheets/sheet[0-9]+\\.xml"))xml(f.getValue(),new DefaultHandler(){
   List<String> row;String type="",coordinate="";StringBuilder value;boolean reading;
   public void startElement(String u,String l,String q,Attributes a)throws SAXException{if(q.equals("row"))row=new ArrayList<>();if(q.equals("c")){type=a.getValue("t");coordinate=a.getValue("r");value=new StringBuilder();}if(q.equals("v")||q.equals("t"))reading=true;}
   public void characters(char[] ch,int s,int n){if(reading&&value!=null)value.append(ch,s,n);}
   public void endElement(String u,String l,String q)throws SAXException{if(q.equals("v")||q.equals("t"))reading=false;if(q.equals("c")&&row!=null){String s=value.toString();if("s".equals(type)){try{s=shared.get(Integer.parseInt(s));}catch(Exception e){throw new SAXException("无效 XLSX 字符串编号");}}int col=0;if(coordinate!=null){for(char c:coordinate.toCharArray()){if(c<'A'||c>'Z')break;col=col*26+c-'A'+1;}}else col=row.size()+1;if(col>100)throw new SAXException("表格列数超限");while(row.size()<col)row.add("");if(col>0)row.set(col-1,s);value=null;}if(q.equals("row")){rows.add(row);row=null;if(rows.size()>50050)throw new SAXException("流水超过 50000 条");}}
  });return rows;
 }
 static String date(String s)throws Exception{
  if(s.matches("[0-9]{5}(?:\\.[0-9]+)?")){double serial=Double.parseDouble(s);long millis=(long)Math.round((serial-25569d)*86400000d);SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(millis));}
  for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy/MM/dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd HH:mm","yyyy/MM/dd HH:mm","yyyy-MM-dd'T'HH:mm"}){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT);f.setLenient(false);ParsePosition p=new ParsePosition(0);Date d=f.parse(s,p);if(d!=null&&p.getIndex()==s.length())return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT).format(d);}
  throw new Exception("时间格式不支持");
 }
 public static Result parse(byte[] bytes,String name)throws Exception{
  List<List<String>> rows;String preamble="";
  if(bytes.length>3&&bytes[0]=='P'&&bytes[1]=='K'){
   Map<String,byte[]> files=unzip(bytes);if(files.containsKey("[Content_Types].xml"))rows=xlsx(files);
   else {String chosen=null;for(String f:files.keySet())if(f.toLowerCase(Locale.ROOT).endsWith(".csv")||f.toLowerCase(Locale.ROOT).endsWith(".xlsx")){if(chosen!=null)throw new Exception("压缩包包含多份流水，请先解压后逐份导入");chosen=f;}if(chosen==null)throw new Exception("压缩包中未找到 CSV / XLSX 流水");return parse(files.get(chosen),chosen);}
  }else{preamble=decode(bytes);rows=csv(preamble);}
  for(int i=0;i<Math.min(rows.size(),100);i++){List<String> h=rows.get(i);if(find(h,"账单日期")>=0&&find(h,"记账分类")>=0&&find(h,"收支类型")>=0)return parseMi(rows,i);if(find(h,"记账日期","交易日期","交易时间")>=0&&find(h,"交易金额","发生额","发生金额","借方金额","支出金额","贷方金额","收入金额")>=0&&find(h,"收/支")<0)return parseBank(rows,i);}
  int start=-1,time=-1,amount=-1,flow=-1,merchant=-1,status=-1,order=-1,description=-1,category=-1;String provider="";
  for(int i=0;i<Math.min(rows.size(),100);i++){List<String> row=rows.get(i);int t=find(row,"交易时间","交易创建时间","付款时间"),a=find(row,"金额(元)","交易金额(元)","金额","金额人民币"),d=find(row,"收/支","收支","收支类型");
   if(t>=0&&a>=0&&d>=0){start=i;time=t;amount=a;flow=d;merchant=find(row,"交易对方","对方名称","商户名称");status=find(row,"当前状态","交易状态","状态");order=find(row,"交易单号","交易订单号","交易号");description=find(row,"商品","商品名称","商品说明","说明");category=find(row,"交易分类","交易类型","类型");
    String joined=String.join("|",row);provider=joined.contains("当前状态")||joined.contains("商户单号")?"微信":"支付宝";
    if(preamble.substring(0,Math.min(2000,preamble.length())).contains("微信支付"))provider="微信";else if(preamble.substring(0,Math.min(2000,preamble.length())).contains("支付宝"))provider="支付宝";break;}}
  if(start<0)throw new Exception("未找到微信 / 支付宝流水表头。请选择官方导出的 CSV 或 XLSX；旧版 XLS 请先转换为 XLSX。");
  if(merchant<0||status<0)throw new Exception("流水缺少交易对方或状态列，请使用完整的官方账单导出文件");
  Result result=new Result();result.provider=provider;Set<String> seen=new HashSet<>();
  for(int i=start+1;i<rows.size();i++){
   List<String> row=rows.get(i);String when=get(row,time);if(when.isEmpty()||when.startsWith("----")||when.contains("交易时间")||when.contains("共"))continue;result.scanned++;
   String dir=get(row,flow),st=get(row,status),cat=get(row,category);
   // Refund is a real cash inflow even when Alipay marks it as non-income.
   // Preserve the original payment and its refund as separate flows, so they net out.
   boolean refund=cat.contains("退款");String originalDirection=dir;
   if(refund&&st.contains("退款")&&(dir.equals("不计收支")||dir.equals("/")))dir="收入";
   if(!dir.equals("收入")&&!dir.equals("支出")){result.skip("不计收支 / 提现充值等内部流转");continue;}
   if(st.matches(".*(失败|关闭|撤销|处理中|等待付款|等待支付|未完成|未支付).*")){result.skip("失败、关闭或未付款状态");continue;}
   if(!st.matches(".*(成功|已收钱|已收款|已转账|已支付|已付款|已到账|已完成|已存入|已转入|已全额退款|已退款|等待确认收货|等待买家确认收货).*")){result.skip("无法确认已付款的状态");continue;}
   try{
    Entry e=new Entry();String a=get(row,amount).replaceAll("[¥￥,\\s]","");e.cents=new BigDecimal(a).abs().movePointRight(2).longValueExact();if(e.cents==0){result.skip("零金额交易");continue;}if(e.cents>99999999999L)throw new Exception("金额超限");e.happened=date(when);e.direction=dir.equals("收入")?"income":"expense";e.merchant=get(row,merchant);e.category=cat.isEmpty()?"待分类":cat;e.source=provider+"流水";
    String orderId=get(row,order);String key=provider+"|"+(orderId.isEmpty()?when+"|"+dir+"|"+a+"|"+e.merchant+"|"+get(row,description):orderId);
    StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));e.id=hash.toString();
    if(!seen.add(e.id)){result.skip("文件内重复交易");continue;}
    e.raw="交易单号："+orderId+"\n交易时间："+when+"\n交易类型："+cat+"\n交易对方："+e.merchant+"\n商品说明："+get(row,description)+"\n金额："+a+"\n原流水收支："+originalDirection+"\n导入收支："+dir+"\n状态："+st;
    if(e.merchant.length()>200||e.category.length()>100||e.raw.length()>100000)throw new Exception("内容过长");result.entries.add(e);if(e.direction.equals("income"))result.income+=e.cents;else result.expense+=e.cents;
   }catch(Exception e){result.skip("金额或时间格式无效");}
  }
  if(result.entries.isEmpty())throw new Exception("没有可导入的已完成收支记录。跳过情况："+result.skipped);return result;
 }
 public static String miKey(Entry e){String time=e.happened.length()==16?e.happened+":00":e.happened;String note=e.note.isEmpty()?e.merchant:e.note;return time+"|"+e.category+"|"+e.subcategory+"|"+e.direction+"|"+note+"|"+e.cents;}
 static Result parseMi(List<List<String>> rows,int start)throws Exception{
  List<String> h=rows.get(start);int t=find(h,"账单日期"),cat=find(h,"记账分类"),sub=find(h,"分类子类"),dir=find(h,"收支类型"),note=find(h,"备注"),amount=find(h,"金额");if(t<0||cat<0||sub<0||dir<0||note<0||amount<0)throw new Exception("小米记账表头缺少字段");
  Result r=new Result();r.provider="小米记账";Map<String,Integer> occurrences=new HashMap<>();
  for(int i=start+1;i<rows.size();i++){List<String> row=rows.get(i);if(get(row,t).isEmpty())continue;r.scanned++;try{
   String d=get(row,dir);if(!d.equals("收入")&&!d.equals("支出")){r.skip("非收入支出类型");continue;}
   Entry e=new Entry();e.happened=date(get(row,t));e.category=get(row,cat);e.subcategory=get(row,sub);e.note=get(row,note);e.merchant=e.note.substring(0,Math.min(200,e.note.length()));e.direction=d.equals("收入")?"income":"expense";e.cents=new BigDecimal(get(row,amount).replaceAll("[¥￥,\\s]","")).abs().movePointRight(2).longValueExact();if(e.cents>99999999999L||e.category.length()>100||e.subcategory.length()>100||e.note.length()>100000)throw new Exception("字段超限");e.source="小米记账流水";e.raw="小米记账导出\n备注："+e.note;
   String key=miKey(e);int n=occurrences.containsKey(key)?occurrences.get(key)+1:1;occurrences.put(key,n);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(("小米记账|"+key+"|"+n).getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));e.id=hash.toString();r.entries.add(e);if(e.direction.equals("income"))r.income+=e.cents;else r.expense+=e.cents;
  }catch(Exception e){r.skip("金额、时间或字段格式无效");}}
  if(r.entries.isEmpty())throw new Exception("小米记账文件中没有有效账单："+r.skipped);return r;
 }
 static Result parseBank(List<List<String>> rows,int start)throws Exception {
  List<String> h=rows.get(start);int ti=find(h,"记账日期","交易日期","交易时间"),am=find(h,"交易金额","发生额","发生金额"),debit=find(h,"借方金额","支出金额","借方发生额"),credit=find(h,"贷方金额","收入金额","贷方发生额"),direction=find(h,"借贷标志","收支方向","收支标志","收支类型"),party=find(h,"对手信息","交易对方","对方户名","对手户名","对方名称"),desc=find(h,"交易摘要","摘要","交易类型","附言"),currency=find(h,"货币","币种"),order=find(h,"交易流水号","流水号","银行流水号","凭证号"),bank=find(h,"银行来源"),account=find(h,"账户标识");Result r=new Result();r.provider="银行";Map<String,Integer> occurrences=new HashMap<>();
  for(int i=start+1;i<rows.size();i++){List<String> row=rows.get(i);String when=get(row,ti);if(when.isEmpty()||when.contains("日期"))continue;r.scanned++;try{
   String cur=get(row,currency);if(!cur.isEmpty()&&!cur.matches("(?i)CNY|RMB|人民币|人民币元|156")){r.skip("非人民币交易（暂不自动换汇）");continue;}
   BigDecimal amount;if(debit>=0||credit>=0){String out=get(row,debit).replaceAll("[,¥￥\\s]",""),in=get(row,credit).replaceAll("[,¥￥\\s]","");BigDecimal d=out.isEmpty()?BigDecimal.ZERO:new BigDecimal(out),c=in.isEmpty()?BigDecimal.ZERO:new BigDecimal(in);if(d.signum()!=0&&c.signum()!=0)throw new Exception("借贷金额同时非零");amount=c.abs().subtract(d.abs());}else{amount=new BigDecimal(get(row,am).replaceAll("[,¥￥\\s]",""));String dir=get(row,direction);if(dir.matches("支出|借|借方|D"))amount=amount.abs().negate();else if(dir.matches("收入|贷|贷方|C"))amount=amount.abs();}
   Entry e=new Entry();e.cents=amount.abs().movePointRight(2).longValueExact();if(e.cents==0){r.skip("零金额交易");continue;}if(e.cents>99999999999L)throw new Exception("金额超限");if(when.matches("20[0-9]{6}"))when=when.substring(0,4)+"-"+when.substring(4,6)+"-"+when.substring(6,8);if(when.matches("20[0-9]{2}[-/][0-9]{2}[-/][0-9]{2}"))when+=" 00:00:00";e.happened=date(when);e.direction=amount.signum()<0?"expense":"income";e.merchant=get(row,party);e.note=get(row,desc);e.category="银行流水";String provider=get(row,bank);e.source=(provider.isEmpty()?"银行":provider)+"流水";if(!provider.isEmpty())r.provider=provider;
   String key=get(row,order);if(key.isEmpty())key=when+"|"+e.direction+"|"+e.cents+"|"+e.merchant+"|"+e.note;key=e.source+"|"+get(row,account)+"|"+key;int count=occurrences.containsKey(key)?occurrences.get(key)+1:1;occurrences.put(key,count);if(order<0)key+="|"+count;StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));e.id=hash.toString();e.raw="银行流水\n记账时间："+when+"\n交易金额："+amount.toPlainString()+"\n币种："+(cur.isEmpty()?"人民币":cur)+"\n摘要："+e.note+"\n对手信息："+e.merchant;e.merchant=e.merchant.substring(0,Math.min(200,e.merchant.length()));r.entries.add(e);if(e.direction.equals("income"))r.income+=e.cents;else r.expense+=e.cents;
  }catch(Exception e){r.skip("金额、日期或借贷方向无法解析");}}
  if(r.entries.isEmpty())throw new Exception("未找到有效人民币银行流水："+r.skipped);return r;
 }
}
