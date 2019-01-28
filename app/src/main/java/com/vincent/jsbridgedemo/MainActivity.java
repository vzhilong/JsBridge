package com.vincent.jsbridgedemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.vincent.jsbridge.BridgeHandler;
import com.vincent.jsbridge.BridgeMethodExistCallback;
import com.vincent.jsbridge.BridgeResponseCallback;
import com.vincent.jsbridge.BridgeWebView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BridgeWebView webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new BridgeWebView.BridgeChromeClient(webView));
        webView.hasJavascriptMethod("callJsNotExists", new BridgeMethodExistCallback() {
            @Override
            public void onResult(boolean exist) {
                Log.d("MainActivity", "callJsNotExists:" + exist);
            }
        });

        webView.hasJavascriptMethod("callJsExists", new BridgeMethodExistCallback() {
            @Override
            public void onResult(boolean exist) {
                Log.d("MainActivity", "callJsExists:" + exist);
            }
        });

        webView.callHandler("callJs", "{\"a\", 0, \"b\": true}", new BridgeResponseCallback() {
            @Override
            public void onResult(String data) {
                Log.d("MainActivity", data);
            }
        });
        webView.registerHandler("callNative", new BridgeHandler() {
            @Override
            public void handler(String data, BridgeResponseCallback callback) {
                callback.onResult(data);
            }
        });
        webView.loadUrl("file:///android_asset/html_test.html");
    }
}
