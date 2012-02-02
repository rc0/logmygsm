package uk.org.rc0.helloandroid;

import java.io.File;
import java.lang.Math;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Rect;

public class Map extends View {

  private final Paint my_paint;
  private final Paint grey_paint;

  private int zoom;

  static private enum Generation {
    GEN_2G, GEN_3G
  }

  // Cache of 2x2 tiles containing the region around the viewport
  private Bitmap tile22;
  private Slip28 ul22;
  private int z22;

  // --------------------------------------------------------------------------
  

  private Generation generation;

  public Map(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Eventually, this should be keyed from the network we're getting tower readings of?
    generation = Generation.GEN_2G;
    tile22 = null;

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
    public Slip28(int x, int y) {
      X = x;
      Y = y;
    }
  };

  private void rebuild_cache(Slip28 pos, int width, int height) {
    int dx, dy;
    z22 = zoom;
    dx = pos.X >> (28 - (z22 + 8));
    dy = pos.Y >> (28 - (z22 + 8));
    dx -= (width >> 1);
    dy -= (height >> 1);
    dx >>= 8;
    dy >>= 8;
    // dx, dy are now the tile x,y coords of the tile containing left hand
    // corner of the viewport
    tile22 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    Canvas my_canv = new Canvas(tile22);
    Paint my_paint = new Paint();
    my_paint.setColor(Color.rgb(255,0,0));
    my_canv.drawRect(0.0f, 0.0f, 256.0f, 256.0f, my_paint);
    my_canv.drawRect(256.0f, 256.0f, 512.0f, 512.0f, my_paint);
    my_paint.setColor(Color.rgb(0,255,0));
    my_canv.drawRect(256.0f, 0.0f, 512.0f, 256.0f, my_paint);
    my_canv.drawRect(0.0f, 256.0f, 256.0f, 512.0f, my_paint);
    ul22 = new Slip28(dx << (28 - zoom), dy << (28 - zoom));
  }

  private void update_map(Canvas canvas, Slip28 pos) {
    // Decide if we have to rebuild the tile22 cache
    int width = getWidth();
    int height = getHeight();
    int dx, dy;
    if (tile22 == null) {
      rebuild_cache(pos, width, height);
      dx = (pos.X - ul22.X) >> (28 - (z22 + 8));
      dy = (pos.Y - ul22.Y) >> (28 - (z22 + 8));
      // dx, dy are now the delta from the current position (centre viewport)
      // away from the top left of the 2x2 tile cache, in pixels
      dx -= (width >> 1);
      dy -= (height >> 1);
    } else {
      dx = (pos.X - ul22.X) >> (28 - (z22 + 8));
      dy = (pos.Y - ul22.Y) >> (28 - (z22 + 8));
      // dx, dy are now the delta from the current position (centre viewport)
      // away from the top left of the 2x2 tile cache, in pixels
      dx -= (width >> 1);
      dy -= (height >> 1);
      // Now correspond to top left-hand corner
      if ((dx >= 0) && ((dx + width) < 512) &&
          (dy >= 0) && ((dy + height) < 512)) {
      } else {
        rebuild_cache(pos, width, height);
        dx = (pos.X - ul22.X) >> (28 - (z22 + 8));
        dy = (pos.Y - ul22.Y) >> (28 - (z22 + 8));
        // dx, dy are now the delta from the current position (centre viewport)
        // away from the top left of the 2x2 tile cache, in pixels
        dx -= (width >> 1);
        dy -= (height >> 1);
      }
    }

    Rect src = new Rect(dx, dy, dx+width, dy+height);
    Rect dest = new Rect(0, 0, width, height);
    canvas.drawBitmap(tile22, src, dest, null);

  }

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

  public void toggle_2g3g() {
    switch (generation) {
      case GEN_2G:
        generation = Generation.GEN_3G;
        break;
      case GEN_3G:
        generation = Generation.GEN_2G;
        break;
    }
  }

  public void update_map() {
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    int width = getWidth();
    int height = getHeight();
    int cwidth = canvas.getWidth();
    int cheight = canvas.getHeight();

    // String foo = String.format("w=%d h=%d cw=%d ch=%d",
    //     width, height,
    //     cwidth, cheight);
    // canvas.drawText(foo, 10, 40, my_paint);

    if (Logger.validFix) {
      Slip28 pos = new Slip28(Logger.lastLat, Logger.lastLon);
      update_map(canvas, pos);
      // int tile_x, tile_y, subtile_x, subtile_y;
      // tile_x = pos.X >> (28 - zoom);
      // tile_y = pos.Y >> (28 - zoom);
      // subtile_x = (pos.X >> (28 - (zoom+8))) & 0xff;
      // subtile_y = (pos.Y >> (28 - (zoom+8))) & 0xff;
      // String foo2 = String.format("%d/%d/%d.png.tile at %d,%d",
      //     zoom, tile_x, tile_y, subtile_x, subtile_y);
      // canvas.drawText(foo2, 10, 80, my_paint);
      // render_tile(canvas, zoom, tile_x, tile_y, subtile_x, subtile_y);
    } else {
      canvas.drawColor(Color.rgb(40,40,40));
      String foo2 = String.format("No fix");
      canvas.drawText(foo2, 10, 80, my_paint);
    }

  }
}

