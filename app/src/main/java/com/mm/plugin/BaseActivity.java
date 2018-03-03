package com.mm.plugin;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

/**
 * <p>Created 16/1/16 上午1:16.</p>
 * <p><a href="mailto:730395591@qq.com">Email:730395591@qq.com</a></p>
 * <p><a href="http://www.happycodeboy.com">LeonLee Blog</a></p>
 *
 * @author LeonLee
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LMPApplication.activityCreateStatistics(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LMPApplication.activityResumeStatistics(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LMPApplication.activityPauseStatistics(this);
    }
}
