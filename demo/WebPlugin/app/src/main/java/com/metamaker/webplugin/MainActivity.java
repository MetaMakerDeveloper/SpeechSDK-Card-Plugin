package com.metamaker.webplugin;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.metamaker.speechlibrary.DialogModule.ASRManager;
import com.metamaker.speechlibrary.Interface.ASRListener;
import com.metamaker.speechlibrary.UtilTools.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private String TAG = "MainActivity";
    private WebView webView;
    // 此处填写 AI 数字人名片服务部署的地址
    private String webUrl = "https://xxxxxx";
    private ASRManager asrManager;
    private Map<String, String> params;

    /**
     * 创建用于绑定，此处为提供给前端调用的方法，可自定义
     * 前端调用方法：window.android.callAndroidFunction('xxx',[])
     */
    public class JavaScriptInterface {
        @JavascriptInterface
        public void callAndroidFunction(String functionName, String[] arguments) {
            Log.d(TAG, "callAndroidFunction: " + functionName);

            switch (functionName) {
                case "asrEngineInit":
                    runOnUiThread(()-> asrManager.init(params));
                    break;
                case "startWakeup":
                    runOnUiThread(() -> asrManager.startWakeup());
                    break;
                case "stopWakeup":
                    runOnUiThread(() -> asrManager.stopWakeup());
                    break;
                case "startASR":
                    runOnUiThread(() -> asrManager.startASR());
                    break;
                case "stopASR":
                    runOnUiThread(() -> asrManager.stopASR());
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取 SDK 所需权限，必须调用权限获取
        PermissionHelper.checkPermission(this);

        // 开启 web调试模式
        WebView.setWebContentsDebuggingEnabled(true);
        webView = findViewById(R.id.webView);

        getAppInfo();
    }

    /**
     * 请求接口获取用于 SDK 的信息
     * url: https://human-screen-v3.metamaker.cn/t_device/bind
     * Content-Type: application/json
     * params:
     *      app_key             后台 appKey
     *      device_no           设备唯一标识，可填 AndroidId
     *      sys_version         SDK 版本号（选填）
     *      hardware_version    硬件版本号（选填），可填 Build.DISPLAY
     */
    private void getAppInfo() {
        new Thread(() -> {
            OkHttpClient okHttpClient = new OkHttpClient();

            String requestUrl = "https://human-screen-v3.metamaker.cn/t_device/bind";
            String appKey = "xxxxxx"; // xxxxxx为需要替换的appKey
            // 填写 AndroidId
            String deviceNo = Settings.System.getString(this.getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            // 填写 SDK 版本
            String sysVersion = "1.0.0";
            String hardwareVersion = Build.DISPLAY;

            Map map = new HashMap();
            map.put("app_key", appKey);
            map.put("device_no", deviceNo);
            map.put("sys_version", sysVersion);
            map.put("hardware_version", hardwareVersion);
            JSONObject bodyObj = new JSONObject(map);
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyObj.toString());

            Request request = new Request.Builder().url(requestUrl)
                    .post(requestBody)
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.d(TAG, "onFailure: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == 200) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "onResponse: " + responseBody);
                        try {
                            JSONObject object = new JSONObject(responseBody);
                            JSONObject retObj = object.getJSONObject("ret");
                            String apiKey = retObj.getString("api_key");
                            String productId = retObj.getString("id");
                            String productKey = retObj.getString("key");
                            String productSecret = retObj.getString("secret");

                            initAsrData(deviceNo, apiKey, productId, productKey, productSecret);
                            runOnUiThread(() -> {
                                initAsrManager();
                                initWebView();
                            });

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.d(TAG, "onResponse: code :" + response.code() + ", message : " + response.message());
                    }
                }
            });
        }).start();
    }

    /**
     * 封装语音 SDK 需要的数据
     * @param deviceId
     * @param apiKey
     * @param productId
     * @param productKey
     * @param productSecret
     */
    private void initAsrData(String deviceId, String apiKey, String productId, String productKey, String productSecret){
        // 设置唤醒词
        String wakeupWord = "你好小镜";
        // 设置唤醒词拼音
        String wakeupWordPinyin = "ni hao xiao jing";

        // 创建热词，格式必须如下所示
        JSONObject hotWordObject = new JSONObject();
        JSONArray hotArray = new JSONArray();
        hotArray.put("黑镜");
        hotArray.put("小黑");
        hotArray.put("大黑");
        try {
            // 热词需要分组
            hotWordObject.put("common", hotArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "onCreate: " + hotWordObject.toString());

        params = new HashMap<>();
        params.put("apiKey", apiKey);
        params.put("productId", productId);
        params.put("productKey", productKey);
        params.put("productSecret", productSecret);
        params.put("deviceId", deviceId);
        params.put("wakeupWord", wakeupWord);
        params.put("wakeupWordPinyin", wakeupWordPinyin);
        // 热词项为非必选项，可以不添加
        params.put("hotWord", hotWordObject.toString());
    }

    /**
     * 初始化 ASR 模块
     */
    private void initAsrManager() {
        // 实例化 ASR 模块
        asrManager = new ASRManager(this.getApplicationContext(), new ASRListener() {
            @Override
            public void onStartInit() {
                sendMsgToWeb("AsrState", "init", "start", null, false);
            }

            @Override
            public void onStopInit() {
                sendMsgToWeb("AsrState", "init", "stop", null, false);
            }

            @Override
            public void onInitError(String message) {

            }

            @Override
            public void onStartWakeup() {
                sendMsgToWeb("AsrState", "wakeup", "start", null, false);
            }

            @Override
            public void onWaekupSuccess() {
                sendMsgToWeb("AsrState", "wakeup", "success", null, false);
            }

            @Override
            public void onStopWakeup() {
                sendMsgToWeb("AsrState", "wakeup", "stop", null, false);
            }

            @Override
            public void onStartAsr() {
                sendMsgToWeb("AsrState", "asr", "start", null, false);
            }

            @Override
            public void onListeningAsr(String message) {
                sendMsgToWeb("AsrState", "asr", "listening", message, false);
            }

            @Override
            public void onRecognizingAsr() {
                sendMsgToWeb("AsrState", "asr", "recognizing", null, false);
            }

            @Override
            public void onStopAsr(String message) {
                sendMsgToWeb("AsrState", "asr", "stop", message, true);
            }
        });
    }

    /**
     * 初始化 webview
     */
    private void initWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webView.addJavascriptInterface(new JavaScriptInterface(), "android");
        webView.setWebViewClient(new WebViewClient() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.loadUrl(webUrl);
    }

    /**
     * 将消息传给页面
     * @param eventName 发送给页面的事件名
     * @param action 执行动作
     * @param state 状态
     * @param result 结果
     * @param resultIsJson 返回结果是否是 json 字符串
     */
    private void sendMsgToWeb(String eventName, String action, String state, String result, boolean resultIsJson){
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("action", action);
            jsonObject.put("state", state);
            jsonObject.put("result", result);
            if (resultIsJson) {
                jsonObject.put("result", new JSONObject(result));
            } else {
                jsonObject.put("result", result);
            }
            String jsonString = jsonObject.toString();
            // 此处为前端提供的方法，可自定义
            String commandStr = String.format("javasript:window.PubSub.publish('%s', '%s')", eventName, jsonString);
            Log.d(TAG, "sendMsgToWeb commandStr: " + commandStr);
            runOnUiThread(() -> {
                webView.evaluateJavascript(commandStr, null);
            });
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


}