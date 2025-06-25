package org.amqp.notification;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import com.rabbitmq.client.*;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import java.util.concurrent.TimeoutException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.List;
import android.util.Pair;

public class NotificationService extends Service {

    protected Thread amqpThread;
    protected static Connection connection;
    private static CordovaWebView cordovaWebView;
    private Channel temporaryChannel;
    private Channel channel;
    private static Context serviceContext;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceContext = this; // Init the context
        Log.e("NotificationService", "Service context initialized.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("NotificationService", "Service started with onStartCommand.");
        if (serviceContext == null) {
            serviceContext = this;
            Log.e("NotificationService", "Service context initialized in onStartCommand.");
        }
        return START_REDELIVER_INTENT;
    }

    protected void connect(Config configuration, CallbackContext callbackContext) {

        amqpThread = new Thread(() -> {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(configuration.host);
                factory.setUsername(configuration.username);
                factory.setPassword(configuration.password);
                factory.setVirtualHost(configuration.virtualHost);
                factory.setPort(configuration.port);
                // factory.useSslProtocol("TLSv1.2");

                factory.setAutomaticRecoveryEnabled(true);
                factory.setHandshakeTimeout(5000);
                factory.setRequestedHeartbeat(30);
                factory.setNetworkRecoveryInterval(5000);
                if (connection!=null && connection.isOpen())
                    connection.close();

                connection = factory.newConnection();
                this.channel = connection.createChannel();
                this.channel.basicQos(1);
                Log.e("RabbitMQ", "Connection established");

                connection.addShutdownListener(cause -> {
                    if (!cause.isInitiatedByApplication()) {
                        Log.e("RabbitMQ", "Connection lost: " + cause.getMessage());
                        String js = "window.push.onConnectionLost()";
                        cordovaWebView.loadUrl("javascript:" + js);
                    }
                });

                callbackContext.success();
            } catch (Exception e) {
                Log.e("RabbitMQ Error", "Error in RabbitMQ listener", e);
                callbackContext.error("connection error");
            }
        });

