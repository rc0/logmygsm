// Copyright (c) 2012, Richard P. Curnow
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of the <organization> nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package uk.org.rc0.logmygsm;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import java.io.File;
import java.lang.Runnable;

class TileStore {

  static final private int bm_log_size = 8;
  static final private int bm_size = 1<<bm_log_size;

  static final private String TAG = "TileStore";

  // -----------
  // State

  private static class Entry {
    int zoom;
    int pixel_shift;
    int tile_shift;
    int map_source;
    int x;
    int y;
    int cycle;
    private Trail.Upto upto;
    Bitmap b;

    Bitmap getBitmap() {
      return b;
    }

    Entry(int _zoom, int _map_source, int _x, int _y, Bitmap _b) {
      zoom = _zoom;
      pixel_shift = (Merc28.shift - (zoom + bm_log_size));
      tile_shift = (Merc28.shift - zoom);
      map_source = _map_source;
      x = _x;
      y = _y;
      b = _b;
      upto = new Trail.Upto ();
      touch();
    }

    boolean isMatch(int _zoom, int _map_source, int _x, int _y) {
      if ((_zoom == zoom) &&
          (_map_source == map_source) &&
          (_x == x) &&
          (_y == y)) {
        return true;
      } else {
        return false;
      }
    }

    void touch () {
      cycle = draw_cycle;
    }

    void add_recent_trail() {
      Canvas my_canv = new Canvas(b);
      Logger.mTrail.draw_recent_trail(my_canv,
          x<<tile_shift, y<<tile_shift,
          pixel_shift, upto);
    }
  }

  // Eventually, make this depend on the canvas size and hence on how much welly the phone has
  // For a 400x240 screen we need 6 tiles for the whole screen or 4 for half the screen
  // so this will deal with 3 whole pans
  static private final int SIZE = 12;

  static private Entry [] front;
  static private int next;
  static private Entry [] back;
  static int draw_cycle;

  static private Paint gray_paint;
  static Paint trail_paint;
  static Paint trail_dot_paint_0;
  static Paint trail_dot_paint_1;
  static final float TRAIL_DOT_SIZE = 2.0f;

  static private Handler mHandler;

  // -----------

  static void init () {
    front = new Entry[SIZE];
    next = 0;
    back = null;

    draw_cycle = 0;

    gray_paint = new Paint();
    gray_paint.setColor(Color.GRAY);
    trail_paint = new Paint();
    trail_paint.setColor(Color.argb(56, 0x6d, 0, 0xb0));
    trail_paint.setStyle(Paint.Style.FILL);
    trail_dot_paint_0 = new Paint();
    trail_dot_paint_0.setColor(Color.argb(255, 0, 0, 0));
    trail_dot_paint_0.setStyle(Paint.Style.FILL);
    trail_dot_paint_1 = new Paint();
    trail_dot_paint_1.setColor(Color.argb(255, 255, 255, 255));
    trail_dot_paint_1.setStyle(Paint.Style.FILL);

    mHandler = new Handler();
  }

  // -----------

  private static class TilingResponse implements Runnable {
    private String message;

    public TilingResponse(String _message) {
      message = _message;
    }

    @Override
    public void run() {
      Log.i(TAG, message);
    }

  }

  private static class TilingThread extends Thread {
    private int zoom;
    private int x;
    private int y;
    private int map_source;
    private String message;


    public TilingThread(int _zoom, int _x, int _y, int _map_source) {
      zoom = _zoom;
      x = _x;
      y = _y;
      map_source = _map_source;
    }

    @Override
    public void run () {
      message = String.format("Tile in thread: z=%d x=%d y=%d m=%d",
          zoom, x, y, map_source);
      mHandler.post (new TilingResponse(message));
    }

  }


  // -----------
  // Internal
  //

  static void render_dot(Canvas c, float px, float py, int parity) {
    c.drawCircle(px, py, Trail.splot_radius, trail_paint);
    if (parity == 1) {
      c.drawCircle(px, py, TRAIL_DOT_SIZE, trail_dot_paint_1);
    } else {
      c.drawCircle(px, py, TRAIL_DOT_SIZE, trail_dot_paint_0);
    }
  }

