package org.amqp.notification;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;

import java.lang.String;
import android.util.Log;


public class PushReceiver extends BroadcastReceiver {
    public final static String PUSH_INTENT_EXTRA = "org.amqp.notification.push.intent.extra";
    public final static String PUSH_INTENT_ACTION = "org.amqp.notification.push.intent.action";   
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("PushReceiver", "Broadcast received with action: " + intent.getAction());
        if (PUSH_INTENT_ACTION.equals(intent.getAction())) {
            String message = intent.getStringExtra(PUSH_INTENT_EXTRA);
            Log.e("PushReceiver", "Message content: " + message);
            Push.proceedNotification(new PushNotification(message));
        } else {
            Log.e("PushReceiver", "Unknown action: " + intent.getAction());
        }
    }

}
