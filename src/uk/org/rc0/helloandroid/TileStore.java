package uk.org.rc0.helloandroid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import java.io.File;

public class TileStore {

  static final private int bm_log_size = 8;
  static final private int bm_size = 1<<bm_log_size;

  // -----------
  // State

  private class Entry {
    int zoom;
    int map_source;
    int x;
    int y;
    Bitmap b;

    Bitmap getBitmap() {
      return b;
    }

    Entry(int azoom, int amap_source, int ax, int ay, Bitmap ab) {
      zoom = azoom;
      map_source = amap_source;
      x = ax;
      y = ay;
      b = ab;
    }

    boolean isMatch(int azoom, int amap_source, int ax, int ay) {
      if ((azoom == zoom) &&
          (amap_source == map_source) &&
          (ax == x) &&
          (ay == y)) {
        return true;
      } else {
        return false;
      }
    }
  }

  // Eventually, make this depend on the canvas size and hence on how much welly the phone has
  static private final int SIZE = 32;

  static private Entry [] front;
  static private int next;
  static private Entry [] back;

  static private Paint gray_paint;
  // -----------

  static void init () {
    front = new Entry[SIZE];
    next = 0;
    gray_paint = new Paint();
    gray_paint.setColor(Color.GRAY);
  }

  // -----------
  // Internal

  private Bitmap render_bitmap(int zoom, int map_source, int x, int y) {
    String filename = null;
    switch (map_source) {
      case Map.MAP_2G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 2/%d/%d/%d.png.tile",
            zoom, x, y);
        break;
      case Map.MAP_3G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 3/%d/%d/%d.png.tile",
            zoom, x, y);
        break;
      case Map.MAP_MAPNIK:
        filename = String.format("/sdcard/Maverick/tiles/mapnik/%d/%d/%d.png.tile",
            zoom, x, y);
        break;
      case Map.MAP_OPEN_CYCLE:
        filename = String.format("/sdcard/Maverick/tiles/OSM Cycle Map/%d/%d/%d.png.tile",
            zoom, x, y);
        break;
      case Map.MAP_OS:
        filename = String.format("/sdcard/Maverick/tiles/Ordnance Survey Explorer Maps (UK)/%d/%d/%d.png.tile",
            zoom, x, y);
        break;
    }
    File file = new File(filename);
    Bitmap bm;
    if (file.exists()) {
      bm = BitmapFactory.decodeFile(filename);
    } else {
      bm = Bitmap.createBitmap(bm_size, bm_size, Bitmap.Config.ARGB_8888);
      Canvas my_canv = new Canvas(bm);
      my_canv.drawRect(0, 0, bm_size, bm_size, gray_paint);
    }
    // TODO : Draw trail points into the bitmap
    return bm;
  }

  private Bitmap lookup(int zoom, int map_source, int x, int y) {
    // front should never be null
    for (int i=0; i<next; i++) {
      if (front[i].isMatch(zoom, map_source, x, y)) {
        return front[i].getBitmap();
      }
    }
    // Miss.  Any space left?
    if (next == SIZE) {
      // No.  Dump old junk
      back = front;
      // Might consider garbage collection here?
      front = new Entry[SIZE];
      next = 0;
    }

    // Search 'back' array for a match.  'back' is either null, or full
    if (back != null) {
      for (int i=0; i<SIZE; i++) {
        if (back[i].isMatch(zoom, map_source, x, y)) {
          front[next++] = back[i];
          return back[i].getBitmap();
        }
      }
    }

    // OK, no match.  We have to build a new bitmap from file
    Bitmap b = render_bitmap(zoom, map_source, x, y);
    front[next++] = new Entry(zoom, map_source, x, y, b);
    return b;

  }

  // -----------
  // Interface with map

  public void draw(Canvas c, int w, int h, int zoom, int map_source, Merc28 midpoint) {
    int pixel_shift = (Merc28.shift - (zoom + bm_log_size));

    // Compute pixels from origin at this zoom level for top-left corner of canvas
    int px, py;
    px = (midpoint.X >> pixel_shift) - (w>>1);
    py = (midpoint.Y >> pixel_shift) - (h>>1);

    // Hence compute tile containing top-left corner of canvas
    int tx, ty;
    tx = px >> bm_log_size;
    ty = py >> bm_log_size;

    // top-left corner of the top-left tile, in pixels relative to top-left corner of canvas
    int ox, oy;
    ox = (tx << bm_log_size) - px;
    oy = (ty << bm_log_size) - py;

    int i, j;
    i = 0;
    while (ox + (i<<bm_log_size) < w) {
      int xx = ox + (i<<bm_log_size);
      j = 0;
      while (oy + (j<<bm_log_size) < h) {
        int yy = oy + (j<<bm_log_size);
        Bitmap bm = lookup(zoom, map_source, tx+i, ty+j);
        Rect dest = new Rect(xx, yy, xx+bm_size, yy+bm_size);
        c.drawBitmap(bm, null, dest, null);
        j++;
      }
      i++;
    }
  }

}


// vim:et:sw=2:sts=2

