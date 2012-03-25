// Copyright (c) 2012, Richard P. Curnow
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the <organization> nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package uk.org.rc0.logmygsm;

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
import android.widget.Toast;
import java.lang.Iterable;
import java.util.Iterator;

public class Logger extends Service {

  // Flag that's set by the UI to tell us to cut the power when we next get
  // called back by the framework.
  static boolean stop_tracing;

  private TelephonyManager myTelephonyManager;
  private LocationManager myLocationManager;
  private NotificationManager myNotificationManager;
  private Notification myNotification;
  private int myNotificationRef = 1;

  // mainlog is null until we've got the first GPS fix - so we don't open the
  // logfile needlessly.
  private Backend mainlog;
  private RawLogger rawlog;

  static Trail mTrail;
  static Landmarks mMarks;

  // -----------------
  // Variables shared with the Activity
  // -----------------
  //
  static final String UPDATE_CELL = "LogMyGSM_Update_Cell";
  static final String UPDATE_GPS  = "LogMyGSM_Update_GPS";

  // --- Telephony
  static char   lastNetworkType;
  static String lastNetworkTypeLong;
  static int    lastNetworkTypeRaw;
  static char   lastState;
  static int    lastCid;
  static int    lastLac;
  static String lastMccMnc;
  static String lastOperator;
  static String lastSimOperator;
  static int    lastdBm;
  static int    lastASU;
  static int    lastBer;

  // --- GPS
  static boolean validFix;
  static String myProvider;
  static int    nReadings;
  static int    nHandoffs;
  static double lastLat;
  static double lastLon;
  static int    lastAcc;
  static int    lastBearing;
  static float  lastSpeed;
  static long   lastFixMillis;

  // --- GPS fix info
  static int    last_n_sats;
  static int    last_fix_sats;
  static int    last_ephem_sats;
  static int    last_alman_sats;

  // --- CID history

  class RecentCID {
    int cid;
    char network_type;
    char state;
    int dbm;
    int handoff;
    // Time this CID was last encountered
    long lastMillis;

    RecentCID() { cid = -1; }
  };


  static final int MAX_RECENT = 8;
  static RecentCID[] recent_cids;

  // This is only called once for the service lifetime
  @Override
  public void onCreate() {
    stop_tracing = false;

    init_state();
    init_recent_cids();

    rawlog = new RawLogger(false); // 'true' to re-enable raw logs for debug
    mTrail = new Trail(this);
    mMarks = new Landmarks();

    myProvider = LocationManager.GPS_PROVIDER;
    myNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    String srvcName = Context.TELEPHONY_SERVICE;
    myTelephonyManager = (TelephonyManager) getSystemService(srvcName);
    String context = Context.LOCATION_SERVICE;
    myLocationManager = (LocationManager) getSystemService(context);

    startListening();
  }

  private void init_recent_cids() {
    recent_cids = new RecentCID[MAX_RECENT];
    for (int i=0; i<MAX_RECENT; i++) {
      recent_cids[i] = new RecentCID();
    }
  }

  private void init_state() {
    nReadings = 0;
    nHandoffs = 0;
    lastCid = 0;
    lastLac = 0;
    lastdBm = 0;
    lastNetworkType = '?';
    validFix = false;
  }

  // We don't care if this gets called multiple times as it doesn't do anything.
  // We don't need to accept any 'args' from the outside.
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return Service.START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    stopListening();
    mTrail.save_state_to_file();
    mMarks.save_state_to_file();
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
    Intent intent = new Intent(this, MainActivity.class);

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

