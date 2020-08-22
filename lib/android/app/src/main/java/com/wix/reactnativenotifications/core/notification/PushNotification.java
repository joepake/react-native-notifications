package com.wix.reactnativenotifications.core.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

import com.facebook.react.bridge.ReactContext;
import com.wix.reactnativenotifications.R;
import com.wix.reactnativenotifications.core.AppLaunchHelper;
import com.wix.reactnativenotifications.core.AppLifecycleFacade;
import com.wix.reactnativenotifications.core.AppLifecycleFacade.AppVisibilityListener;
import com.wix.reactnativenotifications.core.AppLifecycleFacadeHolder;
import com.wix.reactnativenotifications.core.InitialNotificationHolder;
import com.wix.reactnativenotifications.core.JsIOHelper;
import com.wix.reactnativenotifications.core.NotificationIntentAdapter;
import com.wix.reactnativenotifications.core.ProxyService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import static com.wix.reactnativenotifications.Defs.NOTIFICATION_OPENED_EVENT_NAME;
import static com.wix.reactnativenotifications.Defs.NOTIFICATION_RECEIVED_EVENT_NAME;

@RequiresApi(api = Build.VERSION_CODES.N)
public class PushNotification implements IPushNotification {
    public static final String KEY_TEXT_REPLY = "text_reply";
    public static final String CHANNEL_ID = "BBW_ID";
    public static final String CHANNEL_NAME = "BBW";

    final protected Context mContext;
    final protected AppLifecycleFacade mAppLifecycleFacade;
    final protected AppLaunchHelper mAppLaunchHelper;
    final protected JsIOHelper mJsIOHelper;
    final protected PushNotificationProps mNotificationProps;
    final protected AppVisibilityListener mAppVisibilityListener = new AppVisibilityListener() {
        @Override
        public void onAppVisible() {
            mAppLifecycleFacade.removeVisibilityListener(this);
            dispatchImmediately();
        }

        @Override
        public void onAppNotVisible() {
        }
    };

