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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
public class FedmsgMessage {

    public FedmsgMessage() {
        i = 1;
        this.msgId = Integer.toString(Calendar.getInstance().get(1)) + "-" + UUID.randomUUID().toString();
    }

    FedmsgMessage(HashMap<String, Object> var1, String var2, long var3, long var5) {
        this.msg = var1;
        this.topic = var2;
        this.timestamp = var3;
        this.i = var5;
        this.msgId = Integer.toString(Calendar.getInstance().get(1)) + "-" + UUID.randomUUID().toString();
    }

    FedmsgMessage(FedmsgMessage var1) {
        this.msg = var1.getMsg();
        this.topic = var1.getTopic();
        this.timestamp = var1.getTimestamp().getTime();
        this.i = var1.getI();
        this.msgId = var1.getMsgId();
    }

    private long timestamp;
    private String topic;
    private String msgId;
    @JsonProperty("msg")
    private Map<String, Object> msg;
    private long i;

    @JsonProperty("msg_id")
    public final String getMsgId() {
        return this.msgId;
    }

    public final long getI() {
        return this.i;
    }

    public Date getTimestamp() {
        return new Date(this.timestamp);
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMsg() {
        return msg;
    }

    public void setMsg(Map<String, Object> msg) {
        this.msg = msg;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String toJson() {
        String message = "";
        try {
            ByteArrayOutputStream var1 = new ByteArrayOutputStream();
            ObjectMapper var2 = new ObjectMapper();
            ObjectWriter var3 = var2.writer();
            var3.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValue(var1, this);
            var1.close();
            message = new String(var1.toByteArray(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return message;
    }

    @JsonIgnore
    public String getMessageBody() {
        return JSONObject.fromObject(getMsg()).toString();
    }

    @JsonIgnore
    public String getMessageHeaders() {
        // fedmsg messages don't have headers or properties like JMS messages.
        // The only real header is the topic.  This is here to maintain
        // symmetry with MESSAGE_HEADERS provided by the AmqMessagingWorker.
        // https://github.com/jenkinsci/jms-messaging-plugin/pull/20
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.set("topic", mapper.convertValue(getTopic(), JsonNode.class));
            return mapper.writer().writeValueAsString(root);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    @JsonIgnore
    public String getMsgJson() {
        String message = "";
        try {
            ByteArrayOutputStream var1 = new ByteArrayOutputStream();
            ObjectMapper var2 = new ObjectMapper();
            ObjectWriter var3 = var2.writer();
            var3.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValue(var1, msg);
            var1.close();
            message = new String(var1.toByteArray(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return message;
    }
}
