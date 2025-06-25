package org.amqp.notification ;

import java.lang.String;

class PushNotification {
    
    public String content;
    public long deliveryTag;

    public PushNotification(String content, long deliveryTag ) {
        this.content = content;
        this.deliveryTag = deliveryTag;
    }

}
