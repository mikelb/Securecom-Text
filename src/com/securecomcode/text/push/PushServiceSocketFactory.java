package com.securecomcode.text.push;

import android.content.Context;

import com.securecomcode.text.Release;
import com.securecomcode.text.util.TextSecurePreferences;
import org.whispersystems.textsecure.push.PushServiceSocket;

public class PushServiceSocketFactory {

  public static PushServiceSocket create(Context context, String number, String password) {
    return new PushServiceSocket(context, Release.PUSH_URL, new TextSecurePushTrustStore(context),
                                 number, password);
  }

  public static PushServiceSocket create(Context context) {
    return create(context,
                  TextSecurePreferences.getLocalNumber(context),
                  TextSecurePreferences.getPushServerPassword(context));
  }

}
