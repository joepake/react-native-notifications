package com.wix.reactnativenotifications.core;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.wix.reactnativenotifications.BuildConfig;
import com.wix.reactnativenotifications.core.notification.IPushNotification;
import com.wix.reactnativenotifications.core.notification.PushNotification;

import org.json.JSONObject;

public class ProxyService extends IntentService {

    private static final String TAG = ProxyService.class.getSimpleName();

    public ProxyService() {
        super("notificationsProxyService");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onHandleIntent(Intent intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "New intent: " + JSONObject.wrap(intent));
        final Bundle notificationData = NotificationIntentAdapter.extractPendingNotificationDataFromIntent(intent);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
            Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
            if (remoteInput != null) {
                String reply = remoteInput.getCharSequence(PushNotification.KEY_TEXT_REPLY).toString();
                notificationData.putString(PushNotification.KEY_TEXT_REPLY, reply);
            }
        }
        final IPushNotification pushNotification = PushNotification.get(this, notificationData);
        if (pushNotification != null) {
            if (notificationData.containsKey(PushNotification.KEY_TEXT_REPLY)) {
                try {
                    pushNotification.onReceived();
                    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.deleteNotificationChannel(PushNotification.CHANNEL_ID);

                } catch (IPushNotification.InvalidNotificationException e) {
                }
            } else {
                pushNotification.onOpened();
            }
        }
    }
}
