/**
 * Copyright (C) 2013 Open Whisper Systems
 * Copyright (C) 2014 Securecom
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
package com.securecomcode.text.transport;

import android.content.Context;
import android.util.Log;

import com.securecomcode.text.database.DatabaseFactory;
import com.securecomcode.text.database.MmsDatabase;
import com.securecomcode.text.database.model.SmsMessageRecord;
import com.securecomcode.text.mms.MmsSendResult;
import com.securecomcode.text.push.PushServiceSocketFactory;
import com.securecomcode.text.recipients.Recipient;
import com.securecomcode.text.recipients.RecipientFactory;
import com.securecomcode.text.recipients.RecipientFormattingException;
import com.securecomcode.text.sms.IncomingGroupMessage;
import com.securecomcode.text.sms.IncomingIdentityUpdateMessage;
import com.securecomcode.text.util.GroupUtil;
import com.securecomcode.text.util.TextSecurePreferences;
import com.securecomcode.text.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.UnregisteredUserException;
import org.whispersystems.textsecure.storage.Session;
import org.whispersystems.textsecure.util.DirectoryUtil;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.IOException;

import ws.com.google.android.mms.pdu.SendReq;

public class UniversalTransport {

  private final Context       context;
  private final MasterSecret  masterSecret;
  private final PushTransport pushTransport;
  private final SmsTransport  smsTransport;
  private final MmsTransport  mmsTransport;

  public UniversalTransport(Context context, MasterSecret masterSecret) {
    this.context       = context;
    this.masterSecret  = masterSecret;
    this.pushTransport = new PushTransport(context, masterSecret);
    this.smsTransport  = new SmsTransport(context, masterSecret);
    this.mmsTransport  = new MmsTransport(context, masterSecret);
  }

  public void deliver(SmsMessageRecord message)
      throws UndeliverableMessageException, UntrustedIdentityException, RetryLaterException,
             SecureFallbackApprovalException, InsecureFallbackApprovalException
  {
    if (message.isForcedSms()) {
      smsTransport.deliver(message);
      return;
    }

    if (!TextSecurePreferences.isPushRegistered(context)) {
      deliverDirectSms(message);
      return;
    }
        Log.w("UniversalTransport", "Using SMS as transport...");
        deliverDirectSms(message);
  }

  public MmsSendResult deliver(SendReq mediaMessage, long threadId)
      throws UndeliverableMessageException, RetryLaterException, UntrustedIdentityException,
             SecureFallbackApprovalException, InsecureFallbackApprovalException
  {
    if (MmsDatabase.Types.isForcedSms(mediaMessage.getDatabaseMessageBox())) {
      return mmsTransport.deliver(mediaMessage);
    }

    if (Util.isEmpty(mediaMessage.getTo())) {
      return deliverDirectMms(mediaMessage);
    }

    if (GroupUtil.isEncodedGroup(mediaMessage.getTo()[0].getString())) {
      return deliverGroupMessage(mediaMessage, threadId);
    }

    if (!TextSecurePreferences.isPushRegistered(context)) {
      return deliverDirectMms(mediaMessage);
    }

    if (isMultipleRecipients(mediaMessage)) {
      return deliverDirectMms(mediaMessage);
    }

      return deliverDirectMms(mediaMessage);
  }

  private MmsSendResult deliverGroupMessage(SendReq mediaMessage, long threadId)
      throws RetryLaterException, UndeliverableMessageException
  {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      throw new UndeliverableMessageException("Not push registered!");
    }

    try {
      pushTransport.deliver(mediaMessage, threadId);
      return new MmsSendResult("push".getBytes("UTF-8"), 0, true, true);
    } catch (IOException e) {
      Log.w("UniversalTransport", e);
      throw new RetryLaterException(e);
    } catch (RecipientFormattingException e) {
      throw new UndeliverableMessageException(e);
    } catch (InvalidNumberException e) {
      throw new UndeliverableMessageException(e);
    } catch (EncapsulatedExceptions ee) {
      Log.w("UniversalTransport", ee);
      try {
        for (UnregisteredUserException unregistered : ee.getUnregisteredUserExceptions()) {
          IncomingGroupMessage quitMessage = IncomingGroupMessage.createForQuit(mediaMessage.getTo()[0].getString(), unregistered.getE164Number());
          DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageInbox(masterSecret, quitMessage);
          DatabaseFactory.getGroupDatabase(context).remove(GroupUtil.getDecodedId(mediaMessage.getTo()[0].getString()), unregistered.getE164Number());
        }

        for (UntrustedIdentityException untrusted : ee.getUntrustedIdentityExceptions()) {
          IncomingIdentityUpdateMessage identityMessage = IncomingIdentityUpdateMessage.createFor(untrusted.getE164Number(), untrusted.getIdentityKey(), mediaMessage.getTo()[0].getString());
          DatabaseFactory.getEncryptingSmsDatabase(context).insertMessageInbox(masterSecret, identityMessage);
        }

        return new MmsSendResult("push".getBytes("UTF-8"), 0, true, true);
      } catch (IOException ioe) {
        throw new AssertionError(ioe);
      }
    }
  }

  private void deliverDirectSms(SmsMessageRecord message)
      throws InsecureFallbackApprovalException, UndeliverableMessageException
  {
    if (TextSecurePreferences.isDirectSmsAllowed(context)) {
      smsTransport.deliver(message);
    } else {
      throw new UndeliverableMessageException("Direct SMS delivery is disabled!");
    }
  }

  private MmsSendResult deliverDirectMms(SendReq message)
      throws InsecureFallbackApprovalException, UndeliverableMessageException
  {
    if (TextSecurePreferences.isDirectSmsAllowed(context)) {
      return mmsTransport.deliver(message);
    } else {
      throw new UndeliverableMessageException("Direct MMS delivery is disabled!");
    }
  }

  public boolean isMultipleRecipients(SendReq mediaMessage) {
    int recipientCount = 0;

    if (mediaMessage.getTo() != null) {
      recipientCount += mediaMessage.getTo().length;
    }

    if (mediaMessage.getCc() != null) {
      recipientCount += mediaMessage.getCc().length;
    }

    if (mediaMessage.getBcc() != null) {
      recipientCount += mediaMessage.getBcc().length;
    }

    return recipientCount > 1;
  }

  private boolean isSmsFallbackApprovalRequired(String destination) {
    return (isSmsFallbackSupported(destination) && TextSecurePreferences.isFallbackSmsAskRequired(context));
  }

  private boolean isSmsFallbackSupported(String destination) {
    if (GroupUtil.isEncodedGroup(destination)) {
      return false;
    }

    if (TextSecurePreferences.isPushRegistered(context) &&
        !TextSecurePreferences.isFallbackSmsAllowed(context))
    {
      return false;
    }

    Directory directory = Directory.getInstance(context);
    return directory.isSmsFallbackSupported(destination);
  }

  private boolean isPushTransport(String destination) {
    if (GroupUtil.isEncodedGroup(destination)) {
      return true;
    }

    Directory directory = Directory.getInstance(context);

    try {
      return directory.isActiveNumber(destination);
    } catch (NotInDirectoryException e) {
      try {
        PushServiceSocket   socket         = PushServiceSocketFactory.create(context);
        String              contactToken   = DirectoryUtil.getDirectoryServerToken(destination);
        ContactTokenDetails registeredUser = socket.getContactTokenDetails(contactToken);

        if (registeredUser == null) {
          registeredUser = new ContactTokenDetails();
          registeredUser.setNumber(destination);
          directory.setNumber(registeredUser, false);
          return false;
        } else {
          registeredUser.setNumber(destination);
          directory.setNumber(registeredUser, true);
          return true;
        }
      } catch (IOException e1) {
        Log.w("UniversalTransport", e1);
        return false;
      }
    }
  }
}
