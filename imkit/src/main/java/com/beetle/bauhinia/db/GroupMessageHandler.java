package com.beetle.bauhinia.db;

import com.beetle.im.IMMessage;

import java.util.Date;

/**
 * Created by houxh on 15/3/21.
 */
public class GroupMessageHandler implements com.beetle.im.GroupMessageHandler {

    private static GroupMessageHandler instance = new GroupMessageHandler();

    public static GroupMessageHandler getInstance() {
        return instance;
    }


    public static int now() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    public boolean handleMessage(IMMessage msg) {
        GroupMessageDB db = GroupMessageDB.getInstance();
        IMessage imsg = new IMessage();
        imsg.senderID = msg.getSenderID();
        imsg.receiverID = msg.getReceiverID();
        imsg.timestamp = msg.timestamp;
        imsg.setContent(msg.content);
        boolean r = db.insertMessage(imsg, imsg.getReceiver());
        msg.msgLocalID = imsg.msgLocalID;
        return r;

    }
    public boolean handleMessageACK(int msgLocalID, long gid) {
        GroupMessageDB db = GroupMessageDB.getInstance();
        return db.acknowledgeMessage(msgLocalID, gid);
    }

    public boolean handleMessageFailure(int msgLocalID, long gid) {
        GroupMessageDB db = GroupMessageDB.getInstance();
        return db.markMessageFailure(msgLocalID, gid);
    }

    public boolean handleGroupNotification(String notification) {
        GroupMessageDB db = GroupMessageDB.getInstance();
        IMessage.GroupNotification groupNotification = IMessage.newGroupNotification(notification);
        IMessage imsg = new IMessage();

        //todo assign appid
        imsg.senderAppID = 0;
        imsg.receiverAppID = 0;
        imsg.senderID = 0;
        imsg.receiverID = groupNotification.groupID;
        imsg.timestamp = groupNotification.timestamp;
        imsg.setContent(groupNotification);
        return db.insertMessage(imsg, groupNotification.groupID);
    }

}
