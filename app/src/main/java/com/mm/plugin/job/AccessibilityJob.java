package com.mm.plugin.job;

import android.view.accessibility.AccessibilityEvent;

import com.mm.plugin.IStatusBarNotification;
import com.mm.plugin.LMPAccessibilityService;

/**
 * <p>Created 16/1/16 上午12:32.</p>
 * <p><a href="mailto:codeboy2013@gmail.com">Email:codeboy2013@gmail.com</a></p>
 * <p><a href="http://www.happycodeboy.com">LeonLee Blog</a></p>
 *
 * @author LeonLee
 */
public interface AccessibilityJob {
    String getTargetPackageName();
    void onCreateJob(LMPAccessibilityService service);
    void onReceiveJob(AccessibilityEvent event);
    void onStopJob();
    void onNotificationPosted(IStatusBarNotification service);
    boolean isEnable();
}
