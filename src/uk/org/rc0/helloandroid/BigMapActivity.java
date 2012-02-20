package uk.org.rc0.helloandroid;

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

public class BigMapActivity extends Activity {

  private GPSUpdateReceiver myGPSReceiver;
  private Map mMap;
  private Button mAddButton;
  private Button mDeleteButton;
  private Button mDeleteAllButton;

  private static final String PREFS_FILE = "prefs2.txt";

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.bigmap);
    mMap = (Map) findViewById(R.id.big_map);
    mMap.restore_state_from_file(PREFS_FILE);
    mAddButton = (Button) findViewById(R.id.add_button);
    mDeleteButton = (Button) findViewById(R.id.delete_button);
    mDeleteAllButton = (Button) findViewById(R.id.delete_all_button);

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

    mDeleteAllButton.setOnClickListener(new OnClickListener () {
      public void onClick(View v) {
        mMap.delete_all_landmarks();
      }
    });
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
    mMap.update_map();
    super.onResume();
  }

  @Override
  public void onPause() {
    unregisterReceiver(myGPSReceiver);
    // It seems wasteful to do this here, but there is no other safe opportunity to do so -
    // in effect we are 'committing' the user's changes at this point.
    mMap.save_state_to_file(PREFS_FILE);
    super.onPause();
  }

  private final int OPTION_MAP_2G   = 1;
  private final int OPTION_MAP_3G   = 2;
  private final int OPTION_MAP_MAPNIK  = 3;
  private final int OPTION_MAP_CYCLE = 7;
  private final int OPTION_MAP_OS   = 4;
  private final int OPTION_EXIT     = 6;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    SubMenu sub = menu.addSubMenu(0, 0, Menu.NONE, "Maps");
    sub.add (Menu.NONE, OPTION_MAP_2G,  Menu.NONE, "O2 UK 2G map");
    sub.add (Menu.NONE, OPTION_MAP_3G,  Menu.NONE, "O2 UK 3G map");
    sub.add (Menu.NONE, OPTION_MAP_OS,  Menu.NONE, "Ordnance Survey");
    sub.add (Menu.NONE, OPTION_MAP_MAPNIK, Menu.NONE, "Mapnik (OSM)");
    sub.add (Menu.NONE, OPTION_MAP_CYCLE, Menu.NONE, "OpenCycleMap");
    menu.add (Menu.NONE, OPTION_EXIT,    Menu.NONE, "Exit");
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
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
      case OPTION_EXIT:
        finish();
        return true;
      default:
        return false;
    }
  }

  public class GPSUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      mMap.update_map();
    }
  }
}

// vim:et:sw=2:sts=2
//
