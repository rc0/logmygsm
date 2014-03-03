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

import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class BigMapActivity extends Activity implements Map.PositionListener {

  private CellUpdateReceiver myCellReceiver;
  private GPSUpdateReceiver myGPSReceiver;
  private WaypointEditMap mMap;
  private ImageButton mAddButton;
  private ImageButton mDeleteButton;
  private ImageButton mDeleteVisibleButton;
  private ImageButton mDeleteAllButton;
  private ImageButton mSetDestinationButton;
  private ImageButton mAddLMButton;
  private ImageButton mDeleteLMButton;
  private TextView summaryText;
  private TextView gridRefText;
  private MenuItem mTileScalingToggle;
  private MenuItem mTowerlineToggle;

  private static final String PREFS_FILE = "prefs2.txt";

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.bigmap);
    mMap = (WaypointEditMap) findViewById(R.id.big_map);
    mMap.restore_state_from_file(PREFS_FILE);
    mAddButton = (ImageButton) findViewById(R.id.add_button);
    mDeleteButton = (ImageButton) findViewById(R.id.delete_button);
    mDeleteVisibleButton = (ImageButton) findViewById(R.id.delete_visible_button);
    mDeleteAllButton = (ImageButton) findViewById(R.id.delete_all_button);
    mSetDestinationButton = (ImageButton) findViewById(R.id.set_destination_button);
    mAddLMButton = (ImageButton) findViewById(R.id.add_landmark_button);
    mDeleteLMButton = (ImageButton) findViewById(R.id.del_landmark_button);
    summaryText = (TextView) findViewById(R.id.big_summary);
    gridRefText = (TextView) findViewById(R.id.big_grid_ref);

    mAddButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.add_waypoint();
      }
    });

    mDeleteButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_waypoint();
      }
    });

    mDeleteVisibleButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_visible_waypoints();
      }
    });

    mDeleteAllButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_all_waypoints();
      }
    });

    mSetDestinationButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.set_destination_waypoint();
      }
    });

    mAddLMButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.add_landmark();
      }
    });

    mDeleteLMButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_landmark();
      }
    });
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

    filter = new IntentFilter(Logger.UPDATE_GPS);
    myGPSReceiver = new GPSUpdateReceiver();
    registerReceiver(myGPSReceiver, filter);

    filter = new IntentFilter(Logger.UPDATE_CELL);
    myCellReceiver = new CellUpdateReceiver();
    registerReceiver(myCellReceiver, filter);

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
    TileStore.sleep_invalidate();
    finish();
    super.onPause();
  }

  private final int OPTION_CLEAR_TRAIL  = Menus2.OPTION_LOCAL_BASE | 0x1;
  private final int OPTION_SHARE        = Menus2.OPTION_LOCAL_BASE | 0x2;
  private final int OPTION_LOG_MARKER   = Menus2.OPTION_LOCAL_BASE | 0x3;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuItem[] toggles = Menus2.insert_maps_menu(menu);
    mTileScalingToggle = toggles[0];
    mTowerlineToggle = toggles[1];
    Menus2.insert_download_menu(menu);

    MenuItem m_logmark =
      menu.add (Menu.NONE, OPTION_LOG_MARKER, Menu.NONE, "Bookmark");
    m_logmark.setIcon(android.R.drawable.ic_menu_save);
    MenuItem m_share =
      menu.add (Menu.NONE, OPTION_SHARE,  Menu.NONE, "Share grid ref");
    m_share.setIcon(android.R.drawable.ic_menu_share);
    MenuItem m_clear_waypoints =
      menu.add (Menu.NONE, OPTION_CLEAR_TRAIL,  Menu.NONE, "Clear trail");
    m_clear_waypoints.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

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
    if (group == Menus2.OPTION_DOWNLOAD_BASE) {
      return Menus2.decode_download_option(Menus2.option(code), this, mMap);
    } else if (group == Menus2.OPTION_MAP_BASE) {
      return Menus2.decode_map_option(option, mMap);
    } else if (group == Menus2.OPTION_LOCAL_BASE) {
      switch (code) {
        case OPTION_CLEAR_TRAIL:
          mMap.clear_trail();
          return true;
        case OPTION_LOG_MARKER:
          Logger.do_bookmark(this);
          return true;
        case OPTION_SHARE:
          mMap.share_grid_ref(this);
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

  private void updateDisplay() {

    String summaryString;
    if (Logger.validFix) {
      summaryString = String.format("%9d %2dasu %3dm %s",
        Logger.lastCid,
        Logger.lastASU,
        Logger.lastAcc,
        mMap.current_tile_string()
      );
    } else {
      summaryString = String.format("%9d %2dasu  GPS? %s",
        Logger.lastCid,
        Logger.lastASU,
        mMap.current_tile_string()
      );
    }

    if (gridRefText != null) {
      summaryText.setText(summaryString);
      gridRefText.setText(String.format("%2s / %.4f %.4f",
            mMap.current_grid_ref(),
            mMap.current_lat(),
            mMap.current_lon()));
    } else {
      summaryText.setText(String.format("%s %s %.4f %.4f",
            summaryString, mMap.current_grid_ref(),
            mMap.current_lat(), mMap.current_lon()));
    }
  }

  public void display_position_update() {
    updateDisplay();
  }

  public class GPSUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      mMap.update_map();
      updateDisplay();
    }
  }

  public class CellUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      // update the map in case the current cell has changed.
      if (TowerLine.is_active()) {
        mMap.update_map();
      }
      updateDisplay();
    }
  }


}

// vim:et:sw=2:sts=2
//
