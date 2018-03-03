package com.mm.plugin;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.mm.plugin.job.AccessibilityJob;
import com.mm.plugin.job.WechatAccessibilityJob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Created by LeonLee on 15/2/17 下午10:25.</p>
 * <p><a href="mailto:codeboy2013@163.com">Email:codeboy2013@163.com</a></p>
 * <p>
 * 抢红包外挂服务
 */
public class LMPAccessibilityService extends AccessibilityService {

    private static final String TAG = "LuckyMoneyPlugin";

    private static final Class[] ACCESSIBILITY_JOBS = {
            WechatAccessibilityJob.class,
    };

    private static LMPAccessibilityService service;

    private List<AccessibilityJob> mAccessibilityJobs;
    private HashMap<String, AccessibilityJob> mPkgAccessibilityJobMap;

    @Override
    public void onCreate() {
        super.onCreate();

        mAccessibilityJobs = new ArrayList<>();
        mPkgAccessibilityJobMap = new HashMap<>();

        //初始化辅助插件工作
        for (Class clazz : ACCESSIBILITY_JOBS) {
            try {
                Object object = clazz.newInstance();
                if (object instanceof AccessibilityJob) {
                    AccessibilityJob job = (AccessibilityJob) object;
                    job.onCreateJob(this);
                    mAccessibilityJobs.add(job);
                    mPkgAccessibilityJobMap.put(job.getTargetPackageName(), job);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LuckMoneyPlugin service destory");
        if (mPkgAccessibilityJobMap != null) {
            mPkgAccessibilityJobMap.clear();
        }
        if (mAccessibilityJobs != null && !mAccessibilityJobs.isEmpty()) {
            for (AccessibilityJob job : mAccessibilityJobs) {
                job.onStopJob();
            }
            mAccessibilityJobs.clear();
        }

        service = null;
        mAccessibilityJobs = null;
        mPkgAccessibilityJobMap = null;
        //发送广播，已经断开辅助服务
        Intent intent = new Intent(Config.ACTION_LuckMoneyPlugin_SERVICE_DISCONNECT);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "LuckMoneyPlugin service interrupt");
        Toast.makeText(this, "中断抢红包服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        service = this;
        //发送广播，已经连接上了
        Intent intent = new Intent(Config.ACTION_LuckMoneyPlugin_SERVICE_CONNECT);
        sendBroadcast(intent);
        Toast.makeText(this, "已连接抢红包服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "事件--->" + event);
        }
        String pkn = String.valueOf(event.getPackageName());
        if (mAccessibilityJobs != null && !mAccessibilityJobs.isEmpty()) {
            if (!getConfig().isAgreement()) {
                return;
            }
            for (AccessibilityJob job : mAccessibilityJobs) {
                if (pkn.equals(job.getTargetPackageName()) && job.isEnable()) {
                    job.onReceiveJob(event);
                }
            }
        }
    }

    public Config getConfig() {
        return Config.getConfig(this);
    }

    /**
     * 接收通知栏事件
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void handeNotificationPosted(IStatusBarNotification notificationService) {
        if (notificationService == null) {
            return;
        }
        if (service == null || service.mPkgAccessibilityJobMap == null) {
            return;
        }
        String pack = notificationService.getPackageName();
        AccessibilityJob job = service.mPkgAccessibilityJobMap.get(pack);
        if (job == null) {
            return;
        }
        job.onNotificationPosted(notificationService);
    }

    /**
     * 判断当前服务是否正在运行
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static boolean isRunning() {
        if (service == null) {
            return false;
        }
        AccessibilityManager accessibilityManager = (AccessibilityManager) service
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) {
            return false;
        }
        List<AccessibilityServiceInfo> list = accessibilityManager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        Iterator<AccessibilityServiceInfo> iterator = list.iterator();

        boolean isConnect = false;
        while (iterator.hasNext()) {
            AccessibilityServiceInfo i = iterator.next();
            if (i.getId().equals(info.getId())) {
                isConnect = true;
                break;
            }
        }
        if (!isConnect) {
            return false;
        }
        return true;
    }

    /**
     * 快速读取通知栏服务是否启动
     */
    public static boolean isNotificationServiceRunning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }
        //部份手机没有NotificationService服务
        try {
            return LMPNotificationService.isRunning();
        } catch (Throwable t) {
        }
        return false;
    }
}
