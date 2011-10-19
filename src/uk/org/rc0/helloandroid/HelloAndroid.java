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

  private double lastLat;
  private double lastLon;
  private float  lastAcc;

  private TextView latText;
  private TextView lonText;
  private TextView accText;

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

      String context = Context.LOCATION_SERVICE;
      myLocationManager = (LocationManager) getSystemService(context);
      String provider = LocationManager.GPS_PROVIDER;
      Location location = myLocationManager.getLastKnownLocation(provider);
      processNewLocation(location);
      myLocationManager.requestLocationUpdates(provider, 1500, 3, myLocationListener);

    }

  @Override
    public void onStop() {
      myLocationManager.removeUpdates(myLocationListener);
      super.onStop();
    }

  @Override
    public void onPause() {
      myLocationManager.removeUpdates(myLocationListener);
      super.onStop();
    }

  private void updateDisplay() {
    if (validFix) {
      // String latString = "" + lastLat; // String.format("%.6f", lat);
      String latString = String.format("%.6f", lastLat);
      String lonString = String.format("%.6f", lastLon);
      String accString = String.format("%.1f", lastAcc);
      latText.setText(latString);
      lonText.setText(lonString);
      accText.setText(accString);
    } else {
      latText.setText("???");
      lonText.setText("???");
      accText.setText("???");
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
