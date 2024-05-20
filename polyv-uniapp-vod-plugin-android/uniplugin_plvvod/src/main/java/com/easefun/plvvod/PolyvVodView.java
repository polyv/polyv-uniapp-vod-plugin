package com.easefun.plvvod;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.easefun.polyvsdk.marquee.PolyvMarqueeView;
import com.easefun.polyvsdk.video.PolyvVideoView;

public class PolyvVodView extends FrameLayout {

    RelativeLayout mRootView;
    private PolyvVideoView mVideoView;
    private PolyvMarqueeView mMarqueeView;
    private ProgressBar mProgressbar;


    public PolyvVodView(Context context) {
        super(context);
        initView();
    }

    public PolyvVodView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public PolyvVodView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        inflate(getContext(), R.layout.polyv_vod_layout, this);

        mRootView = this.findViewById(R.id.polyv_vod_view);
        mVideoView = this.findViewById(R.id.polyv_video_view);
        mMarqueeView = this.findViewById(R.id.polyv_marquee_view);
        mProgressbar = this.findViewById(R.id.polyv_loading_progress);

        mVideoView.setMediaBufferingIndicator(mProgressbar);

    }

    public PolyvVideoView getVideoView() {
        return mVideoView;
    }

    public PolyvMarqueeView getMarqueeView() {
        return mMarqueeView;
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }
}
