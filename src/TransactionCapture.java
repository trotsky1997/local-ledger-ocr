package local.codex.ledger;
import android.content.Context;
import org.json.JSONObject;
import java.util.*;
import java.security.*;

public final class TransactionCapture {
    public static void save(Context context,String identity,String eventKey,String title,String raw,long time){
        BankRules.Parsed p=BankRules.parse(raw);if(p==null)return;
        try(LocalLedgerActivity.Store db=new LocalLedgerActivity.Store(context)){
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((identity+"\n"+eventKey+"\n"+raw).getBytes("UTF-8"));StringBuilder id=new StringBuilder();for(byte b:hash)id.append(String.format(Locale.ROOT,"%02x",b&255));
            String merchant=p.merchant.isEmpty()?title:p.merchant;
            JSONObject o=new JSONObject();o.put("id",id.toString());o.put("cents",p.cents);o.put("direction",p.direction);o.put("merchant",merchant.substring(0,Math.min(200,merchant.length())));o.put("category","待分类");o.put("happened",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.ROOT).format(new Date(BankRules.messageTime(raw,time))));o.put("raw",raw);o.put("source",identity.substring(0,Math.min(100,identity.length())));o.put("status","pending");db.save(o,true);
        }catch(Exception e){android.util.Log.e("LocalLedger","Capture failed",e);}
    }
}
