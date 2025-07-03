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
import org.json.JSONException;
import java.io.*;
import java.util.List;
import android.util.Pair;

public class NotificationService extends Service {

    protected Thread amqpThread;
    protected static Connection connection;
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

    protected void connect(JSONObject config) throws JSONException, IOException {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(config.getString("host"));
                factory.setUsername(config.getString("username"));
                factory.setPassword(config.getString("password"));
                factory.setVirtualHost(config.getString("virtualHost"));
                factory.setPort(config.getInt("port"));
                // factory.useSslProtocol("TLSv1.2");

                factory.setAutomaticRecoveryEnabled(false);
                factory.setHandshakeTimeout(5000);
                factory.setRequestedHeartbeat(30);
                factory.setNetworkRecoveryInterval(5000);
                if (connection!=null && connection.isOpen())
                    connection.close();

                    try {
                connection = factory.newConnection();
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        throw new IOException(e.getMessage());
                    }

                this.channel = connection.createChannel();
                this.channel.basicQos(1);
                Log.e("RabbitMQ", "Connection established");

                connection.addShutdownListener(cause -> {
                    if (!cause.isInitiatedByApplication()) {
                        Log.e("RabbitMQ", "Connection lost: " + cause.getMessage());
                        String js = "window.push.onConnectionLost()";
                        Push.cordovaWebView.sendJavascript("javascript:" + js);
                    }
                });

    }

    public void listenQueue(String queueName) throws JSONException, IOException {
                if (connection == null || !connection.isOpen()) {
                    throw new IOException("Connection is not open");
                }

                Log.e("RabbitMQ Message", "Consumer cancelled: consumer_" + queueName);

                channel.basicConsume(queueName, false, "consumer_" + queueName, new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                            byte[] body) throws IOException {

                        Push.proceedNotification(new PushNotification(new String(body), envelope.getDeliveryTag()));
                    }
                });

                Log.e("RabbitMQ", "Listening on queue: " + queueName);

    }

    public void sendAck(long deliveryTag) throws IOException {
        channel.basicAck(deliveryTag, false);
    }

    public void sendMessage(String message, String exchange, String routingKey) throws IOException {
            if (connection == null || !connection.isOpen()) {
                throw new IOException("Connection is not open");
            }

            channel.basicPublish(exchange, routingKey, null, message.getBytes("utf-8"));
    }

    public void closeConnection() throws IOException {
        if (connection != null && connection.isOpen())
            this.connection.close();
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
