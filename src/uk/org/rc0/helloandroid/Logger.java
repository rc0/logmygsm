package uk.org.rc0.helloandroid;

import android.app.Service;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.CellLocation;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;

public class Logger extends Service {

  private boolean is_running;

  private TelephonyManager myTelephonyManager;
  private LocationManager myLocationManager;

  // -----------------
  // Variables shared with the Activity
  // -----------------
  //
  public static final String DISPLAY_UPDATE = "Display_Update_LogMyGSM";

  static public boolean do_logging;

  // --- Telephony
  static public char   lastNetworkType;
  static public char   lastState;
  static public int    lastCid;
  static public int    lastLac;
  static public int    lastdBm;

  // --- GPS
  static public boolean validFix;
  static public String myProvider;
  static public int    nReadings;
  static public double lastLat;
  static public double lastLon;
  static public float  lastAcc;
  static public long   lastFixMillis;

  @Override
  public void onCreate() {
    is_running = false;
    do_logging = false;
    myProvider = LocationManager.GPS_PROVIDER;
    nReadings = 0;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!is_running) {
      // Start all the funky stuff
      lastCid = 0;
      lastLac = 0;
      lastdBm = 0;
      lastNetworkType = '?';

      String srvcName = Context.TELEPHONY_SERVICE;
      myTelephonyManager = (TelephonyManager) getSystemService(srvcName);
      myTelephonyManager.listen(myPhoneStateListener,
            PhoneStateListener.LISTEN_CELL_LOCATION |
            PhoneStateListener.LISTEN_SERVICE_STATE |
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
            PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
      String context = Context.LOCATION_SERVICE;
      myLocationManager = (LocationManager) getSystemService(context);
      myLocationManager.requestLocationUpdates(myProvider, 1000, 2, myLocationListener);

      is_running = true;
    }
    return Service.START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    myTelephonyManager.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    myLocationManager.removeUpdates(myLocationListener);
  }

  // --------------------------------------------------------------------------------

  private void updateDisplay() {
    Intent intent = new Intent(DISPLAY_UPDATE);
    sendBroadcast(intent);
  }

  // --------------------------------------------------------------------------------

  private void logToFile() {
    if (do_logging) {
      ++nReadings;


    }
  }

  // --------------------------------------------------------------------------------

  private final PhoneStateListener myPhoneStateListener = new PhoneStateListener () {

    public void onCellLocationChanged(CellLocation location) {
      if (location == null) {
        lastCid = 0;
        lastLac = 0;
      } else {
        GsmCellLocation gsmLocation = (GsmCellLocation) location;
        lastCid = gsmLocation.getCid();
        lastLac = gsmLocation.getLac();
      }
      updateDisplay();
    };

    public void onSignalStrengthsChanged(SignalStrength strength) {
      int asu;
      asu = strength.getGsmSignalStrength();
      if (asu == 99) {
        lastdBm = 0;
      } else {
        lastdBm = -113 + 2*asu;
      }
      updateDisplay();
    };

    public void onServiceStateChanged(ServiceState newState) {
      switch (newState.getState()) {
        case ServiceState.STATE_EMERGENCY_ONLY: lastState = 'E'; break;
        case ServiceState.STATE_IN_SERVICE:     lastState = 'A'; break; // available
        case ServiceState.STATE_OUT_OF_SERVICE: lastState = 'X'; break;
        case ServiceState.STATE_POWER_OFF:      lastState = 'O'; break;
        default:                                lastState = '?'; break;
      }
      updateDisplay();
    };

    private void handle_network_type(int network_type) {
      switch (network_type) {
        case TelephonyManager.NETWORK_TYPE_EDGE:    lastNetworkType = 'E'; break;
        case TelephonyManager.NETWORK_TYPE_GPRS:    lastNetworkType = 'G'; break;
        case TelephonyManager.NETWORK_TYPE_UMTS:    lastNetworkType = 'U'; break;
        case TelephonyManager.NETWORK_TYPE_HSDPA:   lastNetworkType = 'H'; break;
        case TelephonyManager.NETWORK_TYPE_UNKNOWN: lastNetworkType = '-'; break;
        default:                                    lastNetworkType = '?'; break;
      }
      updateDisplay();
    };

    public void onDataConnectionStateChanged(int state, int network_type) {
      handle_network_type(network_type);
    };

  };

  // --------------------------------------------------------------------------------

  private final LocationListener myLocationListener = new LocationListener () {
    public void onLocationChanged(Location location) {
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
        // lastFixMillis = location.getTime();
        lastFixMillis = System.currentTimeMillis();
        logToFile();
      }
      updateDisplay();
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



