package com.vincent.jsbridge;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Keep;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by du on 16/12/29.
 */

public class BridgeWebView extends WebView {
    private static final String BRIDGE_NAME = "WVJBInterface";
    private static final int EXEC_SCRIPT = 1;
    private static final int LOAD_URL = 2;
    private static final int LOAD_URL_WITH_HEADERS = 3;
    private static final int HANDLE_MESSAGE = 4;
    MyHandler mainThreadHandler = null;

    private ArrayList<BridgeMessage> startupMessageQueue = null;
    private Map<String, BridgeResponseCallback> responseCallbacks = null;
    private Map<String, BridgeHandler> messageHandlers = null;
    private long uniqueId = 0;


    public BridgeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    public BridgeWebView(Context context) {
        super(context);
        init();
    }

    public void callHandler(String handlerName) {
        callHandler(handlerName, null, null);
    }

    public void callHandler(String handlerName, String data) {
        callHandler(handlerName, data, null);
    }

    public <T> void callHandler(String handlerName, String data,
                                BridgeResponseCallback responseCallback) {
        sendData(data, responseCallback, handlerName);
    }

    /**
     * Test whether the handler exist in javascript
     *
     * @param handlerName
     * @param callback
     */
    public void hasJavascriptMethod(String handlerName, final BridgeMethodExistCallback callback) {
        callHandler("_hasJavascriptMethod", handlerName, new BridgeResponseCallback() {
            @Override
            public void onResult(String data) {
                callback.onResult(Boolean.parseBoolean(data));
            }
        });
    }

    public void registerHandler(String handlerName, BridgeHandler handler) {
        if (handlerName == null || handlerName.length() == 0 || handler == null) {
            return;
        }
        messageHandlers.put(handlerName, handler);
    }

    // send the onResult message to javascript
    private void sendData(String data, BridgeResponseCallback responseCallback,
                          String handlerName) {
        if (data == null && (handlerName == null || handlerName.length() == 0)) {
            return;
        }
        BridgeMessage message = new BridgeMessage();
        if (data != null) {
            message.data = data;
        }
        if (responseCallback != null) {
            String callbackId = "java_cb_" + (++uniqueId);
            responseCallbacks.put(callbackId, responseCallback);
            message.callbackId = callbackId;
        }
        if (handlerName != null) {
            message.handlerName = handlerName;
        }
        queueMessage(message);
    }

    private synchronized void queueMessage(BridgeMessage message) {

        if (startupMessageQueue != null) {
            startupMessageQueue.add(message);
        } else {
            dispatchMessage(message);
        }
    }

    private void dispatchMessage(BridgeMessage message) {
        String messageJSON = message2JSONObject(message).toString();
        evaluateJavascript(String.format("WebViewJavascriptBridge._handleMessageFromJava(%s)", messageJSON));
    }

