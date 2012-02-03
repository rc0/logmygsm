package uk.org.rc0.helloandroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.location.GpsStatus;
import android.location.GpsSatellite;
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
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Iterable;
import java.util.Iterator;

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

  private File rawfile;
  private FileWriter rawwriter;

  // Start false, set it true once we've tried to open logfiles, following the
  // first GPS position fix.
  private boolean logging_is_active;

  // -----------------
  // Variables shared with the Activity
  // -----------------
  //
  public static final String DISPLAY_UPDATE = "Display_Update_LogMyGSM";

  // --- Telephony
  static public char   lastNetworkType;
  static public String lastNetworkTypeLong;
  static public int    lastNetworkTypeRaw;
  static public char   lastState;
  static public int    lastCid;
  static public int    lastLac;
  static public String lastMccMnc;
  static public String lastOperator;
  static public String lastSimOperator;
  static public int    lastdBm;
  static public int    lastASU;
  static public int    lastBer;

  // --- GPS
  static public boolean validFix;
  static public String myProvider;
  static public int    nReadings;
  static public int    nHandoffs;
  static public double lastLat;
  static public double lastLon;
  static public int    lastAcc;
  static public int    lastBearing;
  static public float  lastSpeed;
  static public long   lastFixMillis;

  // --- GPS fix info
  static public int    last_n_sats;
  static public int    last_fix_sats;
  static public int    last_ephem_sats;
  static public int    last_alman_sats;

  // --- CID history

  public class RecentCID {
    public int cid;
    public char network_type;
    public char state;
    public int dbm;
    public int handoff;
    // Time this CID was last encountered
    public long lastMillis;

    public RecentCID() { cid = -1; }
  };


  static public final int MAX_RECENT = 8;
  static public RecentCID[] recent_cids;

  @Override
  public void onCreate() {
    stop_tracing = false;
    myProvider = LocationManager.GPS_PROVIDER;
    nReadings = 0;
    nHandoffs = 0;
    myNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    init_recent_cids();
  }

  private void init_recent_cids() {
    recent_cids = new RecentCID[MAX_RECENT];
    for (int i=0; i<MAX_RECENT; i++) {
      recent_cids[i] = new RecentCID();
    }
    // Test data
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!is_running) {
      // Start all the funky stuff
      lastCid = 0;
      lastLac = 0;
      lastdBm = 0;
      lastNetworkType = '?';

      logging_is_active = false;

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
    myNotification.flags |= Notification.FLAG_ONGOING_EVENT;

    updateNotification();
  }

  private void stopNotification() {
    if (myNotificationManager != null) {
      myNotificationManager.cancel(myNotificationRef);
    }
  }

  private void updateNotification() {
    Context context = getApplicationContext();
    String expandedText = String.format("%c%d, %ddBm/%c %dm %d/%d/%d",
        lastNetworkType, lastCid,
        lastdBm, lastState,
        lastAcc,
        last_fix_sats, last_ephem_sats, last_alman_sats);
    String expandedTitle = String.format("GSM Logger running (%d)", nReadings);
    Intent intent = new Intent(this, HelloAndroid.class);

    // The next line is to stop Android from creating multiple activities - it
    // jus thas to go back to the one it was using before
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent launchIntent = PendingIntent.getActivity(context, 0, intent, 0);
    myNotification.setLatestEventInfo(context, expandedTitle, expandedText, launchIntent);

    if (myNotificationManager != null) {
      startForeground(myNotificationRef, myNotification);
      // myNotificationManager.notify(myNotificationRef, myNotification);
    }
  }

  // --------------------------------------------------------------------------------

  private void openLog () {
    String basePath = "/sdcard";
    String ourDir = "LogMyGsm";
    CharSequence cs = DateFormat.format("yyyyMMdd-kkmmss", System.currentTimeMillis());
    String timedFileName = cs.toString() + ".log";
    String fullPath = basePath + "/" + ourDir + "/" + timedFileName;

    String rawFileName = "raw_" + cs.toString() + ".log";

    logging_is_active = true;
    try {
      File root = new File(basePath, ourDir);
      if (!root.exists()) {
          root.mkdirs();
      }
      logfile = new File(root, timedFileName);
      logwriter = new FileWriter(logfile);
      announce("Opened logfile");

      rawfile = new File(root, rawFileName);
      rawwriter = new FileWriter(rawfile);
    } catch (IOException e) {
      logfile = null;
      logwriter = null;
      rawfile = null;
      rawwriter = null;
    }
    if (logwriter == null) {
      announce("COULD NOT LOG TO " + fullPath);
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
      announce("Closing logfile");
      try {
        logwriter.flush();
        logwriter.close();
      } catch (IOException e) {
      }
    }
    if (rawwriter != null) {
      try {
        rawwriter.flush();
        rawwriter.close();
      } catch (IOException e) {
      }
    }
    logging_is_active = false;
    logwriter = null;
    rawwriter = null;
  }

  // --------------------------------------------------------------------------------

  private void logCellHistory() {
    int match = -1;
    for (int i=0; i<MAX_RECENT; i++) {
      if (recent_cids[i].cid == lastCid) {
        match = i;
        break;
      }
    }
    if (match == -1) {
      match = MAX_RECENT - 1;
    }
    if (match > 0) {
      for (int i=match; i>0; i--) {
        recent_cids[i].cid = recent_cids[i-1].cid;
        recent_cids[i].network_type = recent_cids[i-1].network_type;
        recent_cids[i].state = recent_cids[i-1].state;
        recent_cids[i].dbm = recent_cids[i-1].dbm;
        recent_cids[i].handoff = recent_cids[i-1].handoff;
        recent_cids[i].lastMillis = recent_cids[i-1].lastMillis;
      }
    }
    // If match==0 we just overwrite the newest record anyway
    recent_cids[0].cid = lastCid;
    recent_cids[0].network_type = lastNetworkType;
    recent_cids[0].state = lastState;
    recent_cids[0].dbm = lastdBm;
    recent_cids[0].handoff = nHandoffs;
    recent_cids[0].lastMillis = System.currentTimeMillis();
  }

  // --------------------------------------------------------------------------------

  private void writeRaw(String tag, String data) {
    if (rawwriter != null) {
      try {
        long now = System.currentTimeMillis();
        long seconds = now / 1000;
        long millis = now % 1000;
        String all = String.format("%10d.%03d %2s %s\n",
            seconds, millis,
            tag, data);
        //String all = String.format("%2s %s\n", 
        //    tag, data);
        rawwriter.append(all);
      } catch (IOException e) {
      }
    }
  }

  private void logRawCell() {
    String data = String.format("%10d %10d %s",
        lastCid, lastLac,
        lastMccMnc);
    writeRaw("CL", data);
  }

  private void logRawASU() {
    String data = String.format("%d", lastASU);
    writeRaw("AS", data);
  }

  private void logRawServiceState() {
    String data = String.format("%c", lastState);
    writeRaw("ST", data);
  }

  private void logRawNetworkType() {
    String data = String.format("%c %d", lastNetworkType, lastNetworkTypeRaw);
    writeRaw("NT", data);
  }

  private void logRawBadLocation() {
    writeRaw("LB", "-- bad --");
  }

  private void logRawLocation() {
    String data = String.format("%12.7f %12.7f %3d",
        lastLat, lastLon, lastAcc);
    writeRaw("LC", data);
  }

  private void logRawLocationDisabled() {
    writeRaw("LD", "-- disabled --");
  }

  private void logRawLocationEnabled() {
    writeRaw("LE", "-- enabled --");
  }

  private void logRawLocationStatus() {
    // This seems to log every second - very wasteful!
    //String data = String.format("%d %d %d %d",
    //    last_n_sats, last_fix_sats, last_ephem_sats, last_alman_sats);
    //writeRaw("LS", data);
  }

  // --------------------------------------------------------------------------------

  private void announce(String text) {
    Context context = getApplicationContext();
    int duration = Toast.LENGTH_LONG;
    Toast toast = Toast.makeText(context, text, duration);
    toast.show();
  }

  // --------------------------------------------------------------------------------

  private void startListening() {
    if (!is_running) {
      is_running = true;
      startNotification();
      // do this lazily now ...  openLog();
      myTelephonyManager.listen(myPhoneStateListener,
          PhoneStateListener.LISTEN_CELL_LOCATION |
          PhoneStateListener.LISTEN_SERVICE_STATE |
          PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
          PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
      myLocationManager.requestLocationUpdates(myProvider, 1000, 3, myLocationListener);
      myLocationManager.addGpsStatusListener(gpsListener);
    }
  }

  // --------------------------------------------------------------------------------

  private void stopListening() {
    if (is_running) {
      myTelephonyManager.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
      myLocationManager.removeGpsStatusListener(gpsListener);
      myLocationManager.removeUpdates(myLocationListener);
      stopNotification();
      closeLog();
      is_running = false;
      stopSelf();
    }
  }

  // --------------------------------------------------------------------------------

  private void updateDisplay() {
    updateNotification();
    Intent intent = new Intent(DISPLAY_UPDATE);
    sendBroadcast(intent);
    if (stop_tracing) {
      stopListening();
    }
  }

  // --------------------------------------------------------------------------------

  private void logToFile() {
    ++nReadings;
    String data = String.format("%12.7f %12.7f %3d %c %c %10d %10d %3d %s\n",
        lastLat, lastLon, lastAcc,
        lastState,
        lastNetworkType, lastCid, lastLac,
        lastdBm,
        lastMccMnc);
    writeLog(data);
  }

  // --------------------------------------------------------------------------------

  private final PhoneStateListener myPhoneStateListener = new PhoneStateListener () {

    public void onCellLocationChanged(CellLocation location) {
      int newCid;
      if (location == null) {
        newCid = 0;
        lastLac = 0;
      } else {
        GsmCellLocation gsmLocation = (GsmCellLocation) location;
        newCid = gsmLocation.getCid();
        lastLac = gsmLocation.getLac();
      }
      if (newCid != lastCid) {
        ++nHandoffs;
      }
      lastCid = newCid;
      lastMccMnc = new String(myTelephonyManager.getNetworkOperator());
      lastOperator = new String(myTelephonyManager.getNetworkOperatorName());
      lastSimOperator = new String(myTelephonyManager.getSimOperatorName());
      logCellHistory();
      logRawCell();
      updateDisplay();
    };

    public void onSignalStrengthsChanged(SignalStrength strength) {
      int asu;
      asu = strength.getGsmSignalStrength();
      lastASU = asu;
      logRawASU();
      if (asu == 99) {
        lastdBm = 0;
      } else {
        lastdBm = -113 + 2*asu;
      }
      lastBer = strength.getGsmBitErrorRate();
      logCellHistory();
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
      logRawServiceState();
      updateDisplay();
    };

    private void handle_network_type(int network_type) {
      switch (network_type) {
        case TelephonyManager.NETWORK_TYPE_GPRS:
          lastNetworkType = 'G';
          lastNetworkTypeLong = "GPRS";
          break;
        case TelephonyManager.NETWORK_TYPE_EDGE:
          lastNetworkType = 'E';
          lastNetworkTypeLong = "EDGE";
          break;
        case TelephonyManager.NETWORK_TYPE_UMTS:
          lastNetworkType = 'U';
          lastNetworkTypeLong = "UMTS";
          break;
        case TelephonyManager.NETWORK_TYPE_HSDPA:
          lastNetworkType = 'H';
          lastNetworkTypeLong = "HSDPA";
          break;
        case TelephonyManager.NETWORK_TYPE_UNKNOWN:
          lastNetworkType = '-';
          lastNetworkTypeLong = "----";
          break;
        default:
          lastNetworkType = '?';
          lastNetworkTypeLong = "????";
          break;
      }
      lastNetworkTypeRaw = network_type;
      logRawNetworkType();
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
        logRawBadLocation();
      } else {
        validFix = true;
        lastLat = location.getLatitude();
        lastLon = location.getLongitude();
        lastBearing = (int) location.getBearing();
        lastSpeed = location.getSpeed();
        if (location.hasAccuracy()) {
          lastAcc = (int)(0.5 + location.getAccuracy());
        } else {
          lastAcc = 0;
        }
        // lastFixMillis = location.getTime();
        lastFixMillis = System.currentTimeMillis();
        if (!logging_is_active) {
          openLog();
        }
        logToFile();
        logRawLocation();
      }
      updateDisplay();
    }

    public void onProviderDisabled(String provider) {
      validFix = false;
      logRawLocationDisabled();
      updateDisplay();
    }

    public void onProviderEnabled(String provider) {
      logRawLocationEnabled();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
      logRawLocationStatus();
    }

  };

  // --------------------------------------------------------------------------------

  private final GpsStatus.Listener gpsListener = new GpsStatus.Listener () {

    public void onGpsStatusChanged(int event) {
      GpsStatus gpsStatus = myLocationManager.getGpsStatus(null);
      switch (event) {
        case GpsStatus.GPS_EVENT_FIRST_FIX:
          break;
        case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
          Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
          Iterator<GpsSatellite> sati = satellites.iterator();
          last_n_sats = 0;
          last_fix_sats = 0;
          last_ephem_sats = 0;
          last_alman_sats = 0;
          while (sati.hasNext()) {
            GpsSatellite sat = sati.next();
            ++last_n_sats;
            if (sat.usedInFix())         { ++last_fix_sats  ; }
            else if (sat.hasEphemeris()) { ++last_ephem_sats; }
            else if (sat.hasAlmanac())   { ++last_alman_sats; }
          }
          logRawLocationStatus();
          break;
        case GpsStatus.GPS_EVENT_STARTED:
          break;
        case GpsStatus.GPS_EVENT_STOPPED:
          break;
      }

    }

  };

}


