package com.securecomcode.text.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import com.securecomcode.text.R;
import com.securecomcode.text.push.PushServiceSocketFactory;
import com.securecomcode.text.util.Dialogs;
import com.securecomcode.text.util.TextSecurePreferences;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GcmRegistrationService extends Service implements Runnable {

  private ExecutorService executor;

  @Override
  public void onCreate() {
    super.onCreate();
    this.executor = Executors.newSingleThreadExecutor();
  }

  @Override
  public int onStartCommand(Intent intent, int flats, int startId) {
    executor.execute(this);
    return START_NOT_STICKY;
  }

  @Override
  public void run() {

  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdown();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

}
