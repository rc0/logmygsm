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
import android.graphics.BitmapFactory;
import android.graphics.Rect;

public class Map extends View {

  private final Paint red_paint;
  private final Paint red_stroke_paint;
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

    red_paint = new Paint();
    red_paint.setStrokeWidth(1);
    red_paint.setColor(Color.RED);

    red_stroke_paint = new Paint();
    red_stroke_paint.setStrokeWidth(1);
    red_stroke_paint.setColor(Color.RED);
    red_stroke_paint.setStyle(Paint.Style.STROKE);

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

  private void render_tile(Canvas canvas, int x01, int y01, int tile_x, int tile_y) {
    int xl = (256*x01);
    int xr = (256*(1+x01));
    int yt = (256*y01);
    int yb = (256*(1+y01));

    String filename = null;
    switch (generation) {
      case GEN_2G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 2/%d/%d/%d.png.tile",
            zoom, tile_x+x01, tile_y+y01);
        break;
      case GEN_3G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 3/%d/%d/%d.png.tile",
            zoom, tile_x+x01, tile_y+y01);
        break;
    }
    File file = new File(filename);
    if (file.exists()) {
      Bitmap bm = BitmapFactory.decodeFile(filename);
      Rect dest = new Rect(xl, yt, xr, yb);
      canvas.drawBitmap(bm, null, dest, null);
    } else {
      Paint p = new Paint();
      p.setColor(Color.GRAY);
      canvas.drawRect(xl, yt, xr, yb, p);
    }

  }

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
    render_tile(my_canv, 0, 0, dx, dy);
    render_tile(my_canv, 0, 1, dx, dy);
    render_tile(my_canv, 1, 0, dx, dy);
    render_tile(my_canv, 1, 1, dx, dy);
    ul22 = new Slip28(dx << (28 - zoom), dy << (28 - zoom));
  }

  private void draw_crosshair(Canvas c, int w, int h) {
    int len1 = 8;
    int len2 = 64;
    int len3 = 3 * len1;
    float x0, x1, x2, x3, xc;
    float y0, y1, y2, y3, yc;
    x0 = (float)(w/2 - len1 - len2);
    x1 = (float)(w/2 - len1);
    xc = (float)(w/2);
    x2 = (float)(w/2 + len1);
    x3 = (float)(w/2 + len1 + len2);
    y0 = (float)(h/2 - len1 - len2);
    y1 = (float)(h/2 - len1);
    yc = (float)(h/2);
    y2 = (float)(h/2 + len1);
    y3 = (float)(h/2 + len1 + len2);
    c.drawLine(x0, yc, x1, yc, red_paint);
    c.drawLine(x2, yc, x3, yc, red_paint);
    c.drawLine(xc, y0, xc, y1, red_paint);
    c.drawLine(xc, y2, xc, y3, red_paint);

    c.drawCircle(xc, yc, len3, red_stroke_paint);
  }

  private void update_map(Canvas canvas, Slip28 pos) {
    // Decide if we have to rebuild the tile22 cache
    int width = getWidth();
    int height = getHeight();
    if (height > 240) {
      // Avoid the viewport height getting bigger than the size of a tile - for now
      height = 240;
    }
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

    draw_crosshair(canvas, width, height);
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
    // force map rebuild
    tile22 = null;
    invalidate();
  }

  public void update_map() {
    invalidate();
  }

  public void zoom_out() {
    if (zoom > 9) {
      zoom--;
      tile22 = null;
      invalidate();
    }
  }

  public void zoom_in() {
    if (zoom < 16) {
      zoom++;
      tile22 = null;
      invalidate();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {

    if (Logger.validFix) {
      Slip28 pos = new Slip28(Logger.lastLat, Logger.lastLon);
      update_map(canvas, pos);
    } else {
      canvas.drawColor(Color.rgb(40,40,40));
      String foo2 = String.format("No fix");
      canvas.drawText(foo2, 10, 80, red_paint);
    }

  }
}

