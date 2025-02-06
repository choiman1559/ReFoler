package com.refoler.app.utils;

import com.refoler.app.BuildConfig;

public class BillingHelper {
    private static BillingHelper billingHelper;

    public static BillingHelper getInstance() {
        if(billingHelper == null) {
            billingHelper = new BillingHelper();
        }

        return billingHelper;
    }

    public boolean isSubscribedOrDebugBuild() {
        return BuildConfig.DEBUG; //TODO: implement billing
    }
}
