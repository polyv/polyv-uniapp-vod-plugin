package com.easefun.plvvod;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.easefun.plvvod.utils.JsonOptionUtil;
import com.easefun.plvvod.utils.PolyvErrorMessageUtils;
import com.easefun.plvvod.utils.PolyvScreenUtils;
import com.easefun.plvvod.utils.PolyvStorageUtils;
import com.easefun.plvvod.utils.PolyvTimeUtils;
import com.easefun.polyvsdk.PolyvSDKClient;
import com.easefun.polyvsdk.PolyvSDKUtil;
import com.easefun.polyvsdk.application.PolyvSettings;
import com.easefun.polyvsdk.marquee.PolyvMarqueeItem;
import com.easefun.polyvsdk.util.PolyvScopedStorageUtil;
import com.easefun.polyvsdk.video.PolyvPlayErrorReason;
import com.easefun.polyvsdk.video.PolyvVideoView;
import com.easefun.polyvsdk.video.listener.IPolyvOnGetCurrentPositionListener;
import com.easefun.polyvsdk.video.listener.IPolyvOnPlayPauseListener;
import com.easefun.polyvsdk.video.listener.IPolyvOnPreparedListener2;
import com.easefun.polyvsdk.video.listener.IPolyvOnVideoPlayErrorListener2;
import com.easefun.polyvsdk.video.listener.IPolyvOnVideoStatusListener;
import com.taobao.weex.WXSDKInstance;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.ui.action.BasicComponentData;
import com.taobao.weex.ui.component.WXComponent;
import com.taobao.weex.ui.component.WXComponentProp;
import com.taobao.weex.ui.component.WXVContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PolyvVodComponent extends WXComponent<PolyvVodView> {
    private String TAG = "PolyvVodComponent";

    private PolyvMarqueeItem marqueeItem = null;
    private boolean isAutoPlay = true;
    private boolean pauseIsFromStop = false;
    private float mVolume = 0.5f;
    private DecimalFormat mDecimalFormat = new DecimalFormat("#.##");

    private boolean isVideoUrl = false;

    //用于码率切换时记录进度
    private int mCurrentPosition = 0;
    private boolean isChangeLevel = false;

    public PolyvVodComponent(WXSDKInstance instance, WXVContainer parent, BasicComponentData basicComponentData) {
        super(instance, parent, basicComponentData);
    }

    @Override
    protected PolyvVodView initComponentHostView(@NonNull Context context) {
        PolyvVodView vodView = new PolyvVodView(context);
        vodView.getVideoView().setOpenAd(false);
        vodView.getVideoView().setOpenTeaser(false);
        vodView.getVideoView().setOpenQuestion(false);
        vodView.getVideoView().setOpenSRT(false);
        vodView.getVideoView().setAutoPlay(true);
        vodView.getVideoView().setOpenPreload(false, 2);
        vodView.getVideoView().setOpenMarquee(false);
        vodView.getVideoView().setAutoContinue(false);
        vodView.getVideoView().setNeedGestureDetector(true);

        vodView.getVideoView().setMarqueeView(vodView.getMarqueeView(), marqueeItem = new PolyvMarqueeItem()
                .setStyle(PolyvMarqueeItem.STYLE_ROLL) //样式
                .setDuration(10000) //滚动时长
                .setText("Polyv uni-app Plugin") //文本
                .setSize(16) //字体大小
                .setColor(0xFFFFE900) //字体颜色
                .setTextAlpha(255) //字体透明度
                .setInterval(0) //隐藏时间
                .setLifeTime(5000) //显示时间
                .setTweenTime(1000) //渐隐渐现时间
                .setHasStroke(false) //是否有描边
                .setStrokeColor(Color.WHITE) //描边颜色
                .setStrokeAlpha(70)); //描边透明度

        initVideoView(vodView);

        Log.d(TAG, "initComponentHostView: " + vodView);

        //打印硬解和rendertype，方便通过日志确认排查
        PolyvSettings polyvSettings = new PolyvSettings(getInstance().getContext());
        Log.d(TAG, "renderType is : " + polyvSettings.getRenderViewType());
        Log.d(TAG, "UsingMediaCodec is : " + polyvSettings.getUsingMediaCodec());


        return vodView;
    }

    @Override
    public void onActivityDestroy() {
        Log.d(TAG, "onActivityDestroy");
        if (getVideoView() != null) {
            getVideoView().stopGetCurrentPositionTask();
            getVideoView().stopPlayback();
            getVideoView().release();
            getVideoView().destroy();
        }

        super.onActivityDestroy();
    }


    // <editor-fold defaultstate="collapsed" desc="自定义属性">

    @WXComponentProp(name = "autoPlay")
    public void setAutoPlay(boolean autoPlay) {
        if (getVideoView() != null)
            getVideoView().setAutoPlay(autoPlay);
        isAutoPlay = autoPlay;
        Log.d(TAG, "autoPlay: " + isAutoPlay);
    }

    @WXComponentProp(name = "rememberLastPosition")
    public void setAutoContinue(boolean rememberLastPosition) {
        if (getVideoView() != null)
            getVideoView().setAutoContinue(rememberLastPosition);
        Log.d(TAG, "rememberLastPosition: " + rememberLastPosition);
    }


    @WXComponentProp(name = "seekType")
    public void setSeekType(int seekType) {
        if (getVideoView() != null)
            getVideoView().setSeekType(seekType);
        Log.d(TAG, "seekType: " + seekType);

    }

    @WXComponentProp(name = "disableScreenCAP")
    public void setDisableScreenCAP(boolean disableScreenCAP) {
        if (getVideoView() != null)
            if (getInstance().getContext() instanceof Activity) {
                getVideoView().disableScreenCAP((Activity) getInstance().getContext(), disableScreenCAP);
                Log.d(TAG, "disableScreenCAP: " + disableScreenCAP);
            } else {
                Log.e(TAG, "context not instanceof activity");
            }

    }


    // </editor-fold>


    // <editor-fold defaultstate="collapsed" desc="VideoView控制">

    @JSMethod(uiThread = true)
    public void changeToLandscape(JSONObject options, JSCallback callback) {
        Log.d(TAG, "changeToLandscape");
        JSONObject result = new JSONObject();
        if (getInstance().getContext() instanceof Activity) {
            Activity context = (Activity) getInstance().getContext();
            PolyvScreenUtils.setLandscape(context);
            //初始为横屏时，状态栏需要隐藏
            PolyvScreenUtils.hideStatusBar(context);
//            result.put("orientation", "portrait");
        } else {
            Log.e(TAG, "can not get Activity context");
            result.put("errMsg", "can not get Activity context");
        }
        if (callback != null) {
            callback.invoke(result);
        }

    }

    @JSMethod(uiThread = true)
    public void changeToPortrait(JSONObject options, JSCallback callback) {
        Log.d(TAG, "changeToPortrait");
        JSONObject result = new JSONObject();
        if (getInstance().getContext() instanceof Activity) {
            Activity context = (Activity) getInstance().getContext();
            PolyvScreenUtils.setPortrait(context);
            PolyvScreenUtils.reSetStatusBar(context);
//            result.put("orientation", "portrait");
        } else {
            Log.e(TAG, "can not get Activity context");
            result.put("errMsg", "can not get Activity context");
        }
        if (callback != null) {
            callback.invoke(result);
        }
    }

    @JSMethod(uiThread = true)
    public void disableScreenCAP(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            return;
        }

        boolean disableScreenCAP = JsonOptionUtil.getBoolean(options, "disableScreenCAP", false);
        if (getInstance().getContext() instanceof Activity) {
            getVideoView().disableScreenCAP((Activity) getInstance().getContext(), disableScreenCAP);
            Log.d(TAG, "disableScreenCAP: " + disableScreenCAP);
        } else {
            if (callback != null) {
                JSONObject result = new JSONObject();
                result.put("errMsg", "can not get Activity context");
                callback.invoke(result);
            }
        }
    }


    @JSMethod(uiThread = false)
    public void isPlaying(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        boolean isPlaying = false;
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("isPlaying", isPlaying);
            callback.invoke(result);
            return;
        }

        isPlaying = getVideoView().isPlaying();
        result.put("isPlaying", isPlaying);
        Log.d(TAG, "isPlaying: " + isPlaying);
        callback.invoke(result);
    }

    @JSMethod(uiThread = true)
    public void setVid(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "播放器为空");
            callback.invoke(error);
        }

        isChangeLevel = false;
        String vid = JsonOptionUtil.getString(options, "vid", "");
        int level = JsonOptionUtil.getInt(options, "level", 0);
        if (TextUtils.isEmpty(vid) || !PolyvSDKUtil.validateVideoId(vid)) {
            String err = "视频id不正确，请设置正确的视频id进行播放";
            if (callback != null) {
                JSONObject errObject = new JSONObject();
                errObject.put("errMsg", err);
                callback.invoke(errObject);
            }
            Log.d(TAG, "vid is incorrect: " + vid);
            return;
        }
        getVideoView().setVid(vid, level);
