Cordova AMQP Plugin

Overview

This plugin provides a messaging notification system using an AMQP broker for both Android and iOS platforms. It allows devices to receive messages from a broker using a unique identifier, ensuring reliable message delivery even when the network connection is lost.

This plugin is an updated and improved version of cordova-plugin-amqp, originally developed 10 years ago.

Features

Connects to an AMQP broker to send and receive messages.

Automatically restarts the listener when the network becomes available.

Ensures messages are retained in the queue until they are processed.

Supports SSL and non-SSL connections but recommendation is to stay only on ssl.

Sends notifications with JSON content.

Works seamlessly with Cordova applications.


Installation

To install this plugin, use:

From GitHub:   
cordova plugin add <plugin-repository-url>

OR 

From NPM:      
cordova plugin add cordova-plugin-amqp-advanced (NPM)


document.addEventListener('deviceready', function () {
    var config = {
      host: "app.domain.com",
      port: "5671",  // 5671 for SSL, 5672 for non-SSL
      username: "myUsername",
      password: "myPassword",
      virtualHost: "/",
      queueName: "your-queue-name",
      routingKey: "some-routing-key"
    };
  
    var myListener = function (event, data) {
      try {
          if (typeof data === "string" && data.startsWith("{") && data.endsWith("}")) {
              var jsonData = JSON.parse(data);
              console.log("Received JSON:", jsonData);
              alert("JSON Status: " + JSON.stringify(jsonData));
          } else {
              console.log("Received string:", data);
              alert("Message: " + data);
          }
      } catch (e) {
          console.error("Error processing message:", e, data);
          alert("Invalid message format: " + data);
      }
    };
  
    push.register(myListener, config);
});
Configuration Object
{
  "host": "app.domain.com",
  "port": "5671",
  "virtualHost": "/",
  "username": "myUsername",
  "password": "myPassword",
  "queueName": "your-queue-name",
  "routingKey": "some-routing-key"
}
Plugin API
register(listener, configuration, onConnectionLostCallback, successAppCallback, errorAppCallback)
Registers the plugin and connects to the AMQP broker.

Parameters:

listener: Function to handle incoming messages.

configuration: JSON object containing broker settings.

onConnectionLostCallback: Optional callback when the connection is lost.

successAppCallback: Optional success callback.

errorAppCallback: Optional error callback.

createTemporaryQueue(successCallback, errorCallback)
Creates a temporary queue for receiving messages.

Parameters:

successCallback(queueName): Callback function that returns the created queue name.

errorCallback(error): Callback function for error handling.

deleteTemporaryQueue(queueName, successCallback, errorCallback)
Deletes a specified temporary queue.

Parameters:

queueName: Name of the queue to be deleted.

successCallback(): Callback function for successful deletion.

errorCallback(error): Callback function for error handling.

Handling Connection Loss
If the connection to the broker is lost, the listener will attempt to reconnect automatically:

var notification = {
    onConnectionLost: function () {
        console.log("Connection lost. Attempting to reconnect...");
        setTimeout(() => {
            push.register(myListener, config, onConnectionLost);
        }, 5000); // Retry after 5 seconds
    }
};


License
This project is licensed under the MIT License.

IMPORTANT:
This is an little advanced / updated version of:
https://github.com/etouraille/cordova-plugin-amqp (11years old now in 2025)