    // handle the onResult message from javascript
    private void handleMessage(String info) {
        try {
            JSONObject jo = new JSONObject(info);
            BridgeMessage message = JSONObject2BridgeMessage(jo);
            if (message.responseId != null) {
                BridgeResponseCallback responseCallback = responseCallbacks
                        .remove(message.responseId);
                if (responseCallback != null) {
                    responseCallback.onResult(message.responseData);
                }
            } else {
                BridgeResponseCallback responseCallback = null;
                if (message.callbackId != null) {
                    final String callbackId = message.callbackId;
                    responseCallback = new BridgeResponseCallback() {
                        @Override
                        public void onResult(String data) {
                            BridgeMessage msg = new BridgeMessage();
                            msg.responseId = callbackId;
                            msg.responseData = data;
                            dispatchMessage(msg);
                        }
                    };
                }

                BridgeHandler handler;
                handler = messageHandlers.get(message.handlerName);
                if (handler != null) {
                    handler.handler(message.data, responseCallback);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONObject message2JSONObject(BridgeMessage message) {
        JSONObject jo = new JSONObject();
        try {
            if (message.callbackId != null) {
                jo.put("callbackId", message.callbackId);
            }
            if (message.data != null) {
                jo.put("data", message.data);
            }
            if (message.handlerName != null) {
                jo.put("handlerName", message.handlerName);
            }
            if (message.responseId != null) {
                jo.put("responseId", message.responseId);
            }
            if (message.responseData != null) {
                jo.put("responseData", message.responseData);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jo;
    }

    private BridgeMessage JSONObject2BridgeMessage(JSONObject jo) {
        BridgeMessage message = new BridgeMessage();
        try {
            if (jo.has("callbackId")) {
                message.callbackId = jo.getString("callbackId");
            }
            if (jo.has("data")) {
                message.data = jo.getString("data");
            }
            if (jo.has("handlerName")) {
                message.handlerName = jo.getString("handlerName");
            }
            if (jo.has("responseId")) {
                message.responseId = jo.getString("responseId");
            }
            if (jo.has("responseData")) {
                message.responseData = jo.getString("responseData");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return message;
    }

    @Keep
    void init() {
        mainThreadHandler = new MyHandler(getContext());
        this.responseCallbacks = new HashMap<>();
        this.messageHandlers = new HashMap<>();
        this.startupMessageQueue = new ArrayList<>();

        try {
            getSettings().setJavaScriptEnabled(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        registerHandler("_hasNativeMethod", new BridgeHandler() {
            @Override
            public void handler(String data, BridgeResponseCallback callback) {
                callback.onResult(String.valueOf(messageHandlers.get(data) != null));
            }
        });

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            super.addJavascriptInterface(new Object() {
                @Keep
                @JavascriptInterface
                public void notice(String info) {
                    Message msg = mainThreadHandler.obtainMessage(HANDLE_MESSAGE, info);
                    mainThreadHandler.sendMessage(msg);
                }

            }, BRIDGE_NAME);
        }

    }

    private void _evaluateJavascript(String script) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            BridgeWebView.super.evaluateJavascript(script, null);
        } else {
            super.loadUrl("javascript:" + script);
        }
    }

    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param script
     */
    public void evaluateJavascript(final String script) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            _evaluateJavascript(script);
        } else {
            Message msg = mainThreadHandler.obtainMessage(EXEC_SCRIPT, script);
            mainThreadHandler.sendMessage(msg);
        }
    }

    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param url
     */
    @Override
    public void loadUrl(String url) {
        Message msg = mainThreadHandler.obtainMessage(LOAD_URL, url);
        mainThreadHandler.sendMessage(msg);
    }

    /**
     * This method can be called in any thread, and if it is not called in the main thread,
     * it will be automatically distributed to the main thread.
     *
     * @param url
     * @param additionalHttpHeaders
     */
    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        Message msg = mainThreadHandler.obtainMessage(LOAD_URL_WITH_HEADERS, new RequestInfo(url, additionalHttpHeaders));
        mainThreadHandler.sendMessage(msg);
    }

    public static class BridgeChromeClient extends WebChromeClient {
        private final BridgeWebView mBridgeWebView;

        public BridgeChromeClient(BridgeWebView bridgeWebView) {
            mBridgeWebView = bridgeWebView;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress > 80) {
                try {
                    InputStream is = view.getContext().getAssets()
                            .open("WebViewJavascriptBridge.js");
                    int size = is.available();
                    byte[] buffer = new byte[size];
                    is.read(buffer);
                    is.close();
                    String js = new String(buffer);
                    mBridgeWebView.evaluateJavascript(js);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                synchronized (mBridgeWebView) {
                    if (mBridgeWebView.startupMessageQueue != null) {
                        for (int i = 0; i < mBridgeWebView.startupMessageQueue.size(); i++) {
                            mBridgeWebView.dispatchMessage(mBridgeWebView.startupMessageQueue.get(i));
                        }
                        mBridgeWebView.startupMessageQueue = null;
                    }
                }
            }

            super.onProgressChanged(view, newProgress);

        }
    }

    class MyHandler extends Handler {
        //  Using WeakReference to avoid memory leak
        WeakReference<Context> mContextReference;

        MyHandler(Context context) {
            super(Looper.getMainLooper());
            mContextReference = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            final Context context = mContextReference.get();
            if (context != null) {
                switch (msg.what) {
                    case EXEC_SCRIPT:
                        _evaluateJavascript((String) msg.obj);
                        break;
                    case LOAD_URL:
                        BridgeWebView.super.loadUrl((String) msg.obj);
                        break;
                    case LOAD_URL_WITH_HEADERS: {
                        RequestInfo info = (RequestInfo) msg.obj;
                        BridgeWebView.super.loadUrl(info.url, info.headers);
                    }
                    break;
                    case HANDLE_MESSAGE:
                        BridgeWebView.this.handleMessage((String) msg.obj);
                        break;
                }
            }
        }
    }

    private class RequestInfo {
        String url;
        Map<String, String> headers;

        RequestInfo(String url, Map<String, String> additionalHttpHeaders) {
            this.url = url;
            this.headers = additionalHttpHeaders;
        }
    }
}
