package uk.org.rc0.helloandroid;

import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.TextView;

public class HelloAndroid extends Activity {

  private TextView latText;
  private TextView lonText;
  private TextView accText;
  private TextView ageText;
  private TextView satText;
  private TextView cidText;
  private TextView lacmncText;
  private TextView netmccText;
  private TextView speedText;
  private TextView dBmText;
  private TextView countText;
  private TextView cidHistoryText;

  private DisplayUpdateReceiver myReceiver;

  private Map mMap;

  /** Called when the activity is first created. */
  @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main_new);
      latText = (TextView) findViewById(R.id.latitude);
      lonText = (TextView) findViewById(R.id.longitude);
      accText = (TextView) findViewById(R.id.accuracy);
      ageText = (TextView) findViewById(R.id.age);
      satText = (TextView) findViewById(R.id.sat);
      cidText = (TextView) findViewById(R.id.cid);
      netmccText = (TextView) findViewById(R.id.net_mcc);
      lacmncText = (TextView) findViewById(R.id.lac_mnc);
      speedText = (TextView) findViewById(R.id.speed);
      dBmText = (TextView) findViewById(R.id.dBm);
      countText = (TextView) findViewById(R.id.count);
      cidHistoryText = (TextView) findViewById(R.id.cid_history);
      mMap = (Map) findViewById(R.id.map);
      mMap.restore_state_from_file();
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
      Logger.stop_tracing = false;
      startService(new Intent(this, Logger.class));
      IntentFilter filter;
      filter = new IntentFilter(Logger.DISPLAY_UPDATE);
      myReceiver = new DisplayUpdateReceiver();
      registerReceiver(myReceiver, filter);
      updateDisplay();
      super.onResume();
    }

    @Override
    public void onPause() {
      unregisterReceiver(myReceiver);
      // It seems wasteful to do this here, but there is no other safe opportunity to do so -
      // in effect we are 'committing' the user's changes at this point.
      mMap.save_state_to_file();
      super.onPause();
    }

  private void updateCidHistory(long current_time) {
    StringBuffer out = new StringBuffer();
    // There's no point in showing the current cell as that's shown in other fields
    for (int i=1; i<Logger.MAX_RECENT; i++) {
      if ((Logger.recent_cids != null) &&
          (Logger.recent_cids[i] != null) &&
          (Logger.recent_cids[i].cid >= 0)) {
          long age = (500 + current_time - Logger.recent_cids[i].lastMillis) / 1000;
          if (age < 60) {
            String temp = String.format("%9d   0:%02d %4d\n",
                Logger.recent_cids[i].cid,
                age,
                Logger.recent_cids[i].handoff);
            out.append(temp);
          } else {
            String temp = String.format("%9d %3d:%02d %4d\n",
                Logger.recent_cids[i].cid,
                age / 60,
                age % 60,
                Logger.recent_cids[i].handoff);
            out.append(temp);
          }
      }
    }

    cidHistoryText.setText(out);
  }

  private boolean bad_cid(int cid) {
    switch (cid) {
      case 50594049:
        return true;
      case 0:
        return true;
      default:
        return false;
    }
  }

  private void updateDisplay() {
    long current_time = System.currentTimeMillis();
    if (Logger.validFix) {
      long age = (500 + current_time - Logger.lastFixMillis) / 1000;
      String latString = String.format("%+9.4f", Logger.lastLat);
      String lonString = String.format("%+9.4f", Logger.lastLon);
      String accString = String.format("%dm", Logger.lastAcc);
      String ageString;
      if (age < 90) {
        ageString = String.format(" %2ds %03d", age, Logger.lastBearing);
      } else if (age < 90*60) {
        ageString = String.format(" %2dm %03d", age/60, Logger.lastBearing);
      } else {
        ageString = String.format(" %2dh %03d", age/3600, Logger.lastBearing);
      }
      String speedString = String.format("%5.1f mph",
        Logger.lastSpeed * 2.237);
      latText.setText(latString);
      lonText.setText(lonString);
      accText.setText(accString);
      ageText.setText(ageString);
      speedText.setText(speedString);
      latText.setTextColor(Color.WHITE);
      lonText.setTextColor(Color.WHITE);
      accText.setTextColor(Color.WHITE);
      ageText.setTextColor(Color.WHITE);
      speedText.setTextColor(Color.WHITE);
    } else {
      latText.setText("GPS?");
      lonText.setText("GPS?");
      accText.setText("GPS?");
      ageText.setText("GPS?");
      speedText.setText("GPS?");
      latText.setTextColor(Color.RED);
      lonText.setTextColor(Color.RED);
      accText.setTextColor(Color.RED);
      ageText.setTextColor(Color.RED);
      speedText.setTextColor(Color.RED);
    }
    String satString = String.format("%d/%d/%d",
        Logger.last_fix_sats,
        Logger.last_ephem_sats, Logger.last_alman_sats);
    String cidString = String.format("%d",
        Logger.lastCid);

    String mnc_string;
    String mcc_string;
    if ((Logger.lastMccMnc != null) &&
        (Logger.lastMccMnc.length() == 5)) {
      mnc_string = Logger.lastMccMnc.substring(3, 5);
      mcc_string = Logger.lastMccMnc.substring(0, 3);
    } else {
      mnc_string = "";
      mcc_string = "";
    }
    String lacmncString = String.format("%5d %3s",
        Logger.lastLac, mnc_string);
    String netmccString = String.format("%5s %3s",
        Logger.lastNetworkTypeLong, mcc_string);
    String dBmString = String.format("%ddBm", Logger.lastdBm);
    satText.setText(satString);

    cidText.setText(cidString);
    switch (Logger.lastState) {
      case 'A':
        if (bad_cid(Logger.lastCid)) {
          cidText.setTextColor(Color.RED);
        } else {
          cidText.setTextColor(Color.WHITE);
        }
        break;
      default:
        cidText.setTextColor(Color.RED);
        break;
    }

    lacmncText.setText(lacmncString);
    netmccText.setText(netmccString);
    dBmText.setText(dBmString);

    String countString = String.format("%d pt", Logger.nReadings);
    countText.setText(countString);

    updateCidHistory(current_time);
    mMap.update_map();
  }

  // --------------------------------------------------------------------------
  //

  public class DisplayUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateDisplay();
    }
  }

  // --------------------------------------------------------------------------
  //

  private final int OPTION_MAP_2G   = 1;
  private final int OPTION_MAP_3G   = 2;
  private final int OPTION_MAP_MAPNIK  = 3;
  private final int OPTION_MAP_CYCLE = 7;
  private final int OPTION_MAP_OS   = 4;
  private final int OPTION_CLEAR_TRAIL = 5;
  private final int OPTION_EXIT     = 6;
  private final int OPTION_BIG_MAP = 10;

  @Override
    public boolean onCreateOptionsMenu(Menu menu) {
      SubMenu sub = menu.addSubMenu(0, 0, Menu.NONE, "Maps");
      sub.add (Menu.NONE, OPTION_MAP_2G,  Menu.NONE, "O2 UK 2G map");
      sub.add (Menu.NONE, OPTION_MAP_3G,  Menu.NONE, "O2 UK 3G map");
      sub.add (Menu.NONE, OPTION_MAP_OS,  Menu.NONE, "Ordnance Survey");
      sub.add (Menu.NONE, OPTION_MAP_MAPNIK, Menu.NONE, "Mapnik (OSM)");
      sub.add (Menu.NONE, OPTION_MAP_CYCLE, Menu.NONE, "OpenCycleMap");
      menu.add (Menu.NONE, OPTION_BIG_MAP, Menu.NONE, "Waypoint map");
      menu.add (Menu.NONE, OPTION_CLEAR_TRAIL,  Menu.NONE, "Clear trail");
      menu.add (Menu.NONE, OPTION_EXIT,    Menu.NONE, "Exit");
      return true;
    }

  @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
        case OPTION_EXIT:
          Logger.stop_tracing = true;
          finish();
          return true;
        case OPTION_CLEAR_TRAIL:
          mMap.clear_trail();
          return true;
        case OPTION_BIG_MAP:
          Intent intent = new Intent(this, BigMapActivity.class);
          startActivity(intent);
          return true;
        case OPTION_MAP_2G:
          mMap.select_map_source(Map.MAP_2G);
          return true;
        case OPTION_MAP_3G:
          mMap.select_map_source(Map.MAP_3G);
          return true;
        case OPTION_MAP_MAPNIK:
          mMap.select_map_source(Map.MAP_MAPNIK);
          return true;
        case OPTION_MAP_CYCLE:
          mMap.select_map_source(Map.MAP_OPEN_CYCLE);
          return true;
        case OPTION_MAP_OS:
          mMap.select_map_source(Map.MAP_OS);
          return true;
        default:
          return false;
      }
    }
}
//
// vim:et:sw=2:sts=2
