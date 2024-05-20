package com.easefun.plvvod;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.easefun.plvvod.database.PolyvDownloadSQLiteHelper;
import com.easefun.plvvod.utils.JsonOptionUtil;
import com.easefun.plvvod.utils.PolyvErrorMessageUtils;
import com.easefun.plvvod.vo.PolyvDownloadInfo;
import com.easefun.plvvod.vo.VideoInfoVO;
import com.easefun.polyvsdk.PolyvBitRate;
import com.easefun.polyvsdk.PolyvDownloader;
import com.easefun.polyvsdk.PolyvDownloaderErrorReason;
import com.easefun.polyvsdk.PolyvDownloaderManager;
import com.easefun.polyvsdk.PolyvSDKClient;
import com.easefun.polyvsdk.PolyvSDKUtil;
import com.easefun.polyvsdk.Video;
import com.easefun.polyvsdk.download.listener.IPolyvDownloaderProgressListener2;
import com.easefun.polyvsdk.download.listener.IPolyvDownloaderStopListener;
import com.easefun.polyvsdk.download.util.PolyvDownloaderUtils;
import com.easefun.polyvsdk.log.PolyvCommonLog;
import com.easefun.polyvsdk.video.PolyvVideoUtil;
import com.easefun.polyvsdk.vo.PolyvValidateLocalVideoVO;
import com.easefun.polyvsdk.vo.PolyvVideoVO;
import com.easefun.polyvsdk.vo.listener.PolyvVideoVOLoadedListener;
import com.taobao.weex.annotation.JSMethod;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.common.WXModule;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PolyvDownloadModule extends WXModule {

    private String TAG = "PolyvDownloadModule";

    /**
     * vid最后下载进度
     */
    private Map<String, Integer> videoLastDownloadProgress = new HashMap<String, Integer>();
    /**
     * 下载中回调间隔秒
     */
    private int downloadingCallbackIntervalSeconds = 1;

    private JSCallback downloadCallback;

    @JSMethod(uiThread = true)
    public void addDownloader(JSONObject options, final JSCallback callback) {
        if (options == null) {
            return;
        }
        final JSONObject error = new JSONObject();

        //添加数据库
        final PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());

        List<VideoInfoVO> downloadArr = VideoInfoVO.arrayVideoInfoVOFromData(options.toJSONString(), "downloadArr");

        for (final VideoInfoVO infoVO : downloadArr) {
            if (PolyvSDKUtil.validateVideoId(infoVO.getVid())) {
                final PolyvDownloadInfo tempInfo = new PolyvDownloadInfo(infoVO.getVid(), infoVO.getLevel());
                if (downloadSQLiteHelper != null && !downloadSQLiteHelper.isAdd(tempInfo)) {
                    //加载视频信息
                    PolyvVideoVO.loadVideo(infoVO.getVid(), new PolyvVideoVOLoadedListener() {
                        @Override
                        public void onloaded(@Nullable Video videoVO) {
                            if (videoVO == null) {
                                Log.d(TAG, "addDownloader: load video is null");
                                error.put(infoVO.getVid(), buildErrorObject("获取视频信息失败"));
                                if (callback != null && !error.isEmpty()) {
                                    callback.invokeAndKeepAlive(error);
                                }
                            } else {
                                PolyvBitRate bitRate = PolyvBitRate.getBitRate(infoVO.getLevel());
                                if (bitRate == null) {
                                    bitRate = PolyvBitRate.liuChang;
                                }

                                //根据视频信息序列化到本地
                                final PolyvDownloadInfo downloadInfo = new PolyvDownloadInfo(infoVO.getVid(), videoVO.getDuration(),
                                        videoVO.getFileSizeMatchFileType(bitRate.getNum(), PolyvDownloader.FILE_VIDEO), bitRate.getNum(), videoVO.getTitle());
                                downloadInfo.setFileType(PolyvDownloader.FILE_VIDEO);
                                PolyvCommonLog.d(TAG, downloadInfo.toString());
                                downloadSQLiteHelper.insert(downloadInfo);

                                //添加到下载器
                                final PolyvDownloader polyvDownloader = PolyvDownloaderManager.getPolyvDownloader(infoVO.getVid(), bitRate.getNum());
                                polyvDownloader.setSpeedCallbackInterval(downloadingCallbackIntervalSeconds);
                                polyvDownloader.setPolyvDownloadProressListener2(new UniVodDownloadListener(downloadCallback, downloadInfo));
                                polyvDownloader.setPolyvDownloadStopListener(new UniVodDownloadStopListener(downloadCallback, downloadInfo));

                                JSONObject result = new JSONObject();
                                JSONObject vidJsonObject = new JSONObject();
                                vidJsonObject.put("downloadStatus", "ready");
                                vidJsonObject.put("downloadPercentage", 0);
                                result.put(infoVO.getVid(), vidJsonObject);
                                if (downloadCallback != null) {
                                    downloadCallback.invokeAndKeepAlive(result);
                                }
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "addDownloader: vid=" + infoVO.getVid() + " already have other bitrate in download list");
                    error.put(infoVO.getVid(), buildErrorObject("有其他码率视频在下载列表，请先删除原有的再添加"));

                }

            } else {
                Log.e(TAG, "vid is not correct: " + infoVO.getVid());
                error.put(infoVO.getVid(), "视频id不正确，请设置正确的视频id进行下载");
            }
        }


        if (callback != null && !error.isEmpty()) {
            callback.invokeAndKeepAlive(error);
        }
    }

    @JSMethod(uiThread = true)
    public void getDownloadList(JSONObject options, final JSCallback callback) {
        if (callback == null) {
            return;
        }

        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());
        LinkedList<PolyvDownloadInfo> downloadInfos = downloadSQLiteHelper.getAll();
        JSONArray downloadList = new JSONArray();
        JSONObject downloadItem;
        for (PolyvDownloadInfo info : downloadInfos) {
            downloadItem = new JSONObject();
            downloadItem.put("vid", info.getVid());
            downloadItem.put("level", info.getBitrate());
            downloadItem.put("duration", info.getDuration());
            downloadItem.put("fileSize", info.getFilesize());
            downloadItem.put("title", info.getTitle());
            downloadItem.put("progress", info.getPercent());
            downloadList.add(downloadItem);
        }

        Log.d(TAG, "getDownloadList: " + downloadList.toString());

        JSONObject result = new JSONObject();
        result.put("downloadList", downloadList);
        callback.invoke(result);
    }


    @JSMethod(uiThread = true)
    public void startDownloader(JSONObject options, final JSCallback callback) {
        if (options == null)
            return;

        String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                Log.d(TAG, "startDownloader: " + "vid is not correct: " + vid);
                error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
                callback.invoke(error);
            }
            return;
        }

        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());

        PolyvDownloadInfo downloadInfo = downloadSQLiteHelper.getDownloadInfo(vid);
        if (downloadInfo == null) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                Log.d(TAG, "startDownloader: " + "download list no this vid: " + vid);
                error.put("errMsg", "下载列表没有此视频");
                callback.invoke(error);
            }
            return;
        }

        Log.d(TAG, "startDownloader: vid=" + vid + " level=" + downloadInfo.getBitrate());
        PolyvDownloader polyvDownloader = PolyvDownloaderManager.getPolyvDownloader(vid, downloadInfo.getBitrate());
        polyvDownloader.start(mWXSDKInstance.getContext());

    }

    @JSMethod(uiThread = true)
    public synchronized void startAllDownloader() {
        Log.d(TAG, "startAllDownloader");

        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());

        // 已完成的任务key集合
        List<String> finishKey = new ArrayList<>();
        List<PolyvDownloadInfo> downloadInfos = downloadSQLiteHelper.getAll();
        for (int i = 0; i < downloadInfos.size(); i++) {
            PolyvDownloadInfo downloadInfo = downloadInfos.get(i);
            long percent = downloadInfo.getPercent();
            long total = downloadInfo.getTotal();
            int progress = 0;
            if (total != 0)
                progress = (int) (percent * 100 / total);
            if (progress == 100)
                finishKey.add(PolyvDownloaderManager.getKey(downloadInfo.getVid(), downloadInfo.getBitrate(), downloadInfo.getFileType()));
        }

        PolyvDownloaderManager.startUnfinished(finishKey, mWXSDKInstance.getContext());
        Log.d(TAG, "startAllDownloader-finish");

    }

    @JSMethod(uiThread = true)
    public void stopDownloader(JSONObject options, final JSCallback callback) {
        if (options == null)
            return;

        String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                Log.d(TAG, "stopDownloader" + "download list no this vid: " + vid);
                error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
                callback.invoke(error);
            }
            return;
        }

        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());
        PolyvDownloadInfo downloadInfo = downloadSQLiteHelper.getDownloadInfo(vid);
        if (downloadInfo == null) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                PolyvCommonLog.d(TAG, "startDownloader: " + "download list no this vid: " + vid);
                error.put("vid", vid);
                error.put("errMsg", "下载列表没有此视频");
                callback.invoke(error);
            }
            return;
        }

        PolyvDownloader polyvDownloader = PolyvDownloaderManager.getPolyvDownloader(vid, downloadInfo.getBitrate());
        polyvDownloader.stop();
        PolyvCommonLog.d(TAG, "stopDownloader" + " vid: " + vid + " level:" + downloadInfo.getBitrate());
    }


    @JSMethod(uiThread = true)
    public synchronized void stopAllDownloader(JSONObject options, final JSCallback callback) {
        Log.d(TAG, "stopAllDownloader");
        PolyvDownloaderManager.stopAll();
    }

    @JSMethod(uiThread = false)
    public JSONObject isVideoExist(JSONObject options) {
        JSONObject error = new JSONObject();
        if (options == null)
            return null;

        final String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
            Log.e(TAG, "downloadedVideoExist: vid is incorrect by " + vid);
            return error;
        }

        int level = JsonOptionUtil.getInt(options, "level", -99);
        PolyvBitRate bitRate = PolyvBitRate.getBitRate(level);
        if (bitRate == null) {
            error.put("errMsg", "码率错误，请设置正确的码率进行下载");
            Log.e(TAG, "downloadedVideoExist: bitrate is incorrect by " + level);
            return error;
        }

        final File downloadDir = PolyvSDKClient.getInstance().getDownloadDir();
        if (downloadDir == null) {
            error.put("errMsg", "下载目录未设置，请重启手机再次下载或者向管理员反馈");
            Log.e(TAG, "downloadedVideoExist: download dir not set ");
            return error;
        }

        PolyvValidateLocalVideoVO localVideo = PolyvVideoUtil.validateLocalVideo(vid, level);

        JSONObject result = new JSONObject();
        result.put("exist", localVideo.hasLocalVideo());
        Log.d(TAG, "downloadedVideoExist: " + localVideo.hasLocalVideo());
        return result;

    }

    @JSMethod(uiThread = true)
    public void deleteVideo(JSONObject options, final JSCallback callback) {
        if (options == null)
            return;

        String vid = JsonOptionUtil.getString(options, "vid", "");
        if (!PolyvSDKUtil.validateVideoId(vid)) {
            if (callback != null) {
                JSONObject error = new JSONObject();
                error.put("errMsg", "视频id不正确，请设置正确的视频id进行下载");
                callback.invoke(error);
            }
            return;
        }

        Log.d(TAG, "start deleteVideo: " + vid);

        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());
        PolyvDownloadInfo downloadInfo = downloadSQLiteHelper.getDownloadInfo(vid);
        if (downloadInfo != null) {
            downloadSQLiteHelper.delete(vid);
            PolyvDownloader polyvDownloader = PolyvDownloaderManager.clearPolyvDownload(vid, downloadInfo.getBitrate());
            polyvDownloader.delete();
        }
        PolyvDownloaderUtils.deleteVideo(vid);

    }

    @JSMethod(uiThread = true)
    public void deleteAllVideo() {
        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());

        PolyvDownloaderUtils.deleteDownloaderDir();
        downloadSQLiteHelper.release();
    }

    @JSMethod(uiThread = true)
    public void setDownloadCallbackInterval(JSONObject options, JSCallback callback) {
        if (options == null)
            return;

        int seconds = JsonOptionUtil.getInt(options, "seconds", 1);
        if (seconds < 0) {
            seconds = 0;
        }
        Log.d(TAG, "setDownloadCallbackInterval: " + seconds);
        downloadingCallbackIntervalSeconds = seconds;
    }

    @JSMethod(uiThread = true)
    public void setListenDownloadStatus(JSONObject options, JSCallback callback) {
        PolyvCommonLog.d(TAG, "setListenDownloadStatus");
        //因为Android中离开页面后callback重新进入，callback不能正常回调进度，keepAlive不行，成员变量callback也不行
        //设置自定义事件也会失败，只有第一次进入页面可以。可能是多线程下实例在跨语言通信中存在问题（基于3.0.5测试）
        //所以这里通过接口专门获取这个callback，每次都getALl，重新设置监听器

        PolyvDownloadSQLiteHelper downloadSQLiteHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());

        downloadCallback = callback;
        // 已完成的任务key集合

        List<PolyvDownloadInfo> downloadInfos = downloadSQLiteHelper.getAll();
        for (int i = 0; i < downloadInfos.size(); i++) {
            PolyvDownloadInfo downloadInfo = downloadInfos.get(i);
            long percent = downloadInfo.getPercent();
            long total = downloadInfo.getTotal();
            int progress = 0;
            if (total != 0) {
                progress = (int) (percent * 100 / total);
            }
            if (progress != 100) {
                //getDownloader
                PolyvDownloader downloader = PolyvDownloaderManager.getPolyvDownloader(downloadInfo.getVid(), downloadInfo.getBitrate());
                if (callback == null) {
                    downloader.setPolyvDownloadProressListener2(null);
                    downloader.setPolyvDownloadStopListener(null);
                    continue;
                }
                downloader.setPolyvDownloadProressListener2(new UniVodDownloadListener(callback, downloadInfo));
                downloader.setPolyvDownloadStopListener(new UniVodDownloadStopListener(callback, downloadInfo));
            }
        }
    }

    private JSONObject buildErrorObject(String errMsg) {
        JSONObject errorObject = new JSONObject();
        errorObject.put("errMsg", errMsg);
        return errorObject;
    }

    class UniVodDownloadListener implements IPolyvDownloaderProgressListener2 {

        PolyvDownloadSQLiteHelper downloadHelper;
        JSCallback jsCallback;
        PolyvDownloadInfo info;

        UniVodDownloadListener(JSCallback callback, PolyvDownloadInfo downloadInfo) {
            downloadHelper = PolyvDownloadSQLiteHelper.getInstance(mWXSDKInstance.getContext());
            jsCallback = callback;
            info = downloadInfo;
        }

        @Override
        public void onDownload(long current, long total) {
            int progress = (int) (current * 100 / total);
            videoLastDownloadProgress.put(info.getVid(), progress);
            Log.d(TAG, "onDownload: " + progress);

            downloadHelper.update(info, progress, total);

            JSONObject result = new JSONObject();
            JSONObject vidJsonObject = new JSONObject();
            vidJsonObject.put("downloadStatus", "downloading");
            vidJsonObject.put("downloadPercentage", progress);
            result.put(info.getVid(), vidJsonObject);
            if (jsCallback != null) {
                jsCallback.invokeAndKeepAlive(result);
            }

        }

        @Override
        public void onDownloadSuccess(int i) {
            videoLastDownloadProgress.put(info.getVid(), 100);
            info.setBitrate(i);
            downloadHelper.update(info, 100, info.getFilesize());

            JSONObject result = new JSONObject();
            JSONObject vidJsonObject = new JSONObject();
            vidJsonObject.put("downloadStatus", "finished");
            vidJsonObject.put("downloadPercentage", 100);
            Log.d(TAG, "DownloadSuccess: " + info.getVid());
            result.put(info.getVid(), vidJsonObject);
            if (jsCallback != null) {
                jsCallback.invokeAndKeepAlive(result);
            }
        }

        @Override
        public void onDownloadFail(@NonNull PolyvDownloaderErrorReason polyvDownloaderErrorReason) {
            JSONObject result = new JSONObject();
            JSONObject vidJsonObject = new JSONObject();
            Integer progress = videoLastDownloadProgress.get(info.getVid());

            vidJsonObject.put("downloadStatus", "failed");
            vidJsonObject.put("downloadPercentage", progress != null ? progress : 0);
            String message = PolyvErrorMessageUtils.getDownloaderErrorMessage(polyvDownloaderErrorReason.getType());
            message += "(error code " + polyvDownloaderErrorReason.getType().getCode() + ")";
            vidJsonObject.put("errMsg", message);
            Log.d(TAG, "DownloadFail: " + message);

            result.put(info.getVid(), vidJsonObject);

            if (jsCallback != null) {
                jsCallback.invokeAndKeepAlive(result);
            }
        }
    }

    class UniVodDownloadStopListener implements IPolyvDownloaderStopListener {

        JSCallback jsCallback;
        PolyvDownloadInfo infoVO;

        UniVodDownloadStopListener(JSCallback callback, PolyvDownloadInfo downloadInfo) {
            jsCallback = callback;
            infoVO = downloadInfo;
        }

        @Override
        public void onStop() {
            PolyvCommonLog.d(TAG, "onDownloadStop");
            Integer progress = videoLastDownloadProgress.get(infoVO.getVid());
            JSONObject result = new JSONObject();
            JSONObject vidJsonObject = new JSONObject();
            vidJsonObject.put("downloadStatus", "stopped");
            vidJsonObject.put("downloadPercentage", progress != null ? progress : 0);
            result.put(infoVO.getVid(), vidJsonObject);
            if (jsCallback != null) {
                jsCallback.invokeAndKeepAlive(result);
            }
        }
    }

}
