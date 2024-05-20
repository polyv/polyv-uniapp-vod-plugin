package com.easefun.plvvod;

import android.text.TextUtils;

import com.alibaba.fastjson.JSONObject;
import com.easefun.plvvod.utils.JsonOptionUtil;
import com.easefun.polyvsdk.PolyvBitRate;
import com.easefun.polyvsdk.PolyvSDKClient;
import com.easefun.polyvsdk.PolyvSDKUtil;
import com.easefun.polyvsdk.vo.PolyvVideoVO;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.common.WXModule;


public class PolyvVideoInfoModule extends WXModule {


    @JSMethod(uiThread = true)
    public void getVideoInfo(JSONObject options, final JSCallback callback) {
        if (options == null || callback == null) {
            return;
        }

        final String vid = JsonOptionUtil.getString(options, "vid", "");
        if (TextUtils.isEmpty(vid) || !PolyvSDKUtil.validateVideoId(vid)) {
            String err = "视频id不正确，请设置正确的视频id进行播放";
            JSONObject errObject = new JSONObject();
            errObject.put("errMsg", err);
            callback.invoke(errObject);
            return;
        }

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                PolyvVideoVO video = null;
                try {
                    video = PolyvSDKUtil.loadVideoJSON2Video(vid);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!validateVideo(video, callback)) {
                    return;
                }


                int levelNum = video.getDfNum();
                String duration = video.getDuration();


                JSONObject result = new JSONObject();
                result.put("duration", duration);
                result.put("levelNum", levelNum);
                callback.invoke(result);
            }
        });

        thread.start();
    }

    @JSMethod(uiThread = true)
    public void getFileSize(JSONObject options, final JSCallback callback) {
        if (options == null || callback == null)
            return;

        final String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!validateVid(vid, callback)) {
            return;
        }

        int level = JsonOptionUtil.getInt(options, "level", 0);
        if (level == 0) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "请设置码率后再进行播放");
            callback.invoke(error);
            return;
        }

        final PolyvBitRate bitRate = PolyvBitRate.getBitRate(level);
        if (bitRate == null) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "码率不正确，请设置正确的码率进行播放");
            callback.invoke(error);
            return;
        }

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                PolyvVideoVO video = null;
                try {
                    video = PolyvSDKUtil.loadVideoJSON2Video(vid);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (!validateVideo(video, callback)) {
                    return;
                }

                long fileSize = -1;
                if (video != null) {
                    fileSize = video.getFileSizeMatchVideoType(bitRate.getNum());
                }

                if (fileSize == -1) {
                    JSONObject error = new JSONObject();
                    error.put("errMsg", "没有文件大小信息，请向管理员反馈");
                    callback.invoke(error);
                    return;
                }

                JSONObject result = new JSONObject();
                result.put("fileSize", fileSize);
                callback.invoke(result);
            }
        });

        thread.start();
    }


    private boolean validateVid(String videoId, JSCallback callback) {
        if (TextUtils.isEmpty(videoId) || !PolyvSDKUtil.validateVideoId(videoId)) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "视频id不正确，请设置正确的视频id进行播放");
            callback.invoke(error);
            return false;
        }

        String userId = PolyvSDKClient.getInstance().getUserId();
        if (TextUtils.isEmpty(userId)) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "请先设置播放凭证，再进行播放");
            callback.invoke(error);
            return false;
        }

        String validateUserId = videoId.substring(0, 10);
        if (!userId.equals(validateUserId)) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "播放凭证设置错误");
            callback.invoke(error);
            return false;
        }

        return true;
    }

    private boolean validateVideo(PolyvVideoVO video, JSCallback callback) {
        if (video == null) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "视频信息加载失败，请尝试切换网络重新播放");
            callback.invoke(error);
            return false;
        }

        if (video.isOutflow()) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "流量超标，请向管理员反馈");
            callback.invoke(error);
            return false;
        }

        if (video.isTimeoutFlow()) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "账号过期，请向管理员反馈");
            callback.invoke(error);
            return false;
        }

        if (video.getStatus() < 60) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "视频状态异常，无法播放，请向管理员反馈(" + video.getStatus() + ")");
            callback.invoke(error);
            return false;
        }

        return true;
    }


}
