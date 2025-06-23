    var notification = {
        listener: function (event, data) {},  

        listenerCallback: function (event, data) {
            //console.log("Listener callback invoked:", event, JSON.stringify(data));
            notification.listener(event, data);
        },

        connect: function (configuration, onConnectionLostCallback) {
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

        createTemporaryQueue: function (successCallback, errorCallback) {
            cordova.exec(
                function (queueName) {
                    //console.log("Temporary queue created:", queueName);
                    notification.listenerCallback("temporaryQueueCreated", {
                        queueName: queueName
                    });

                    successCallback(queueName);
                },
                errorCallback,
                "Push",
                "createTemporaryQueue",
                []
            );
        },
        deleteTemporaryQueue: function(queueName, successCallback, errorCallback) {
            if (!queueName || typeof queueName !== "string") {
                console.error("Queue name is required and must be a string");
                if (errorCallback) {
                    errorCallback("Queue name is required and must be a string");
                }
                return;
            }
        
            cordova.exec(
                successCallback,
                errorCallback,
                "Push",
                "deleteTemporaryQueue",
                [queueName]
            );
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
        }
    };

    module.exports = notification;
