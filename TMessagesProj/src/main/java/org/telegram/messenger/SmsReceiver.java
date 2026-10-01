/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import android.provider.Telephony;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            String message = "";
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            String hash = preferences.getString("sms_hash", null);
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                if (!AndroidUtilities.isWaitingForSms()) {
                    return;
                }
                StringBuilder messageBuilder = new StringBuilder();
                for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    if (smsMessage != null) {
                        messageBuilder.append(smsMessage.getDisplayMessageBody());
                    }
                }
                message = messageBuilder.toString();
            }
            if (TextUtils.isEmpty(message)) {
                return;
            }
            Pattern pattern = Pattern.compile("[0-9\\-]+");
            final Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String code = matcher.group(0).replace("-", "");
                if (code.length() >= 3) {
                    if (hash != null) {
                        preferences.edit().putString("sms_hash_code", hash + "|" + code).commit();
                    }
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didReceiveSmsCode, code));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
