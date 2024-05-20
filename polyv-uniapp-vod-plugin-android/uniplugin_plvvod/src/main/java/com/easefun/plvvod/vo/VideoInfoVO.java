package com.easefun.plvvod.vo;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by tanqu on 2018/8/24.
 */

public class VideoInfoVO {


    /**
     * vid : 视频 vid，字符串类型
     * level : 1
     */

    @SerializedName("vid")
    private String vid;
    @SerializedName("level")
    private Integer level;

    public VideoInfoVO(String vid, Integer level) {
        this.vid = vid;
        this.level = level;
    }

    public static VideoInfoVO objectFromData(String str) {

        return new Gson().fromJson(str, VideoInfoVO.class);
    }

    public static VideoInfoVO objectFromData(String str, String key) {

        try {
            JSONObject jsonObject = new JSONObject(str);

            return new Gson().fromJson(jsonObject.getString(str), VideoInfoVO.class);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static List<VideoInfoVO> arrayVideoInfoVOFromData(String str) {

        Type listType = new TypeToken<ArrayList<VideoInfoVO>>() {
        }.getType();

        return new Gson().fromJson(str, listType);
    }

    public static List<VideoInfoVO> arrayVideoInfoVOFromData(String str, String key) {

        try {
            JSONObject jsonObject = new JSONObject(str);
            Type listType = new TypeToken<ArrayList<VideoInfoVO>>() {
            }.getType();

            return new Gson().fromJson(jsonObject.getString(key), listType);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new ArrayList();


    }

    public String getVid() {
        return vid;
    }

    public void setVid(String vid) {
        this.vid = vid;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }
}