  static private void render_old_trail(Bitmap bm, int zoom, int tile_x, int tile_y) {
    int pixel_shift = (Merc28.shift - (zoom + bm_log_size));
    int tile_shift = (Merc28.shift - zoom);
    int xnw = tile_x << tile_shift;
    int ynw = tile_y << tile_shift;
    int parity = 0;
    Canvas my_canv = new Canvas(bm);
    Trail.PointArray pa = Logger.mTrail.get_historical();
    int last_x = 0, last_y = 0;
    for (int i = 0; i < pa.n; i++) {
      int px = (pa.x[i] - xnw) >> pixel_shift;
      int py = (pa.y[i] - ynw) >> pixel_shift;
      boolean do_add = true;
      if (i > 0) {
        int manhattan = Math.abs(px - last_x) + Math.abs(py - last_y);
        if (manhattan < Trail.splot_gap) {
          do_add = false;
        }
      }
      if (do_add) {
        render_dot(my_canv, px, py, parity);
        parity = parity ^ 1;
        last_x = px;
        last_y = py;
      }
    }
  }

  static private TilingThread make_tiling_thread(int zoom, int x, int y, int map_source) {
    return new TilingThread(zoom, x, y, map_source);
  }

  static private Bitmap render_bitmap(int zoom, int map_source, int x, int y) {
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
      case Map.MAP_TODO:
        filename = String.format("/sdcard/Maverick/tiles/logmygsm_todo/%d/%d/%d.png.tile",
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
    make_tiling_thread(zoom, x, y, map_source).start();
    File file = new File(filename);
    Bitmap bm;
    if (file.exists()) {
      Bitmap temp_bm = BitmapFactory.decodeFile(filename);
      bm = temp_bm.copy(Bitmap.Config.ARGB_8888, true);
    } else {
      bm = Bitmap.createBitmap(bm_size, bm_size, Bitmap.Config.ARGB_8888);
      Canvas my_canv = new Canvas(bm);
      my_canv.drawRect(0, 0, bm_size, bm_size, gray_paint);
    }
    // TODO : Draw trail points into the bitmap
    render_old_trail(bm, zoom, x, y);
    return bm;
  }

  static private Entry make_entry(int zoom, int map_source, int x, int y, Bitmap b) {
    return new Entry(zoom, map_source, x, y, b);
  }

  static private Entry lookup(int zoom, int map_source, int x, int y) {
    // front should never be null
    for (int i=next-1; i>=0; i--) {
      if (front[i].isMatch(zoom, map_source, x, y)) {
        return front[i];
      }
    }
    // Miss.  Any space left?
    if (next == SIZE) {
      // No.  Dump old junk
      back = front;
      // Might consider garbage collection here?
      front = new Entry[SIZE];
      next = 0;
      //Log.i(TAG, "Flushed tile store");
    }

    // Search 'back' array for a match.  'back' is either null, or full
    if (back != null) {
      for (int i=SIZE-1; i>=0; i--) {
        if (back[i].isMatch(zoom, map_source, x, y)) {
          front[next++] = back[i];
          return back[i];
        }
      }
    }

    // OK, no match.  We have to build a new bitmap from file
    Bitmap b = render_bitmap(zoom, map_source, x, y);
    Entry e = make_entry(zoom, map_source, x, y, b); // auto touch
    front[next++] = e;
    return e;

  }

  static private void ripple() {
    // gravitate entries in 'front' towards the end that's checked first
    for (int i=1; i<next; i++) {
      if (front[i-1].cycle > front[i].cycle) {
        // swap two entries over
        Entry t = front[i];
        front[i] = front[i-1];
        front[i-1] = t;
      }
    }
  }

  // -----------
  // Interface with map

  static void invalidate() {
    front = new Entry[SIZE];
    next = 0;
    back = null;
  }

  static void semi_invalidate() {
    back = null;
    System.gc();
  }

  static void draw(Canvas c, int w, int h, int zoom, int map_source, Merc28 midpoint) {
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

    // This is used in maintaining the cache so that the most recently used
    // entries are the ones hit first in the search.
    draw_cycle++;

    int i, j;
    i = 0;
    while (ox + (i<<bm_log_size) < w) {
      int xx = ox + (i<<bm_log_size);
      j = 0;
      while (oy + (j<<bm_log_size) < h) {
        int yy = oy + (j<<bm_log_size);
        Entry e = lookup(zoom, map_source, tx+i, ty+j);
        e.add_recent_trail();
        e.touch();
        Bitmap bm = e.getBitmap();
        Rect dest = new Rect(xx, yy, xx+bm_size, yy+bm_size);
        c.drawBitmap(bm, null, dest, null);
        j++;
      }
      i++;
    }

    ripple();

  }

}


// vim:et:sw=2:sts=2

