package com.easefun.plvvod;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.easefun.plvvod.utils.PolyvStorageUtils;
import com.easefun.polyvsdk.PolyvDevMountInfo;
import com.easefun.polyvsdk.PolyvDownloaderManager;
import com.easefun.polyvsdk.PolyvSDKClient;
import com.easefun.polyvsdk.log.PolyvCommonLog;

import java.io.File;
import java.util.ArrayList;

import io.dcloud.feature.uniapp.AbsSDKInstance;
import io.dcloud.feature.uniapp.UniSDKInstance;
import io.dcloud.weex.AppHookProxy;

public class PolyvAppProxy implements AppHookProxy {
    String TAG = "PolyvAppProxy";

    public static AbsSDKInstance mAppSDKInstance;

    @Override
    public void onCreate(Application application) {
        PolyvCommonLog.d(TAG, "Polyv Init");
        PolyvSDKClient client = PolyvSDKClient.getInstance();
        client.enableHttpDns(false);
        client.enableIPV6(true);
        client.initSetting(application);
        //设置下载目录
        setDownloadDir(application);

        mAppSDKInstance = new UniSDKInstance(application);
    }

    /**
     * 设置下载视频目录
     */
    private void setDownloadDir(Context context) {
        final String rootDownloadDirName = "polyvdownload";
        ArrayList<File> externalFilesDirs = PolyvStorageUtils.getExternalFilesDirs(context);
        if (externalFilesDirs.size() == 0) {

            Log.e(TAG, "没有可用的存储设备,后续不能使用视频缓存功能");
            return;
        }

        File downloadDir = new File(externalFilesDirs.get(0), rootDownloadDirName);
        PolyvSDKClient.getInstance().setDownloadDir(downloadDir);

        ArrayList<File> subDirList = new ArrayList<>();
        String externalSDCardPath = PolyvDevMountInfo.getInstance().getExternalSDCardPath();
        if (!TextUtils.isEmpty(externalSDCardPath)) {
            StringBuilder dirPath = new StringBuilder();
            dirPath.append(externalSDCardPath).append(File.separator).append(rootDownloadDirName);
            File saveDir = new File(dirPath.toString());
            if (!saveDir.exists()) {
                saveDir.mkdirs();//创建下载目录
            }

            //设置下载存储目录
            subDirList.add(saveDir);
        }

        //如果没有可移除的存储介质（例如 SD 卡），那么一定有内部（不可移除）存储介质可用，都不可用的情况在前面判断过了。
        File saveDir = new File(PolyvDevMountInfo.getInstance().getInternalSDCardPath() + File.separator + rootDownloadDirName);
        if (!saveDir.exists()) {
            saveDir.mkdirs();//创建下载目录
        }

        //设置下载存储目录
        subDirList.add(saveDir);
        PolyvSDKClient.getInstance().setSubDirList(subDirList);
        PolyvDownloaderManager.setDownloadQueueCount(3);
    }
}
