package local.codex.ledger;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.*;
import android.graphics.Rect;
import android.os.*;
import java.util.*;

/** Reads only visible payment/bank assistant cards in WeChat after explicit system opt-in. */
public class LedgerAccessibility extends AccessibilityService {
 final Handler handler=new Handler(Looper.getMainLooper());long last;
 public void onInterrupt(){}
 public void onAccessibilityEvent(AccessibilityEvent e){if(e.getPackageName()==null||!"com.tencent.mm".contentEquals(e.getPackageName()))return;if(SystemClock.uptimeMillis()-last<1200)return;last=SystemClock.uptimeMillis();handler.removeCallbacks(scan);handler.postDelayed(scan,400);}
 final Runnable scan=()->{
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null||!"com.tencent.mm".contentEquals(root.getPackageName()))return;
  try{
   List<AccessibilityNodeInfo> nodes=new ArrayList<>();walk(root,nodes,600);String title="";int topLimit=getResources().getDisplayMetrics().heightPixels*13/100;
   for(AccessibilityNodeInfo n:nodes){Rect r=new Rect();n.getBoundsInScreen(r);String text=txt(n);if(r.top<topLimit&&(text.equals("微信支付")||text.equals("微信收款助手")||BankRules.bankTitle(text))){title=text;break;}}
   if(title.isEmpty()||!getSharedPreferences(LocalLedgerActivity.PREFS,0).getBoolean(BankRules.bankTitle(title)?"bank":"wechat",true))return;
   Set<String> captured=new HashSet<>();for(AccessibilityNodeInfo n:nodes){String text=txt(n);boolean marker=text.contains("交易成功提醒")||text.contains("交易提醒")||text.contains("消费提醒")||text.contains("收款成功提醒"),pay=text.contains("使用")&&text.contains("支付");if(!marker&&!pay)continue;
    AccessibilityNodeInfo parent=n;for(int level=0;level<8&&parent!=null;level++,parent=parent.getParent()){
     String raw=flatten(parent);if(raw.length()>12000)break;if(raw.matches("(?s).*(记账日报|本月统计|昨日支出|昨日入账).*"))break;
     if(marker&&raw.split("交易成功提醒",-1).length>2)break;
     if(marker&&!raw.contains("交易金额"))continue;
     BankRules.Parsed parsed=BankRules.parse(raw);if(parsed==null)continue;
     Rect bounds=new Rect();parent.getBoundsInScreen(bounds);String previous="";int prevY=-1;for(AccessibilityNodeInfo t:nodes){Rect tr=new Rect();t.getBoundsInScreen(tr);String tx=txt(t);if(tr.bottom<=bounds.top&&tr.top>prevY&&tx.matches("(?:昨天|今天|昨日|今日)?\\s*[0-9]{1,2}:[0-9]{2}|[0-9]{1,2}月[0-9]{1,2}日.*")){previous=tx;prevY=tr.top;}}
     raw=previous+"\n"+raw;if(!captured.add(raw))break;long time=BankRules.messageTime(raw,System.currentTimeMillis());String key=raw;
     // When a card has no date/time, keep the same visible content as one draft.
     TransactionCapture.save(this,title+"页面",key,title,raw,time);break;
    }
   }
  }catch(Exception e){android.util.Log.e("LocalLedger","Visible card scan failed",e);}
 };
 static String txt(AccessibilityNodeInfo n){CharSequence s=n.getText();if(s==null||s.length()==0)s=n.getContentDescription();return s==null?"":s.toString().trim();}
 static void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out,int limit){if(n==null||out.size()>=limit)return;out.add(n);for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),out,limit);}
 static String flatten(AccessibilityNodeInfo n){List<AccessibilityNodeInfo> nodes=new ArrayList<>();walk(n,nodes,150);StringBuilder s=new StringBuilder();String last="";for(AccessibilityNodeInfo x:nodes){String t=txt(x);if(!t.isEmpty()&&!t.equals(last)){s.append(t).append('\n');last=t;}}return s.toString();}
 public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
