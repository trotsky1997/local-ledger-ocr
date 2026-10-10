package local.codex.ledger;
import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;
/** Conservative transaction recognition, independent of Android for regression tests. */
public final class BankRules {
    public static final String BANK_PACKAGES="com.icbc|com.icbc.im|com.chinamworld.main|com.chinamworld.bocmbci|com.ccb.longjiLife|com.ccb.ccbnetpay|com.android.bankabc|com.bankcomm.Bankcomm|cmb.pb|cn.com.cmbc.newmbank|com.citicbank|com.citiccard.mobilebank|com.ecitic.bank.mobile|com.cebbank.mobile.cemb|com.cib.cibmb|com.pingan.paces.ccms|com.bank.pingan|cn.com.spdb.mobilebank.per|com.cgbchina.xpt|com.psbc.mbank|com.bocop.eronghui|com.hxb.mobile.client";
    static final Pattern DENY=Pattern.compile("验证码|动态码|校验码|一次性密码|登录密码|认证码|(?i)\\bOTP\\b|待还款|应还|需还|请还款|还款提醒|还款日|最低还款|账单已出|账单日|优惠券|抽奖|点击链接|点击领取|限时优惠|额度提升|提额邀请|邀请您|贷款申请|待支付|待付款|支付失败|付款失败|扣款失败|交易失败|转账失败|未成功|未完成|处理中|将于|预计扣|拟扣款|预约|请[^。\\n]{0,25}(?:存入|转入|还款)");
    static final Pattern EVENT=Pattern.compile("消费|支出|扣款|代扣|支付|付款|取现|取款|转出|汇出|借记|还款成功|偿还|存入|存款|收入|入账|汇入|转入|贷记|到账|收款|退款|退回|转账成功");
    static final Pattern INCOME=Pattern.compile("存入|存款|收入|入账|汇入|转入|贷记|到账|收款|退款|退回");
    static final Pattern BALANCE=Pattern.compile("余额|可用额度|授信额度|信用额度|剩余额度|积分|尾号|卡号|账号|手续费|服务费|优惠|原价");
    static final String NUMBER="([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?![0-9.]|,[0-9])";
    static final Pattern MONEY=Pattern.compile("(?:人民币|RMB|CNY|[¥￥])\\s*"+NUMBER+"|(?<![0-9.,])"+NUMBER+"\\s*(?:人民币|元|RMB|CNY)",Pattern.CASE_INSENSITIVE);
    public static final class Parsed {public long cents;public String direction,merchant="";public List<Long> candidates=new ArrayList<>();}
    public static Parsed parse(String raw){
        if(raw==null||raw.length()>100000||DENY.matcher(raw).find()||raw.matches("(?s).*(记账日报|本月统计|昨日支出|昨日入账|月度统计).*"))return null;
        String text=raw.replace('，',',');Matcher event=EVENT.matcher(text);if(!event.find())return null;
        Parsed p=new Parsed();String chosenDirection="expense";Matcher money=MONEY.matcher(text);
        while(money.find()){
            String before=text.substring(Math.max(0,money.start()-80),money.start());
            Matcher ev=EVENT.matcher(before);int lastEvent=-1;String direction="expense";while(ev.find()){lastEvent=ev.start();direction=INCOME.matcher(ev.group()).find()?"income":"expense";}
            Matcher balance=BALANCE.matcher(before);int lastOther=-1;while(balance.find())lastOther=balance.start();
            if(lastOther>lastEvent)continue;
            if(lastEvent<0&&money.start()>80)continue;
            try{long cents=new BigDecimal(money.group(1)!=null?money.group(1).replace(",",""):money.group(2).replace(",","")).movePointRight(2).longValueExact();
                if(cents<=0||cents>99999999999L)continue;if(!p.candidates.contains(cents)){p.candidates.add(cents);chosenDirection=direction;}
            }catch(Exception ignored){}
        }
        if(p.candidates.isEmpty())return null;
        p.cents=p.candidates.size()==1?p.candidates.get(0):0;p.direction=chosenDirection;
        Matcher merchant=Pattern.compile("(?:交易商户|商户名称|商户全称|交易对方)[：:\\s]*([^\\n]+(?:\\n[（(][^\\n]+)?)").matcher(text);if(merchant.find())p.merchant=merchant.group(1).replace("\n","").trim();
        if(p.merchant.isEmpty()){Matcher paid=Pattern.compile("([^\\n]+)\\n使用[^\\n]+支付").matcher(text);if(paid.find())p.merchant=paid.group(1).trim();}
        return p;
    }
    public static boolean bankIdentity(String pkg,String label,String extra){
        if(label!=null&&label.matches(".*(银行|农信|农商|信用社).*"))return true;
        for(String p:BANK_PACKAGES.split("\\|"))if(p.equals(pkg))return true;
        if(extra!=null)for(String p:extra.split("[\\s,;，；]+"))if(!p.isEmpty()&&p.equals(pkg))return true;
        return false;
    }
    public static boolean bankTitle(String title){return title!=null&&title.matches(".*(银行|招行|建行|工行|农行|中行|交行|浦发|兴业|中信|民生|广发|平安|光大|邮储|农商|农信|掌上生活).*?");}
    public static long messageTime(String raw,long fallback){
        Matcher m=Pattern.compile("(20[0-9]{2})[-年/]([0-9]{1,2})[-月/]([0-9]{1,2})日?\\s*([0-9]{1,2}):([0-9]{2})").matcher(raw);
        Calendar c=Calendar.getInstance();c.setTimeInMillis(fallback);c.setLenient(false);try{if(m.find()){c.set(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))-1,Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)),0);return c.getTimeInMillis();}
            m=Pattern.compile("([0-9]{1,2})月([0-9]{1,2})日\\s*([0-9]{1,2}):([0-9]{2})").matcher(raw);if(m.find()){c.set(Calendar.MONTH,Integer.parseInt(m.group(1))-1);c.set(Calendar.DAY_OF_MONTH,Integer.parseInt(m.group(2)));c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(m.group(3)));c.set(Calendar.MINUTE,Integer.parseInt(m.group(4)));c.set(Calendar.SECOND,0);if(c.getTimeInMillis()-fallback>30L*86400000)c.add(Calendar.YEAR,-1);return c.getTimeInMillis();}
            m=Pattern.compile("(昨天|昨日|今天|今日)\\s*([0-9]{1,2}):([0-9]{2})").matcher(raw);if(m.find()){if(m.group(1).equals("昨天")||m.group(1).equals("昨日"))c.add(Calendar.DAY_OF_MONTH,-1);c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(m.group(2)));c.set(Calendar.MINUTE,Integer.parseInt(m.group(3)));c.set(Calendar.SECOND,0);return c.getTimeInMillis();}
            m=Pattern.compile("(?:^|\\n)\\s*([0-9]{1,2}):([0-9]{2})\\s*(?:$|\\n)").matcher(raw);if(m.find()){c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(m.group(1)));c.set(Calendar.MINUTE,Integer.parseInt(m.group(2)));c.set(Calendar.SECOND,0);return c.getTimeInMillis();}
        }catch(Exception ignored){}return fallback;
    }
}
