package org.amqp.notification;


import org.amqp.notification.NotificationService;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.util.Log;

class PushManager  {

    public PushManager( Activity activity , Push push) {
        Log.e("RabbitMQ - PushManager", "Starting NotificationService...");
        if (activity == null) {
            Log.e("PushManager", "Activity is null. Cannot start service.");
            return;
        }
        Intent intent = new Intent(activity, NotificationService.class);
        activity.startService(intent);
        Log.e("RabbitMQ - PushManager", "NotificationService started successfully.");

    }
}

