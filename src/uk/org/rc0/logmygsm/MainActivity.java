// Copyright (c) 2012, 2013, Richard P. Curnow
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
import android.text.method.ScrollingMovementMethod;
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
  private TextView twrText;
  private TextView netlacmncText;
  private TextView asuText;
  private TextView daOffsetText;
  private TextView countText;
  private TextView odoText;
  private TextView cidHistoryText;
  private TextView gridRefText;

  private CellUpdateReceiver myCellReceiver;
  private GPSUpdateReceiver myGPSReceiver;

  private Map mMap;

  private MenuItem mTileScalingToggle;
  private MenuItem mTowerlineToggle;

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
      twrText = (TextView) findViewById(R.id.twr);
      netlacmncText = (TextView) findViewById(R.id.net_lac_mnc);
      asuText = (TextView) findViewById(R.id.asu);
      countText = (TextView) findViewById(R.id.count);
      odoText = (TextView) findViewById(R.id.odo);
      cidHistoryText = (TextView) findViewById(R.id.cid_history);
      cidHistoryText.setMovementMethod(new ScrollingMovementMethod());
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

      updateCellDisplay();
      updateGPSDisplay();
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
      TileStore.sleep_invalidate();
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

  private void tower_update() {
    Map.LocationOffset tow_off = mMap.get_tower_offset();
    if (tow_off.known == false) {
      twrText.setText("TOWER?");
      twrText.setTextColor(Color.RED);
    } else {
      String distance;
      String bearing;
      String relative;
      if (tow_off.metres < 1000.0) {
        distance = String.format("%3dm", (int) tow_off.metres);
      } else {
        distance = String.format("%.1fkm", tow_off.metres * 0.001);
      }
      bearing = String.format("%03d\u00B0", (int) tow_off.bearing);
      if (Logger.validFix && !tow_off.dragged) {
        int angle = (int) tow_off.bearing - Logger.lastBearing;
        if (angle < -180) { angle += 360; }
        if (angle >= 180) { angle -= 360; }
        if (angle < 0) {
          relative = String.format(" %03dL", -angle);
        } else {
          relative = String.format(" %03dR",  angle);
        }
      } else {
        relative = "";
      }
      twrText.setText(distance + " " + bearing + relative);
      twrText.setTextColor(Color.WHITE);
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

    String odoString = String.format("%6dm", (int) Logger.metres_covered);
    odoText.setText(odoString);
    String gridString = mMap.current_grid_ref();
    gridRefText.setText(gridString);

  }

  public void display_position_update() {
    position_update();
    tower_update();
  }

  private void updateCellDisplay() {
    long current_time = System.currentTimeMillis();
    String cidString = String.format("%d",
        Logger.lastCid);
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

    String netlacmncString = String.format("%1c%5d %3s",
        Logger.lastNetworkType,
        Logger.lastLac,
        mnc_string);
    String asuString = String.format("%dasu", Logger.lastASU);
    netlacmncText.setText(netlacmncString);
    asuText.setText(asuString);
    if (Logger.lastASU == 99) {
      asuText.setTextColor(Color.RED);
    } else {
      asuText.setTextColor(Color.WHITE);
    }

    updateCidHistory(current_time);
  }

  private void updateGPSDisplay() {
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
    display_position_update();
    String satString = String.format("%d/%d/%d/%d",
        Logger.last_fix_sats,
        Logger.last_ephem_sats, Logger.last_alman_sats,
        Logger.last_n_sats);
    satText.setText(satString);

    String countString;
    // But it's so approximate that it can't be used for accurate purposes anyway.
    if (Logger.validFix) {
      countString = String.format("%dp %dm", Logger.nReadings, 
          (int)Merc28.odn(Logger.lastAlt, Logger.lastLat, Logger.lastLon));
    } else {
      countString = String.format("%dp GPS?", Logger.nReadings);
    }
    countText.setText(countString);
  }

  // --------------------------------------------------------------------------
  //

  public class CellUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      // update the map in case the current cell has changed.
      updateCellDisplay();
      tower_update();
      if (TowerLine.is_active()) {
        // The map only depends on the RF behaviour if there has been a handoff
        // when the tower-line is shown
        mMap.update_map();
      }
    }
  }

  public class GPSUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateGPSDisplay();
      mMap.update_map();
    }
  }

  // --------------------------------------------------------------------------
  //

  static private final int OPTION_EXIT             = Menus2.OPTION_LOCAL_BASE | 0x1;
  static private final int OPTION_SHARE            = Menus2.OPTION_LOCAL_BASE | 0x2;
  static private final int OPTION_LOG_MARKER       = Menus2.OPTION_LOCAL_BASE | 0x3;
  static private final int OPTION_BIG_MAP          = Menus2.OPTION_LOCAL_BASE | 0x4;

  @Override
    public boolean onCreateOptionsMenu(Menu menu) {
      // Top row
      MenuItem[] toggles = Menus2.insert_maps_menu(menu);
      mTileScalingToggle = toggles[0];
      mTowerlineToggle = toggles[1];
      MenuItem m_waypoints =
        menu.add (Menu.NONE, OPTION_BIG_MAP, Menu.NONE, "Waypoints");
      m_waypoints.setIcon(android.R.drawable.ic_menu_myplaces);
      Menus2.insert_download_menu(menu);

      // Bottom row
      MenuItem m_logmark =
        menu.add (Menu.NONE, OPTION_LOG_MARKER, Menu.NONE, "Bookmark");
      m_logmark.setIcon(android.R.drawable.ic_menu_save);
      MenuItem m_share =
        menu.add (Menu.NONE, OPTION_SHARE,  Menu.NONE, "Share OS ref");
      m_share.setIcon(android.R.drawable.ic_menu_share);
      MenuItem m_exit =
        menu.add (Menu.NONE, OPTION_EXIT,    Menu.NONE, "Exit");
      m_exit.setIcon(android.R.drawable.ic_lock_power_off);
      return true;
    }

  @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
      mTileScalingToggle.setChecked(mMap.is_scaled());
      mTowerlineToggle.setChecked(TowerLine.is_active());
      return true;
    }


  @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      int code = item.getItemId();
      int group = Menus2.group(code);
      int option = Menus2.option(code);
      //Log.i(TAG, "code=" + code + " group=" + group + " option=" + option);
      if (group == Menus2.OPTION_DOWNLOAD_BASE) {
        return Menus2.decode_download_option(option, this, mMap);
      } else if (group == Menus2.OPTION_MAP_BASE) {
        return Menus2.decode_map_option(option, mMap);
      } else if (group == Menus2.OPTION_LOCAL_BASE) {
        switch (code) {
          case OPTION_EXIT:
            Logger.stop_tracing = true;

            // If app stays in memory, start 'clean' next time wrt tile downloading
            TileStore.refresh_epoch();
            // avoid holding onto oodles of memory at Application level...
            TileStore.invalidate();

            // Send an intent to the Logger to get it to exit promptly even if there are no
            // GPS or cell updates soon
            Intent quit_intent = new Intent(Logger.QUIT_LOGGER);
            sendBroadcast(quit_intent);

            finish();
            return true;
          case OPTION_SHARE:
            mMap.share_grid_ref(this);
            return true;
          case OPTION_LOG_MARKER:
            Logger.do_bookmark(this);
            return true;
          case OPTION_BIG_MAP:
            Intent launch_intent = new Intent(this, BigMapActivity.class);
            startActivity(launch_intent);
            return true;
          case Menus2.OPTION_TOGGLE_TOWERLINE:
            TowerLine.toggle_active();
            return true;
          default:
            return false;
        }
      } else {
        return false;
      }
    }
}
//
// vim:et:sw=2:sts=2
