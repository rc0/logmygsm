package uk.org.rc0.helloandroid;

import java.io.File;
import java.lang.Math;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

public class Map extends View {

  private final Paint my_paint;
  private final Paint grey_paint;

  private int zoom;

  public Map(Context context, AttributeSet attrs) {
    super(context, attrs);

    my_paint = new Paint();
    my_paint.setStrokeWidth(2);
    my_paint.setColor(Color.RED);

    grey_paint = new Paint();
    grey_paint.setStrokeWidth(2);
    grey_paint.setColor(Color.GRAY);

    zoom = 14;
  }

  public class Slip28 {
    public int X;
    public int Y;

    public final double scale28 = 268435456.0;

    public Slip28(double lat, double lon) {
      double x, yy, y, XX, YY;
      x = Math.toRadians(lon);
      yy = Math.toRadians(lat);
      y = Math.log(Math.tan(yy) + 1.0/Math.cos(yy));
      XX = 0.5 * (1.0 + x/Math.PI);
      YY = 0.5 * (1.0 - y/Math.PI);
      X = (int) Math.round(XX * scale28);
      Y = (int) Math.round(YY * scale28);
    }

  };

  private void render_tile(Canvas canvas, int zoom, int tile_x, int tile_y, int subtile_x, int subtile_y) {
    String filename = String.format("/sdcard/Maverick/tiles/Custom 2/%d/%d/%d.png.tile", zoom, tile_x, tile_y);
    File file = new File(filename);
    if (file.exists()) {
      String foo2 = String.format("Tile found on SD card");
      canvas.drawText(foo2, 10, 120, my_paint);
    } else {
      String foo2 = String.format("No tile on SD card");
      canvas.drawText(foo2, 10, 120, my_paint);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int width = getWidth();
    int height = getHeight();
    int cwidth = canvas.getWidth();
    int cheight = canvas.getHeight();
    canvas.drawColor(Color.rgb(40,40,40));

    String foo = String.format("w=%d h=%d cw=%d ch=%d",
        width, height,
        cwidth, cheight);
    canvas.drawText(foo, 10, 40, my_paint);

    if (Logger.validFix) {
      Slip28 pos = new Slip28(Logger.lastLat, Logger.lastLon);
      int tile_x, tile_y, subtile_x, subtile_y;
      tile_x = pos.X >> (28 - zoom);
      tile_y = pos.Y >> (28 - zoom);
      subtile_x = (pos.X >> (28 - (zoom+8))) & 0xff;
      subtile_y = (pos.Y >> (28 - (zoom+8))) & 0xff;
      String foo2 = String.format("%d/%d/%d.png.tile at %d,%d",
          zoom, tile_x, tile_y, subtile_x, subtile_y);
      canvas.drawText(foo2, 10, 80, my_paint);
      render_tile(canvas, zoom, tile_x, tile_y, subtile_x, subtile_y);
    } else {
      String foo2 = String.format("No fix");
      canvas.drawText(foo2, 10, 80, my_paint);
    }

  }
}

