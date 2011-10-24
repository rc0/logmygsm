package uk.org.rc0.helloandroid;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.CellLocation;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;

public class Logger extends Service {

  private boolean is_running;

  private TelephonyManager myTelephonyManager;

  // -----------------
  // Variables shared with the Activity
  // -----------------
  //
  static public boolean do_logging;

  static public char   lastNetworkType;
  static public char   lastState;
  static public int    lastCid;
  static public int    lastLac;
  static public int    lastdBm;

  @Override
  public void onCreate() {
    is_running = false;
    do_logging = false;
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
    };

    public void onSignalStrengthsChanged(SignalStrength strength) {
      int asu;
      asu = strength.getGsmSignalStrength();
      if (asu == 99) {
        lastdBm = 0;
      } else {
        lastdBm = -113 + 2*asu;
      }
    };

    public void onServiceStateChanged(ServiceState newState) {
      switch (newState.getState()) {
        case ServiceState.STATE_EMERGENCY_ONLY: lastState = 'E'; break;
        case ServiceState.STATE_IN_SERVICE:     lastState = 'A'; break; // available
        case ServiceState.STATE_OUT_OF_SERVICE: lastState = 'X'; break;
        case ServiceState.STATE_POWER_OFF:      lastState = 'O'; break;
        default:                                lastState = '?'; break;
      }
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
    };


    public void onDataConnectionStateChanged(int state, int network_type) {
      handle_network_type(network_type);
    };

  };
}



