// Copyright (c) 2012, 2013, Richard P. Curnow
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import java.util.LinkedList;

class TileStore {

  static final private int bm_log_size = 8;
  static final private int bm_size = 1<<bm_log_size;

  static final private String TAG = "TileStore";
  static final private boolean do_log = false;

  // -----------
  // State

  static class TilePos {
    int zoom;
    int x;
    int y;
    MapSource map_source;

    TilePos(int _zoom, int _x, int _y, MapSource _map_source) {
      zoom = _zoom;
      x = _x;
      y = _y;
      map_source = _map_source;
    }

    TilePos(TilePos old) {
      zoom = old.zoom;
      x = old.x;
      y = old.y;
      map_source = old.map_source;
    }

    boolean isMatch(int _zoom, int _x, int _y, MapSource _map_source) {
      if ((_zoom == zoom) &&
          (_map_source.get_code() == map_source.get_code()) &&
          (_x == x) &&
          (_y == y)) {
        return true;
      } else {
        return false;
      }
    }

  };

  private static class Entry extends TilePos {
    int pixel_shift;
    int tile_shift;
    int cycle;
    private Trail.Upto upto;
    boolean is_dummy;
    Bitmap b;

    Bitmap getBitmap() {
      return b;
    }

    Entry(int _zoom, MapSource _map_source, int _x, int _y, Bitmap _b, boolean _is_dummy) {
      super(_zoom, _x, _y, _map_source);
      pixel_shift = (Merc28.shift - (zoom + bm_log_size));
      tile_shift = (Merc28.shift - zoom);
      b = _b;
      is_dummy = _is_dummy;
      upto = new Trail.Upto ();
      touch();
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

  static private int last_w, last_h;
  static private Entry [] front;
  static private int next;
  static private Entry [] back;
  static int draw_cycle;

  static private Paint gray_paint;
  static private Paint light_gray_paint;
  static Paint trail_paint;
  static Paint trail_dot_paint_0;
  static Paint trail_dot_paint_1;
  static final float TRAIL_DOT_SIZE = 2.0f;

  static private Handler mHandler;

  static private LinkedList<TilePos> bg_queue;
  static private Bitmap loading_bitmap;
  static private Context mContext;

  static private long start_time;
  static Paint highlight_border_paint;
  final static private int HIGHLIGHT_WIDTH = 16;

  // -----------

  static Entry [] new_cache() {
    int ww = (last_w >> 8) + 2;
    int hh = (last_h >> 8) + 2;
    int tt = ww * hh;
    // oversize by 50% to give a little pan space around edges
    tt += (tt >> 1);
    if (do_log) { Log.i(TAG, "New cache w=" + ww + " h=" + hh + " tt=" + tt); }
    return new Entry[tt];
  }

  static void init (Context the_app_context) {

    refresh_epoch();

    mContext = the_app_context;
    last_w = 240;
    last_h = 400;
    front = new_cache();
    next = 0;
    back = null;

    draw_cycle = 0;

    gray_paint = new Paint();
    gray_paint.setColor(Color.GRAY);
    light_gray_paint = new Paint();
    light_gray_paint.setColor(Color.argb(255, 0xa0, 0xa0, 0xa0));
    trail_paint = new Paint();
    trail_paint.setColor(Color.argb(56, 0x6d, 0, 0xb0));
    trail_paint.setStyle(Paint.Style.FILL);
    trail_dot_paint_0 = new Paint();
    trail_dot_paint_0.setColor(Color.argb(255, 0, 0, 0));
    trail_dot_paint_0.setStyle(Paint.Style.FILL);
    trail_dot_paint_1 = new Paint();
    trail_dot_paint_1.setColor(Color.argb(255, 255, 255, 255));
    trail_dot_paint_1.setStyle(Paint.Style.FILL);

    highlight_border_paint = new Paint();
    highlight_border_paint.setColor(Color.argb(32,0x00,0x00,0xff));
    highlight_border_paint.setStyle(Paint.Style.STROKE);
    highlight_border_paint.setStrokeWidth(HIGHLIGHT_WIDTH);
    highlight_border_paint.setStrokeCap(Paint.Cap.SQUARE);

    mHandler = new Handler();
    bg_queue = new LinkedList<TilePos> ();

    loading_bitmap = Bitmap.createBitmap(bm_size, bm_size, Bitmap.Config.ARGB_8888);
    Canvas my_canv = new Canvas(loading_bitmap);
    my_canv.drawRect(0, 0, bm_size, bm_size, gray_paint);
    my_canv.drawRect(bm_size>>3, bm_size>>3,
        bm_size - (bm_size>>3), bm_size - (bm_size>>3),
        light_gray_paint);
  }

  // -----------

  private static class TilingResponse implements Runnable {
    private Bitmap bm;
    private boolean is_dummy;

    public TilingResponse(Bitmap _bm, boolean _is_dummy) {
      bm = _bm;
      is_dummy = _is_dummy;
    }

    @Override
    public void run() {
      check_full();
      TilePos tp = bg_queue.remove(); // head of list
      Entry e = make_entry(tp.zoom, tp.map_source, tp.x, tp.y, bm, is_dummy);
      if (do_log) { Log.i(TAG, "Putting load response at position " + next); }
      front[next++] = e;

      if (bg_queue.size() > 0) {
        tp = bg_queue.getFirst();
        if (do_log) { Log.i(TAG, "Start next job, queue size is " + bg_queue.size()); }
        (new TilingThread(tp.zoom, tp.x, tp.y, tp.map_source)).start();
      } else {
        // Last job done.  Force map redraw
        Intent intent = new Intent(Logger.UPDATE_GPS);
        mContext.sendBroadcast(intent);
      }
    }
  }

  private static class TilingThread extends Thread {
    private int zoom;
    private int x;
    private int y;
    private MapSource map_source;

    public TilingThread(int _zoom, int _x, int _y, MapSource _map_source) {
      zoom = _zoom;
      x = _x;
      y = _y;
      map_source = _map_source;
      //setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run () {
      TilingResponse resp = render_bitmap(zoom, map_source, x, y);
      mHandler.post (resp);
      resp = null;
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

  static private void render_highlight_border(Bitmap bm)
  {
    Canvas my_canv = new Canvas(bm);
    int hw, hw2;
    hw = HIGHLIGHT_WIDTH - (HIGHLIGHT_WIDTH>>4);
    hw2 = 256 - hw;
    my_canv.drawLine(hw, hw, hw2, hw, highlight_border_paint);
    my_canv.drawLine(hw, hw2, hw2, hw2, highlight_border_paint);
    my_canv.drawLine(hw, hw, hw, hw2, highlight_border_paint);
    my_canv.drawLine(hw2, hw, hw2, hw2, highlight_border_paint);
  }

  static private void render_coarse_zoom_cross(Bitmap bm)
  {
    Canvas my_canv = new Canvas(bm);
    int hw, hw2;
    hw = HIGHLIGHT_WIDTH - (HIGHLIGHT_WIDTH>>4);
    hw2 = 256 - hw;
    my_canv.drawLine(hw, hw, hw2, hw2, highlight_border_paint);
    my_canv.drawLine(hw, hw2, hw2, hw, highlight_border_paint);
  }

  static private TilingResponse render_bitmap(int zoom, MapSource map_source, int x, int y) {
    String filename = null;
    filename = map_source.get_tile_path(zoom, x, y);
    File file = new File(filename);
    Bitmap bm = null;
    boolean is_dummy = false;
    try {
      if (file.exists()) {
        Bitmap temp_bm = BitmapFactory.decodeFile(filename);
        bm = temp_bm.copy(Bitmap.Config.ARGB_8888, true);

        if (file.lastModified() > start_time) {
          render_highlight_border(bm);
        }
      } else {
        // Try to use a sub-region of a zoomed out bitmap instead
        // NOTE : this could be modified so that the above 'then' clause is the initial iteration,
        // but that would introduce extra bitmap copy operations to the normal case
        int x0, y0; // top corner of source bitmap
        int swh;    // sub width/height
        int tx, ty; // tile coords
        tx = x;
        ty = y;
        swh = bm_size;
        x0 = y0 = 0;
        for (int parent=1; parent<=3; parent++) {
          int local_zoom = zoom - parent;
          swh >>= 1;
          x0 = ((tx & 1) << 7) + (x0 >> 1);
          y0 = ((ty & 1) << 7) + (y0 >> 1);
          tx >>= 1;
          ty >>= 1;
          filename = map_source.get_tile_path(local_zoom, tx, ty);
          file = new File(filename);
          if (file.exists()) {
            Bitmap temp_bm = BitmapFactory.decodeFile(filename);
            Bitmap ancestor_bm = temp_bm.copy(Bitmap.Config.ARGB_8888, true);
            Bitmap sub_bm = Bitmap.createBitmap(ancestor_bm, x0, y0, swh, swh);
            bm = Bitmap.createScaledBitmap(sub_bm, bm_size, bm_size, false);
            render_coarse_zoom_cross(bm);
            if (file.lastModified() > start_time) {
              render_highlight_border(bm);
            }
            break;
          }
        }
      }

    } catch (Exception e) {
      // to deal with corrupt tile files and such horrors
    }
    if (bm == null) {
      bm = Bitmap.createBitmap(bm_size, bm_size, Bitmap.Config.ARGB_8888);
      Canvas my_canv = new Canvas(bm);
      my_canv.drawRect(0, 0, bm_size, bm_size, gray_paint);
      is_dummy = true;
    }
    // TODO : Draw trail points into the bitmap
    render_old_trail(bm, zoom, x, y);
    return new TilingResponse(bm, is_dummy);
  }

  static private void start_bg_load(int zoom, int x, int y, MapSource map_source) {
    // Check if this job is already on the queue
    int i;
    for (i=0; i<bg_queue.size(); i++) {
      if (bg_queue.get(i).isMatch(zoom, x, y, map_source)) {
        return;
      }
    }
    // Not already in the queue of tiles to render.  Let's go....
    bg_queue.add(new TilePos(zoom, x, y, map_source));
    if (bg_queue.size() == 1) {
      // We've just queued the 1st piece of work: kick off the bg stuff
      if (do_log) { Log.i(TAG, "Starting first bg load op"); }
      (new TilingThread(zoom, x, y, map_source)).start();
    }
  }

  static private Entry make_entry(int zoom, MapSource map_source, int x, int y, Bitmap b, boolean is_dummy) {
    return new Entry(zoom, map_source, x, y, b, is_dummy);
  }

  static private void check_full () {
    if (next == front.length) {
      back = front;
      front = new_cache();
      next = 0;
      if (do_log) { Log.i(TAG, "Flushed tile store"); }
    }
  }

  static private Entry lookup(int zoom, MapSource map_source, int x, int y) {
    // front should never be null
    for (int i=next-1; i>=0; i--) {
      if (front[i].isMatch(zoom, x, y, map_source)) {
        return front[i];
      }
    }
    // Miss. Match in back?
    Entry back_match = null;
    if (back != null) {
      for (int i=back.length-1; i>=0; i--) {
        if (back[i].isMatch(zoom, x, y, map_source)) {
          if (do_log) { Log.i(TAG, "Back match found at " + i); }
          back_match = back[i];
          break;
        }
      }
    }
    if (back_match != null) {
      check_full();
      front[next++] = back_match;
      return back_match;
    } else {
      // Full miss.
      start_bg_load(zoom, x, y, map_source);
      return null;
    }

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
    // Get rid of cache when changes occur in tiles - to force reload
    front = new_cache();
    next = 0;
    back = null;
    // Todo : drop all bar first entry in bg_queue ?
  }

  static void sleep_invalidate() {
    // Get rid of cache to free up memory when activities exit
    front = new_cache();
    next = 0;
    back = null;
    System.gc();
  }

  static void draw(Canvas c, int w, int h, int zoom, MapSource map_source, Merc28 midpoint) {
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

    // These are used to size the new 'front' cache when it gets re-allocated
    last_w = w;
    last_h = h;

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
        Bitmap bm;
        if (e != null) {
          e.add_recent_trail();
          e.touch();
          bm = e.getBitmap();
        } else {
          bm = loading_bitmap;
        }
        Rect dest = new Rect(xx, yy, xx+bm_size, yy+bm_size);
        c.drawBitmap(bm, null, dest, null);
        j++;
      }
      i++;
    }

    ripple();

  }

  static void trigger_fetch(Context context) {
    LinkedList<TilePos> targets;
    targets = new LinkedList<TilePos> ();
    for (int i=0; i<next; i++) {
      if (front[i].is_dummy) {
        targets.add(new TilePos(front[i]));
      }
    }
    if (back != null) {
      for (int i=0; i<back.length; i++) {
        if (back[i].is_dummy) {
          targets.add(new TilePos(back[i]));
        }
      }
    }

    Downloader.start_multiple_fetch(targets, true, context);
    targets = null;
  }

  static long get_epoch() {
    return start_time;
  }

  static void refresh_epoch() {
    start_time = System.currentTimeMillis();
  }

}


// vim:et:sw=2:sts=2