    public static IPushNotification get(Context context, Bundle bundle) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof INotificationsApplication) {
            return ((INotificationsApplication) appContext).getPushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper());
        }
        return new PushNotification(context, bundle, AppLifecycleFacadeHolder.get(), new AppLaunchHelper(), new JsIOHelper());
    }

    protected PushNotification(Context context, Bundle bundle, AppLifecycleFacade appLifecycleFacade, AppLaunchHelper appLaunchHelper, JsIOHelper JsIOHelper) {
        mContext = context;
        mAppLifecycleFacade = appLifecycleFacade;
        mAppLaunchHelper = appLaunchHelper;
        mJsIOHelper = JsIOHelper;
        mNotificationProps = createProps(bundle);
    }

    @Override
    public void onReceived() throws InvalidNotificationException {
        if (!mAppLifecycleFacade.isAppVisible()) {
            postNotification(null);
        }
        notifyReceivedToJS();
    }

    @Override
    public void onOpened() {
        digestNotification();
    }

    @Override
    public int onPostRequest(Integer notificationId) {
        return postNotification(notificationId);
    }

    @Override
    public PushNotificationProps asProps() {
        return mNotificationProps.copy();
    }

    protected int postNotification(Integer notificationId) {
        final PendingIntent pendingIntent = getCTAPendingIntent();
        final Notification notification = buildNotification(pendingIntent);
        return postNotification(notification, notificationId);
    }

    protected void digestNotification() {
        if (!mAppLifecycleFacade.isReactInitialized()) {
            setAsInitialNotification();
            launchOrResumeApp();
            return;
        }

        final ReactContext reactContext = mAppLifecycleFacade.getRunningReactContext();
        if (reactContext.getCurrentActivity() == null) {
            setAsInitialNotification();
        }

        if (mAppLifecycleFacade.isAppVisible()) {
            dispatchImmediately();
        } else if (mAppLifecycleFacade.isAppDestroyed()) {
            launchOrResumeApp();
        } else {
            dispatchUponVisibility();
        }
    }

    protected PushNotificationProps createProps(Bundle bundle) {
        return new PushNotificationProps(bundle);
    }

    protected void setAsInitialNotification() {
        InitialNotificationHolder.getInstance().set(mNotificationProps);
    }

    protected void dispatchImmediately() {
        notifyOpenedToJS();
    }

    protected void dispatchUponVisibility() {
        mAppLifecycleFacade.addVisibilityListener(getIntermediateAppVisibilityListener());

        // Make the app visible so that we'll dispatch the notification opening when visibility changes to 'true' (see
        // above listener registration).
        launchOrResumeApp();
    }

    protected AppVisibilityListener getIntermediateAppVisibilityListener() {
        return mAppVisibilityListener;
    }

    protected PendingIntent getCTAPendingIntent() {
        final Intent cta = new Intent(mContext, ProxyService.class);
        return NotificationIntentAdapter.createPendingNotificationIntent(mContext, cta, mNotificationProps);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    protected Notification buildNotification(PendingIntent intent) {
        return getNotificationBuilder(intent).build();
    }

    protected Notification.Builder getNotificationBuilder(PendingIntent intent) {
        String replyLabel = mContext.getString(R.string.reply);

        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(replyLabel)
                .setAllowFreeFormInput(true)
                .build();

        Notification.Action action =
                new Notification.Action.Builder(R.mipmap.ic_launcher, replyLabel, getCTAPendingIntent())
                        .addRemoteInput(remoteInput)
                        .setAllowGeneratedReplies(true)
                        .build();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();

        final Notification.Builder notification = new Notification.Builder(mContext)
                .setContentTitle(mNotificationProps.getTitle())
                .setContentText(mNotificationProps.getBody())
                .setContentIntent(intent)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSound(getSound(mNotificationProps.getSound()), attributes)
                .setAutoCancel(true);

        if (mNotificationProps.isNewMsgType()) {
            notification.setActions(action);
        }

        setUpIcon(notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);

                // Configure the notification channel.
                channel.enableLights(true);
                channel.setVibrationPattern(new long[]{1000, 1000});
                channel.enableVibration(true);
                channel.setSound(getSound(mNotificationProps.getSound()), attributes);

                notificationManager.createNotificationChannel(channel);
            }
            notification.setChannelId(CHANNEL_ID);
        }

        return notification;
    }

    private void setUpIcon(Notification.Builder notification) {
        int iconResId = getAppResourceId("notification_icon", "drawable");
        if (iconResId != 0) {
            notification.setSmallIcon(iconResId);
        } else {
            notification.setSmallIcon(mContext.getApplicationInfo().icon);
        }

        setUpIconColor(notification);
    }

    private void setUpIconColor(Notification.Builder notification) {
        int colorResID = getAppResourceId("colorAccent", "color");
        if (colorResID != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color = mContext.getResources().getColor(colorResID);
            notification.setColor(color);
        }
    }

    protected int postNotification(Notification notification, Integer notificationId) {
        int id = notificationId != null ? notificationId : createNotificationId(notification);
        postNotification(id, notification);
        return id;
    }

    protected void postNotification(int id, Notification notification) {
        final NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);
    }

    protected int createNotificationId(Notification notification) {
        return (int) System.nanoTime();
    }

    private void notifyReceivedToJS() {
        mJsIOHelper.sendEventToJS(NOTIFICATION_RECEIVED_EVENT_NAME, mNotificationProps.asBundle(), mAppLifecycleFacade.getRunningReactContext());
    }

    private void notifyOpenedToJS() {
        Bundle response = new Bundle();
        response.putBundle("notification", mNotificationProps.asBundle());

        mJsIOHelper.sendEventToJS(NOTIFICATION_OPENED_EVENT_NAME, response, mAppLifecycleFacade.getRunningReactContext());
    }

    protected void launchOrResumeApp() {
        final Intent intent = mAppLaunchHelper.getLaunchIntent(mContext);
        mContext.startActivity(intent);
    }

    private int getAppResourceId(String resName, String resType) {
        return mContext.getResources().getIdentifier(resName, resType, mContext.getPackageName());
    }

    private Uri getSound(String sound) {
        if (sound == null) {
            return null;
        } else if (sound.contains("://")) {
            return Uri.parse(sound);
        } else if (sound.equalsIgnoreCase("default")) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else {
            int soundResourceId = getResourceId("raw", sound);
            if (soundResourceId == 0) {
                soundResourceId = getResourceId("raw", sound.substring(0, sound.lastIndexOf('.')));
            }
            return Uri.parse("android.resource://" + mContext.getPackageName() + "/" + soundResourceId);
        }
    }

    private int getResourceId(String type, String image) {
        return mContext
                .getResources()
                .getIdentifier(image, type, mContext.getPackageName());
    }

}