//        if (isAutoPlay)
//            getVideoView().start();
        sendPlayStatusEvent("preparedToPlay", false);

    }


    @JSMethod(uiThread = true)
    public void setURL(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            JSONObject error = new JSONObject();
            error.put("errMsg", "播放器为空");
            callback.invoke(error);
            return;
        }

        isChangeLevel = false;

        String path = JsonOptionUtil.getString(options, "url", "");

        Pattern pattern = Pattern.compile("^https?://");
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            Log.d(TAG, "setVideoURL : " + path);
            getVideoView().setVideoURI(Uri.parse(path));
            isVideoUrl = true;
        } else {
            File file = new File(path);
            if (!file.exists()) {
                JSONObject error = new JSONObject();
                error.put("errMsg", "本地缓存视频不存在");
                Log.e(TAG, "video not found in path: " + path);
                callback.invoke(error);
                return;
            }
            getVideoView().setVideoPath(path);
            isVideoUrl = true;
        }
        sendPlayStatusEvent("preparedToPlay", false);
    }


    @JSMethod(uiThread = true)
    public void start() {
        if (getVideoView() != null) {
            getVideoView().start();
        }
    }

    @JSMethod(uiThread = true)
    public void pause() {
        if (getVideoView() != null) {
            pauseIsFromStop = false;
            getVideoView().pause();
        }
    }

    @JSMethod(uiThread = true)
    public void stop() {
        if (getVideoView() != null) {
            pauseIsFromStop = true;
            getVideoView().seekTo(0);
            getVideoView().pause();
        }
    }

    /**
     * 快进
     */
    @JSMethod(uiThread = true)
    public void forward(JSONObject options) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            return;
        }

        int seconds = JsonOptionUtil.getInt(options, "seconds", 0);
        long forward = getVideoView().getCurrentPosition() + seconds * 1000;
        if (forward > getVideoView().getDuration()) {
            forward = getVideoView().getDuration();
        }
        getVideoView().seekTo(forward);
    }


    @JSMethod(uiThread = true)
    public void rewind(JSONObject options) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            return;
        }

        int seconds = JsonOptionUtil.getInt(options, "seconds", 0);
        long rewind = getVideoView().getCurrentPosition() - seconds * 1000;
        if (rewind < 0) {
            rewind = 0;
        }
        getVideoView().seekTo(rewind);
    }

    @JSMethod(uiThread = true)
    public void seekTo(JSONObject options) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            return;
        }

        int seconds = JsonOptionUtil.getInt(options, "seconds", 0);
        if (seconds < 0) {
            seconds = 0;
        }
        getVideoView().seekTo(seconds * 1000);
    }

    @JSMethod(uiThread = false)
    public void setVolume(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        if (getVideoView() != null) {
            double volume = JsonOptionUtil.getDouble(options, "volume", 0);
            if (volume < 0) {
                volume = 0;
            } else if (volume > 1) {
                volume = 1;
            }
            if (getVideoView().getIjkMediaPlayer() != null) {
                mVolume = (float) volume;
                getVideoView().getIjkMediaPlayer().setVolume(mVolume, mVolume);
            }
        }
    }

    @JSMethod(uiThread = false)
    public void getVolume(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("volume", 0);
            callback.invoke(result);
            return;
        }

        float volume = mVolume;
        String format = mDecimalFormat.format(volume);
        result.put("volume", Float.valueOf(format));
        callback.invoke(result);
    }

    @JSMethod(uiThread = false)
    public void setSpeed(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            return;
        }

        double speed = JsonOptionUtil.getDouble(options, "speed", 1.0);

        if (speed < 0.5)
            speed = 0.5;
        if (speed > 2)
            speed = 2;

        getVideoView().setSpeed((float) speed);
    }

    @JSMethod(uiThread = false)
    public void getSpeed(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("speed", 1);
            callback.invoke(result);
            return;
        }

        result.put("speed", getVideoView().getSpeed());
        callback.invoke(result);
    }

    /**
     * 获取码率支持个数
     */
    @JSMethod(uiThread = false)
    public void getLevelNum(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("levelNum", 0);
            callback.invoke(result);
            return;
        }

        result.put("levelNum", getVideoView().getLevel());
        Log.d(TAG, "levelNum: " + getVideoView().getLevel());
        callback.invoke(result);
    }

    @JSMethod(uiThread = false)
    public void getCurrentLevel(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("currentLevel", 0);
            callback.invoke(result);
            return;
        }

        result.put("currentLevel", getVideoView().getBitRate());
        callback.invoke(result);
    }

    @JSMethod(uiThread = true)
    public void changeLevel(JSONObject options, JSCallback callback) {
        if (options == null) {
            return;
        }
        if (getVideoView() == null) {
            return;
        }

        isChangeLevel = true;
        int level = JsonOptionUtil.getInt(options, "level", 1);
        mCurrentPosition = getVideoView().getCurrentPosition();
        boolean result = getVideoView().changeBitRate(level);

        if (callback != null && !result) {
            JSONObject data = new JSONObject();
            data.put("errMsg", "该码率不支持");
            callback.invoke(data);
        }
    }

    @JSMethod(uiThread = false)
    public void getDuration(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("duration", 0);
            callback.invoke(result);
            return;
        }
        int duration = getVideoView().getDuration() / 1000;
        result.put("duration", duration);
        callback.invoke(result);
    }

    @JSMethod(uiThread = false)
    public void getCurrentPosition(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("currentPosition", 0);
            callback.invoke(result);
            return;
        }
        result.put("currentPosition", (getVideoView().getCurrentPosition() / 1000));
        callback.invoke(result);
    }

    @JSMethod(uiThread = false)
    public void getBufferPercentage(JSONObject options, JSCallback callback) {
        if (callback == null) {
            return;
        }
        JSONObject result = new JSONObject();
        if (getVideoView() == null) {
            result.put("bufferPercentage", 0);
            callback.invoke(result);
            return;
        }
        result.put("bufferPercentage", getVideoView().getBufferPercentage());
        callback.invoke(result);
    }

    @JSMethod(uiThread = true)
    public void snapshot(JSONObject options, JSCallback callback) {
        Log.d(TAG, "snapshot start");
        if (getVideoView() == null) {
            return;
        }

        String error = null;
        String success = null;


        Bitmap bitmap = getVideoView().screenshot();

        if (bitmap != null) {
            String fileName = getVideoView().getCurrentVid() + "_" +
                    PolyvTimeUtils.generateTime(getVideoView().getCurrentPosition()) + "_" + new SimpleDateFormat("yyyy-MM-dd_kk:mm:ss").format(new Date()) + ".jpg";
            File saveFile;
            saveFile = new File(PolyvStorageUtils.getExternalFilesDirs(getInstance().getContext()).get(0), fileName);
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(saveFile);
                boolean compressResult = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                if (compressResult) {
                    Uri uri = PolyvScopedStorageUtil.createUriInMediaStore(null, saveFile.getName(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    boolean b = PolyvScopedStorageUtil.saveFileToMediaStore(saveFile, uri);
                    if (!b) {
                        error = "截图失败：bitmap save fail";
                    } else {
                        success = "截图已保存到相册";
                    }
                } else {
                    error = "截图失败：bitmap compress fail";
                }
                saveFile.delete();
            } catch (Exception e) {
                error = "截图失败：" + e.getMessage();
            } finally {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            error = "截图失败：bitmap is null";
        }

        Log.d(TAG, "snapshot: " + error + success);

        if (callback != null) {
            JSONObject object = new JSONObject();
            if (error != null) {
                object.put("errMsg", error);
                callback.invoke(error);
                Toast.makeText(getInstance().getContext(), error, Toast.LENGTH_SHORT).show();
            } else {
//                callback.invoke();
                Toast.makeText(getInstance().getContext(), success, Toast.LENGTH_SHORT).show();
            }
        }

        Log.d(TAG, "snapshot result: error:" + error + " success：" + success);
    }


    // </editor-fold>


    // <editor-fold defaultstate="collapsed" desc="跑马灯">

    @JSMethod(uiThread = true)
    public void showMarquee(JSONObject options, JSCallback callback) {
        Log.d(TAG, "showMarquee");
        if (getVideoView() == null)
            return;
        //options为空则默认
        if (options == null) {
            getHostView().getVideoView().setOpenMarquee(true);
            resetMarquee();
            return;
        }

        getHostView().getVideoView().setOpenMarquee(true);

        String marquee = JsonOptionUtil.getString(options, "marquee", "Polyv Uni-App Plugin");
        int duration = JsonOptionUtil.getInt(options, "duration", 5);
        int interval = JsonOptionUtil.getInt(options, "interval", 0);
        //字体
        int textSize = JsonOptionUtil.getInt(options, "font", 16);
        if (textSize < 5) {
            textSize = 5;
        } else if (textSize > 72) {
            textSize = 72;
        }
        //透明度
        double alpha = JsonOptionUtil.getDouble(options, "alpha", 1.0);
        int textAlpha;
        if (alpha <= 0) {
            textAlpha = 1;
        } else if (alpha > 1) {
            textAlpha = 255;
        } else {
            textAlpha = (int) (alpha * 255);
        }

        //判断颜色
        String color = options.getString("color");
        int textColor;
        try {
            color = color.replace("0x", "#");
            textColor = Color.parseColor(color);
        } catch (Exception e) {
            e.printStackTrace();
            textColor = Color.parseColor("#FFE900");
        }

        if (marqueeItem != null) {
            //duration参数是秒，方法的参数是毫秒
            marqueeItem.setDuration(duration * 1000)
                    .setLifeTime(duration * 1000)
                    .setText(marquee)
                    .setSize(textSize)
                    .setColor(textColor)
                    .setTextAlpha(textAlpha)
                    .setInterval(interval * 1000);
        }

        resetMarquee();

    }

    private void resetMarquee() {
        if (getHostView().getMarqueeView() != null) {
            getHostView().getMarqueeView().removeAllItem();
            getHostView().getMarqueeView().addItem(marqueeItem);
        }
    }

    @JSMethod(uiThread = true)
    public void hideMarquee() {
        Log.d(TAG, "hideMarquee");
        if (getHostView().getVideoView() != null) {
            getHostView().getVideoView().setOpenMarquee(false);
        }

        if (getHostView().getMarqueeView() != null) {
            getHostView().getMarqueeView().removeAllItem();
        }
    }


    // </editor-fold>

    private PolyvVideoView getVideoView() {
        if (getHostView() != null) {
            return getHostView().getVideoView();
        } else {
            Log.e(TAG, "your host view is null");
        }
        return null;
    }

    /**
     * videoView 基本设置
     */
    private void initVideoView(final PolyvVodView videoView) {

        videoView.getVideoView().setOnPreparedListener(new IPolyvOnPreparedListener2() {
            @Override
            public void onPrepared() {
                sendPlayStatusEvent("preparedToPlay", true);
                //setUrl 没有进度回调，需要主动start一下
                if (isVideoUrl) {
                    getVideoView().startGetCurrentPositionTask();
                    isVideoUrl = false;
                }
                if (isChangeLevel) {
                    //切换码率不能记住进度，这里seekTo
                    videoView.getVideoView().seekTo(mCurrentPosition);
                    isChangeLevel = false;
                }
            }
        });

        videoView.getVideoView().setOnVideoStatusListener(new IPolyvOnVideoStatusListener() {
            @Override
            public void onStatus(int status) {
                if (status < 60) {
                    String error = "status_error(" + status + ")";
                    Log.e(TAG, error);
                    sendPlayErrorEvent(2, error);
                }
            }
        });

        videoView.getVideoView().setOnVideoPlayErrorListener(new IPolyvOnVideoPlayErrorListener2() {
            @Override
            public boolean onVideoPlayError(@PolyvPlayErrorReason.PlayErrorReason int playErrorReason) {
                if (playErrorReason == PolyvPlayErrorReason.USER_TOKEN_ERROR) {
                    sendPlayErrorEvent(0, "config_invalid");
                    return true;
                }

                if (getVideoView() != null && !TextUtils.isEmpty(getVideoView().getCurrentVideoId())) {
                    if (!getVideoView().getCurrentVideoId().startsWith(PolyvSDKClient.getInstance().getUserId())) {
                        sendPlayErrorEvent(1, "illegal_vid");
                    }
                }

                String message = PolyvErrorMessageUtils.getPlayErrorMessage(playErrorReason);
                message += "(error code " + playErrorReason + ")";
                Log.e(TAG, message);
                sendPlayErrorEvent(3, "play_error#" + message);
                return true;
            }
        });

        videoView.getVideoView().setOnPlayPauseListener(new IPolyvOnPlayPauseListener() {
            @Override
            public void onPause() {
                if (!pauseIsFromStop) {
                    sendPlayStatusEvent("playbackState", "pause");
                } else {
                    sendPlayStatusEvent("playbackState", "stop");
                }
            }

            @Override
            public void onPlay() {
                sendPlayStatusEvent("playbackState", "start");
            }

            @Override
            public void onCompletion() {
                sendPlayStatusEvent("playbackState", "complete");
            }
        });

        videoView.getVideoView().setOnGetCurrentPositionListener(500, new IPolyvOnGetCurrentPositionListener() {
            @Override
            public void onGet(String vid, int currentPositionMs) {
                sendCurrentPosition("currentPosition", currentPositionMs / 1000);
            }
        });
    }


    //<editor-fold defaultstate="collapsed" desc="自定义事件">

    /*
     * 发送自定义事件，这些内容会发送到前端
     */

    private void sendPlayStatusEvent(Object key, Object value) {
        //原生触发fireEvent 自定义事件onPlayStatus
        Map<String, Object> params = new HashMap<>();
        Map<Object, Object> number = new HashMap<>();
        number.put(key, value);
        //目前uni限制 参数需要放入到"detail"中 否则会被清理
        params.put("detail", number);
        fireEvent("onPlayStatus", params);
    }

    private void sendPlayErrorEvent(int errCode, String errMsg) {
        //原生触发fireEvent 自定义事件onPlayStatus
        Map<String, Object> params = new HashMap<>();
        Map<Object, Object> number = new HashMap<>();
        number.put("errCode", errCode);
        number.put("errEvent", errMsg);
        //目前uni限制 参数需要放入到"detail"中 否则会被清理
        params.put("detail", number);
        fireEvent("onPlayError", params);
    }

    private void sendCurrentPosition(Object key, Object value) {
        //原生触发fireEvent 自定义事件onPlayStatus
        Map<String, Object> params = new HashMap<>();
        Map<Object, Object> number = new HashMap<>();
        number.put(key, value);
        //目前uni限制 参数需要放入到"detail"中 否则会被清理
        params.put("detail", number);
        fireEvent("positionChange", params);
    }
    // </editor-fold>
}
