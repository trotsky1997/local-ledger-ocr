import local.codex.ledger.StatementImporter;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
public class TestStatementImport {
 public static void main(String[] args)throws Exception{
  Set<String> ids=new HashSet<>();for(String path:args){StatementImporter.Result r=StatementImporter.parse(Files.readAllBytes(Paths.get(path)),path);System.out.println(r.provider+": scanned="+r.scanned+" valid="+r.entries.size()+" skipped="+r.skipped);for(StatementImporter.Entry e:r.entries)if(!ids.add(e.id))throw new AssertionError("Duplicate ID across supplied files");StatementImporter.Result again=StatementImporter.parse(Files.readAllBytes(Paths.get(path)),path);for(int i=0;i<r.entries.size();i++)if(!r.entries.get(i).id.equals(again.entries.get(i).id))throw new AssertionError("Unstable ID");}
  String h="支付宝交易记录\n交易时间,交易分类,交易对方,商品说明,收/支,金额,交易状态,交易订单号\n";
  String s=h+"2026-10-10 09:05:00,餐饮,\"商户,带逗号\",\"商品\n第二行\",支出,23.80,交易成功,`12345678901234567890\n"+"2026-10-10 09:06:00,退款,商户,退款,不计收支,5.00,退款成功,refund-001\n"+"2026-10-10 09:05:00,餐饮,重复,重复,支出,23.80,交易成功,`12345678901234567890\n"+"2026-10-10 09:10:00,其他,商户,未付,支出,9.90,等待付款,wait-001\n";
  StatementImporter.Result r=StatementImporter.parse(s.getBytes(StandardCharsets.UTF_8),"synthetic.csv");if(r.entries.size()!=2||!r.entries.get(0).merchant.equals("商户,带逗号")||r.income!=500||r.expense!=2380)throw new AssertionError("CSV quoting / refund / dedup / pending");
  r=StatementImporter.parse(s.getBytes(Charset.forName("GB18030")),"gb.csv");if(r.entries.size()!=2)throw new AssertionError("GB18030");
  System.out.println("PASS supplied files (if any) + deterministic IDs + quoted multiline CSV + GB18030 + refunds + duplicate IDs + pending exclusion");
 }
}
