package uk.org.rc0.helloandroid;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class Logger extends Service {

  private boolean is_running;

  @Override
  public void onCreate() {
    is_running = false;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!is_running) {
      // Start all the funky stuff
      is_running = true;
    }
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