  private void startListening() {
    startNotification();
    myTelephonyManager.listen(myPhoneStateListener,
        PhoneStateListener.LISTEN_CELL_LOCATION |
        PhoneStateListener.LISTEN_SERVICE_STATE |
        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS |
        PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
    // 8m steps regardless of time - get finer grained data whilst driving -
    // power consumption then doesn't matter as the phone will typically be
    // tethered.
    myLocationManager.requestLocationUpdates(myProvider, 0, 8, myLocationListener);
    myLocationManager.addGpsStatusListener(gpsListener);
  }

  // --------------------------------------------------------------------------------

  private void stopListening() {
    myTelephonyManager.listen(myPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    myLocationManager.removeGpsStatusListener(gpsListener);
    myLocationManager.removeUpdates(myLocationListener);
    stopNotification();
    if (mainlog != null) {
      mainlog.close();
    }
    if (rawlog != null) {
      rawlog.close();
    }
  }

  // --------------------------------------------------------------------------------

  private void updateUIGPS() {
    updateNotification();
    Intent intent = new Intent(UPDATE_GPS);
    sendBroadcast(intent);
    if (stop_tracing) {
      stopSelf();
    }
  }

  private void updateUICell() {
    updateNotification();
    Intent intent = new Intent(UPDATE_CELL);
    sendBroadcast(intent);
    if (stop_tracing) {
      stopSelf();
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
    if (mainlog == null) {
      mainlog = new Backend("", this);
    }
    mainlog.write(data);
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
      rawlog.log_cell();
      updateUICell();
    };

    public void onSignalStrengthsChanged(SignalStrength strength) {
      int asu;
      asu = strength.getGsmSignalStrength();
      lastASU = asu;
      rawlog.log_asu();
      if (asu == 99) {
        lastdBm = 0;
      } else {
        lastdBm = -113 + 2*asu;
      }
      lastBer = strength.getGsmBitErrorRate();
      logCellHistory();
      updateUICell();
    };

    public void onServiceStateChanged(ServiceState newState) {
      switch (newState.getState()) {
        case ServiceState.STATE_EMERGENCY_ONLY: lastState = 'E'; break;
        case ServiceState.STATE_IN_SERVICE:     lastState = 'A'; break; // available
        case ServiceState.STATE_OUT_OF_SERVICE: lastState = 'X'; break;
        case ServiceState.STATE_POWER_OFF:      lastState = 'O'; break;
        default:                                lastState = '?'; break;
      }
      rawlog.log_service_state();
      updateUICell();
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
      rawlog.log_network_type();
      updateUICell();
    };

    public void onDataConnectionStateChanged(int state, int network_type) {
      handle_network_type(network_type);
    };

  };

  // --------------------------------------------------------------------------------

  // Ensure we re-read the cell information every time we log a GPS update, on
  // the offchance that we've lost a callback along the way and we're not
  // changing cells very much
  private void sample_cell_info () {
    GsmCellLocation loc = (GsmCellLocation) myTelephonyManager.getCellLocation();
    if (loc != null) {
      lastCid = loc.getCid();
      lastLac = loc.getLac();
    }
  }

  // --------------------------------------------------------------------------------

  private final LocationListener myLocationListener = new LocationListener () {
    public void onLocationChanged(Location location) {
      if (location == null) {
        validFix = false;
        rawlog.log_bad_location();
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

        sample_cell_info();

        logToFile();
        rawlog.log_raw_location();
        mTrail.add_point(new Merc28(lastLat, lastLon));
        Merc28.update_latitude(lastLat);
      }
      updateUIGPS();
      updateUICell();
    }

    public void onProviderDisabled(String provider) {
      validFix = false;
      rawlog.log_location_disabled();
      updateUIGPS();
    }

    public void onProviderEnabled(String provider) {
      rawlog.log_location_enabled();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
      rawlog.log_location_status();
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
          rawlog.log_location_status();
          break;
        case GpsStatus.GPS_EVENT_STARTED:
          break;
        case GpsStatus.GPS_EVENT_STOPPED:
          break;
      }

    }

  };

  // --------------------------------------------------------------------------------

  void announce(String text) {
    Context context = getApplicationContext();
    int duration = Toast.LENGTH_LONG;
    Toast toast = Toast.makeText(context, text, duration);
    toast.show();
  }
}


// vim:et:sw=2:sts=2
//
