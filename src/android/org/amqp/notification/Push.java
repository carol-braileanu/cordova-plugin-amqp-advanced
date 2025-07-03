package org.amqp.notification;

import java.util.ArrayList;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import org.amqp.notification.PushNotification;
import org.amqp.notification.PushManager;
import org.amqp.notification.Config;
import android.util.Pair;
import com.rabbitmq.client.Channel;

public class Push extends CordovaPlugin {
	public static CallbackContext clbContext;
	private PushManager manager;
	public static boolean inPause;
	public static String notificationEventListener;
	public static CordovaInterface cordovaImpl;
	public static CordovaWebView cordovaWebView;
	private static List<PushNotification> cachedNnotifications = new ArrayList<PushNotification>();

	public static final String TAG = "Push";
	private NotificationService notificationService;

	public Push() {
		Log.e("RabbitMQ - Push Debug", "Push instance created");
	}

	public static final String ACTION_INITIALIZE = "connect";

	@Override
    public void pluginInitialize()
    {
		this.notificationService = new NotificationService();
		this.cordovaWebView = this.webView;
		this.cordovaImpl = cordova;

    }

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		try {
			Log.e("RabbitMQ - Push Debug", "Action received: " + action);

			clbContext = callbackContext;


			if ("listenQueue".equals(action)) {
				Log.e("RabbitMQ - Push Debug", "Processing action: createDirectQueueWithBinding");

				if (notificationService == null) {
					Log.e("RabbitMQ - Push Debug", "NotificationService is null. Cannot create temporary queue.");
					callbackContext.error("NotificationService not initialized.");
					return false;
				}

				this.cordova.getThreadPool().execute(() -> {
					try {
						notificationService.listenQueueAsync(args.getString(0));
					} catch (JSONException ex) {
					}
				});

				return true;
			}

			if ("sendMessage".equals(action)) {
				if (notificationService == null) {
					Log.e("RabbitMQ - Push Debug", "NotificationService is null. Cannot create temporary queue.");
					callbackContext.error("NotificationService not initialized.");
					return false;
				}
				notificationService.sendMessageAsync(args.getString(0), args.getString(1), args.getString(2), callbackContext);
				return true;
			}

			if ("sendAck".equals(action)) {
				if (notificationService == null) {
					Log.e("RabbitMQ - Push Debug", "NotificationService is null. Cannot create temporary queue.");
					callbackContext.error("NotificationService not initialized.");
					return false;
				}
				notificationService.sendAck(args.getLong(0), callbackContext);
				return true;
			}

			if ("closeConnection".equals(action)) {
				if (notificationService == null) {
					Log.e("RabbitMQ - Push Debug", "NotificationService is null. Cannot create temporary queue.");
					callbackContext.error("NotificationService not initialized.");
					return false;
				}
				notificationService.closeConnectionAsync(callbackContext);
				return true;
			}

			// Setting `notificationEventListener` only if exists in args
			if (args.length() > 0 && args.getJSONObject(0).has("notificationListener")) {
				notificationEventListener = args.getJSONObject(0).getString("notificationListener");
				Log.e("RabbitMQ - Push Debug", "Notification listener set: " + notificationEventListener);
			} else {
				Log.e("RabbitMQ - Push Debug", "Notification listener not provided in arguments.");
			}

			if (args.length() > 0 && args.getJSONObject(0).has("configuration")) {
				JSONObject configJson = args.getJSONObject(0).getJSONObject("configuration");
				Log.e("RabbitMQ - Push Debug", "Full configuration received: " + configJson.toString());

				// Extragem lista de cozi (queues) din configurație
				// JSONArray configArray = configJson.getJSONArray("queues");
				// Log.e("RabbitMQ - Push Debug", "Queues array: " + configArray.toString());

				// Saving here configs using config class
				Config.init(configJson, cordova.getActivity().getApplicationContext());
			}

			Log.e("RabbitMQ - Push INIT", "Initialization complete");

			this.manager = new PushManager(cordova.getActivity(), this);

			// check if action is init
			if (ACTION_INITIALIZE.equals(action)) {
				// check if something exists in cache
				if (!cachedNnotifications.isEmpty()) {
					for (PushNotification notification : cachedNnotifications) {
						Log.d(Push.TAG, "RabbitMQ - Push - Processing cached push: " + notification.toString());
						proceedNotification(notification);
					}
					cachedNnotifications.clear();
				}
				notificationService.connect(new Config(cordova.getActivity().getApplicationContext()), clbContext);
				return true;
			}

			// unknown action
			callbackContext.error("RabbitMQ - Push - Invalid action: " + action);
			return false;

		} catch (JSONException e) {
			Log.e("<<<<<<EXCEPTION >>>>>>>", e.getMessage());
			callbackContext.error(e.getMessage());
			return false;
		}
	}

	public static boolean isActive() {

		if (cordovaWebView != null) {
			return true;
		}
		return false;
	}

	@Override
	public void onPause(boolean multitasking) {
		super.onPause(multitasking);
		Push.inPause = true;
	}

	@Override
	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		Push.inPause = false;
	}

	public static void sendJavascript(String js) {
		if (null != cordovaWebView) {
			Log.d("RabbitMQ -JScript", "JS" + js);
			Log.e("RabbitMQ - Push CordovaWebView", "CordovaWebView status: " + (cordovaWebView != null));

			cordovaWebView.loadUrl("javascript:" + js);
		}
	}

	public static void proceedNotification(PushNotification extras) {
		if (null != extras) {
			if (null != cordovaWebView) {
				try {
					// check if is valid JSON
					String message = extras.content; // message String
					String js;

					if (isJsonValid(message)) {
						// if JSON, send it directly
						js = "window.push.listenerCallback(" + extras.deliveryTag + ", " + extras.content + ")";
					} else {
						js = "window.push.listenerCallback(" + extras.deliveryTag + ", \"" + extras.content.replace("\"", "\\\"") + "\")";
					}

					cordovaWebView.sendJavascript(js);
				} catch (Exception e) {
					Log.e("RabbitMQ -JScript Push", "Error while sending notification", e);
				}
			}
		}
	}

	public static List<JSONObject> getConfigurations(Context context) {
		try {
			// create list for configurations
			List<JSONObject> configurations = new ArrayList<>();

			// read configs
			Config config = new Config(context);

			// Convert `queues` in JSONArray
			JSONArray queues = new JSONArray(config.queues);

			// Iterte queues and add
			for (int i = 0; i < queues.length(); i++) {
				configurations.add(queues.getJSONObject(i));
			}

			return configurations;
		} catch (Exception e) {
			Log.e("RabbitMQ - Push", "Error retrieving configurations", e);
			return new ArrayList<>(); // Returnează o listă goală în caz de eroare
		}
	}

	private static boolean isJsonValid(String json) {
		try {
			new JSONObject(json);
			return true;
		} catch (JSONException ex) {
			return false;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		cordovaWebView = null;
	}

	@Override
	public boolean onOverrideUrlLoading(String url) {
		return false;
	}

}
