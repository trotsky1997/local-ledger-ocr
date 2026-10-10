import local.codex.ledger.BankRules;
public class TestBankRules {
 static int count;
 static void match(String s,long cents,String direction){count++;BankRules.Parsed p=BankRules.parse(s);if(p==null||p.cents!=cents||!p.direction.equals(direction))throw new AssertionError("Case "+count+": "+s+" => "+(p==null?"null":p.cents+" "+p.direction+" "+p.candidates));}
 static void reject(String s){count++;if(BankRules.parse(s)!=null)throw new AssertionError("Must reject: "+s);}
 public static void main(String[] args){
 match("【招商银行】您尾号1234的借记卡10月11日12:34在测试商户消费人民币23.80元，余额1234.56元。",2380,"expense");
 match("【工商银行】您尾号1234卡转入人民币5,000.00，可用余额8,000.00元。",500000,"income");
 match("【建设银行】尾号1234账户收入(工资)人民币10000.00元,余额23000.00元。",1000000,"income");
 match("【农业银行】账户支出人民币88.00，余额9999.00。",8800,"expense");
 match("【中国银行】您信用卡消费CNY123.45，可用额度10000.00元。",12345,"expense");
 match("【邮储银行】尾号1234账户入账人民币200元，余额600元。",20000,"income");
 match("【交通银行】尾号1234卡取现500.00元，手续费2.00元，余额6000元。",50000,"expense");
 match("【平安银行】尾号1234信用卡还款成功人民币500元，可用额度10000元。",50000,"expense");
 match("【兴业银行】尾号1234代扣人民币49.90元，余额980.10元。",4990,"expense");
 match("【农商银行】账户存入人民币300.00元，余额500.00元。",30000,"income");
 match("【浦发银行】您信用卡退款人民币80.00元，可用额度10000元。",8000,"income");
 match("微信收款助手\n收款成功，收款23.80元",2380,"income");
 match("微信支付\n支付成功\n￥23.80",2380,"expense");
 match("支付宝\n付款成功 30.00元",3000,"expense");
 match("银行\n快捷支付人民币9.99元，余额1000元",999,"expense");
 match("银行\n支出100元，退款20元",0,"income");
 match("交易成功提醒\n交易时间：\n尾号1234信用卡10月10日21:58\n交易类型：\n消费-个人消费卡\n交易金额：\n10.00人民币，点击查看详情\n交易商户：\n财付通-测试茶饮店\n可用额度：\n￥9000.00",1000,"expense");
 match("昨天21:58\n测试茶饮店\n使用招商银行信用卡(1234)支付\n¥10.00",1000,"expense");
 reject("记账日报\n昨日支出 ¥19.00\n本月统计 已支出¥500.00 已入账¥300.00");
 reject("【建设银行】验证码123456，支付金额100.00元，请勿告知他人。");
 reject("【招商银行】您账单已出，应还款2000元，请于还款日还款。");
 reject("【工商银行】您的余额1000元，可用额度2000元。");
 reject("【某银行】邀请您消费满100元领优惠券。");
 reject("尾号1234转账成功，详见银行App。");
 reject("消费金额人民币1.234元");
 reject("您好，今天去银行存入点钱吧");
 reject("【银行】您的账户将于明日扣款人民币100元，请备足资金。");
 reject("【银行】扣款失败人民币100元，余额不足。");
 reject("【银行】退款处理中，退款金额人民币100元。");
 reject("【银行】请在明天前存入1000元，确保自动还款成功。");
 if(!BankRules.bankIdentity("com.chinamworld.main","unknown",""))throw new AssertionError();
 if(!BankRules.bankIdentity("com.unknown.bank","某农商银行",""))throw new AssertionError();
 if(!BankRules.bankIdentity("com.custom.app","未知App","com.custom.app"))throw new AssertionError();
 if(BankRules.bankIdentity("com.chat.app","聊天",""))throw new AssertionError();
 System.out.println("PASS "+count+" bank/WeChat/Alipay regex cases + 4 app identity cases");
 }
}
