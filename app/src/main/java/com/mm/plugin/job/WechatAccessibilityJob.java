package com.mm.plugin.job;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.mm.plugin.Config;
import com.mm.plugin.IStatusBarNotification;
import com.mm.plugin.LMPAccessibilityService;
import com.mm.plugin.util.AccessibilityHelper;
import com.mm.plugin.util.NotifyHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>Created 16/1/16 上午12:40.</p>
 * <p><a href="mailto:codeboy2013@gmail.com">Email:codeboy2013@gmail.com</a></p>
 * <p><a href="http://www.happycodeboy.com">LeonLee Blog</a></p>
 *
 * @author LeonLee
 */
public class WechatAccessibilityJob extends BaseAccessibilityJob {

    private static final String TAG = "WechatAccessibilityJob";

    /**
     * 微信的包名
     */
    public static final String WECHAT_PACKAGENAME = "com.tencent.mm";

    /**
     * 红包消息的关键字
     */
    private static final String HONGBAO_TEXT_KEY = "[微信红包]";

    private static final String BUTTON_CLASS_NAME = "android.widget.Button";

    private static final int WINDOW_NONE = 0;
    private static final int WINDOW_LUCKYMONEY_RECEIVEUI = 1;
    private static final int WINDOW_LUCKYMONEY_DETAIL = 2;
    private static final int WINDOW_LAUNCHER = 3;
    private static final int WINDOW_OTHER = -1;

    private int mCurrentWindow = WINDOW_NONE;

    /**
     * 是否需要领取红包
     */
    public static boolean isReceivingHongbao;
    private PackageInfo mWechatPackageInfo = null;
    private Handler mHandler = null;

    /**
     * 微信6.5.23版本的版本号
     */
    private int wx523VersionCode = 1180;
    /**
     * 微信6.6.1版本的版本号
     */
    private int wx661VersionCode = 1220;

    /**
     * 用来存储接收到的通知栏红包事件意图
     */
    private List<Notification> curHongbaoNotificationList = new ArrayList<>();

