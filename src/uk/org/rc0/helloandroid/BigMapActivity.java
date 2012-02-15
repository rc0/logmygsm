package uk.org.rc0.helloandroid;

import android.app.Activity;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Menu;
import android.view.MenuItem;

public class BigMapActivity extends Activity {

  private DisplayUpdateReceiver myReceiver;
  private Map mMap;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.bigmap);
    mMap = (Map) findViewById(R.id.big_map);
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
    //mMap.save_state_to_file();
    super.onPause();
  }

  private final int OPTION_EXIT     = 6;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add (Menu.NONE, OPTION_EXIT,    Menu.NONE, "Exit");
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case OPTION_EXIT:
        finish();
        return true;
      default:
        return false;
    }
  }

  private void updateDisplay() {
    mMap.update_map();
  }

  public class DisplayUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateDisplay();
    }
  }
}

