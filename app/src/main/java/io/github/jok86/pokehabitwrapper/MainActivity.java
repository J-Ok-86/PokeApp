package io.github.jok86.pokehabitwrapper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

/** Thin remote GitHub Pages WebView wrapper; native code only owns reminders and file handoff. */
public final class MainActivity extends Activity {
    private static final String APP_URL="https://j-ok-86.github.io/PokeHabit/";
    private static final String TRUSTED_ORIGIN="https://j-ok-86.github.io";
    private static final int PICK_IMPORT=4101,SAVE_JSON=4102,ASK_NOTIFICATIONS=4103;
    private WebView web;
    private ValueCallback<Uri[]> filePicker;
    private FileOutputStream downloadStream;
    private File pendingDownload;
    private String downloadToken="",downloadName="";
    private long downloadExpected,downloadReceived;
    private boolean bridgeAvailable;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AlarmStore.ensureChannel(this);
        web = new WebView(this);
        // Android 15+ enforces edge-to-edge for targetSdk 35. Keep the HTML
        // below the system status bar (and above the navigation bar) instead
        // of allowing the status icons to overlap PokeHabit's clock/weather.
        android.widget.FrameLayout appRoot = new android.widget.FrameLayout(this);
        appRoot.setBackgroundColor(android.graphics.Color.rgb(14, 33, 59));
        appRoot.addView(web, new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 35) {
            appRoot.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(appRoot);
        WebSettings ws=web.getSettings();
        // Chrome-like mobile viewport instead of WebView's default narrow-column layout.
        // Keep the HTML's own small/normal/large/xlarge preference untouched.
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(false);
        ws.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        // Android WebView text rendering can be smaller than Chrome. This is a
        // wrapper-only readable baseline, not an app preference or JSON setting.
        int deviceTextZoom = Math.round(getResources().getConfiguration().fontScale * 100f);
        ws.setTextZoom(Math.max(115, Math.min(140, deviceTextZoom)));
        ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(false);ws.setAllowUniversalAccessFromFileURLs(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);ws.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                Uri uri=request.getUrl();
                if(isTrustedAppPath(uri))return false;
                openExternal(uri);return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(filePicker!=null)filePicker.onReceiveValue(null);
                filePicker=cb;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
                try{startActivityForResult(intent,PICK_IMPORT);}catch(Exception e){filePicker.onReceiveValue(null);filePicker=null;show("파일 선택기를 열 수 없습니다.");}
                return true;
            }
        });
        CookieManager.getInstance().setAcceptCookie(true);
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
            WebViewCompat.addWebMessageListener(web,"PokeNative",Collections.singleton(TRUSTED_ORIGIN),
                (view,message,origin,isMainFrame,reply)->{
                    if(!isMainFrame||origin==null||!TRUSTED_ORIGIN.equals(origin.toString())||!isTrustedAppPath(Uri.parse(view.getUrl()==null?"":view.getUrl())))return;
                    String raw=message.getData();
                    runOnUiThread(()->handleMessage(raw,reply));
                });
            bridgeAvailable=true;
        } else {
            show("Android System WebView 업데이트가 필요합니다.");
        }
        if(state==null)web.loadUrl(APP_URL);else web.restoreState(state);
    }
    private boolean isTrustedAppPath(Uri u){
        return u!=null&&"https".equalsIgnoreCase(u.getScheme())&&"j-ok-86.github.io".equalsIgnoreCase(u.getHost())
            &&(u.getPort()==-1||u.getPort()==443)&&((u.getPath()!=null&&u.getPath().startsWith("/PokeHabit/"))||"/PokeHabit".equals(u.getPath()));
    }
    private void openExternal(Uri uri){
        if(uri==null)return;
        String scheme=uri.getScheme();
        if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return;
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){show("외부 페이지를 열 수 없습니다.");}
    }
    private void reply(androidx.webkit.JavaScriptReplyProxy proxy,JSONObject obj){
        try{proxy.postMessage(obj.toString());}catch(Exception ignored){}
    }
    private JSONObject response(String type,String message){
        JSONObject obj=new JSONObject();try{obj.put("type",type);obj.put("message",message);}catch(Exception ignored){}return obj;
    }
    private void status(androidx.webkit.JavaScriptReplyProxy proxy){
        JSONObject obj=response("status","");
        try{obj.put("exact",AlarmStore.exactGranted(this));obj.put("notifications",AlarmStore.notificationsGranted(this));obj.put("notice",AlarmStore.status(this));}catch(Exception ignored){}
        reply(proxy,obj);
    }
    private void handleMessage(String raw,androidx.webkit.JavaScriptReplyProxy proxy){
        try{
            JSONObject msg=new JSONObject(raw);
            String type=msg.optString("type");
            switch(type){
                case "sync": {
                    String user=msg.optString("userId");
                    if(!user.matches("[a-zA-Z0-9_-]{2,24}"))return;
                    JSONArray items=msg.optJSONArray("items");if(items==null)return;
                    int n=AlarmStore.replace(this,user,items);
                    status(proxy);break;
                }
                case "permissions": requestAlarmPermissions();status(proxy);break;
                case "status":status(proxy);break;
                case "test":
                    if(AlarmStore.notificationsGranted(this)){AlarmStore.test(this);reply(proxy,response("info","테스트 알림을 보냈습니다. 휴대폰 알림창에서 확인하세요."));}
                    else reply(proxy,response("info","알림 권한이 없습니다. 먼저 권한 요청을 눌러 주세요."));
                    break;
                case "clear":AlarmStore.replace(this,"",new JSONArray());status(proxy);break;
                case "exit":finish();break;
                case "fileBegin": fileBegin(msg,proxy);break;
                case "fileChunk": fileChunk(msg,proxy);break;
                case "fileEnd": fileEnd(msg,proxy);break;
                default:break;
            }
        }catch(Exception e){reply(proxy,response("info","Android 연결 오류: "+String.valueOf(e.getMessage()).substring(0,Math.min(120,String.valueOf(e.getMessage()).length()))));}
    }
    private void requestAlarmPermissions(){
        if(Build.VERSION.SDK_INT>=33&&!AlarmStore.notificationsGranted(this)){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},ASK_NOTIFICATIONS);return;
        }
        requestExactPermission();
    }
    private void requestExactPermission(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S&&!AlarmStore.exactGranted(this)){
            try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
            catch(Exception e){show("정확한 알람 권한은 휴대폰 설정에서 허용할 수 있습니다.");}
        }else show("알림 권한이 이미 허용되어 있습니다.");
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==ASK_NOTIFICATIONS){
            if(AlarmStore.notificationsGranted(this))requestExactPermission();
            else show("알림이 차단되어 있습니다. 앱 설정에서 알림을 허용하세요.");
            updateStatusInWeb();
        }
    }
    private void updateStatusInWeb(){
        if(web==null||!bridgeAvailable||!isTrustedAppPath(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;
        JSONObject obj=response("status","");
        try{obj.put("exact",AlarmStore.exactGranted(this));obj.put("notifications",AlarmStore.notificationsGranted(this));obj.put("notice",AlarmStore.status(this));}catch(Exception ignored){}
        web.evaluateJavascript("if(typeof nativeAlarmMessage==='function')nativeAlarmMessage({data:"+JSONObject.quote(obj.toString())+"})",null);
    }
    @Override protected void onResume(){super.onResume();AlarmStore.rescheduleAll(this);updateStatusInWeb();}
    private void fileBegin(JSONObject msg,androidx.webkit.JavaScriptReplyProxy proxy)throws Exception{
        closeStream();
        String token=msg.optString("token"),name=msg.optString("name"),safe=name.replaceAll("[^a-zA-Z0-9._-]","_");
        long size=msg.optLong("size",-1);
        if(!token.matches("backup_[a-zA-Z0-9_]{5,60}")||size<0||size>100L*1024*1024)throw new IllegalArgumentException("잘못된 파일 전송 요청");
        if(!safe.endsWith(".json"))safe="pokehabit-backup.json";
        downloadToken=token;downloadName=safe;downloadExpected=size;downloadReceived=0;
        pendingDownload=File.createTempFile("pokehabit-json-",".json",getCacheDir());
        downloadStream=new FileOutputStream(pendingDownload);
    }
    private void fileChunk(JSONObject msg,androidx.webkit.JavaScriptReplyProxy proxy)throws Exception{
        if(downloadStream==null||!downloadToken.equals(msg.optString("token")))throw new IllegalStateException("파일 전송이 시작되지 않음");
        byte[] chunk=Base64.decode(msg.optString("data"),Base64.DEFAULT);
        if(chunk.length>24576||downloadReceived+chunk.length>downloadExpected)throw new IllegalArgumentException("파일 크기 초과");
        downloadStream.write(chunk);downloadReceived+=chunk.length;
    }
    private void fileEnd(JSONObject msg,androidx.webkit.JavaScriptReplyProxy proxy)throws Exception{
        if(downloadStream==null||!downloadToken.equals(msg.optString("token"))||downloadReceived!=downloadExpected)throw new IllegalStateException("파일이 전부 전달되지 않음");
        closeStream();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json").putExtra(Intent.EXTRA_TITLE,downloadName);
        startActivityForResult(intent,SAVE_JSON);
    }
    private void closeStream(){
        if(downloadStream!=null){try{downloadStream.close();}catch(Exception ignored){}downloadStream=null;}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent result){
        super.onActivityResult(requestCode,resultCode,result);
        if(requestCode==PICK_IMPORT){
            if(filePicker!=null){
                Uri[] uris=null;
                if(resultCode==RESULT_OK&&result!=null){
                    if(result.getClipData()!=null){int n=result.getClipData().getItemCount();uris=new Uri[n];for(int i=0;i<n;i++)uris[i]=result.getClipData().getItemAt(i).getUri();}
                    else if(result.getData()!=null)uris=new Uri[]{result.getData()};
                }
                filePicker.onReceiveValue(uris);filePicker=null;
            }
        }else if(requestCode==SAVE_JSON){
            if(resultCode==RESULT_OK&&result!=null&&result.getData()!=null&&pendingDownload!=null){
                try(InputStream in=new FileInputStream(pendingDownload);OutputStream out=getContentResolver().openOutputStream(result.getData())){
                    if(out==null)throw new IllegalStateException("대상 파일에 접근 불가");
                    byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                    show("JSON 백업을 저장했습니다.");
                }catch(Exception e){show("JSON 저장 실패: "+e.getMessage());}
            }
            if(pendingDownload!=null){pendingDownload.delete();pendingDownload=null;}
        }
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);}
    @Override protected void onSaveInstanceState(Bundle out){if(web!=null)web.saveState(out);super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){
        if(web!=null&&isTrustedAppPath(Uri.parse(web.getUrl()==null?"":web.getUrl()))){
            web.evaluateJavascript("(function(){if(typeof prBackAction==='function'&&typeof startUnlocked!=='undefined'&&startUnlocked&&currentUserId){prBackAction();return true}return false})()", result->{
                if(!"true".equals(result)){if(web!=null&&web.canGoBack())web.goBack();else finish();}
            });
        }else if(web!=null&&web.canGoBack())web.goBack();else finish();
    }
    @Override protected void onDestroy(){closeStream();if(web!=null){web.destroy();web=null;}super.onDestroy();}
    private void show(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
}
