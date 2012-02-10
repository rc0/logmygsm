package uk.org.rc0.helloandroid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.File;

public class TileCache {

  // All OSM / Maverick tiles are this many pixels square - we hope!
  static final private int bm_log_width = 8;
  static final private int bm_width = 1<<bm_log_width;
  static final private int tiles_along_side = 2;
  static final private int pixels_along_side = tiles_along_side * bm_width;

  private Map mOwner;
  private Bitmap cache;
  private Merc28 nw_corner;
  private int map_source;
  private int zoom;
  private int pixel_shift_1;
  private int tile_shift;

  private Paint gray_paint;

  // ----------------------

  // Public interface

  public TileCache(Map owner) {
    Paint gray_paint = new Paint();
    gray_paint.setColor(Color.GRAY);
    mOwner = owner;
  }

  public void setZoom(int z) {
    if (zoom != z) {
      cache = null;
    }
    zoom = z;
    tile_shift = (Merc28.shift - (zoom));
    pixel_shift_1 = (Merc28.shift - (zoom + bm_log_width)) - 1;
  }

  public void setMapSource(int code) {
    if (map_source != code) {
      cache = null;
    }
    map_source = code;
  }

  public int getMapSource() {
    return map_source;
  }

  public void clear() {
    cache = null;
    nw_corner = null;
  }

  private class XY {
    int X;
    int Y;

    public XY(int x, int y) {
      X = x;
      Y = y;
    }

  }

  private XY trans_relative(Merc28 geo) {
    int x, y;
    x = (geo.X - nw_corner.X) >> pixel_shift_1;
    x = (x + 1) >> 1;
    y = (geo.Y - nw_corner.Y) >> pixel_shift_1;
    y = (y + 1) >> 1;
    return new XY(x, y);
  }

  private XY trans_relative(int x28, int y28) {
    int x, y;
    x = (x28 - nw_corner.X) >> pixel_shift_1;
    x = (x + 1) >> 1;
    y = (y28 - nw_corner.Y) >> pixel_shift_1;
    y = (y + 1) >> 1;
    return new XY(x, y);
  }

  private XY trans_absolute(Merc28 geo) {
    int x, y;
    x = (geo.X) >> pixel_shift_1;
    x = (x + 1) >> 1;
    y = (geo.Y) >> pixel_shift_1;
    y = (y + 1) >> 1;
    return new XY(x, y);
  }

  // I suspect there are boundary problems around 85S and the date line.
  // Hopefully I'm not going there.

  private void render_tile(Canvas canvas, int z, int tile_x, int tile_y, int dx, int dy) {
    int xl = dx     << bm_log_width;
    int xr = (dx+1) << bm_log_width;
    int yt = dy     << bm_log_width;
    int yb = (dy+1) << bm_log_width;

    String filename = null;
    switch (map_source) {
      case Map.MAP_2G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 2/%d/%d/%d.png.tile",
            z, tile_x+dx, tile_y+dy);
        break;
      case Map.MAP_3G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 3/%d/%d/%d.png.tile",
            z, tile_x+dx, tile_y+dy);
        break;
      case Map.MAP_OSM:
        filename = String.format("/sdcard/Maverick/tiles/mapnik/%d/%d/%d.png.tile",
            z, tile_x+dx, tile_y+dy);
        break;
      case Map.MAP_OS:
        filename = String.format("/sdcard/Maverick/tiles/Ordnance Survey Explorer Maps (UK)/%d/%d/%d.png.tile",
            z, tile_x+dx, tile_y+dy);
        break;
    }
    File file = new File(filename);
    if (file.exists()) {
      Bitmap bm = BitmapFactory.decodeFile(filename);
      Rect dest = new Rect(xl, yt, xr, yb);
      canvas.drawBitmap(bm, null, dest, null);
    } else {
      canvas.drawRect(xl, yt, xr, yb, gray_paint);
    }
  }

  private void rebuild(Merc28 geo) {
    XY pos;
    int tile_x, tile_y;
    pos = trans_absolute(geo);
    tile_x = pos.X >> 8;
    tile_y = pos.Y >> 8;
    cache = Bitmap.createBitmap(pixels_along_side, pixels_along_side, Bitmap.Config.ARGB_8888);
    Canvas my_canv = new Canvas(cache);
    for (int dx=0; dx<tiles_along_side; dx++) {
      for (int dy=0; dy<tiles_along_side; dy++) {
        render_tile(my_canv, zoom, tile_x, tile_y, dx, dy);
      }
    }
    nw_corner = new Merc28(tile_x << tile_shift, tile_y << tile_shift);

    // Render old part of trail
    Trail.PointArray pa = Logger.mTrail.get_historical();
    int last_x = 0, last_y = 0;
    for (int i = 0; i < pa.n; i++) {
      XY s = trans_relative(pa.x[i], pa.y[i]);
      boolean do_add = true;
      if (i > 0) {
        int manhattan = Math.abs(s.X - last_x) + Math.abs(s.Y - last_y);
        if (manhattan < Trail.splot_gap) {
          do_add = false;
        }
      }
      if (do_add) {
        my_canv.drawCircle((float)s.X, (float)s.Y, Trail.splot_radius, mOwner.trail_paint);
        last_x = s.X;
        last_y = s.Y;
      }
    }
  }

  // midpoint is the geographical position to render at centre-screen
  public void draw(Canvas c, int w, int h, Merc28 midpoint) {
    XY pos;

    if (cache == null) {
      rebuild(midpoint);
    }

    pos = trans_relative(midpoint);
    if ((pos.X < 0) ||
        (pos.X + w >= pixels_along_side) ||
        (pos.Y < 0) ||
        (pos.Y + h >= pixels_along_side)) {

      rebuild(midpoint);
      pos = trans_relative(midpoint);
    }

    Rect src = new Rect(pos.X, pos.Y, pos.X + w, pos.Y + h);
    Rect dest = new Rect(0, 0, w, h);
    c.drawBitmap(cache, src, dest, null);
  }

}


// vim:et:sw=2:sts=2

