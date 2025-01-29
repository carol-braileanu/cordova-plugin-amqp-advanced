import Foundation
import RMQClient

@objc(Push)
class Push: CDVPlugin, RMQConnectionDelegate {
    private var connection: RMQConnection?
    private var channel: RMQChannel?
    private var notificationEventListener: String?
    private var cachedNotifications = [String]()
    private var rabbitConfiguration: [String: Any]? // Adaugă această linie
    //private var temporaryQueueName: String?
    private var temporaryQueueNames: Set<String> = []


    // MARK: - Initialize RabbitMQ connection
    @objc(initialize:)
    func initialize(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId
        print("Push - Starting initialization of RabbitMQ connection...")

        do {
            // Validăm și extragem configurația din argumentele primite
            guard let args = command.arguments.first as? [String: Any],
                let configuration = args["configuration"] as? [String: Any] else {
                throw NSError(domain: "Push", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid configuration"])
            }

            // Salvăm configurația și inițializăm RabbitMQ
            self.initializeRabbitMQ(configuration: configuration)

            let result = CDVPluginResult(status: .ok, messageAs: "RabbitMQ initialized successfully")
            self.commandDelegate.send(result, callbackId: callbackId)

        } catch let error as NSError {
            print("Push - Initialization error: \(error.localizedDescription)")
            let result = CDVPluginResult(status: .error, messageAs: error.localizedDescription)
            self.commandDelegate.send(result, callbackId: callbackId)
        }
    }

/*
     @objc(deleteTemporaryQueue:)
     func deleteTemporaryQueue(command: CDVInvokedUrlCommand) {
         let callbackId = command.callbackId

         do {
             guard let channel = self.channel,
                   let queueName = self.temporaryQueueName else {
                 throw NSError(domain: "Push", code: 500, userInfo: [NSLocalizedDescriptionKey: "No active channel or queue name not found"])
             }

             print("Push - Deleting temporary queue: \(queueName)")
             channel.queueDelete(queueName)
             print("Push - Temporary queue \(queueName) deleted successfully.")

             // Închidem canalul dacă nu mai este utilizat
             channel.close()
             print("Push - Channel closed successfully.")

             let result = CDVPluginResult(status: .ok, messageAs: "Temporary queue deleted successfully")
             self.commandDelegate.send(result, callbackId: callbackId)
            
         } catch let error as NSError {
             print("Push - Error deleting temporary queue: \(error.localizedDescription)")
             let result = CDVPluginResult(status: .error, messageAs: error.localizedDescription)
             self.commandDelegate.send(result, callbackId: callbackId)
         }
     } */

    @objc(deleteTemporaryQueue:)
    func deleteTemporaryQueue(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId

        guard let queueName = command.arguments.first as? String else {
            let result = CDVPluginResult(status: .error, messageAs: "Queue name is required")
            self.commandDelegate.send(result, callbackId: callbackId)
            return
        }

        do {
            guard let channel = self.channel else {
                throw NSError(domain: "Push", code: 500, userInfo: [NSLocalizedDescriptionKey: "No active channel"])
            }

            // Verificăm dacă coada există în lista noastră
            if self.temporaryQueueNames.contains(queueName) {
                print("Push - Deleting temporary queue: \(queueName)")
                channel.queueDelete(queueName)
                self.temporaryQueueNames.remove(queueName)
                print("Push - Temporary queue \(queueName) deleted successfully.")

                let result = CDVPluginResult(status: .ok, messageAs: "Temporary queue \(queueName) deleted successfully")
                self.commandDelegate.send(result, callbackId: callbackId)
            } else {
                let result = CDVPluginResult(status: .error, messageAs: "Queue \(queueName) not found")
                self.commandDelegate.send(result, callbackId: callbackId)
            }

        } catch let error as NSError {
            print("Push - Error deleting temporary queue \(queueName): \(error.localizedDescription)")
            let result = CDVPluginResult(status: .error, messageAs: error.localizedDescription)
            self.commandDelegate.send(result, callbackId: callbackId)
        }
    }



    // MARK: - Create Temporary Queue
    @objc(createTemporaryQueue:)
    func createTemporaryQueue(command: CDVInvokedUrlCommand) {
        let callbackId = command.callbackId

        do {
            guard let channel = self.channel else {
                throw NSError(domain: "Push", code: 500, userInfo: [NSLocalizedDescriptionKey: "No active channel"])
            }

            print("Push - Creating temporary queue...")
            let options: RMQQueueDeclareOptions = [.exclusive, .autoDelete] // .autoDelete
            let queue = channel.queue("", options: options)

            print("Push - Temporary queue created: \(queue.name)")

            self.temporaryQueueNames.insert(queue.name)

            queue.subscribe({ (message: RMQMessage) in
                if let body = String(data: message.body, encoding: .utf8) {
                    print("Push - Received message from temporary queue \(queue.name): \(body)")
                    let js = """
                        window.push.listenerCallback("message", \(body))
                        """
                    self.sendJavascript(js)
                }                
            })

            // Reține numele cozii pentru ștergere ulterioară
            //self.temporaryQueueName = queue.name

            let result = CDVPluginResult(status: .ok, messageAs: queue.name)
            self.commandDelegate.send(result, callbackId: callbackId)
            
        } catch let error as NSError {
            print("Push - Temporary queue error: \(error.localizedDescription)")
            let result = CDVPluginResult(status: .error, messageAs: error.localizedDescription)
            self.commandDelegate.send(result, callbackId: callbackId)
        }
    }


    // MARK: - RMQConnectionDelegate methods
    func connection(_ connection: RMQConnection, failedToConnectWithError error: Error) {
        print("Push - Connection failed: \(error.localizedDescription)")
    }

    func connection(_ connection: RMQConnection, disconnectedWithError error: Error?) {
        if let error = error {
            print("Push - Connection disconnected with error: \(error.localizedDescription)")
        } else {
            print("Push - Connection disconnected gracefully.")
        }
    }

    func willStartRecovery(with connection: RMQConnection) {
        print("Push - Will start recovery for connection.")
    }

    func startingRecovery(with connection: RMQConnection) {
        print("Push - Starting recovery process.")
    }

    func recoveredConnection(_ connection: RMQConnection) {
        print("Push - Connection recovered successfully.")
    }

    func channel(_ channel: RMQChannel, error: Error) {
        print("Push - Channel error occurred: \(error.localizedDescription)")
    }


    private func initializeRabbitMQ(configuration: [String: Any]) {
    self.rabbitConfiguration = configuration

    guard let host = configuration["host"] as? String,
          let port = configuration["port"] as? Int,
          let username = configuration["username"] as? String,
          let password = configuration["password"] as? String,
          let virtualHost = configuration["virtualHost"] as? String else {
        print("Push - Invalid RabbitMQ configuration.")
        return
    }

    let uri = "amqps://\(username):\(password)@\(host):\(port)/\(virtualHost)"
    print("Push - Connecting to RabbitMQ with URI: \(uri)")

    let tlsOptions = RMQTLSOptions.fromURI(uri)
    self.connection = RMQConnection(uri: uri, tlsOptions: tlsOptions, delegate: self)

    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
        guard let self = self else { return }

        self.connection?.start()
        DispatchQueue.main.async {
            guard let connection = self.connection else {
                print("Push - Connection failed.")
                return
            }

            print("Push - Connection established successfully.")
            let channel = connection.createChannel()
            self.channel = channel
            self.startListeningToQueues(channel: channel)
        }
    }
} 




private func startListeningToQueues(channel: RMQChannel) {
    guard let configuration = rabbitConfiguration,
          let queues = configuration["queues"] as? [[String: String]] else {
        print("Push - No queues found in configuration.")
        return
    }

    for queueConfig in queues {
        guard let queueName = queueConfig["queueName"], !queueName.isEmpty else {
            print("Push - Invalid queue configuration.")
            continue
        }

        print("Push - Attempting to subscribe to queue: \(queueName)")

        let queue = channel.queue(queueName, options: .durable)
        queue.subscribe({ (message: RMQMessage) in
            if let body = String(data: message.body, encoding: .utf8) {
                print("Push - Received message from queue \(queueName): \(body)")

                // Validăm dacă este JSON valid
                if let jsonData = body.data(using: .utf8),
                let jsonObject = try? JSONSerialization.jsonObject(with: jsonData, options: []) {

                    // Serializăm JSON-ul pentru a-l trimite ca obiect
                    if let jsonString = String(data: try! JSONSerialization.data(withJSONObject: jsonObject, options: []), encoding: .utf8) {
                        let js = """
                        window.push.listenerCallback("message", \(jsonString))
                        """
                        self.sendJavascript(js)
                        print("Push - Sending valid JSON to JavaScript: \(js)")
                    } else {
                        print("Push - Failed to serialize JSON body.")
                    }
                } else {
                    print("Push - Received invalid JSON, discarding: \(body)")
                }
            }
        })

        print("Push - Subscribed to queue: \(queueName)")
    }
}



    // MARK: - Helper Methods
    private func sendJavascript(_ js: String) {
        guard let webView = self.webViewEngine?.engineWebView as? WKWebView else {
            print("Push - Failed to find WKWebView for sending JavaScript.")
            return
        }

        DispatchQueue.main.async {
            webView.evaluateJavaScript(js, completionHandler: { result, error in
                if let error = error {
                    print("Push - Error executing JavaScript: \(error.localizedDescription)")
                } else {
                    print("Push - JavaScript executed successfully.")
                }
            })
        }
    }

    private func isJsonValid(_ json: String) -> Bool {
        if let data = json.data(using: .utf8) {
            return (try? JSONSerialization.jsonObject(with: data, options: [])) != nil
        }
        return false
    }

}
