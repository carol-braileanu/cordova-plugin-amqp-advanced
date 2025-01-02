First of all I have to say that is an little advanced / updated version of:
https://github.com/etouraille/cordova-plugin-amqp (10years old now)


Notification system for broker meesaging 

 * Notification are send via a broker, and can adress a certain device 
  - the device is identified via a unique identifier
  - the brocker listener must restart when the network is available
  - the brocker listener must retain the message in the queue
  - the brocker listener must restart automatically when it stops
 * The sended message must raise a branded notification. 
 * The notification have a json content, the json format
 * The plugin is available for cordova.


 Simply access with -----

 document.addEventListener('deviceready', function () {
    var config = {
      host: "app.domain.com",
      port: "5671",  // 5671-ssl 5672-nonssl
      username: "myUsername",
      password: "myPassword",
      virtualHost: "/",
      queueName: "your-queue-name",
      routingKey: "some-routing-key"
    };
  

    var myListener = function (event, data) {
      try {
          // JSON or String -> + work must be done here
          if (typeof data === "string" && data.startsWith("{") && data.endsWith("}")) {
              var jsonData = JSON.parse(data);
              console.log("Received JSON:", jsonData);
              alert("JSON Status: " + JSON.stringify(jsonData));
          } else {
              // if is string just use it -> + work must be done here
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


  -------------


The config json example: 
  { 
       host : host, 
       port : port, 
       virtualhost : virtualhost, 
       login : login, 
       password : password,
       routingKey : key, 
  }

