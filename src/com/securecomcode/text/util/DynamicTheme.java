package com.securecomcode.text.util;

import android.app.Activity;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.securecomcode.text.ApplicationPreferencesActivity;
import com.securecomcode.text.ConversationActivity;
import com.securecomcode.text.ConversationListActivity;
import com.securecomcode.text.R;

public class DynamicTheme {

  private int currentTheme;

  public void onCreate(Activity activity) {
    currentTheme = getSelectedTheme(activity);
    activity.setTheme(currentTheme);
  }

  public void onResume(Activity activity) {
    if (currentTheme != getSelectedTheme(activity)) {
      Intent intent = activity.getIntent();
      activity.finish();
      OverridePendingTransition.invoke(activity);
      activity.startActivity(intent);
      OverridePendingTransition.invoke(activity);
    }
  }

  private static int getSelectedTheme(Activity activity) {
    String theme = TextSecurePreferences.getTheme(activity);

    if (theme.equals("light")) {
      if (activity instanceof ConversationActivity) return R.style.TextSecure_LightTheme_ConversationActivity;
      else                                          return R.style.TextSecure_LightTheme;
    } else if (theme.equals("dark")) {
      if (activity instanceof ConversationActivity) return R.style.TextSecure_DarkTheme_ConversationActivity;
      else                                          return R.style.TextSecure_DarkTheme;
    }

    return R.style.TextSecure_LightTheme;
  }

  private static final class OverridePendingTransition {
    static void invoke(Activity activity) {
      activity.overridePendingTransition(0, 0);
    }
  }
}
