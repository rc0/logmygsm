package uk.org.rc0.helloandroid;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.widget.TextView;

public class HelloAndroid extends Activity {

  private boolean validFix;
  private String myProvider;

  private double lastLat;
  private double lastLon;
  private float  lastAcc;
  private long   lastFixMillis;

  private TextView latText;
  private TextView lonText;
  private TextView accText;
  private TextView ageText;

  private LocationManager myLocationManager;

  /** Called when the activity is first created. */
  @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      //Object o = null;
      setContentView(R.layout.main);
      validFix = false;
      latText = (TextView) findViewById(R.id.latitude);
      lonText = (TextView) findViewById(R.id.longitude);
      accText = (TextView) findViewById(R.id.accuracy);
      ageText = (TextView) findViewById(R.id.age);

      String context = Context.LOCATION_SERVICE;
      myLocationManager = (LocationManager) getSystemService(context);
      myProvider = LocationManager.GPS_PROVIDER;
      Location location = myLocationManager.getLastKnownLocation(myProvider);
      processNewLocation(location);

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

//   @Override
//     public void onPause() {
//       myLocationManager.removeUpdates(myLocationListener);
//       super.onStop();
//     }

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
  }

  private void processNewLocation(Location location) {
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
    updateDisplay();
  }
  private final LocationListener myLocationListener = new LocationListener () {
    public void onLocationChanged(Location location) {
      processNewLocation(location);
    }

    public void onProviderDisabled(String provider) {
      validFix = false;
      updateDisplay();
    }

    public void onProviderEnabled(String provider) {

    }


    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

  };

}
