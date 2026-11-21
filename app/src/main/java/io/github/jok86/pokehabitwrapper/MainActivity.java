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
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Collections;

/** Thin remote GitHub Pages WebView wrapper; native code only owns reminders and file handoff. */
public final class MainActivity extends Activity {
    private static final String APP_URL="https://j-ok-86.github.io/PokeHabit/";
    private static final String TRUSTED_ORIGIN="https://j-ok-86.github.io";
    private static final int PICK_IMPORT=4101,SAVE_JSON=4102,ASK_NOTIFICATIONS=4103,ASK_GEOLOCATION=4104,ASK_WALK=4105;
    private WebView web;
    private android.widget.FrameLayout appRoot;
    private ValueCallback<Uri[]> filePicker;
    private FileOutputStream downloadStream;
    private File pendingDownload;
    private String downloadToken="",downloadName="";
    private long downloadExpected,downloadReceived;
    private boolean bridgeAvailable;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private boolean geoSystemPermissionDialogActive;
    private String pendingWalkUser="";
    private String pendingBriefingKind="",pendingBriefingUser="";
    private boolean webRefreshInProgress=false;
    private String checkedWebVersion="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        captureBriefingIntent(getIntent());
        AlarmStore.ensureChannel(this);
        web = new WebView(this);
        // Android 15+ draws system bars edge-to-edge. The native root owns the
        // insets; the HTML must NOT add a second safe-area footer spacer.
        appRoot = new android.widget.FrameLayout(this);
        applySystemBarTheme(true); // PokeRoutine's initial light theme.
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
        // Match Chrome PWA's CSS viewport and text sizing exactly. The old
        // 115-140% text zoom + TEXT_AUTOSIZING made small fonts appear huge.
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(false);
        ws.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        ws.setTextZoom(100);
        ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);
        ws.setGeolocationEnabled(true);
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
            @Override public void onPageFinished(WebView view,String url){
                super.onPageFinished(view,url);
                if(isTrustedAppPath(Uri.parse(url))){
                    // The native frame has ALREADY consumed the Android navigation
                    // bar inset. Prevent Web CSS from reserving it a second time.
                    view.evaluateJavascript(NATIVE_APPEARANCE_SCRIPT,null);
                    if(webRefreshInProgress){
                        webRefreshInProgress=false;
                        view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                        view.evaluateJavascript("document.title",title->{
                            if(title!=null&&title.contains("v"+checkedWebVersion))
                                show("웹 v"+checkedWebVersion+" 새로고침 완료. 기록 데이터는 유지됩니다.");
                            else
                                show("새 페이지의 버전이 확인되지 않습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.");
                        });
                    }
                    // Remove a previous WebView-origin denial left by older versions.
                    // We do not retain new grants, and Android's runtime permission still rules.
                    GeolocationPermissions.getInstance().clear(TRUSTED_ORIGIN);
                    GeolocationPermissions.getInstance().clear(TRUSTED_ORIGIN+"/");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback){
                // The WebView supplies an ORIGIN, not necessarily the literal URL string.
                // For example, https://j-ok-86.github.io/ and :443 are the same trusted
                // HTTPS origin. Never grant a different host, scheme, port or top-level page.
                if(!TrustedGeoOrigin.accepts(origin,web==null?null:web.getUrl())){
                    Log.w("PokeGeo","WebView location denied: untrusted origin or main frame");
                    callback.invoke(origin,false,false);return;
                }
                if(hasLocationPermission()){
                    Log.i("PokeGeo","WebView location granted from existing Android permission");
                    callback.invoke(origin,true,false);return;
                }
                // Prompt only when a trusted page asks for location.
                // Retain=false keeps the WebView decision tied to Android permissions.
                if(pendingGeoCallback!=null){
                    Log.w("PokeGeo","Superseded pending WebView geolocation prompt");
                    clearPendingGeolocation(false);
                }
                pendingGeoOrigin=origin;
                pendingGeoCallback=callback;
                if(Build.VERSION.SDK_INT>=23){
                    geoSystemPermissionDialogActive=true;
                    try {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION},ASK_GEOLOCATION);
                    }catch(Exception e){
                        geoSystemPermissionDialogActive=false;
                        Log.e("PokeGeo","Android location permission request failed",e);
                        clearPendingGeolocation(false);
                    }
                }else{
                    clearPendingGeolocation(hasLocationPermission());
                }
            }
            @Override public void onGeolocationPermissionsHidePrompt(){
                // A system permission dialog may temporarily hide the WebView prompt.
                // Do not discard its callback until Android returns the permission result.
                if(geoSystemPermissionDialogActive){
                    Log.i("PokeGeo","Keeping pending callback while Android permission dialog is active");
                    return;
                }
                clearPendingGeolocation(false);
            }
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
                    if(!isMainFrame||origin==null||!TrustedGeoOrigin.accepts(origin.toString(),view.getUrl()))return;
                    String raw=message.getData();
                    runOnUiThread(()->handleMessage(raw,reply));
                });
            bridgeAvailable=true;
        } else {
            show("Android System WebView 업데이트가 필요합니다.");
        }
        if(state==null)web.loadUrl(APP_URL);else web.restoreState(state);
    }
    // The WebView uses the PWA's existing HTML/CSS. Only window decoration
    // and the double-counted Android safe-area padding are adapted here.
    private static final String NATIVE_APPEARANCE_SCRIPT =
        "(function(){"+
        "var root=document.documentElement;"+
        "root.style.setProperty('--nav-safe','0px');"+
        "if(window.__pokeNativeAppearanceInstalled)return;"+
        "window.__pokeNativeAppearanceInstalled=true;"+
        "function sync(){if(window.PokeNative&&typeof PokeNative.postMessage==='function'){"+
        "PokeNative.postMessage(JSON.stringify({type:'appearance',theme:root.dataset.theme==='dark'?'dark':'light'}));}}"+
        "new MutationObserver(sync).observe(root,{attributes:true,attributeFilter:['data-theme']});"+
        "sync();"+
        "})()";

    private void applySystemBarTheme(boolean light){
        int bg=Color.parseColor(light?"#f9fcff":"#0b1d3a");
        if(appRoot!=null)appRoot.setBackgroundColor(bg);
        if(web!=null)web.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=getWindow().getInsetsController();
            if(c!=null){
                int flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light?flags:0,flags);
            }
        }else{
            View decor=getWindow().getDecorView();
            int bits=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if(Build.VERSION.SDK_INT>=26)bits|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility((decor.getSystemUiVisibility()&~bits)|(light?bits:0));
        }
    }

    private void captureBriefingIntent(Intent intent){
        if(intent==null)return;
        String kind=intent.getStringExtra("briefing_kind"),user=intent.getStringExtra("briefing_user");
        if(("morning".equals(kind)||"evening".equals(kind))&&user!=null&&user.matches("[a-zA-Z0-9_-]{2,24}")){
            pendingBriefingKind=kind;pendingBriefingUser=user;
        }
    }
    private void addPendingBriefing(JSONObject obj){
        if(pendingBriefingKind.isEmpty()||pendingBriefingUser.isEmpty())return;
        try{obj.put("openBriefing",pendingBriefingKind);obj.put("openBriefingUser",pendingBriefingUser);
            pendingBriefingKind="";pendingBriefingUser="";}catch(Exception ignored){}
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);captureBriefingIntent(intent);
        // Ask JS to fetch the pending intent. Do not consume it before the page/bridge is ready.
        if(web!=null&&isTrustedAppPath(Uri.parse(web.getUrl()==null?"":web.getUrl())))
            web.evaluateJavascript("if(typeof nativeAlarmStatusRequest==='function')nativeAlarmStatusRequest()",null);
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
        try{obj.put("exact",AlarmStore.exactGranted(this));obj.put("notifications",AlarmStore.notificationsGranted(this));obj.put("notice",AlarmStore.status(this));obj.put("webRefreshSupported",true);obj.put("usageAccess",SleepEstimator.permission(this));
            if(SleepEstimator.permission(this))obj.put("sleepEstimates",SleepEstimator.estimates(this));
            obj.put("widgetLogs",NativeLogStore.pending(this,NativeLogStore.activeUser(this)));
            obj.put("walkLogs",WalkTracker.pending(this,NativeLogStore.activeUser(this)));
            obj.put("walkPermission",WalkTracker.permissionGranted(this));
            obj.put("walkSensor",WalkTracker.stepSensorSupported(this));
            JSONObject walking=WalkTracker.session(this,NativeLogStore.activeUser(this));
            if(walking!=null)obj.put("walkSession",walking);}catch(Exception ignored){}
        addPendingBriefing(obj);reply(proxy,obj);
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
                    JSONArray buttons=msg.optJSONArray("widgetButtons");
                    if(buttons!=null)NativeLogStore.configure(this,user,buttons,msg.optJSONArray("widgetRecords"));
                    status(proxy);break;
                }
                case "permissions": requestAlarmPermissions();status(proxy);break;
                case "usagePermission":
                    try{startActivity(SleepEstimator.permissionSettings(this));}
                    catch(Exception e){startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}
                    status(proxy);break;
                case "walkPermission":{
                    if(!WalkTracker.stepSensorSupported(this)){
                        reply(proxy,response("info","이 기기는 TYPE_STEP_COUNTER 걸음 센서를 지원하지 않습니다. 걷기 시간은 기록됩니다."));
                    }else if(android.os.Build.VERSION.SDK_INT>=29&&!WalkTracker.permissionGranted(this)){
                        pendingWalkUser="";
                        requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},ASK_WALK);
                    }else status(proxy);
                    break;
                }
                case "walkToggle":{
                    String actor=msg.optString("userId");
                    if(!actor.equals(NativeLogStore.activeUser(this))){reply(proxy,response("info","걷기 기록을 위해 앱에서 먼저 계정을 선택해 주세요."));break;}
                    if(WalkTracker.session(this,actor)==null&&android.os.Build.VERSION.SDK_INT>=29&&
                        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){
                        pendingWalkUser=actor;
                        requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},ASK_WALK);
                        break;
                    }
                    WalkTracker.toggle(this,actor);TimeLogWidget.refresh(this);status(proxy);break;
                }
                case "ackWalkLogs":{
                    String actor=msg.optString("userId");JSONArray ids=msg.optJSONArray("ids");
                    if(actor.equals(NativeLogStore.activeUser(this))&&ids!=null)WalkTracker.acknowledge(this,actor,ids);
                    break;
                }
                case "ackWidgetLogs":{
                    String actor=msg.optString("userId");JSONArray ids=msg.optJSONArray("ids");
                    if(actor.equals(NativeLogStore.activeUser(this))&&ids!=null)NativeLogStore.acknowledge(this,actor,ids);
                    break;
                }
                case "appearance":
                    applySystemBarTheme(!"dark".equals(msg.optString("theme","light")));
                    break;
                case "status":status(proxy);break;
                case "refreshWeb":refreshWebSafely(proxy);break;
                case "test":
                    if(AlarmStore.notificationsGranted(this)){AlarmStore.test(this);reply(proxy,response("info","테스트 알림을 보냈습니다. 휴대폰 알림창에서 확인하세요."));}
                    else reply(proxy,response("info","알림 권한이 없습니다. 먼저 권한 요청을 눌러 주세요."));
                    break;
                case "clear":AlarmStore.replace(this,"",new JSONArray());status(proxy);break;
                case "shareText":{
                    // A deliberate user click in the trusted GitHub Pages WebView is required.
                    String shareText=msg.optString("text", "");
                    String shareTitle=msg.optString("title", "PokeHabit 기록 분석");
                    if(shareText.isEmpty()||shareText.length()>60000){
                        reply(proxy,response("info","공유할 기록이 너무 길거나 비어 있어요. 기간을 조정해 주세요."));
                        break;
                    }
                    Intent send=new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_SUBJECT,shareTitle);
                    send.putExtra(Intent.EXTRA_TEXT,shareText);
                    try{startActivity(Intent.createChooser(send,"기록 공유"));}
                    catch(Exception e){reply(proxy,response("info","공유 메뉴를 열 수 없습니다. 복사를 이용해 주세요."));}
                    break;
                }
                case "exit":finish();break;
                case "fileBegin": fileBegin(msg,proxy);break;
                case "fileChunk": fileChunk(msg,proxy);break;
                case "fileEnd": fileEnd(msg,proxy);break;
                default:break;
            }
        }catch(Exception e){reply(proxy,response("info","Android 연결 오류: "+String.valueOf(e.getMessage()).substring(0,Math.min(120,String.valueOf(e.getMessage()).length()))));}
    }
    // v1.0.14: Verify GitHub Pages availability/version BEFORE touching the cache.
    // WebView.clearCache(true) is HTTP resource cache only: NEVER clear WebStorage,
    // cookies, app data or the offline cache when remote verification fails.
    private void refreshWebSafely(androidx.webkit.JavaScriptReplyProxy proxy){
        if(webRefreshInProgress){reply(proxy,response("webRefresh","이미 새로고침을 진행하고 있습니다."));return;}
        webRefreshInProgress=true;
        reply(proxy,response("webRefresh","GitHub Pages에서 최신 버전을 확인하는 중입니다…"));
        new Thread(()->{
            String remoteVersion="";
            String error="";
            HttpURLConnection connection=null;
            try{
                URL url=new URL(APP_URL+"index.html?verify="+System.currentTimeMillis());
                connection=(HttpURLConnection)url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control","no-cache, no-store");
                if(connection.getResponseCode()!=200)throw new java.io.IOException("서버 응답 "+connection.getResponseCode());
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()){
                    byte[] buffer=new byte[2048];int read;
                    while(out.size()<16384&&(read=in.read(buffer))!=-1)out.write(buffer,0,Math.min(read,16384-out.size()));
                }
                String html=out.toString(StandardCharsets.UTF_8.name());
                Matcher match=Pattern.compile("<title>[^<]*?v(\\d+\\.\\d+\\.\\d+)",Pattern.CASE_INSENSITIVE).matcher(html);
                if(!match.find())throw new java.io.IOException("웹 버전 정보가 확인되지 않습니다.");
                remoteVersion=match.group(1);
            }catch(Exception e){error=e.getMessage()==null?"네트워크 확인 실패":e.getMessage();}
            finally{if(connection!=null)connection.disconnect();}
            final String version=remoteVersion,failed=error;
            runOnUiThread(()->{
                if(web==null){webRefreshInProgress=false;return;}
                if(!failed.isEmpty()){
                    webRefreshInProgress=false;
                    sendWebRefreshResult("최신 버전 확인 실패: "+failed+" · 기존 화면과 기록을 그대로 유지합니다.");
                    return;
                }
                checkedWebVersion=version;
                sendWebRefreshResult("서버 웹 v"+version+" 확인 · 화면을 새로 불러오는 중입니다…");
                // This does NOT erase localStorage, IndexedDB, preferences, or cookies.
                web.clearCache(true);
                web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
                web.loadUrl(APP_URL+"?web_refresh="+System.currentTimeMillis());
            });
        },"PokeHabit-WebRefresh").start();
    }
    private void sendWebRefreshResult(String message){
        if(web==null||!isTrustedAppPath(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;
        JSONObject result=response("webRefresh",message);
        web.evaluateJavascript("if(typeof nativeAlarmMessage==='function')nativeAlarmMessage({data:"+JSONObject.quote(result.toString())+"})",null);
    }
    private boolean hasLocationPermission(){
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
            ||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void clearPendingGeolocation(boolean granted){
        GeolocationPermissions.Callback cb=pendingGeoCallback;
        String origin=pendingGeoOrigin;
        pendingGeoCallback=null;
        pendingGeoOrigin=null;
        if(cb!=null&&origin!=null)cb.invoke(origin,granted,false);
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
        if(requestCode==ASK_GEOLOCATION){
            geoSystemPermissionDialogActive=false;
            boolean allowed=hasLocationPermission();
            Log.i("PokeGeo", allowed ? "Android location allowed; returning WebView grant" :
                "Android location denied; returning WebView denial");
            clearPendingGeolocation(allowed);
            if(!allowed)show("위치를 사용하려면 PokeHabit 위치 권한을 허용해 주세요.");
        }else if(requestCode==ASK_WALK){
            String actor=pendingWalkUser;pendingWalkUser="";
            if(!actor.isEmpty()&&actor.equals(NativeLogStore.activeUser(this))){
                // Even after denial, keep a manual elapsed-time session; steps remain "unmeasured".
                WalkTracker.toggle(this,actor);TimeLogWidget.refresh(this);
            }
            WalkTracker.restoreSensor(this);
            updateStatusInWeb();
        }else if(requestCode==ASK_NOTIFICATIONS){
            if(AlarmStore.notificationsGranted(this))requestExactPermission();
            else show("알림이 차단되어 있습니다. 앱 설정에서 알림을 허용하세요.");
            updateStatusInWeb();
        }
    }
    private void updateStatusInWeb(){
        if(web==null||!bridgeAvailable||!isTrustedAppPath(Uri.parse(web.getUrl()==null?"":web.getUrl())))return;
        JSONObject obj=response("status","");
        try{obj.put("exact",AlarmStore.exactGranted(this));obj.put("notifications",AlarmStore.notificationsGranted(this));obj.put("notice",AlarmStore.status(this));obj.put("webRefreshSupported",true);obj.put("usageAccess",SleepEstimator.permission(this));
            if(SleepEstimator.permission(this))obj.put("sleepEstimates",SleepEstimator.estimates(this));
            obj.put("widgetLogs",NativeLogStore.pending(this,NativeLogStore.activeUser(this)));
            obj.put("walkLogs",WalkTracker.pending(this,NativeLogStore.activeUser(this)));
            obj.put("walkPermission",WalkTracker.permissionGranted(this));
            obj.put("walkSensor",WalkTracker.stepSensorSupported(this));
            JSONObject walking=WalkTracker.session(this,NativeLogStore.activeUser(this));
            if(walking!=null)obj.put("walkSession",walking);}catch(Exception ignored){}
        web.evaluateJavascript("if(typeof nativeAlarmMessage==='function')nativeAlarmMessage({data:"+JSONObject.quote(obj.toString())+"})",null);
    }
    @Override protected void onResume(){super.onResume();AlarmStore.rescheduleAll(this);WalkTracker.restoreSensor(this);updateStatusInWeb();}
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
    @Override protected void onDestroy(){
        geoSystemPermissionDialogActive=false;
        clearPendingGeolocation(false);
        closeStream();if(web!=null){web.destroy();web=null;}super.onDestroy();
    }
    private void show(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
}
