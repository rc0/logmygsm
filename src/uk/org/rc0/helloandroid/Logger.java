package uk.org.rc0.helloandroid;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class Logger extends Service {

  private boolean is_running;

  // -----------------
  // Variables shared with the Activity
  // -----------------
  //
  static public int xyz;
  static public boolean do_logging;

  @Override
  public void onCreate() {
    is_running = false;
    do_logging = false;
    xyz = 5067;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!is_running) {
      // Start all the funky stuff
      is_running = true;
    }
    xyz = 9034;
    return Service.START_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
  }

}



