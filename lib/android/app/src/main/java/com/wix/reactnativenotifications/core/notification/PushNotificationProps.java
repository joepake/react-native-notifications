package com.wix.reactnativenotifications.core.notification;

import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

public class PushNotificationProps {

    protected Bundle mBundle;

    public PushNotificationProps(Bundle bundle) {
        mBundle = bundle;
    }

    public String getTitle() {
        return getBundleStringFirstNotNull("gcm.notification.title", "title");
    }

    public String getBody() {
        return getBundleStringFirstNotNull("gcm.notification.body", "alert");
    }

    public boolean isNewMsgType() {
        String type = "";
        Object additionalData = mBundle.get("additionalData");
        try {
            JSONObject jsonObject = new JSONObject(additionalData.toString());
            type = jsonObject.getString("type");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return type.equals("2");
    }

    public String getSound(){
        return mBundle.getString("sound");
    }

    public Bundle asBundle() {
        return (Bundle) mBundle.clone();
    }

    public boolean isFirebaseBackgroundPayload() {
        return mBundle.containsKey("google.message_id");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(1024);
        for (String key : mBundle.keySet()) {
            sb.append(key).append("=").append(mBundle.get(key)).append(", ");
        }
        return sb.toString();
    }

    protected PushNotificationProps copy() {
        return new PushNotificationProps((Bundle) mBundle.clone());
    }

    private String getBundleStringFirstNotNull(String key1, String key2) {
        Object result = mBundle.get(key1);
        return result == null ? String.valueOf(mBundle.get(key2)) : String.valueOf(result);
    }
}
