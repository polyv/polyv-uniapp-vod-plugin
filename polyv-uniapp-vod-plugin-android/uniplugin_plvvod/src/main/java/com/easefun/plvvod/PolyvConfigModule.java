package com.easefun.plvvod;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.easefun.plvvod.utils.JsonOptionUtil;
import com.easefun.polyvsdk.PolyvSDKClient;
import com.easefun.polyvsdk.application.PolyvSettings;
import com.easefun.polyvsdk.po.PolyvViewerInfo;
import com.easefun.polyvsdk.video.constant.PolyvRenderViewType;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.common.WXModule;

public class PolyvConfigModule extends WXModule {
    private static final String DEF_AESKEY = "VXtlHmwfS2oYm0CZ";
    private static final String DEF_IV = "2u9gDPKdX6GyQJKU";
    String TAG = "PolyvConfigModule";

    @JSMethod(uiThread = true)
    public void setConfig(JSONObject options, JSCallback callback) {
        boolean result = false;

        if (options == null || TextUtils.isEmpty(options.getString("config"))) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "请设置正确的参数");
            callback.invoke(error);
        }
//        Log.d(TAG, "setConfig" + options.toJSONString());
        String config = options.getString("config");
        String aeskey = JsonOptionUtil.getString(options, "aeskey", DEF_AESKEY);
        String iv = JsonOptionUtil.getString(options, "iv", DEF_IV);
        PolyvSDKClient instance = PolyvSDKClient.getInstance();
        result = instance.settingsWithConfigString(config, aeskey, iv);
        Log.d(TAG, "setConfig" + result);
        if (callback != null) {
            JSONObject data = new JSONObject();
            data.put("isSuccess", result);
            callback.invoke(data);
            return;
        }

    }

    @JSMethod(uiThread = true)
    public void setToken(JSONObject options, JSCallback callback) {
        boolean result = false;

        if (options == null || TextUtils.isEmpty(options.getString("userid"))
                || TextUtils.isEmpty(options.getString("writetoken"))
                || TextUtils.isEmpty(options.getString("readtoken"))
                || TextUtils.isEmpty(options.getString("secretkey"))) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "请设置正确的参数");
            callback.invoke(error);
        }
        Log.d(TAG, "setToken");
        String userid = JsonOptionUtil.getString(options, "userid", "");
        String writetoken = JsonOptionUtil.getString(options, "writetoken", "");
        String readtoken = JsonOptionUtil.getString(options, "readtoken", "");
        String secretkey = JsonOptionUtil.getString(options, "secretkey", "");
        PolyvSDKClient instance = PolyvSDKClient.getInstance();
        result = instance.settingsWithUserid(userid, secretkey, readtoken, writetoken);
        Log.d(TAG, "setToken" + result);
        if (callback != null) {
            JSONObject data = new JSONObject();
            data.put("isSuccess", result);
            callback.invoke(data);
        }
    }

    @JSMethod(uiThread = true)
    public void openMediaCodec(JSONObject options, JSCallback callback) {
        if (options != null) {
            boolean mediaCodec = JsonOptionUtil.getBoolean(options, "mediaCodec", false);
            Log.d(TAG, "openMediaCodec：" + mediaCodec);
            SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mWXSDKInstance.getContext());
            mSharedPreferences.edit().putBoolean("polyv.pref.using_media_codec", mediaCodec).apply();
        }
    }

    @JSMethod(uiThread = true)
    public void setRenderView(JSONObject options, JSCallback callback) {
        if (options == null)
            return;
        PolyvSettings polyvSettings = new PolyvSettings(mWXSDKInstance.getContext());
        int defaultRenderViewType = polyvSettings.getRenderViewType();
        Integer type = options.getInteger("renderViewType");
        if (type != PolyvRenderViewType.RENDER_SURFACE_VIEW
                && type != PolyvRenderViewType.RENDER_TEXTURE_VIEW) {
            type = defaultRenderViewType;
        }

        polyvSettings.setRenderViewType(type);
        Log.d(TAG, "setRenderView：" + options.toJSONString());
    }

    @JSMethod(uiThread = true)
    public void setViewerInfo(JSONObject options, JSCallback callback) {
        PolyvViewerInfo info = PolyvSDKClient.getInstance().getViewerInfo();
        info.setViewerExtraInfo1(options.getString("param3"));
        info.setViewerExtraInfo2(options.getString("param4"));
        info.setViewerExtraInfo3(options.getString("param5"));
        PolyvSDKClient.getInstance().setViewerInfo(info);

    }

    @JSMethod(uiThread = true)
    public void setViewerId(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }

        String viewerid = JsonOptionUtil.getString(options, "viewerId", "");
        PolyvSDKClient.getInstance().getViewerInfo().setViewerId(viewerid);
    }

    @JSMethod(uiThread = true)
    public void setViewerName(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        String viewerName = JsonOptionUtil.getString(options, "viewerName", "");
        PolyvSDKClient.getInstance().getViewerInfo().setViewerName(viewerName);
    }


}
