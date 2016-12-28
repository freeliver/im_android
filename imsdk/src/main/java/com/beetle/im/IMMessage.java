package com.beetle.im;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by houxh on 14-7-23.
 */

public class IMMessage {
    public long sender;
    public long receiver;
    public int timestamp;
    public int msgLocalID;
    public String content;

    //应用内的uid
    public long getSenderID() {
        return (sender&0x00ffffffffffffffL);
    }

    public long getReceiverID() {
        return (receiver&0x00ffffffffffffffL);
    }

    public int getSenderAppID() {
        return (int)(sender >> 56);
    }

    public int getReceiverAppID() {
        return (int)(receiver >> 56);
    }


}

