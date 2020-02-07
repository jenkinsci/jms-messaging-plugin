/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.redhat.jenkins.plugins.ci.messaging.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder(
        alphabetic = true
)
public class RabbitMQMessage {

    private static final Logger log = Logger.getLogger(RabbitMQMessage.class.getName());

    private long timestamp;
    private String topic;
    private String msgId;
    private long deliveryTag;
    @JsonProperty("msg")
    private Map<String, Object> msg = null;

    public RabbitMQMessage() {}

    public RabbitMQMessage(String topic) {
        this(topic, null, null);
    }

    public RabbitMQMessage(String topic, String body) {
        this(topic, body, null);
    }

    public RabbitMQMessage(String topic, String body, String msgId) {
        this.topic = topic;
        if (msgId != null) {
            this.msgId = msgId;
        }
        else {
            this.msgId = Integer.toString(Calendar.getInstance().get(1)) + "-" + UUID.randomUUID().toString();
        }

        if (!StringUtils.isBlank(body)) {
            try {
                log.fine("Message to deserialize: '" + body + "'");
                ObjectMapper mapper = new ObjectMapper();
                TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
                msg = mapper.readValue(body, typeRef);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unable to deserialize message body.", e);
            }
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Date getTimestamp() {
        return new Date(this.timestamp);
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getDeliveryTag() { return deliveryTag; }

    public void setDeliveryTag(long deliveryTag) { this.deliveryTag = deliveryTag; }

    @JsonProperty("msg_id")
    public final String getMsgId() {
        return this.msgId;
    }

    public Map<String, Object> getMsg() {
        return msg;
    }

    public void setMsg(Map<String, Object> msg) {
        this.msg = msg;
    }

    @JsonIgnore
    public String getBodyJson() {
        if (msg != null) {
            return JSONObject.fromObject(msg).toString();
        }
        return "";
    }

    public String toJson() {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writer();
            writer.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValue(os, this);
            os.close();
            return new String(os.toByteArray(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
