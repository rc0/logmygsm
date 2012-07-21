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

//import android.R.drawable;
import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.TextView;

public class MainActivity extends Activity implements Map.PositionListener {

  private TextView latText;
  private TextView lonText;
  private TextView accText;
  private TextView ageText;
  private TextView satText;
  private TextView cidText;
  private TextView lacmncText;
  private TextView netmccText;
  private TextView dBmText;
  private TextView daOffsetText;
  private TextView countText;
  private TextView tileText;
  private TextView cidHistoryText;
  private TextView gridRefText;

  private CellUpdateReceiver myCellReceiver;
  private GPSUpdateReceiver myGPSReceiver;

  private Map mMap;

  private static final String PREFS_FILE = "prefs.txt";
  static final private String TAG = "MainActivity";

  /** Called when the activity is first created. */
  @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.main);
      latText = (TextView) findViewById(R.id.latitude);
      lonText = (TextView) findViewById(R.id.longitude);
      accText = (TextView) findViewById(R.id.accuracy);
      ageText = (TextView) findViewById(R.id.age);
      satText = (TextView) findViewById(R.id.sat);
      cidText = (TextView) findViewById(R.id.cid);
      netmccText = (TextView) findViewById(R.id.net_mcc);
      lacmncText = (TextView) findViewById(R.id.lac_mnc);
      dBmText = (TextView) findViewById(R.id.dBm);
      countText = (TextView) findViewById(R.id.count);
      tileText = (TextView) findViewById(R.id.tile);
      cidHistoryText = (TextView) findViewById(R.id.cid_history);
      daOffsetText = (TextView) findViewById(R.id.da_offset);
      gridRefText = (TextView) findViewById(R.id.grid_ref);
      mMap = (Map) findViewById(R.id.map);
      mMap.restore_state_from_file(PREFS_FILE);
      mMap.register_position_listener(this);
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
      filter = new IntentFilter(Logger.UPDATE_CELL);
      myCellReceiver = new CellUpdateReceiver();
      registerReceiver(myCellReceiver, filter);

      filter = new IntentFilter(Logger.UPDATE_GPS);
      myGPSReceiver = new GPSUpdateReceiver();
      registerReceiver(myGPSReceiver, filter);

      updateDisplay();
      mMap.update_map();
      super.onResume();
    }

    @Override
    public void onPause() {
      unregisterReceiver(myCellReceiver);
      unregisterReceiver(myGPSReceiver);
      // It seems wasteful to do this here, but there is no other safe opportunity to do so -
      // in effect we are 'committing' the user's changes at this point.
      mMap.save_state_to_file(PREFS_FILE);
      // Dump the old tiles that haven't been rescued yet - avoid the most gratuituous memory wastage
      TileStore.semi_invalidate();
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
      case 0:
        return true;
      default:
        return false;
    }
  }

  private boolean odd_cid(int cid) {
    switch (cid) {
      case 50594049:
        return true;
      default:
        return false;
    }
  }

  private void position_update() {
    if (Logger.validFix) {
      String daOffsetString;
      double da_offset_m = mMap.da_offset_metres();
      if (da_offset_m == 0) {
        daOffsetString = String.format("%5.1f mph",
            Logger.lastSpeed * 2.237);
      } else if (da_offset_m < 10000) {
        daOffsetString = String.format("DA %5dm", (int)da_offset_m);
      } else {
        daOffsetString = String.format("DA %5.1fkm", 0.001*da_offset_m);
      }
      daOffsetText.setText(daOffsetString);
    } else {
      daOffsetText.setText("DA -----");
    }

    String tileString = mMap.current_tile_string();
    tileText.setText(tileString);
    String gridString = mMap.current_grid_ref();
    gridRefText.setText(gridString);

  }

  public void display_position_update() {
    position_update();
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
      latText.setText(latString);
      lonText.setText(lonString);
      accText.setText(accString);
      ageText.setText(ageString);
      latText.setTextColor(Color.WHITE);
      lonText.setTextColor(Color.WHITE);
      accText.setTextColor(Color.WHITE);
      ageText.setTextColor(Color.WHITE);
    } else {
      latText.setText("GPS?");
      lonText.setText("GPS?");
      accText.setText("GPS?");
      ageText.setText("GPS?");
      daOffsetText.setText("DA -----");
      latText.setTextColor(Color.RED);
      lonText.setTextColor(Color.RED);
      accText.setTextColor(Color.RED);
      ageText.setTextColor(Color.RED);
    }

    position_update();

    String satString = String.format("%d/%d/%d/%d",
        Logger.last_fix_sats,
        Logger.last_ephem_sats, Logger.last_alman_sats,
        Logger.last_n_sats);
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
        } else if (odd_cid(Logger.lastCid)) {
          cidText.setTextColor(Color.YELLOW);
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

    String countString;
    if (Logger.validFix) {
      countString = String.format("%dp %dm", Logger.nReadings, 
          (int)Merc28.odn(Logger.lastAlt, Logger.lastLat, Logger.lastLon));
    } else {
      countString = String.format("%dp GPS?", Logger.nReadings);
    }
    countText.setText(countString);

    updateCidHistory(current_time);
  }

  // --------------------------------------------------------------------------
  //

  public class CellUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      // update the map in case the current cell has changed.
      mMap.update_map();
      updateDisplay();
    }
  }

  public class GPSUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      mMap.update_map();
      updateDisplay();
    }
  }

  // --------------------------------------------------------------------------
  //

  private final int OPTION_CLEAR_TRAIL = 5;
  private final int OPTION_EXIT     = 6;
  private final int OPTION_BIG_MAP = 10;

  @Override
    public boolean onCreateOptionsMenu(Menu menu) {
      SubMenu sub = menu.addSubMenu(0, 0, Menu.NONE, "Maps");
      sub.setIcon(android.R.drawable.ic_menu_mapmode);
      for (MapSource source : MapSources.sources) {
        sub.add (Menu.NONE, source.get_code(), Menu.NONE, source.get_menu_name());
      }
      MenuItem m_waypoints =
        menu.add (Menu.NONE, OPTION_BIG_MAP, Menu.NONE, "Waypoint map");
      m_waypoints.setIcon(android.R.drawable.ic_menu_myplaces);
      MenuItem m_clear_waypoints =
        menu.add (Menu.NONE, OPTION_CLEAR_TRAIL,  Menu.NONE, "Clear trail");
      m_clear_waypoints.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
      MenuItem m_exit =
        menu.add (Menu.NONE, OPTION_EXIT,    Menu.NONE, "Exit");
      m_exit.setIcon(android.R.drawable.ic_lock_power_off);
      return true;
    }

  @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      int code = item.getItemId();
      switch (code) {
        case OPTION_EXIT:
          Logger.stop_tracing = true;
          // avoid holding onto oodles of memory at Application level...
          TileStore.invalidate();
          finish();
          return true;
        case OPTION_CLEAR_TRAIL:
          mMap.clear_trail();
          return true;
        case OPTION_BIG_MAP:
          Intent intent = new Intent(this, BigMapActivity.class);
          startActivity(intent);
          return true;
        default:
          MapSource source;
          source = MapSources.lookup(code);
          if (source != null) {
            mMap.select_map_source(source);
            return true;
          } else {
            return false;
          }
      }
    }
}
//
// vim:et:sw=2:sts=2
