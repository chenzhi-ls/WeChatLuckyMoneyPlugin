package com.mm.plugin.job;

import android.content.Context;

import com.mm.plugin.Config;
import com.mm.plugin.LMPAccessibilityService;

/**
 * <p>Created 16/1/16 上午12:38.</p>
 * <p><a href="mailto:codeboy2013@gmail.com">Email:codeboy2013@gmail.com</a></p>
 * <p><a href="http://www.happycodeboy.com">LeonLee Blog</a></p>
 *
 * @author LeonLee
 */
public abstract class BaseAccessibilityJob implements AccessibilityJob {

    private LMPAccessibilityService service;

    @Override
    public void onCreateJob(LMPAccessibilityService service) {
        this.service = service;
    }

    public Context getContext() {
        return service.getApplicationContext();
    }

    public Config getConfig() {
        return service.getConfig();
    }

    public LMPAccessibilityService getService() {
        return service;
    }
}
