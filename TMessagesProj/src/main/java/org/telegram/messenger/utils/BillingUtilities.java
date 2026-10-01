package org.telegram.messenger.utils;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

import com.google.common.base.Charsets;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class BillingUtilities {
    private static final String CURRENCY_FILE = "currencies.json";
    private static final String CURRENCY_EXP = "exp";

    @SuppressWarnings("ConstantConditions")
    public static void extractCurrencyExp(Map<String, Integer> currencyExpMap) {
        if (!currencyExpMap.isEmpty()) {
            return;
        }
        try {
            Context ctx = ApplicationLoader.applicationContext;
            InputStream in = ctx.getAssets().open(CURRENCY_FILE);
            JSONObject obj = new JSONObject(new String(Util.toByteArray(in), Charsets.UTF_8));
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String key = it.next();
                JSONObject currency = obj.optJSONObject(key);
                currencyExpMap.put(key, currency.optInt(CURRENCY_EXP));
            }
            in.close();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
