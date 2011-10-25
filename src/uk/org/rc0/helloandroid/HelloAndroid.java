package uk.org.rc0.helloandroid;

import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.TextView;
import android.widget.ToggleButton;

public class HelloAndroid extends Activity {


  private TextView latText;
  private TextView lonText;
  private TextView accText;
  private TextView ageText;
  private TextView cidText;
  private TextView lacText;
  private TextView dBmText;
  // private TextView neighborsText;
  private TextView countText;
  private ToggleButton toggleButton;

  private ComponentName myService;
  private DisplayUpdateReceiver myReceiver;

  /** Called when the activity is first created. */
  @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      latText = (TextView) findViewById(R.id.latitude);
      lonText = (TextView) findViewById(R.id.longitude);
      accText = (TextView) findViewById(R.id.accuracy);
      ageText = (TextView) findViewById(R.id.age);
      cidText = (TextView) findViewById(R.id.cid);
      lacText = (TextView) findViewById(R.id.lac);
      dBmText = (TextView) findViewById(R.id.dBm);
      // neighborsText = (TextView) findViewById(R.id.neighbors);
      countText = (TextView) findViewById(R.id.count);
      toggleButton = (ToggleButton) findViewById(R.id.toggleBgLog);
    }

  @Override
    public void onStart() {
      super.onStart();
    }

  @Override
    public void onStop() {
      super.onStop();
    }

    @Override
    public void onResume () {
      Logger.do_logging = true;
      myService = startService(new Intent(this, Logger.class));
      IntentFilter filter;
      filter = new IntentFilter(Logger.DISPLAY_UPDATE);
      myReceiver = new DisplayUpdateReceiver();
      registerReceiver(myReceiver, filter);
      super.onResume();
    }

    @Override
    public void onPause() {
      unregisterReceiver(myReceiver);
      if (toggleButton.isChecked()) {
        Logger.do_logging = false;
        stopService(new Intent(this, myService.getClass()));
      }
      super.onPause();
    }

  private void updateDisplay() {
    if (Logger.validFix) {
      long current_time = System.currentTimeMillis();
      long age = (500 + current_time - Logger.lastFixMillis) / 1000;
      String latString = String.format("%.6f", Logger.lastLat);
      String lonString = String.format("%.6f", Logger.lastLon);
      String accString = String.format("%.1f", Logger.lastAcc);
      String ageString = String.format("%d", age);
      latText.setText(latString);
      lonText.setText(lonString);
      accText.setText(accString);
      ageText.setText(ageString);
    } else {
      latText.setText("???");
      lonText.setText("???");
      accText.setText("???");
      ageText.setText("???");
    }
    String cidString = String.format("%c %d", Logger.lastNetworkType, Logger.lastCid);
    String lacString = String.format("%d", Logger.lastLac);
    String dBmString = String.format("%d %c", Logger.lastdBm, Logger.lastState);
    cidText.setText(cidString);
    lacText.setText(lacString);
    dBmText.setText(dBmString);

    String countString = String.format("%d", Logger.nReadings);
    countText.setText(countString);
  }

  // --------------------------------------------------------------------------
  //

  public class DisplayUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateDisplay();
    }
  }

}
