package com.beetle.bauhinia;

import android.content.Intent;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;

import com.beetle.bauhinia.db.IMessage;
import com.beetle.bauhinia.db.MessageFlag;
import com.beetle.bauhinia.db.MessageIterator;
import com.beetle.bauhinia.db.PeerMessageDB;
import com.beetle.bauhinia.tools.AudioDownloader;
import com.beetle.bauhinia.tools.Notification;
import com.beetle.bauhinia.tools.NotificationCenter;
import com.beetle.bauhinia.tools.PeerOutbox;
import com.beetle.im.*;

import com.beetle.bauhinia.tools.FileCache;
import java.util.*;


public class PeerMessageActivity extends MessageActivity implements
        IMServiceObserver, PeerMessageObserver, AudioDownloader.AudioDownloaderObserver,
        PeerOutbox.OutboxObserver
{

    public static final int APPID = 7;

    public static final String SEND_MESSAGE_NAME = "send_message";
    public static final String CLEAR_MESSAGES = "clear_messages";
    public static final String CLEAR_NEW_MESSAGES = "clear_new_messages";

    private final int PAGE_SIZE = 10;

    protected long currentUID;
    protected long peerUID;
    protected String peerName;
    protected int peerAppID;

    private long getPeerID() {
        return (peerAppID << 56)|peerUID;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        currentUID = intent.getLongExtra("current_uid", 0);
        if (currentUID == 0) {
            Log.e(TAG, "current uid is 0");
            return;
        }
        peerUID = intent.getLongExtra("peer_uid", 0);
        if (peerUID == 0) {
            Log.e(TAG, "peer uid is 0");
            return;
        }
        peerName = intent.getStringExtra("peer_name");
        if (peerName == null) {
            Log.e(TAG, "peer name is null");
            return;
        }

        peerAppID = intent.getIntExtra("peer_appid", 0);
        if (peerAppID == 0) {
            //设置成当前的APPID
            peerAppID = APPID;
        }

        Log.i(TAG, "local id:" + currentUID +  "peer id:" + peerUID);

        this.loadConversationData();
        getSupportActionBar().setTitle(peerName);

        //显示最后一条消息
        if (this.messages.size() > 0) {
            listview.setSelection(this.messages.size() - 1);
        }

        PeerOutbox.getInstance().addObserver(this);
        IMService.getInstance().addObserver(this);
        IMService.getInstance().addPeerObserver(this);
        AudioDownloader.getInstance().addObserver(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "peer message activity destory");

        NotificationCenter nc = NotificationCenter.defaultCenter();
        Notification notification = new Notification(getPeerID(), CLEAR_NEW_MESSAGES);
        nc.postNotification(notification);

        PeerOutbox.getInstance().removeObserver(this);
        IMService.getInstance().removeObserver(this);
        IMService.getInstance().removePeerObserver(this);
        AudioDownloader.getInstance().removeObserver(this);
    }

    protected void loadConversationData() {
        HashSet<String> uuidSet = new HashSet<String>();
        messages = new ArrayList<IMessage>();

        int count = 0;
        MessageIterator iter = PeerMessageDB.getInstance().newMessageIterator(this.getPeerID());
        while (iter != null) {
            IMessage msg = iter.next();
            if (msg == null) {
                break;
            }

            //不加载重复的消息
            if (!TextUtils.isEmpty(msg.getUUID()) && uuidSet.contains(msg.getUUID())) {
                continue;
            }

            if (!TextUtils.isEmpty(msg.getUUID())) {
                uuidSet.add(msg.getUUID());
            }

            if (msg.content.getType() == IMessage.MessageType.MESSAGE_ATTACHMENT) {
                IMessage.Attachment attachment = (IMessage.Attachment)msg.content;
                attachments.put(attachment.msg_id, attachment);
            } else {
                msg.isOutgoing = (msg.senderID == currentUID);
                messages.add(0, msg);
                if (++count >= PAGE_SIZE) {
                    break;
                }
            }
        }

        downloadMessageContent(messages, count);
        loadUserName(messages, count);
        checkMessageFailureFlag(messages, count);
        resetMessageTimeBase();
    }

    protected void loadEarlierData() {
        if (messages.size() == 0) {
            return;
        }

        IMessage firstMsg = null;
        for (int i  = 0; i < messages.size(); i++) {
            IMessage msg = messages.get(i);
            if (msg.msgLocalID > 0) {
                firstMsg = msg;
                break;
            }
        }
        if (firstMsg == null) {
            return;
        }

        HashSet<String> uuidSet = new HashSet<String>();
        for (int i  = 0; i < messages.size(); i++) {
            IMessage msg = messages.get(i);
            if (!TextUtils.isEmpty(msg.getUUID())) {
                uuidSet.add(msg.getUUID());
            }
        }

        int count = 0;
        MessageIterator iter = PeerMessageDB.getInstance().newMessageIterator(this.getPeerID(), firstMsg.msgLocalID);
        while (iter != null) {
            IMessage msg = iter.next();
            if (msg == null) {
                break;
            }

            //不加载重复的消息
            if (!TextUtils.isEmpty(msg.getUUID()) && uuidSet.contains(msg.getUUID())) {
                continue;
            }

            if (!TextUtils.isEmpty(msg.getUUID())) {
                uuidSet.add(msg.getUUID());
            }

            if (msg.content.getType() == IMessage.MessageType.MESSAGE_ATTACHMENT) {
                IMessage.Attachment attachment = (IMessage.Attachment)msg.content;
                attachments.put(attachment.msg_id, attachment);
            } else{
                msg.isOutgoing = (msg.senderID == currentUID);
                messages.add(0, msg);
                if (++count >= PAGE_SIZE) {
                    break;
                }
            }
        }
        if (count > 0) {
            downloadMessageContent(messages, count);
            loadUserName(messages, count);
            checkMessageFailureFlag(messages, count);
            resetMessageTimeBase();
            adapter.notifyDataSetChanged();
            listview.setSelection(count);
        }
    }

    @Override
    protected MessageIterator getMessageIterator() {
        return PeerMessageDB.getInstance().newMessageIterator(this.getPeerID());
    }

    public void onConnectState(IMService.ConnectState state) {
        if (state == IMService.ConnectState.STATE_CONNECTED) {
            enableSend();
        } else {
            disableSend();
        }
        setSubtitle();
    }


    @Override
    public void onPeerInputting(long uid) {

    }

    @Override
    public void onPeerMessage(IMMessage msg) {
        if (msg.sender != this.getPeerID() && msg.receiver != this.getPeerID()) {
            return;
        }
        Log.i(TAG, "recv msg:" + msg.content);
        final IMessage imsg = new IMessage();
        imsg.timestamp = msg.timestamp;
        imsg.msgLocalID = msg.msgLocalID;

        imsg.senderAppID = msg.getSenderAppID();
        imsg.receiverAppID = msg.getReceiverAppID();
        imsg.senderID = msg.getSenderID();
        imsg.receiverID = msg.getReceiverID();

        imsg.setContent(msg.content);
        imsg.isOutgoing = (imsg.senderID == this.currentUID);
        if (imsg.isOutgoing) {
            imsg.flags |= MessageFlag.MESSAGE_FLAG_ACK;
        }

        if (!TextUtils.isEmpty(imsg.getUUID()) && findMessage(imsg.getUUID()) != null) {
            Log.i(TAG, "receive repeat message:" + imsg.getUUID());
            return;
        }

        loadUserName(imsg);
        downloadMessageContent(imsg);
        insertMessage(imsg);
    }

    @Override
    public void onPeerMessageACK(int msgLocalID, long uid) {
        if (this.getPeerID() != uid) {
            return;
        }
        Log.i(TAG, "message ack");

        IMessage imsg = findMessage(msgLocalID);
        if (imsg == null) {
            Log.i(TAG, "can't find msg:" + msgLocalID);
            return;
        }
        imsg.setAck(true);
    }

    @Override
    public void onPeerMessageFailure(int msgLocalID, long uid) {
        if (this.getPeerID() != uid) {
            return;
        }
        Log.i(TAG, "message failure");

        IMessage imsg = findMessage(msgLocalID);
        if (imsg == null) {
            Log.i(TAG, "can't find msg:" + msgLocalID);
            return;
        }
        imsg.setFailure(true);
    }



    void checkMessageFailureFlag(IMessage msg) {
        if (msg.senderID == this.currentUID) {
            if (msg.content.getType() == IMessage.MessageType.MESSAGE_AUDIO) {
                msg.setUploading(PeerOutbox.getInstance().isUploading(msg));
            } else if (msg.content.getType() == IMessage.MessageType.MESSAGE_IMAGE) {
                msg.setUploading(PeerOutbox.getInstance().isUploading(msg));
            }
            if (!msg.isAck() &&
                    !msg.isFailure() &&
                    !msg.getUploading() &&
                    !IMService.getInstance().isPeerMessageSending(this.getPeerID(), msg.msgLocalID)) {
                markMessageFailure(msg);
                msg.setFailure(true);
            }
        }
    }

    void checkMessageFailureFlag(ArrayList<IMessage> messages, int count) {
        for (int i = 0; i < count; i++) {
            IMessage m = messages.get(i);
            checkMessageFailureFlag(m);
        }
    }


    @Override
    protected void resend(IMessage msg) {
        eraseMessageFailure(msg);
        msg.setFailure(false);
        this.sendMessage(msg);
    }

    void sendMessage(IMessage imsg) {
        if (imsg.content.getType() == IMessage.MessageType.MESSAGE_AUDIO) {
            PeerOutbox ob = PeerOutbox.getInstance();
            IMessage.Audio audio = (IMessage.Audio)imsg.content;
            imsg.setUploading(true);
            ob.uploadAudio(imsg, FileCache.getInstance().getCachedFilePath(audio.url));
        } else if (imsg.content.getType() == IMessage.MessageType.MESSAGE_IMAGE) {
            IMessage.Image image = (IMessage.Image)imsg.content;
            //prefix:"file:"
            String path = image.url.substring(5);
            imsg.setUploading(true);
            PeerOutbox.getInstance().uploadImage(imsg, path);
        } else {
            IMMessage msg = new IMMessage();
            msg.sender = imsg.getSender();
            msg.receiver = imsg.getReceiver();
            msg.content = imsg.content.getRaw();
            msg.msgLocalID = imsg.msgLocalID;
            IMService im = IMService.getInstance();
            im.sendPeerMessage(msg);
        }
    }

    @Override
    void saveMessageAttachment(IMessage msg, String address) {
        IMessage attachment = new IMessage();
        attachment.content = IMessage.newAttachment(msg.msgLocalID, address);
        attachment.senderID = msg.senderID;
        attachment.receiverID = msg.receiverID;
        attachment.senderAppID = msg.senderAppID;
        attachment.receiverAppID = msg.receiverAppID;
        saveMessage(attachment);
    }

    void saveMessage(IMessage imsg) {
        if (imsg.senderID == this.currentUID) {
            PeerMessageDB.getInstance().insertMessage(imsg, imsg.getReceiver());
        } else {
            PeerMessageDB.getInstance().insertMessage(imsg, imsg.getSender());
        }
    }

    @Override
    protected void markMessageListened(IMessage imsg) {
        long cid = 0;
        if (imsg.senderID == this.currentUID) {
            cid = imsg.getReceiver();
        } else {
            cid = imsg.getSender();
        }
        PeerMessageDB.getInstance().markMessageListened(imsg.msgLocalID, cid);
    }

    void markMessageFailure(IMessage imsg) {
        long cid = 0;
        if (imsg.senderID == this.currentUID) {
            cid = imsg.getReceiver();
        } else {
            cid = imsg.getSender();
        }
        PeerMessageDB.getInstance().markMessageFailure(imsg.msgLocalID, cid);
    }

    void eraseMessageFailure(IMessage imsg) {
        long cid = 0;
        if (imsg.senderID == this.currentUID) {
            cid = imsg.getReceiver();
        } else {
            cid = imsg.getSender();
        }
        PeerMessageDB.getInstance().eraseMessageFailure(imsg.msgLocalID, cid);
    }

    @Override
    void clearConversation() {
        super.clearConversation();

        PeerMessageDB db = PeerMessageDB.getInstance();
        db.clearCoversation(this.getPeerID());


        NotificationCenter nc = NotificationCenter.defaultCenter();
        Notification notification = new Notification(this.getPeerID(), CLEAR_MESSAGES);
        nc.postNotification(notification);

    }

    @Override
    public void onAudioUploadSuccess(IMessage imsg, String url) {
        Log.i(TAG, "audio upload success:" + url);
        if (imsg.getReceiver() == this.getPeerID()) {
            IMessage m = findMessage(imsg.msgLocalID);
            if (m != null) {
                m.setUploading(false);
            }
        }
    }

    @Override
    public void onAudioUploadFail(IMessage msg) {
        Log.i(TAG, "audio upload fail");
        if (msg.getReceiver() == this.getPeerID()) {
            IMessage m = findMessage(msg.msgLocalID);
            if (m != null) {
                m.setFailure(true);
                m.setUploading(false);
            }
        }
    }

    @Override
    public void onImageUploadSuccess(IMessage msg, String url) {
        Log.i(TAG, "image upload success:" + url);
        if (msg.getReceiver() == this.getPeerID()) {
            IMessage m = findMessage(msg.msgLocalID);
            if (m != null) {
                m.setUploading(false);
            }
        }
    }

    @Override
    public void onImageUploadFail(IMessage msg) {
        Log.i(TAG, "image upload fail");
        if (msg.getReceiver() == this.getPeerID()) {
            IMessage m = findMessage(msg.msgLocalID);
            if (m != null) {
                m.setFailure(true);
                m.setUploading(false);
            }
        }
    }

    @Override
    public void onAudioDownloadSuccess(IMessage msg) {
        Log.i(TAG, "audio download success");
    }
    @Override
    public void onAudioDownloadFail(IMessage msg) {
        Log.i(TAG, "audio download fail");
    }


    protected void sendMessageContent(IMessage.MessageContent content) {
        IMessage imsg = new IMessage();
        imsg.senderAppID = APPID;
        imsg.receiverAppID = peerAppID;
        imsg.senderID = this.currentUID;
        imsg.receiverID = this.peerUID;
        imsg.setContent(content);
        imsg.timestamp = now();
        imsg.isOutgoing = true;
        saveMessage(imsg);
        loadUserName(imsg);

        if (imsg.content.getType() == IMessage.MessageType.MESSAGE_LOCATION) {
            IMessage.Location loc = (IMessage.Location)imsg.content;

            if (TextUtils.isEmpty(loc.address)) {
                queryLocation(imsg);
            } else {
                saveMessageAttachment(imsg, loc.address);
            }
        }

        sendMessage(imsg);

        insertMessage(imsg);

        NotificationCenter nc = NotificationCenter.defaultCenter();
        Notification notification = new Notification(imsg, SEND_MESSAGE_NAME);
        nc.postNotification(notification);
    }

}
