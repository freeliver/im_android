package com.beetle.bauhinia;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.beetle.bauhinia.db.IMessage;
import com.beetle.imkit.R;

public class MessageTextView extends MessageRowView {
    public MessageTextView(Context context, boolean incomming, boolean isShowUserName) {
        super(context, incomming, isShowUserName);
        final int contentLayout;
        contentLayout = R.layout.chat_content_text;

        ViewGroup group = (ViewGroup)this.findViewById(R.id.content);
        group.addView(inflater.inflate(contentLayout, group, false));
    }

    @Override
    public void setMessage(IMessage msg, boolean incomming) {
        super.setMessage(msg, incomming);
        IMessage.MessageType mediaType = message.content.getType();

        if (mediaType == IMessage.MessageType.MESSAGE_TEXT) {
            TextView content = (TextView) findViewById(R.id.text);
            String text = ((IMessage.Text) msg.content).text;
            content.setText(text);

            if (!TextUtils.isEmpty(msg.getTranslation())) {
                TextView translationView = (TextView) findViewById(R.id.translation);
                translationView.setVisibility(View.VISIBLE);
                findViewById(R.id.divider).setVisibility(View.VISIBLE);
                translationView.setText(msg.getTranslation());
            } else {
                findViewById(R.id.translation).setVisibility(View.GONE);
                findViewById(R.id.divider).setVisibility(View.GONE);
            }
        } else {
            TextView content = (TextView) findViewById(R.id.text);
            content.setText("unknown");
            findViewById(R.id.translation).setVisibility(View.GONE);
            findViewById(R.id.divider).setVisibility(View.GONE);
        }
        requestLayout();
    }



}