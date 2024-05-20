package com.easefun.plvvod.utils;

import java.util.Locale;

/**
 * 常用时间工具类
 */
public class PolyvTimeUtils {

    /**
     * 以友好的方式显示视频时间
     */
    public static String generateTime(long millisecond) {
        return generateTime(millisecond, false);
    }

    /**
     * 以友好的方式显示视频时间
     *
     * @param millisecond 毫秒
     * @param fit         小时是否适配"00"
     * @return
     */
    public static String generateTime(long millisecond, boolean fit) {
        millisecond = Math.max(0, millisecond);
        int totalSeconds = (int) (millisecond / 1000);

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        if (fit || hours > 0)
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        else
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
