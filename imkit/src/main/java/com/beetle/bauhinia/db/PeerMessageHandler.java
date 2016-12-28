package com.beetle.bauhinia.db;

import com.beetle.im.IMMessage;

/**
 * Created by houxh on 14-7-22.
 */
public class PeerMessageHandler implements com.beetle.im.PeerMessageHandler {
    private static PeerMessageHandler instance = new PeerMessageHandler();

    public static PeerMessageHandler getInstance() {
        return instance;
    }

    public boolean handleMessage(IMMessage msg, long uid) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        IMessage imsg = new IMessage();
        imsg.timestamp = msg.timestamp;
        imsg.senderAppID = msg.getSenderAppID();
        imsg.senderID = msg.getSenderID();
        imsg.receiverAppID = msg.getReceiverAppID();
        imsg.receiverID = msg.getReceiverID();
        imsg.setContent(msg.content);
        boolean r = db.insertMessage(imsg, uid);
        msg.msgLocalID = imsg.msgLocalID;
        return r;
    }

    public boolean handleMessageACK(int msgLocalID, long uid) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        return db.acknowledgeMessage(msgLocalID, uid);
    }

    public boolean handleMessageFailure(int msgLocalID, long uid) {
        PeerMessageDB db = PeerMessageDB.getInstance();
        return db.markMessageFailure(msgLocalID, uid);
    }
}
