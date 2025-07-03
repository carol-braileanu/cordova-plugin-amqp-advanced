    var notification = {
        listener: function (event, data) {},  

        listenerCallback: function (event, data) {
            //console.log("Listener callback invoked:", event, JSON.stringify(data));
            notification.listener(event, data);
        },

        connect: function (configuration, onConnectionLostCallback = null) {
            notification.onConnectionLost =
                onConnectionLostCallback ||
                function () {
                    console.warn("Default onConnectionLost callback: Will try to reconect"); // onConnectionLost
                };

            return new Promise((resolve, reject) => { cordova.exec(
                    resolve,
                    reject,
                    "Push",
                    "connect",
                    [
                        {
                            notificationListener: "window.push.listenerCallback",
                            configuration: configuration
                        }
                    ]
                );
            });
        },

        listenQueue: function(listener, queueName) {
            notification.listener = listener
            return new Promise((resolve, reject) => cordova.exec(
                resolve,
                reject,
                "Push",
                "listenQueue",
                [queueName, "window.push.listenerCallback"]
            ));
        },
        sendMessage: function(message, exchange, routingKey) {
            return new Promise((resolve, reject) => cordova.exec(
                resolve,
                reject,
                "Push",
                "sendMessage",
                [message, exchange, routingKey]
            ));
        },
        sendAck: function(tag) {
            return new Promise((resolve, reject) => cordova.exec(
                resolve,
                reject,
                "Push",
                "sendAck",
                [tag]
            ));
        },
        closeConnection: function(tag) {
            return new Promise((resolve, reject) => cordova.exec(
                resolve,
                reject,
                "Push",
                "closeConnection",
                []
            ));
        }

    };

    module.exports = notification;
