package local.codex.ledger;
import android.content.*;
import android.telephony.SmsMessage;

public class LedgerSms extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent){
        if(!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())||!c.getSharedPreferences(LocalLedgerActivity.PREFS,0).getBoolean("sms",true))return;
        Object[] pdus=(Object[])intent.getExtras().get("pdus");if(pdus==null)return;
        StringBuilder body=new StringBuilder();String sender="";long time=System.currentTimeMillis();
        for(Object p:pdus){SmsMessage m=SmsMessage.createFromPdu((byte[])p,intent.getStringExtra("format"));if(m==null)continue;sender=m.getOriginatingAddress();body.append(m.getMessageBody());time=m.getTimestampMillis();}
        String raw=body.toString();
        if(!(sender!=null&&sender.matches("(?:\\+?86)?(?:95[0-9]{3,8}|106[0-9]{3,16})")||raw.matches("(?s).*[【\\[](?:[^】\\]]{0,25})(?:银行|农信|农商|信用社)[^】\\]]*[】\\]].*")))return;
        final String from=sender==null?"银行短信":sender, text=raw;final long at=time;
        final PendingResult pending=goAsync();new Thread(()->{try{TransactionCapture.save(c.getApplicationContext(),"短信 "+from,from+at,from,text,at);}finally{pending.finish();}},"ledger-sms").start();
    }
}