        amqpThread.start();
    }

    public void createAndListenTemporaryQueueAsync(CallbackContext callbackContext) {
        new Thread(() -> {
            try {
                if (connection == null || !connection.isOpen()) {
                    Log.e("RabbitMQ", "Connection is not open. Cannot create temporary queue.");
                    callbackContext.error("Connection is not open.");
                    return;
                }

                /*
                 * String queueName = "NotificationQueue";
                 * boolean durable = false; // if the rabbitMQ server stops, the queue is stile
                 * available
                 * boolean exclusive = false; //the queue can be consume by other connexion, not
                 * dedicated to that connection only.
                 * boolean autoDelete = false; //the queue must not be deleted, because it miht
                 * be use eventually by other apps
                 * channel.queueDeclare(queueName, durable, exclusive, autoDelete, null);
                 */

                // Map<String, Object> argsMap = new HashMap<>();
                // argsMap.put("x-expires", 120000);
                // argsMap

                // String queueName = channel.queueDeclare("", false, true, true,
                // null).getQueue();
                String queueName = channel.queueDeclare(
                        "", // Numele cozii va fi generat automat
                        false, // Durabilitatea cozii (false = nu e durabilă)
                        true, // Exclusivă (true = se poate folosi doar de către conexiunea curentă)
                        true, // Auto-deleted (coada va fi ștearsă automat când nu mai există consumatori)
                        null // x-expires = 120000 milisecunde (adică 120 de secunde)
                ).getQueue();

                Log.e("RabbitMQ", "Temporary queue created: " + queueName);

                channel.basicConsume(queueName, false, "consumer_" + queueName, new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                            byte[] body) throws IOException {

                        JSONObject message = new JSONObject();
                        message.put("message", new String(body));
                        message.put("deliveryTag", envelope.getDeliveryTag());
                        Log.e("RabbitMQ Message", "Received from TEMP queue: " + message);

                        Intent intent = new Intent();
                        intent.setAction(PushReceiver.PUSH_INTENT_ACTION);
                        intent.putExtra(PushReceiver.PUSH_INTENT_EXTRA, message.toString());
                        intent.setClassName(NotificationService.serviceContext.getPackageName(),
                                "org.amqp.notification.PushReceiver");
                        NotificationService.serviceContext.sendBroadcast(intent);

                        Log.e("RabbitMQ", "Deleting temporary queue: " + queueName);
                        channel.queueDelete(queueName);
                        try {
                            channel.close();
                            Log.e("RabbitMQ", "Channel closed successfully.");
                        } catch (TimeoutException e) {
                            Log.e("RabbitMQ", "TimeoutException while closing the channel: " + e.getMessage());
                        } catch (IOException e) {
                            Log.e("RabbitMQ", "IOException while closing the channel: " + e.getMessage());
                        }
                    }
                });

                Log.e("RabbitMQ", "Listening on temporary queue: " + queueName);
                callbackContext.success(queueName);
            } catch (IOException e) {
                Log.e("RabbitMQ", "Error in createAndListenTemporaryQueueAsync: " + e.getMessage());
                callbackContext.error("Error creating temporary queue: " + e.getMessage());
            }
        }).start();
    }

    public void listenQueueAsync(String queueName,
            CallbackContext callbackContext) {
        new Thread(() -> {
            try {
                if (connection == null || !connection.isOpen()) {
                    Log.e("RabbitMQ", "Connection is not open. Cannot create temporary queue.");
                    callbackContext.error("Connection is not open.");
                    return;
                }

                Log.e("RabbitMQ Message", "Coonecting to queue: " + queueName);
                try {
                    channel.basicCancel("consumer_" + queueName);
                } catch (IOException ex) {
                    ex.printStackTrace();

                }

                Log.e("RabbitMQ Message", "Consumer cancelled: consumer_" + queueName);

                channel.basicConsume(queueName, false, "consumer_" + queueName, new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                            byte[] body) throws IOException {

                        JSONObject message = new JSONObject();
                        message.put("message", new String(body));
                        message.put("deliveryTag", envelope.getDeliveryTag());
                        Log.e("RabbitMQ Message", "Received from TEMP queue: " + message);

                        Intent intent = new Intent();
                        intent.setAction(PushReceiver.PUSH_INTENT_ACTION);
                        intent.putExtra(PushReceiver.PUSH_INTENT_EXTRA, message.toString());
                        intent.setClassName(NotificationService.serviceContext.getPackageName(),
                                "org.amqp.notification.PushReceiver");
                        NotificationService.serviceContext.sendBroadcast(intent);

                        Log.e("RabbitMQ", "Deleting temporary queue: " + queueName);
                        channel.queueDelete(queueName);
                        try {
                            channel.close();
                            Log.e("RabbitMQ", "Channel closed successfully.");
                        } catch (TimeoutException e) {
                            Log.e("RabbitMQ", "TimeoutException while closing the channel: " + e.getMessage());
                        } catch (IOException e) {
                            Log.e("RabbitMQ", "IOException while closing the channel: " + e.getMessage());
                        }
                    }
                });

                Log.e("RabbitMQ", "Listening on queue: " + queueName);
                callbackContext.success();
            } catch (IOException e) {
                Log.e("RabbitMQ", "Error in createAndListenTemporaryQueueAsync: " + e.getMessage());
                callbackContext.error("Error creating temporary queue: " + e.getMessage());
            }
        }).start();
    }

    public void sendAck(long deliveryTag, CallbackContext callbackContext) {
        channel.basicAck(deliveryTag, false);
        callbackContext.success();
    }

    public void sendMessageAsync(String message, String exchange, String routingKey, CallbackContext callbackContext) {
        new Thread(() -> {
            if (connection == null || !connection.isOpen()) {
                Log.e("RabbitMQ", "Connection is not open. Cannot create temporary queue.");
                callbackContext.error("Connection is not open.");
                return;
            }

            try {
                channel.basicPublish(exchange, routingKey, null, message.getBytes("utf-8"));
                callbackContext.success();
            } catch (IOException ex) {
                ex.printStackTrace();
                callbackContext.error(ex.getMessage());
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        amqpThread = null;
        try {
            if (connection != null && connection.isOpen())
                this.connection.close();
        } catch(IOException ex) {}
        super.onDestroy();
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}
