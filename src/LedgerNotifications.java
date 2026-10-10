package local.codex.ledger;
import android.service.notification.*;
import android.app.Notification;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.*;
import java.util.regex.*;
import java.security.*;

/** Captures only financial assistant notifications, always as unconfirmed drafts. */
public class LedgerNotifications extends NotificationListenerService {
    public void onNotificationPosted(StatusBarNotification sbn){
        String pkg=sbn.getPackageName(),setting,label=pkg;
        try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
        if(pkg.equals("com.tencent.mm"))setting="wechat";
        else if(pkg.equals("com.eg.android.AlipayGphone"))setting="alipay";
        else if(BankRules.bankIdentity(pkg,label,getSharedPreferences(LocalLedgerActivity.PREFS,0).getString("bankPackages","")))setting="bank";else return;
        if(!getSharedPreferences(LocalLedgerActivity.PREFS,0).getBoolean(setting,true))return;
        Notification n=sbn.getNotification();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;
        Bundle b=n.extras;if(b==null)return;
        String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));
        String body=String.valueOf(b.getCharSequence(Notification.EXTRA_BIG_TEXT,b.getCharSequence(Notification.EXTRA_TEXT,"")));
        if(pkg.equals("com.tencent.mm")&&!title.matches(".*(微信支付|微信收款助手|收款助手|微信收款商业版).*")){
            if(!BankRules.bankTitle(title)||!getSharedPreferences(LocalLedgerActivity.PREFS,0).getBoolean("bank",true))return;
        }
        String raw=title+"\n"+body;
        if(body.isEmpty())return;
        TransactionCapture.save(this,label+"通知",sbn.getKey()+":"+sbn.getPostTime(),title,raw,sbn.getPostTime());
    }
}
