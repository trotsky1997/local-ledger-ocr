import local.codex.ledger.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class TestSyntheticFormats {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  List<StatementImporter.Entry> rows=new ArrayList<>();
  for(int i=0;i<4;i++){StatementImporter.Entry e=new StatementImporter.Entry();e.happened="2026-01-02T03:04:05";e.category="测试分类";e.subcategory="测试子类";e.direction=i==1?"income":"expense";e.cents=i==2?0:1234;e.note="合成备注 <&> \"双引号\"\n换行";e.merchant="合成商户";rows.add(e);}
  StatementImporter.Result first=StatementImporter.parse(MiExport.write(rows),"synthetic.xlsx");
  StatementImporter.Result back=StatementImporter.parse(MiExport.write(first.entries),"roundtrip.xlsx");
  check(first.entries.size()==4&&back.entries.size()==4,"Mi row count incl duplicate and zero");
  check(!first.entries.get(0).id.equals(first.entries.get(3).id),"Identical separate rows retain multiplicity");
  for(int i=0;i<4;i++){check(StatementImporter.miKey(first.entries.get(i)).equals(StatementImporter.miKey(back.entries.get(i))),"Mi six fields");check(first.entries.get(i).id.equals(back.entries.get(i).id),"Mi stable IDs");}
  String csv="记账日期,币种,交易金额,联机余额,交易摘要,对手信息\n2026-01-02,CNY,-12.34,9000.00,消费,测试商户\n2026-01-03,CNY,100.00,9100.00,转入,测试对方\n2026-01-04,USD,50.00,9150.00,转入,测试外币\n";
  StatementImporter.Result bank=StatementImporter.parse(csv.getBytes(StandardCharsets.UTF_8),"bank.csv");
  check(bank.entries.size()==2&&bank.expense==1234&&bank.income==10000,"Bank signed amount not balance; foreign currency excluded");
  csv="交易日期,借方金额,贷方金额,对方户名,摘要\n20260102,20.00,,测试商户,消费\n20260103,,30.00,测试对方,转入\n";
  bank=StatementImporter.parse(csv.getBytes(StandardCharsets.UTF_8),"debit.csv");
  check(bank.entries.size()==2&&bank.expense==2000&&bank.income==3000,"Bank debit/credit columns");
  System.out.println("PASS generated XLSX Mi six-field roundtrip/seconds/escaping/zero/duplicate multiplicity; synthetic bank CSV signed/debit-credit/currency/balance");
 }
}
