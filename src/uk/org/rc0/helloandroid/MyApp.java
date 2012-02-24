package uk.org.rc0.helloandroid;
import android.app.Application;

public class MyApp extends Application {
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

