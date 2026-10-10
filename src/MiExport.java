package local.codex.ledger;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;

/** Six-column XLSX matching the supplied Xiaomi bookkeeping export. */
public final class MiExport {
 static String esc(String s){return (s==null?"":s).replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
 static void put(ZipOutputStream z,String name,String value)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(value.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
 public static byte[] write(List<StatementImporter.Entry> entries)throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){
   put(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
   put(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
   put(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookPr date1904=\"false\"/><sheets><sheet name=\"账单导出\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
   put(z,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
   StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
   List<String[]> rows=new ArrayList<>();rows.add(new String[]{"账单日期","记账分类","分类子类","收支类型","备注","金额"});
   for(StatementImporter.Entry e:entries){String time=e.happened.replace('T',' ');if(time.length()==16)time+=":00";rows.add(new String[]{time,e.category,e.subcategory,e.direction.equals("income")?"收入":"支出",e.note.isEmpty()?e.merchant:e.note,BigDecimal.valueOf(e.cents,2).toPlainString()});}
   for(int i=0;i<rows.size();i++){xml.append("<row r=\"").append(i+1).append("\">");for(int col=0;col<6;col++){xml.append("<c r=\"").append((char)('A'+col)).append(i+1).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(rows.get(i)[col])).append("</t></is></c>");}xml.append("</row>");}xml.append("</sheetData></worksheet>");put(z,"xl/worksheets/sheet1.xml",xml.toString());
  }return out.toByteArray();
 }
}