    // 以下为6.5.23微信页面名称和控件信息
    private String wxChatListContentTxtID = "com.tencent.mm:id/aol";
    private String wxChatListContentNumID = "com.tencent.mm:id/io";
    private String wxChatListContentNumIDFor661Group = "com.tencent.mm:id/iu";
    private String wxHongbaoPageOpenViewID = "com.tencent.mm:id/bx4";
    private String wxHongbaoPageName = "com.tencent.mm.plugin.luckymoney.ui.En_fba4b94f";
    private String wxChatPageName = "com.tencent.mm.ui.LauncherUI";
    private String wxHongbaoDetailsPageName = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
    private String wxChatListListViewID = "com.tencent.mm:id/bya";
    private String wxChatListItemLayoutID = "com.tencent.mm:id/aoh";
    private boolean isGotoWxChatPage = false;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //更新安装包信息
            updatePackageInfo();
        }
    };

    @Override
    public void onCreateJob(LMPAccessibilityService service) {
        super.onCreateJob(service);

        updatePackageInfo();

        IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REPLACED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");

        getWxAppInfo();

        getContext().registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onStopJob() {
        try {
            getContext().unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onNotificationPosted(IStatusBarNotification sbn) {
        Notification nf = sbn.getNotification();
        String text = String.valueOf(sbn.getNotification().tickerText);
        notificationEvent(text, nf);
    }

    @Override
    public boolean isEnable() {
        return getConfig().isEnableWechat();
    }

    @Override
    public String getTargetPackageName() {
        return WECHAT_PACKAGENAME;
    }

    @Override
    public void onReceiveJob(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        Log.e(TAG, "AccessibilityEvent " + eventType);
        //通知栏事件
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Parcelable data = event.getParcelableData();
            if (data == null || !(data instanceof Notification)) {
                return;
            }
            if (LMPAccessibilityService.isNotificationServiceRunning() && getConfig()
                    .isEnableNotificationService()) { //开启快速模式，不处理
                return;
            }
            List<CharSequence> texts = event.getText();
            if (!texts.isEmpty()) {
                String text = String.valueOf(texts.get(0));
                notificationEvent(text, (Notification) data);
            }
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 这里同一个应用内 如果页面内容没有变化的话是没有执行的
            Log.e(TAG, "TYPE_WINDOW_STATE_CHANGED");
            onWindowStateChanged(event);
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // 这里只要页面内容变化 就会执行 例如不同应用之间的跳转
            Log.e(TAG, "TYPE_WINDOW_CONTENT_CHANGED");
            if (mCurrentWindow != WINDOW_LAUNCHER) { //不在聊天界面或聊天列表，不处理
                return;
            }
            handleChatListHongBao();
        }
    }

    private void getWxAppInfo() {
        int curVersionCode = getWechatVersion();
        if (curVersionCode == wx523VersionCode){
            wxChatListContentTxtID = "com.tencent.mm:id/aol";
            wxChatListContentNumID = "com.tencent.mm:id/io";
            wxHongbaoPageOpenViewID = "com.tencent.mm:id/bx4";
            wxHongbaoPageName = "com.tencent.mm.plugin.luckymoney.ui.En_fba4b94f";
            wxChatPageName = "com.tencent.mm.ui.LauncherUI";
            wxHongbaoDetailsPageName = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
            wxChatListListViewID = "com.tencent.mm:id/bya";
            wxChatListItemLayoutID = "com.tencent.mm:id/aoh";

        } else if (curVersionCode == wx661VersionCode) {
            wxChatListContentTxtID = "com.tencent.mm:id/apv";
            wxChatListContentNumID = "com.tencent.mm:id/aps";
            wxHongbaoPageOpenViewID = "com.tencent.mm:id/bx4";
            wxHongbaoPageName = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
            wxChatPageName = "com.tencent.mm.ui.LauncherUI";
            wxHongbaoDetailsPageName = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
            wxChatListListViewID = "com.tencent.mm:id/c3p";
            wxChatListItemLayoutID = "com.tencent.mm:id/apr";

        } else {
            // 暂不处理
        }
    }

    /**
     * 通知栏事件
     */
    private void notificationEvent(String ticker, Notification nf) {
        String text = ticker;
        int index = text.indexOf(":");
        if (index != -1) {
            text = text.substring(index + 1);
        }
        text = text.trim();
        if (text.contains(HONGBAO_TEXT_KEY)) { //红包消息
            handleNewHongBaoNotification(nf);
        }
    }

    /**
     * 打开通知栏消息 如果当前页面不是微信的消息列表页面 新的消息会以通知栏的形式弹出
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void handleNewHongBaoNotification(Notification notification) {
        // 判断当前设备是否锁屏或者黑屏
        boolean lock = NotifyHelper.isLockScreen(getContext());
        if (lock) {
            isReceivingHongbao = false;
            curHongbaoNotificationList.clear();
            openHongbaoPage4Notification(notification);
        } else {
            if (!isReceivingHongbao) {
                isReceivingHongbao = true;
                openHongbaoPage4Notification(notification);
            } else {
                curHongbaoNotificationList.add(notification);
            }
        }
    }

    private void openHongbaoPage4Notification(Notification notification) {
        //以下是精华，将微信的通知栏消息打开
        PendingIntent pendingIntent = notification.contentIntent;
        // 当前屏幕是否锁屏
        boolean lock = NotifyHelper.isLockScreen(getContext());

        if (!lock) {
            isGotoWxChatPage = true;
            NotifyHelper.send(pendingIntent);
        } else {
            // 锁屏状态下发送提醒
            NotifyHelper.showNotify(getContext(), String.valueOf(notification.tickerText),
                    pendingIntent);
        }
        // 如果当前设备锁屏并且开启了抢红包则进行提醒
        if (lock || getConfig().getWechatMode() != Config.WX_MODE_0) {
            NotifyHelper.playEffect(getContext(), getConfig());
        }

        if (curHongbaoNotificationList.size() > 0) {
            Iterator<Notification> iterator = curHongbaoNotificationList.iterator();
            while(iterator.hasNext()) {
                Notification curNotification = iterator.next();
                if (curNotification.equals(notification)) {
                    iterator.remove();
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void onWindowStateChanged(AccessibilityEvent event) {
        Log.d(TAG, event.getClassName() + "");
        if (wxHongbaoPageName.equals(event.getClassName())) {
            mCurrentWindow = WINDOW_LUCKYMONEY_RECEIVEUI;
            //点中了红包，下一步就是去拆红包
            openHongbao();
        } else if (wxHongbaoDetailsPageName.equals(event.getClassName())) {
            mCurrentWindow = WINDOW_LUCKYMONEY_DETAIL;

            if (curHongbaoNotificationList.size() == 0) {
                // 设置当前是否需要领取红包状态为否 后续重新接收新的领取红包事件
                isReceivingHongbao = false;
                //拆完红包后看详细的纪录界面 在这里可以返回到当前的上一个页面
                if (getConfig().getWechatAfterGetHongBaoEvent() == Config.WX_AFTER_GET_GOHOME) {
                    //返回主界面，以便收到下一次的红包通知
                    AccessibilityHelper.performHome(getService());
                } else {
                    // 返回到上个页面
                    AccessibilityHelper.performBack(getService());
                }
            } else {
                for (int i = 0; i < curHongbaoNotificationList.size(); i++) {
                    openHongbaoPage4Notification(curHongbaoNotificationList.get(i));
                }
            }

        } else if (wxChatPageName.equals(event.getClassName())) {
            mCurrentWindow = WINDOW_LAUNCHER;
            //在聊天界面,去点中红包
            handleChatListHongBao();
        } else {
            mCurrentWindow = WINDOW_OTHER;
        }
    }

    /**
     * 点击聊天里的红包后，显示的界面
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void openHongbao() {
        AccessibilityNodeInfo nodeInfo = getService().getRootInActiveWindow();
        if (nodeInfo == null) {
            Log.w(TAG, "rootWindow为空");
            return;
        }

        AccessibilityNodeInfo targetNode = null;

        int event = getConfig().getWechatAfterOpenHongBaoEvent();
        int wechatVersion = getWechatVersion();
        if (event == Config.WX_AFTER_OPEN_HONGBAO) { //拆红包
            // 通过遍历当前页面的节点树，获取到红包页面的“开”按钮
            if (wechatVersion == wx523VersionCode || wechatVersion == wx661VersionCode) {
                targetNode = AccessibilityHelper.findNodeInfosByClassName(nodeInfo, BUTTON_CLASS_NAME);
            } else {
                Log.d(TAG, "抱歉，暂时不支持该微信版本！");
                Toast.makeText(getContext(), "抱歉，暂时不支持该微信版本！", Toast.LENGTH_SHORT).show();
            }

        } else if (event == Config.WX_AFTER_OPEN_NONE) {
            return;
        }

        if (targetNode != null) {
            final AccessibilityNodeInfo n = targetNode;
            long sDelayTime = getConfig().getWechatOpenDelayTime();
            if (sDelayTime != 0) {
                getHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // 模拟拆开红包
                        AccessibilityHelper.performClick(n);
                    }
                }, sDelayTime);
            } else {
                // 模拟拆开红包
                AccessibilityHelper.performClick(n);
            }
        }
    }

    /**
     * 收到聊天里的红包 这个页面包含了消息列表和聊天页面
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void handleChatListHongBao() {
        Log.d(TAG, "handleChatListHongBaohandleChatListHongBao");
        int mode = getConfig().getWechatMode();
        if (mode == Config.WX_MODE_3) { //只通知模式
            return;
        }

        AccessibilityNodeInfo rootNodeInfo = getService().getRootInActiveWindow();
        if (rootNodeInfo == null) {
            Log.w(TAG, "rootWindow为空");
            return;
        }

        // 来到聊天页面
        List<AccessibilityNodeInfo> notifyInfoList = rootNodeInfo.findAccessibilityNodeInfosByText("领取红包");
        if (notifyInfoList.size() > 0) {
            // 如果有新的红包需要领取
            AccessibilityNodeInfo node = notifyInfoList.get(0);
            AccessibilityHelper.performClick(node);
        }

        if (isGotoWxChatPage){
            isGotoWxChatPage = false;
            // 这里是从通知栏直接跳转到聊天页面
            /*List<AccessibilityNodeInfo> notifyInfoList = rootNodeInfo.findAccessibilityNodeInfosByText("领取红包");
            if (notifyInfoList.size() > 0) {
                // 如果有新的红包需要领取
                AccessibilityNodeInfo node = notifyInfoList.get(notifyInfoList.size() - 1);
                AccessibilityHelper.performClick(node);
            }*/
        } else {
            // 这里是从微信消息列表页面跳转到聊天页面
            List<AccessibilityNodeInfo> chatListViewNodeInfoList = rootNodeInfo.findAccessibilityNodeInfosByViewId(wxChatListListViewID);
            if (chatListViewNodeInfoList.size() > 0) {
                AccessibilityNodeInfo chatListViewNodeInfo = chatListViewNodeInfoList.get(0);
                List<AccessibilityNodeInfo> itemLayoutNodeList = chatListViewNodeInfo.findAccessibilityNodeInfosByViewId(wxChatListItemLayoutID);
                List<AccessibilityNodeInfo> itemContentTxtNodeList = chatListViewNodeInfo.findAccessibilityNodeInfosByViewId(wxChatListContentTxtID);

                for (int i = 0; i < itemContentTxtNodeList.size(); i++) {
                    AccessibilityNodeInfo textInfo = itemContentTxtNodeList.get(i);
                    if (textInfo.getText().toString().contains(HONGBAO_TEXT_KEY)) {
                        if (itemLayoutNodeList != null && itemLayoutNodeList.get(i) != null) {
                            List<AccessibilityNodeInfo> numInfoList = itemLayoutNodeList.get(i).findAccessibilityNodeInfosByViewId(wxChatListContentNumID);
                            List<AccessibilityNodeInfo> onlyNumInfoList = itemLayoutNodeList.get(i).findAccessibilityNodeInfosByViewId(wxChatListContentNumID);
                            List<AccessibilityNodeInfo> groupNumInfoList = itemLayoutNodeList.get(i).findAccessibilityNodeInfosByViewId(wxChatListContentNumIDFor661Group);
                            // 适配6.5.23和6.6.1版本页面逻辑
                            if (getWechatVersion() == wx523VersionCode) {
                                if (numInfoList.size() > 0) {
                                    // 是否存在未读消息 这条消息就是新的红包
                                    // AccessibilityNodeInfo numInfo = numInfoList.get(0);
                                    //最新的红包领起
                                    AccessibilityHelper.performClick(textInfo.getParent());
                                    isGotoWxChatPage = true;
                                }
                            } else if (getWechatVersion() == wx661VersionCode) {
                                if (onlyNumInfoList.size() > 0 || groupNumInfoList.size() > 0) {
                                    // 是否存在未读消息 这条消息就是新的红包
                                    // AccessibilityNodeInfo numInfo = numInfoList.get(0);
                                    //最新的红包领起
                                    AccessibilityHelper.performClick(textInfo.getParent());
                                    isGotoWxChatPage = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        return mHandler;
    }

    /**
     * 获取微信的版本
     */
    private int getWechatVersion() {
        if (mWechatPackageInfo == null) {
            return 0;
        }
        return mWechatPackageInfo.versionCode;
    }

    /**
     * 更新微信包信息
     */
    private void updatePackageInfo() {
        try {
            mWechatPackageInfo = getContext().getPackageManager().getPackageInfo(WECHAT_PACKAGENAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
