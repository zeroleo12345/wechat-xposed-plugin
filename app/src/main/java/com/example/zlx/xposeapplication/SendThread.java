package com.example.zlx.xposeapplication;

import com.elvishew.xlog.XLog;
import com.jcraft.jsch.IO;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class SendThread extends Thread{
    String host;
    int port;
    String user;
    String password;
    String virtual_host = "/";
    int connect_timeout = 2000;  //毫秒
    String wxsend_queue = null;
    String wxrecv_queue = null;
    SendThread(String host, int port, String virtual_host, String user, String password, int connect_timeout, String wxsend_queue, String wxrecv_queue) throws IOException, TimeoutException{
        this.host = host;
        this.port = port;
        this.virtual_host = virtual_host;
        this.user = user;
        this.password = password;
        this.connect_timeout = connect_timeout;
        this.wxsend_queue = wxsend_queue;
        this.wxrecv_queue = wxrecv_queue;
        this.connect_mq();
    }


    Connection connection = null;
    void connect_mq() throws IOException, TimeoutException{
        try{
            XLog.w("ready to connect mq, host: %s, port: %d virtual_host: %s, user: %s, password: %s, connect_timeout: %d",
                    this.host, this.port, this.virtual_host, this.user, this.password, this.connect_timeout);
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(this.host);
            factory.setPort(this.port);
            factory.setVirtualHost(this.virtual_host);
            factory.setUsername(this.user);
            factory.setPassword(this.password);
            factory.setConnectionTimeout(connect_timeout);
            this.connection = factory.newConnection();
        }catch (Throwable e){
            XLog.e("SendThread connect mq failed. trace:%s", android.util.Log.getStackTraceString(e));
            throw e;
        }
    }

    void disconnect_mq() throws Exception {
        if(this.connection != null){
            this.connection.close();
            this.connection = null;
        }
    }

    /** 在主UI线程中调用redis的rpush, 会导致抛异常: android.os.NetworkOnMainThreadException */
    @Override
    public void run() {
        Channel send_channel = null;
        RecvThread thread_recv = null;
        String exchange="", routing_key="", body="", expiration="";
        try {
            send_channel  = connection.createChannel();
            // TODO 该线程允许退出, 退出时杀掉thread_recv, 退出后由TouchThread重新拉起
            send_channel.queueDeclare(wxsend_queue, true, false, false, null); // note 我是生产者, 可以不声明, 由消费者声明
            send_channel.queueDeclare(wxrecv_queue, true, false, false, null); // note 我是消费者, 由我声明, 仅仅是声明
            send_channel.exchangeDeclare("ProcIdle", "topic", true); // note 我是生产者
            send_channel.exchangeDeclare("PyIdle", "topic", true); // note 我是消费者
            send_channel.queueBind(wxsend_queue, "PyIdle", "#");
            List<String> msg;

            AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();      // https://www.programcreek.com/java-api-examples/index.php?api=com.rabbitmq.client.AMQP.BasicProperties
            while (true) {
                if(thread_recv == null || !thread_recv.isAlive()) {
                    XLog.i("new recv thread");
                    thread_recv = new RecvThread();
                    thread_recv.start();
                }
                // note, Array:  exchange | routing_key | body | expiration
                msg = WechatHook.rpush_queue.take();       // 阻塞读
                if( msg.size() != 4){
                    XLog.e("msg size !=4, msg:%s", msg);
                    continue;
                }
                exchange = msg.get(0);
                routing_key = msg.get(1);
                body = msg.get(2);
                expiration = msg.get(3);    // note: 单位毫秒, 以字符串形式传入
                WechatHook.last_send_time = (int) (System.currentTimeMillis() / 1000);
                if( Integer.parseInt( expiration ) > 0 ){
                    builder.expiration( expiration );
                    send_channel.basicPublish(exchange, routing_key, builder.build(), body.getBytes("UTF-8"));
                }else{
                    send_channel.basicPublish(exchange, routing_key, null, body.getBytes("UTF-8"));
                }
                //if(BuildConfig.DEBUG) XLog.d("basicPublicsh exchange:%s, routing_key:%s, expiration:%s", exchange, routing_key, expiration);
            }
        } catch (Throwable e) {
            XLog.e("SendThread error. stack:%s", android.util.Log.getStackTraceString(e));
            XLog.e("exchange:%s, routing_key:%s, body:%s, expiration:%s", exchange, routing_key, body, expiration);
        } finally {
            if( send_channel != null ) try{send_channel.close();} catch (Exception e){}
            if( thread_recv != null ) try{thread_recv.interrupt();thread_recv.join();} catch (Exception e){}
        }
    }   /** run 结束 */


    // note 接收消息线程
    public class RecvThread extends Thread{
        public void run() {
            Channel recv_channel = null;

            XLog.d("Listening on queue:%s", wxsend_queue );
            try {
                recv_channel  = connection.createChannel();
                QueueingConsumer consumer = new QueueingConsumer(recv_channel);
                recv_channel.basicConsume(wxsend_queue, true, consumer);    // 通过显式设置autoAsk=true, 自动回复ack

                /* 读取队列，并且阻塞，即在读到消息之前在这里阻塞，直到等到消息，完成消息的阅读后，继续阻塞循环 */
                while (true) {
                    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                    String message = new String(delivery.getBody());
                    //if(BuildConfig.DEBUG) XLog.d("RecvThread Received: %s", message );
                    WechatHook.recv_queue.put(message);
                }
            } catch (Throwable e) {
                XLog.e("RecvThread error. stack:%s", android.util.Log.getStackTraceString(e));
            } finally {
                if( recv_channel != null ) try{recv_channel.close();} catch (Exception e){}
            }
        }
    }   /** RecvThread 类结束 */


}
