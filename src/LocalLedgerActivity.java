package local.codex.ledger;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import android.webkit.*;
import android.view.*;
import android.graphics.Color;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;

/** Local-only ledger. No account tokens or original wallet APIs are used. */
public class LocalLedgerActivity extends Activity {
    WebView web; ServerSocket server; volatile boolean stopped;
    ExecutorService sockets = Executors.newFixedThreadPool(3);
    String origin, secret = UUID.randomUUID().toString();
    static final int IMAGE=11, EXPORT=12, IMPORT=13, STATEMENT=15, MI_EXPORT=16;
    static final String PREFS="local_ledger";
    String pendingExport;
    byte[] pendingMiExport;
    volatile StatementImporter.Result pendingStatement;
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(246,248,252));
        getWindow().setNavigationBarColor(Color.rgb(246,248,252));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        web=new WebView(this); setContentView(web);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(false); s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {return !r.getUrl().toString().startsWith(origin+"/");}
            public boolean shouldOverrideUrlLoading(WebView v,String url){return !url.startsWith(origin+"/");}
        });
        web.addJavascriptInterface(new Bridge(),"Ledger");
        try { startServer(); web.loadUrl(origin+"/"+secret+"/index.html"); }
        catch(Exception e){ android.widget.TextView t=new android.widget.TextView(this);t.setText("本地页面启动失败："+e.getMessage());setContentView(t); }
    }
    void startServer() throws Exception {
        server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));
        origin="http://127.0.0.1:"+server.getLocalPort();
        Thread t=new Thread(() -> {while(!stopped) try { final Socket c=server.accept(); sockets.execute(()->serve(c)); }catch(Exception e){if(!stopped)android.util.Log.e("LocalLedger","Server failed",e);} },"ledger-assets");
        t.setDaemon(true);t.start();
    }
    void serve(Socket c){
        try(Socket sock=c){
            sock.setSoTimeout(5000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(sock.getInputStream(),"US-ASCII"));
            String first=reader.readLine();if(first==null)return;
            String[] parts=first.split(" ");String host="";String line;
            while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("host:"))host=line.substring(5).trim();
            String path=parts.length>1?parts[1].split("\\?",2)[0]:"";
            String prefix="/"+secret+"/";
            if(!host.equals("127.0.0.1:"+server.getLocalPort())||!"GET".equals(parts[0])||!path.startsWith(prefix)){reply(sock,403,"text/plain",new byte[0]);return;}
            String file=path.substring(prefix.length());
            if(!file.matches("[A-Za-z0-9_.-]+")||file.equals(".")||file.equals("..")){reply(sock,404,"text/plain",new byte[0]);return;}
            try(InputStream in=getAssets().open("local-ledger/"+file)){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                String mime=file.endsWith(".html")?"text/html; charset=utf-8":file.endsWith(".mjs")?"text/javascript; charset=utf-8":file.endsWith(".css")?"text/css":file.endsWith(".wasm")?"application/wasm":file.endsWith(".json")?"application/json":"text/plain; charset=utf-8";
                reply(sock,200,mime,out.toByteArray());
            }catch(FileNotFoundException e){reply(sock,404,"text/plain",new byte[0]);}
        }catch(Exception ignored){}
    }
    void reply(Socket s,int code,String mime,byte[] body)throws IOException{
        OutputStream out=s.getOutputStream();
        String h="HTTP/1.1 "+code+" OK\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self'; connect-src 'self'; img-src 'self' blob: data:; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'\r\n\r\n";
        out.write(h.getBytes("US-ASCII"));out.write(body);out.flush();
    }
    void js(String code){runOnUiThread(()->{if(!stopped)web.evaluateJavascript(code,null);});}
    void error(Exception e){js("window.nativeError("+JSONObject.quote(e.getMessage()==null?e.toString():e.getMessage())+")");}
    public void onResume(){super.onResume();if(web!=null)js("window.refresh&&window.refresh()");}
    public void onDestroy(){stopped=true;try{if(server!=null)server.close();}catch(Exception ignored){} sockets.shutdownNow();if(web!=null){web.removeJavascriptInterface("Ledger");web.destroy();}super.onDestroy();}
    public class Bridge {
        @JavascriptInterface public String snapshot(){try(Store db=new Store(LocalLedgerActivity.this)){
            JSONObject o=new JSONObject();o.put("entries",db.rows(false));o.put("summary",db.summary());
            o.put("wechat",getSharedPreferences(PREFS,0).getBoolean("wechat",true));o.put("alipay",getSharedPreferences(PREFS,0).getBoolean("alipay",true));
            o.put("bank",getSharedPreferences(PREFS,0).getBoolean("bank",true));o.put("sms",getSharedPreferences(PREFS,0).getBoolean("sms",true));
            o.put("bankPackages",getSharedPreferences(PREFS,0).getString("bankPackages",""));
            o.put("smsPermission",checkSelfPermission("android.permission.RECEIVE_SMS")==android.content.pm.PackageManager.PERMISSION_GRANTED);
            String accessibility=android.provider.Settings.Secure.getString(getContentResolver(),"enabled_accessibility_services");o.put("accessibility",accessibility!=null&&accessibility.contains("local.codex.ledger.LedgerAccessibility"));
            String enabled=android.provider.Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
            o.put("notificationAccess",enabled!=null&&enabled.contains(getPackageName()+"/local.codex.ledger.LedgerNotifications"));return o.toString();
        }catch(Exception e){return "{\"error\":"+JSONObject.quote(e.getMessage())+"}";}}
        @JavascriptInterface public String save(String json){try(Store db=new Store(LocalLedgerActivity.this)) {JSONObject o=new JSONObject(json);db.save(o,false);return "{}";}catch(Exception e){return "{\"error\":"+JSONObject.quote(e.getMessage())+"}";}}
        @JavascriptInterface public String remove(String id){try(Store db=new Store(LocalLedgerActivity.this)){db.getWritableDatabase().delete("entries","id=?",new String[]{id});return "{}";}catch(Exception e){return "{\"error\":"+JSONObject.quote(e.getMessage())+"}";}}
        @JavascriptInterface public void setSwitch(String name,boolean value){if(name.equals("wechat")||name.equals("alipay")||name.equals("bank")||name.equals("sms"))getSharedPreferences(PREFS,0).edit().putBoolean(name,value).apply();}
        @JavascriptInterface public String setBankPackages(String value){if(value.length()>4000)return "{\"error\":\"包名列表过长\"}";for(String p:value.split("[\\s,;，；]+"))if(!p.isEmpty()&&!p.matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+"))return "{\"error\":\"包名格式有误\"}";getSharedPreferences(PREFS,0).edit().putString("bankPackages",value).apply();return "{}";}
        @JavascriptInterface public void smsPermission(){runOnUiThread(()->requestPermissions(new String[]{"android.permission.RECEIVE_SMS"},14));}
        @JavascriptInterface public void notificationSettings(){runOnUiThread(()->startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));}
        @JavascriptInterface public void accessibilitySettings(){runOnUiThread(()->startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS")));}
        @JavascriptInterface public void pickImage(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMAGE);});}
        @JavascriptInterface public void exportData(){runOnUiThread(()->{try(Store db=new Store(LocalLedgerActivity.this)){JSONObject o=new JSONObject();o.put("format","local-ledger-v1");o.put("entries",db.rows(true));pendingExport=o.toString(2);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"本地账本-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json");startActivityForResult(i,EXPORT);}catch(Exception e){error(e);}});}
        @JavascriptInterface public void importData(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);});}
        @JavascriptInterface public void importStatement(){runOnUiThread(()->{pendingStatement=null;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,STATEMENT);});}
        @JavascriptInterface public String previewBankCsv(String csv){try{if(csv.length()>10*1024*1024)throw new Exception("流水过大");previewStatement(StatementImporter.parse(csv.getBytes("UTF-8"),"bank.csv"));return "{}";}catch(Exception e){return "{\"error\":"+JSONObject.quote(e.getMessage())+"}";}}
        @JavascriptInterface public String saveBatch(String json){try(Store db=new Store(LocalLedgerActivity.this)){JSONArray a=new JSONArray(json);if(a.length()>200)throw new Exception("单次截图记录超限");int count=0;SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{for(int i=0;i<a.length();i++)if(db.save(a.getJSONObject(i),true))count++;sql.setTransactionSuccessful();}finally{sql.endTransaction();}return new JSONObject().put("count",count).toString();}catch(Exception e){return "{\"error\":"+JSONObject.quote(e.getMessage())+"}";}}
        @JavascriptInterface public void exportMi(){runOnUiThread(()->{try(Store db=new Store(LocalLedgerActivity.this)){List<StatementImporter.Entry> rows=new ArrayList<>();JSONArray all=db.rows(true);for(int n=0;n<all.length();n++){JSONObject o=all.getJSONObject(n);if(o.getString("status").equals("confirmed"))rows.add(statementEntry(o));}pendingMiExport=MiExport.write(rows);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"小米格式账单-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".xlsx");startActivityForResult(i,MI_EXPORT);}catch(Exception e){error(e);}});}
        @JavascriptInterface public synchronized String confirmStatement(){
            StatementImporter.Result parsed=pendingStatement;if(parsed==null)return "{\"error\":\"导入预览已失效，请重新选择流水\"}";
            try(Store db=new Store(LocalLedgerActivity.this)){int count=0;SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{for(StatementImporter.Entry e:parsed.entries)if(db.save(statementJson(e),true))count++;sql.setTransactionSuccessful();}finally{sql.endTransaction();}pendingStatement=null;return new JSONObject().put("count",count).put("duplicates",parsed.entries.size()-count).toString();}
            catch(Exception e){return "{\"error\":"+JSONObject.quote(e.getMessage())+"}";}
        }
    }
    static StatementImporter.Entry statementEntry(JSONObject o)throws Exception {StatementImporter.Entry e=new StatementImporter.Entry();e.id=o.getString("id");e.cents=o.getLong("cents");e.direction=o.getString("direction");e.merchant=o.getString("merchant");e.category=o.getString("category");e.subcategory=o.optString("subcategory","");e.note=o.optString("note","");e.happened=o.getString("happened");return e;}
    static JSONObject statementJson(StatementImporter.Entry e)throws Exception {JSONObject o=new JSONObject();o.put("id",e.id);o.put("cents",e.cents);o.put("direction",e.direction);o.put("merchant",e.merchant);o.put("category",e.category);o.put("subcategory",e.subcategory);o.put("note",e.note);o.put("happened",e.happened);o.put("raw",e.raw);o.put("source",e.source);o.put("status","confirmed");return o;}
    void previewStatement(StatementImporter.Result parsed)throws Exception {
                JSONObject preview=new JSONObject();preview.put("provider",parsed.provider);preview.put("scanned",parsed.scanned);preview.put("valid",parsed.entries.size());preview.put("skipped",new JSONObject(parsed.skipped));int duplicate=0;long income=0,expense=0;JSONArray sample=new JSONArray();
                try(Store db=new Store(this)){
                    if(parsed.provider.equals("小米记账")){Map<String,ArrayDeque<String>> existing=new HashMap<>();JSONArray all=db.rows(true);for(int n=0;n<all.length();n++){JSONObject o=all.getJSONObject(n);if(!o.getString("status").equals("confirmed"))continue;StatementImporter.Entry old=statementEntry(o);String key=StatementImporter.miKey(old);if(!existing.containsKey(key))existing.put(key,new ArrayDeque<>());existing.get(key).add(old.id);}for(StatementImporter.Entry e:parsed.entries){ArrayDeque<String> ids=existing.get(StatementImporter.miKey(e));if(ids!=null&&!ids.isEmpty())e.id=ids.removeFirst();}}
                    for(StatementImporter.Entry e:parsed.entries){boolean exists;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM entries WHERE id=?",new String[]{e.id})){exists=c.moveToFirst();}if(exists){duplicate++;continue;}if(e.direction.equals("income"))income+=e.cents;else expense+=e.cents;if(sample.length()<8)sample.put(statementJson(e));}}
                preview.put("duplicates",duplicate);preview.put("newCount",parsed.entries.size()-duplicate);preview.put("income",income);preview.put("expense",expense);preview.put("sample",sample);pendingStatement=parsed;js("window.receiveStatementPreview("+preview.toString()+")");
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);js("window.refresh()");}
    static byte[] readLimit(InputStream in,int max)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("文件过大，请选择小于 20 MB 的文件");out.write(b,0,n);}return out.toByteArray();}
    public void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null){if(req==IMAGE)js("window.imageCancelled()");return;}final Uri uri=data.getData();
        new Thread(()->{try{
            if(req==EXPORT){if(pendingExport==null)throw new IOException("导出已失效，请重试");try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入文件");out.write(pendingExport.getBytes("UTF-8"));}pendingExport=null;js("window.notice('备份已导出')");}
            else if(req==MI_EXPORT){if(pendingMiExport==null)throw new IOException("导出已失效，请重试");try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入文件");out.write(pendingMiExport);}pendingMiExport=null;js("window.notice('小米格式 XLSX 已导出')");}
            else if(req==IMAGE){byte[] bytes;try(InputStream in=getContentResolver().openInputStream(uri)){bytes=readLimit(in,20*1024*1024);}js("window.receiveImage("+JSONObject.quote(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))+")");}
            else if(req==STATEMENT){byte[] bytes;try(InputStream in=getContentResolver().openInputStream(uri)){bytes=readLimit(in,20*1024*1024);}
                if(bytes.length>4&&bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F')js("window.receivePdf("+JSONObject.quote(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))+")");
                else previewStatement(StatementImporter.parse(bytes,uri.toString()));

            }
            else if(req==IMPORT){JSONObject file;try(InputStream in=getContentResolver().openInputStream(uri)){file=new JSONObject(new String(readLimit(in,20*1024*1024),"UTF-8"));}if(!"local-ledger-v1".equals(file.optString("format")))throw new IOException("不是本地账本备份文件");JSONArray a=file.getJSONArray("entries");if(a.length()>50000)throw new IOException("备份记录数超限");int count=0;try(Store db=new Store(this)){SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{for(int n=0;n<a.length();n++)if(db.save(a.getJSONObject(n),true))count++;sql.setTransactionSuccessful();}finally{sql.endTransaction();}}js("window.notice('已导入 "+count+" 条新记录');window.refresh()");}
        }catch(Exception e){error(e);}},"ledger-file").start();
    }
    public static class Store extends SQLiteOpenHelper {
        Store(Context c){super(c,"local-ledger.db",null,2);}
        public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY, cents INTEGER NOT NULL, direction TEXT NOT NULL, merchant TEXT NOT NULL, category TEXT NOT NULL, happened TEXT NOT NULL, raw TEXT NOT NULL, source TEXT NOT NULL, status TEXT NOT NULL, created INTEGER NOT NULL, subcategory TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')");}
        public void onUpgrade(SQLiteDatabase db,int a,int b){if(a<2){db.execSQL("ALTER TABLE entries ADD COLUMN subcategory TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE entries ADD COLUMN note TEXT NOT NULL DEFAULT ''");}}
        static String bounded(JSONObject o,String key,int max)throws Exception{String v=o.optString(key,"");if(v.length()>max)throw new Exception(key+" 内容过长");return v;}
        boolean save(JSONObject o,boolean importing)throws Exception {
            String id=o.optString("id","");if(id.isEmpty())id=UUID.randomUUID().toString();if(!id.matches("[a-zA-Z0-9-]{1,128}"))throw new Exception("无效记录编号");
            String direction=o.optString("direction","expense"),status=o.optString("status","confirmed");if(!direction.equals("expense")&&!direction.equals("income"))throw new Exception("无效收支类型");if(!status.equals("confirmed")&&!status.equals("pending"))throw new Exception("无效确认状态");
            long cents;if(o.has("cents")){BigDecimal n=new BigDecimal(o.get("cents").toString());cents=n.longValueExact();}else{cents=new BigDecimal(o.getString("amount")).movePointRight(2).longValueExact();}
            if(cents<0||cents>99999999999L||status.equals("confirmed")&&cents==0&&!o.optString("source").equals("小米记账流水"))throw new Exception("金额必须大于 0，且最多两位小数");
            String happened=bounded(o,"happened",19);if(!happened.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?"))throw new Exception("请填写有效的时间");
            java.text.SimpleDateFormat df=new java.text.SimpleDateFormat(happened.length()==19?"yyyy-MM-dd'T'HH:mm:ss":"yyyy-MM-dd'T'HH:mm",Locale.ROOT);df.setLenient(false);df.parse(happened);
            ContentValues v=new ContentValues();v.put("id",id);v.put("cents",cents);v.put("direction",direction);v.put("merchant",bounded(o,"merchant",200));v.put("category",bounded(o,"category",100));v.put("happened",happened);v.put("raw",bounded(o,"raw",100000));v.put("source",bounded(o,"source",100));v.put("status",status);v.put("created",o.optLong("created",System.currentTimeMillis()));
            v.put("subcategory",bounded(o,"subcategory",100));v.put("note",bounded(o,"note",100000));
            return getWritableDatabase().insertWithOnConflict("entries",null,v,importing?SQLiteDatabase.CONFLICT_IGNORE:SQLiteDatabase.CONFLICT_REPLACE)!=-1;
        }
        JSONArray rows(boolean all)throws Exception {JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM entries ORDER BY happened DESC,created DESC"+(all?"":" LIMIT 500"),null)){while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++){String k=c.getColumnName(i);o.put(k,k.equals("cents")||k.equals("created")?c.getLong(i):c.getString(i));}a.put(o);}}return a;}
        JSONObject summary()throws Exception{JSONObject o=new JSONObject();String month=new java.text.SimpleDateFormat("yyyy-MM",Locale.ROOT).format(new Date());try(Cursor c=getReadableDatabase().rawQuery("SELECT direction,SUM(cents) FROM entries WHERE status='confirmed' AND substr(happened,1,7)=? GROUP BY direction",new String[]{month})){while(c.moveToNext())o.put(c.getString(0),c.getLong(1));}try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*),SUM(CASE WHEN status='pending' THEN 1 ELSE 0 END) FROM entries",null)){c.moveToFirst();o.put("total",c.getLong(0));o.put("pending",c.getLong(1));}return o;}
    }
}
