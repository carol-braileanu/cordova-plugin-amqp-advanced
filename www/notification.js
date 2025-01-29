    var notification = {
        listener: function (event, data) {},  

        onConnectionLost: function () {
            console.log("Connection lost. Attempting to reconnect...");
            setTimeout(() => {
                push.register(myListener, config, onConnectionLost);
            }, 5000); // Reîncercare după 5 secunde
        },

        listenerCallback: function (event, data) {
            //console.log("Listener callback invoked:", event, JSON.stringify(data));
            notification.listener(event, data);
        },

        register: function (listener, configuration, onConnectionLostCallback, successAppCallback, errorAppCallback) {
            notification.listener = listener;
            notification.onConnectionLost =
                onConnectionLostCallback ||
                function () {
                    console.warn("Default onConnectionLost callback: Will try to reconect"); // onConnectionLost
                };

            function successCb() {
                console.log("Success in registration Broker");
                if (successAppCallback) {
                    successAppCallback();  
                }
            }

            function errorCb(data) {
                //console.log("Error while registration: " + data);
                if (errorAppCallback) {
                    errorAppCallback(data);  
                }
            }

            cordova.exec(
                successCb,
                errorCb,
                "Push",
                "initialize",
                [
                    {
                        notificationListener: "window.push.listenerCallback",
                        configuration: configuration
                    }
                ]
            );
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
        }
    };

    module.exports = notification;
