package uk.org.rc0.helloandroid;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;
import android.widget.TextView;
import java.lang.StringBuffer;
import java.util.List;

public class HelloAndroid extends Activity {

  private boolean validFix;
  private String myProvider;

  private double lastLat;
  private double lastLon;
  private float  lastAcc;
  private long   lastFixMillis;

  private char   lastNetworkType;
  private char   lastState;
  private int    lastCid;
  private int    lastLac;
  private int    lastdBm;

  private TextView latText;
  private TextView lonText;
  private TextView accText;
  private TextView ageText;
  private TextView cidText;
  private TextView lacText;
  private TextView dBmText;
  private TextView neighborsText;

  private LocationManager myLocationManager;
  private TelephonyManager myTelephonyManager;

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
      cidText = (TextView) findViewById(R.id.cid);
      lacText = (TextView) findViewById(R.id.lac);
      dBmText = (TextView) findViewById(R.id.dBm);
      neighborsText = (TextView) findViewById(R.id.neighbors);

      lastCid = 0;
      lastLac = 0;
      lastdBm = 0;
      lastNetworkType = '?';

      String srvcName = Context.TELEPHONY_SERVICE;
      myTelephonyManager = (TelephonyManager) getSystemService(srvcName);
      String context = Context.LOCATION_SERVICE;
      myLocationManager = (LocationManager) getSystemService(context);
      myProvider = LocationManager.GPS_PROVIDER;
    }

  @Override
    public void onStart() {
      super.onStart();
      myTelephonyManager.listen(myPhoneStateListener,
            PhoneStateListener.LISTEN_CELL_LOCATION |
            PhoneStateListener.LISTEN_SERVICE_STATE |
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
            PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
      myLocationManager.requestLocationUpdates(myProvider, 1500, 3, myLocationListener);
    }
  @Override
    public void onStop() {
      myTelephonyManager.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
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
    String cidString = String.format("%c %d", lastNetworkType, lastCid);
    String lacString = String.format("%d", lastLac);
    String dBmString = String.format("%d %c", lastdBm, lastState);
    cidText.setText(cidString);
    lacText.setText(lacString);
    dBmText.setText(dBmString);

    List<NeighboringCellInfo> lnci = myTelephonyManager.getNeighboringCellInfo();
    StringBuffer neighbors = new StringBuffer(128);
    for (NeighboringCellInfo nci : lnci) {
      String myText = String.format("%8d %8d %8d\n", nci.getCid(), nci.getLac(), nci.getRssi());
      neighbors.append(myText);
    }
    neighborsText.setText(neighbors);
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

    CellLocation cl = myTelephonyManager.getCellLocation();
    if (cl == null) {
      lastCid = 0;
      lastLac = 0;
    } else {
      GsmCellLocation gsmLocation = (GsmCellLocation) cl;
      lastCid = gsmLocation.getCid();
      lastLac = gsmLocation.getLac();
    }
    updateDisplay();
  };

//   private void processNewLocation(Location location) {
//     if (location == null) {
//       validFix = false;
//     } else {
//       validFix = true;
//       lastLat = location.getLatitude();
//       lastLon = location.getLongitude();
//       if (location.hasAccuracy()) {
//         lastAcc = location.getAccuracy();
//       } else {
//         lastAcc = 0.0f;
//       }
//       lastFixMillis = location.getTime();
//     }
//     updateDisplay();
//   }

  private void handle_network_type(int network_type) {
    switch (network_type) {
      case TelephonyManager.NETWORK_TYPE_EDGE:    lastNetworkType = 'E'; break;
      case TelephonyManager.NETWORK_TYPE_GPRS:    lastNetworkType = 'G'; break;
      case TelephonyManager.NETWORK_TYPE_UMTS:    lastNetworkType = 'U'; break;
      case TelephonyManager.NETWORK_TYPE_HSDPA:   lastNetworkType = 'H'; break;
      case TelephonyManager.NETWORK_TYPE_UNKNOWN: lastNetworkType = '-'; break;
      default:                                    lastNetworkType = '?'; break;
    }
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

  private final PhoneStateListener myPhoneStateListener = new PhoneStateListener () {

    public void onCellLocationChanged(CellLocation location) {
      CollectInfo();
    };

    public void onSignalStrengthsChanged(SignalStrength strength) {
      int asu;
      asu = strength.getGsmSignalStrength();
      if (asu == 99) {
        lastdBm = 0;
      } else {
        lastdBm = -113 + 2*asu;
      }
      CollectInfo();
    };

    public void onServiceStateChanged(ServiceState newState) {
      switch (newState.getState()) {
        case ServiceState.STATE_EMERGENCY_ONLY: lastState = 'E'; break;
        case ServiceState.STATE_IN_SERVICE:     lastState = 'A'; break; // available
        case ServiceState.STATE_OUT_OF_SERVICE: lastState = 'X'; break;
        case ServiceState.STATE_POWER_OFF:      lastState = 'O'; break;
        default:                                lastState = '?'; break;
      }
      CollectInfo();
    };

    public void onDataConnectionStateChanged(int state, int network_type) {
      handle_network_type(network_type);
      CollectInfo();
    };

  };

}
