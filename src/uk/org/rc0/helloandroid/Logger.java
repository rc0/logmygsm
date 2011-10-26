package uk.org.rc0.helloandroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Environment;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.telephony.TelephonyManager;
import android.telephony.PhoneStateListener;
import android.telephony.CellLocation;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;
import android.text.format.DateFormat;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Logger extends Service {

  static private boolean is_running = false;
  static public boolean stop_tracing;

  private TelephonyManager myTelephonyManager;
  private LocationManager myLocationManager;
  private NotificationManager myNotificationManager;
  private Notification myNotification;
  private int myNotificationRef = 1;

  private File logfile;
  private FileWriter logwriter;

  // -----------------
  // Variables shared with the Activity
  // -----------------
  //
  public static final String DISPLAY_UPDATE = "Display_Update_LogMyGSM";

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
  static public int    lastAcc;
  static public long   lastFixMillis;

  @Override
  public void onCreate() {
    stop_tracing = false;
    myProvider = LocationManager.GPS_PROVIDER;
    nReadings = 0;
    myNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
      String context = Context.LOCATION_SERVICE;
      myLocationManager = (LocationManager) getSystemService(context);

      startListening();
    }
    return Service.START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    stopListening();
  }

  // --------------------------------------------------------------------------------

  private void startNotification() {
    int icon = R.drawable.notification;
    String notifyText = "Logger running";
    long when = System.currentTimeMillis();
    myNotification = new Notification(icon, notifyText, when);

    Context context = getApplicationContext();
    String expandedText = "(Extended status text)";
    String expandedTitle = "GSM Logger running";
    Intent intent = new Intent(this, HelloAndroid.class);
    PendingIntent launchIntent = PendingIntent.getActivity(context, 0, intent, 0);
    myNotification.setLatestEventInfo(context, expandedTitle, expandedText, launchIntent);

    if (myNotificationManager != null) {
      myNotificationManager.notify(myNotificationRef, myNotification);
    }
  }

  private void stopNotification() {
    if (myNotificationManager != null) {
      myNotificationManager.cancel(myNotificationRef);
    }
  }


  // --------------------------------------------------------------------------------

  private void openLog () {
    CharSequence cs = DateFormat.format("yyyyMMdd-kkmmss", System.currentTimeMillis());
    String timedFileName = cs.toString();
    try {
      File root = new File(Environment.getExternalStorageDirectory(), "LogMyGsm");
      if (!root.exists()) {
          root.mkdirs();
      }
      logfile = new File(root, timedFileName);
      logwriter = new FileWriter(logfile);
    } catch (IOException e) {
      logfile = null;
      logwriter = null;
    }
  }

  private void writeLog (String data) {
    if (logwriter != null) {
      try {
        logwriter.append(data);
      } catch (IOException e) {
      }
    }
  }

  private void closeLog() {
    if (logwriter != null) {
      try {
        logwriter.flush();
        logwriter.close();
      } catch (IOException e) {
      }
    }
  }

  // --------------------------------------------------------------------------------

  private void startListening() {
    if (!is_running) {
      is_running = true;
      startNotification();
      openLog();
      myTelephonyManager.listen(myPhoneStateListener,
          PhoneStateListener.LISTEN_CELL_LOCATION |
          PhoneStateListener.LISTEN_SERVICE_STATE |
          PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
          PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
      myLocationManager.requestLocationUpdates(myProvider, 1000, 3, myLocationListener);
    }
  }

  // --------------------------------------------------------------------------------

  private void stopListening() {
    if (is_running) {
      myTelephonyManager.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
      myLocationManager.removeUpdates(myLocationListener);
      stopNotification();
      closeLog();
      is_running = false;
      stopSelf();
    }
  }

  // --------------------------------------------------------------------------------

  private void updateDisplay() {
    Intent intent = new Intent(DISPLAY_UPDATE);
    sendBroadcast(intent);
    if (stop_tracing) {
      stopListening();
    }
  }

  // --------------------------------------------------------------------------------

  private void logToFile() {
    ++nReadings;
    String data = String.format("%12.7f %12.7f %3d %c %c %10d %10d %3d\n",
        lastLat, lastLon, lastAcc,
        lastState,
        lastNetworkType, lastCid, lastLac,
        lastdBm);
    writeLog(data);
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
          lastAcc = (int)(0.5 + location.getAccuracy());
        } else {
          lastAcc = 0;
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



