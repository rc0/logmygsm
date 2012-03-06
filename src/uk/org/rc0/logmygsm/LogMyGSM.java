package uk.org.rc0.logmygsm;
import android.app.Application;

public class LogMyGSM extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    TileStore.init();
    TowerLine.init();
  }

  @Override
  public void onLowMemory() {
    TileStore.invalidate();
  }

}

