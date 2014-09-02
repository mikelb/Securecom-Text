/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.securecomcode.text.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;

import com.securecomcode.text.protocol.WirePrefix;
import com.securecomcode.text.util.TextSecurePreferences;
import com.securecomcode.text.util.Util;

import ws.com.google.android.mms.pdu.GenericPdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;

public class MmsListener extends BroadcastReceiver {

  private boolean isRelevant(Context context, Intent intent) {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.DONUT) {
      return false;
    }

    if (!ApplicationMigrationService.isDatabaseImported(context)) {
      return false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
        Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction()) &&
        Util.isDefaultSmsProvider(context))
    {
      return false;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT &&
        TextSecurePreferences.isInterceptAllMmsEnabled(context))
    {
      return true;
    }

    byte[] mmsData   = intent.getByteArrayExtra("data");
    PduParser parser = new PduParser(mmsData);
    GenericPdu pdu   = parser.parse();

    if (pdu.getMessageType() != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
      return false;

    NotificationInd notificationPdu = (NotificationInd)pdu;

    if (notificationPdu.getSubject() == null)
      return false;

    return WirePrefix.isEncryptedMmsSubject(notificationPdu.getSubject().getString());
  }

  @Override
    public void onReceive(Context context, Intent intent) {
    Log.w("MmsListener", "Got MMS broadcast..." + intent.getAction());

    if (Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction()) ||
        (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction()) &&
         isRelevant(context, intent)))
    {
      Log.w("MmsListener", "Relevant!");
      intent.setAction(SendReceiveService.RECEIVE_MMS_ACTION);
      intent.putExtra("ResultCode", this.getResultCode());
      intent.setClass(context, SendReceiveService.class);

      context.startService(intent);
      abortBroadcast();
    }
  }



}
