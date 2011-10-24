package uk.org.rc0.helloandroid;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class HelloAndroid extends Activity {

  private boolean validFix;
  private String myProvider;

  private int    nReadings;

  private double lastLat;
  private double lastLon;
  private float  lastAcc;
  private long   lastFixMillis;

  private TextView latText;
  private TextView lonText;
  private TextView accText;
  private TextView ageText;
  private TextView cidText;
  private TextView lacText;
  private TextView dBmText;
  private TextView neighborsText;
  private TextView countText;
  private ToggleButton toggleButton;

  private ComponentName myService;

  private LocationManager myLocationManager;

  /** Called when the activity is first created. */
  @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      nReadings = 0;
      validFix = false;
      latText = (TextView) findViewById(R.id.latitude);
      lonText = (TextView) findViewById(R.id.longitude);
      accText = (TextView) findViewById(R.id.accuracy);
      ageText = (TextView) findViewById(R.id.age);
      cidText = (TextView) findViewById(R.id.cid);
      lacText = (TextView) findViewById(R.id.lac);
      dBmText = (TextView) findViewById(R.id.dBm);
      neighborsText = (TextView) findViewById(R.id.neighbors);
      countText = (TextView) findViewById(R.id.count);
      toggleButton = (ToggleButton) findViewById(R.id.toggleBgLog);

      String context = Context.LOCATION_SERVICE;
      myLocationManager = (LocationManager) getSystemService(context);
      myProvider = LocationManager.GPS_PROVIDER;
    }

  @Override
    public void onStart() {
      super.onStart();
      myLocationManager.requestLocationUpdates(myProvider, 1500, 3, myLocationListener);
    }

  @Override
    public void onStop() {
      myLocationManager.removeUpdates(myLocationListener);
      super.onStop();
    }

    @Override
    public void onResume () {
      super.onResume();
      Logger.do_logging = true;
      myService = startService(new Intent(this, Logger.class));
    }

    @Override
    public void onPause() {
      if (toggleButton.isChecked()) {
        Logger.do_logging = false;
        stopService(new Intent(this, myService.getClass()));
      }
      super.onPause();
    }

  private void updateDisplay() {
    if (validFix) {
      long current_time = System.currentTimeMillis();
      long age = (500 + current_time - lastFixMillis) / 1000;
      String latString = String.format("%.6f", lastLat);
      String lonString = String.format("%.6f", lastLon);
      String accString = String.format("%.1f", lastAcc);
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

    String countString = String.format("%d", nReadings);
    countText.setText(countString);
  }

  private void CollectInfo() {
    Location location = myLocationManager.getLastKnownLocation(myProvider);
    if (location == null) {
      validFix = false;
    } else {
      validFix = true;
      lastLat = location.getLatitude();
      lastLon = location.getLongitude();
      if (location.hasAccuracy()) {
        lastAcc = location.getAccuracy();
      } else {
        lastAcc = 0.0f;
      }
      lastFixMillis = location.getTime();
    }
    ++nReadings;
    updateDisplay();
  };

  private final LocationListener myLocationListener = new LocationListener () {
    public void onLocationChanged(Location location) {
      CollectInfo();
    }
    public void onProviderDisabled(String provider) {
      validFix = false;
      CollectInfo();
    }
    public void onProviderEnabled(String provider) {
      CollectInfo();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
      CollectInfo();
    }

  };


}
