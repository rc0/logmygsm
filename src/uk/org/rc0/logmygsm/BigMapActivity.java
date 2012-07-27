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
import android.widget.TextView;

public class BigMapActivity extends Activity implements Map.PositionListener {

  private CellUpdateReceiver myCellReceiver;
  private GPSUpdateReceiver myGPSReceiver;
  private Map mMap;
  private Button mAddButton;
  private Button mDeleteButton;
  private Button mDeleteVisibleButton;
  private Button mDeleteAllButton;
  private TextView summaryText;
  private TextView gridRefText;

  private static final String PREFS_FILE = "prefs2.txt";

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.bigmap);
    mMap = (Map) findViewById(R.id.big_map);
    mMap.restore_state_from_file(PREFS_FILE);
    mAddButton = (Button) findViewById(R.id.add_button);
    mDeleteButton = (Button) findViewById(R.id.delete_button);
    mDeleteVisibleButton = (Button) findViewById(R.id.delete_visible_button);
    mDeleteAllButton = (Button) findViewById(R.id.delete_all_button);
    summaryText = (TextView) findViewById(R.id.big_summary);
    gridRefText = (TextView) findViewById(R.id.big_grid_ref);

    mAddButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.add_landmark();
      }
    });

    mDeleteButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_landmark();
      }
    });

    mDeleteVisibleButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_visible_landmarks();
      }
    });

    mDeleteAllButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_all_landmarks();
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
    //Logger.stop_tracing = false;
    //startService(new Intent(this, Logger.class));
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
    super.onPause();
  }

  private final int OPTION_CLEAR_TRAIL =  5;
  private final int OPTION_DOWNLOAD    = 11;
  private final int OPTION_SHARE       = 12;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    SubMenu sub = menu.addSubMenu(0, 0, Menu.NONE, "Maps");
    sub.setIcon(android.R.drawable.ic_menu_mapmode);
    for (MapSource source : MapSources.sources) {
      sub.add (Menu.NONE, source.get_code(), Menu.NONE, source.get_menu_name());
    }
    MenuItem m_download =
      menu.add (Menu.NONE, OPTION_DOWNLOAD, Menu.NONE, "Download tile");
    m_download.setIcon(android.R.drawable.ic_menu_view);

    MenuItem m_share =
      menu.add (Menu.NONE, OPTION_SHARE,  Menu.NONE, "Share grid ref");
    m_share.setIcon(android.R.drawable.ic_menu_share);
    MenuItem m_clear_waypoints =
      menu.add (Menu.NONE, OPTION_CLEAR_TRAIL,  Menu.NONE, "Clear trail");
    m_clear_waypoints.setIcon(android.R.drawable.ic_menu_close_clear_cancel);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int code = item.getItemId();
    switch (code) {
        case OPTION_CLEAR_TRAIL:
          mMap.clear_trail();
          return true;
        case OPTION_DOWNLOAD:
          mMap.trigger_fetch(getApplicationContext());
          return true;
        case OPTION_SHARE:
          mMap.share_grid_ref(this);
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

  private void updateDisplay() {

    String summaryString;
    if (Logger.validFix) {
      summaryString = String.format("%9d %2d %3dm %s",
        Logger.lastCid,
        Logger.lastASU,
        Logger.lastAcc,
        mMap.current_tile_string()
      );
    } else {
      summaryString = String.format("%9d %2d  GPS? %s",
        Logger.lastCid,
        Logger.lastASU,
        mMap.current_tile_string()
      );
    }

    summaryText.setText(summaryString);
    gridRefText.setText(mMap.current_grid_ref());
  }

  public void display_position_update() {
    updateDisplay();
  }

  public class GPSUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      mMap.update_map();
    }
  }

  public class CellUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      // update the map in case the current cell has changed.
      mMap.update_map();
      updateDisplay();
    }
  }


}

// vim:et:sw=2:sts=2
//
