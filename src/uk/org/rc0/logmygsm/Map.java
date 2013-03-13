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

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Integer;
import java.lang.Math;
import java.lang.NumberFormatException;
import java.util.LinkedList;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.util.Log;

public class Map extends View {

  private final Paint red_paint;
  private final Paint red_stroke_paint;
  private final Paint red_double_stroke_paint;
  private final Paint button_stroke_paint;

  static final private String TAG = "Map";
  static final private int MAX_ZOOM = 18;
  static final private int MIN_ZOOM = 5;

  private int zoom;
  private int pixel_shift;
  private int tile_shift;
  private float drag_scale;

  private int last_w, last_h;

  private MapSource map_source;

  // the GPS fix from the logger
  private Merc28 estimated_pos;

  // the location at the centre of the screen - may be != estimated_pos if is_dragged is true.
  private Merc28 display_pos;
  // Set to true if we've off-centred the map
  private boolean is_dragged;

  // --------------------------------------------------------------------------

  public interface PositionListener {
    void display_position_update();
  }

  private PositionListener mPL;

  void register_position_listener(PositionListener pl) {
    mPL = pl;
  }

  // --------------------------------------------------------------------------

  public Map(Context context, AttributeSet attrs) {
    super(context, attrs);

    red_paint = new Paint();
    red_paint.setStrokeWidth(2);
    red_paint.setColor(Color.RED);

    red_stroke_paint = new Paint();
    red_stroke_paint.setStrokeWidth(2);
    red_stroke_paint.setColor(Color.RED);
    red_stroke_paint.setStyle(Paint.Style.STROKE);

    red_double_stroke_paint = new Paint();
    red_double_stroke_paint.setStrokeWidth(4);
    red_double_stroke_paint.setColor(Color.RED);
    red_double_stroke_paint.setStyle(Paint.Style.STROKE);

    button_stroke_paint = new Paint();
    button_stroke_paint.setStrokeWidth(4);
    button_stroke_paint.setColor(Color.BLACK);
    button_stroke_paint.setStyle(Paint.Style.STROKE);

    setZoom(14);
    map_source = MapSources.get_default();
    estimated_pos = null;
    display_pos = null;

    last_w = last_h = 0;

  }

  // Main drawing routines...

  private float len1, len2, len3;

  private int button_half_line;
  private int button_radius;
  private int button_size;

  private void set_lengths(int width, int height) {
    int t;
    t = (width + height) >> 1;
    button_half_line = (t>>4) - (t>>6); // approx 12 * (t/240)
    button_radius = (t>>4); // approx 16 * (t/240)
    button_size = (t>>2) - (t>>3) + (t>>5); // approx 40 * (t/240)
    len1 = (float)(t>>5); // approx 8 * (t/240)
    len2 = (float)(t>>2); // approx 64 * (t/240)
    len3 = (float)((t+t+t)>>5); // approx 3*len1
  }

  private void draw_crosshair(Canvas c, int w, int h) {
    if (estimated_pos != null) {
      float x0, x1, x2, x3, xc;
      float y0, y1, y2, y3, yc;

      xc = (float)(w/2);
      yc = (float)(h/2);
      if (is_dragged) {
        int dx = (estimated_pos.X - display_pos.X) >> pixel_shift;
        int dy = (estimated_pos.Y - display_pos.Y) >> pixel_shift;
        xc += (float) dx;
        yc += (float) dy;
      }
      x0 = xc - (len1 + len2);
      x1 = xc - (len1);
      x2 = xc + (len1);
      x3 = xc + (len1 + len2);
      y0 = yc - (len1 + len2);
      y1 = yc - (len1);
      y2 = yc + (len1);
      y3 = yc + (len1 + len2);
      c.drawLine(x0, yc, x1, yc, red_double_stroke_paint);
      c.drawLine(x2, yc, x3, yc, red_double_stroke_paint);
      c.drawLine(xc, y0, xc, y1, red_double_stroke_paint);
      c.drawLine(xc, y2, xc, y3, red_double_stroke_paint);
    }
  }

  private void draw_centre_circle(Canvas c, int w, int h) {
    float x0, x1, x2, x3, xc;
    float y0, y1, y2, y3, yc;
    xc = (float)(w/2);
    yc = (float)(h/2);
    x0 = xc - len3;
    x1 = xc - len1;
    x2 = xc + len1;
    x3 = xc + len3;
    y0 = yc - len3;
    y1 = yc - len1;
    y2 = yc + len1;
    y3 = yc + len3;
    c.drawCircle(xc, yc, len3, red_stroke_paint);
    c.drawLine(x0, yc, x1, yc, red_paint);
    c.drawLine(x2, yc, x3, yc, red_paint);
    c.drawLine(xc, y0, xc, y1, red_paint);
    c.drawLine(xc, y2, xc, y3, red_paint);
  }

  void draw_bearing(Canvas c, int w, int h) {
    if (Logger.validFix) {
      c.save();
      c.rotate((float) Logger.lastBearing, (w>>1), (h>>1));
      float xl = (float)(w>>1) - (1.0f*len1);
      float xc = (float)(w>>1);
      float xr = (float)(w>>1) + (1.0f*len1);
      float yb = (float)(h>>1) - (len3 + (0.5f*len1));
      float yt = (float)(h>>1) - (len3 + (3.5f*len1));
      c.drawLine(xc, yt, xl, yb, red_double_stroke_paint);
      c.drawLine(xc, yt, xr, yb, red_double_stroke_paint);
      c.drawLine(xl, yb, xr, yb, red_double_stroke_paint);
      c.restore();
    }
  }

  private void draw_buttons(Canvas c, int w, int h) {
    int button_offset = button_radius + (button_radius >> 1);
    // draw plus
    c.drawCircle(button_offset, button_offset, button_radius, button_stroke_paint);
    c.drawLine(button_offset - button_half_line, button_offset,
        button_offset + button_half_line, button_offset,
        button_stroke_paint);
    c.drawLine(button_offset, button_offset - button_half_line,
        button_offset, button_offset + button_half_line,
        button_stroke_paint);
    // draw minus
    c.drawCircle(w - button_offset, button_offset, button_radius, button_stroke_paint);
    c.drawLine(w - button_offset - button_half_line, button_offset,
        w - button_offset + button_half_line, button_offset,
        button_stroke_paint);
  }

  private void redraw_map(Canvas canvas) {
    int width = getWidth();
    int height = getHeight();

    last_w = width;
    last_h = height;

    TileStore.draw(canvas, width, height, zoom, map_source, display_pos);

    set_lengths(width, height);
    draw_crosshair(canvas, width, height);
    draw_centre_circle(canvas, width, height);
    draw_buttons(canvas, width, height);
    draw_bearing(canvas, width, height);
    Logger.mMarks.draw(canvas, display_pos, width, height, pixel_shift);
    if (TowerLine.is_active()) {
      TowerLine.draw_line(canvas, width, height, pixel_shift, display_pos);
    }
  }

  // Interface with main UI activity

  void update_map() {
    // This try-catch shouldn't be necessary.
    // What is the real bug?
    try {
      estimated_pos = Logger.mTrail.get_estimated_position();
    } catch (NullPointerException n) {
      estimated_pos = null;
    }
    if ((estimated_pos != null) &&
        ((display_pos == null) || !is_dragged)) {
      display_pos = new Merc28(estimated_pos);
    }
    invalidate();
  }

  void select_map_source(MapSource which) {
    map_source = which;
    invalidate();
  }

  private void setZoom(int z) {
    //tile_cache.setZoom(z);
    zoom = z;
    pixel_shift = Merc28.shift - (z+8);
    tile_shift = Merc28.shift - (z);
    drag_scale = (float)(1 << pixel_shift);
  }

  // Yes, we should use the Android preferences system for this.
  // Saving to SD card allows us to edit the file offline etc
  // for debugging and so on
  void restore_state_from_file(String tail) {
    File file = new File("/sdcard/LogMyGsm/prefs/" + tail);
    // defaults in case of strife
    display_pos = new Merc28(54.5, -2.0); // in the wilderness
    setZoom(14);
    map_source = MapSources.get_default();
    if (file.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        line = br.readLine();
        setZoom(Integer.parseInt(line));
        int x, y;
        line = br.readLine();
        x = Integer.parseInt(line);
        line = br.readLine();
        y = Integer.parseInt(line);
        // If we survive unexcepted to here we parsed the file OK
        display_pos = new Merc28(x, y);
        line = br.readLine();
        map_source = MapSources.lookup(Integer.parseInt(line));
        if (map_source == null) {
          map_source = MapSources.get_default();
        }
        br.close();
      } catch (IOException e) {
      } catch (NumberFormatException n) {
      }
    }
  }

  void save_state_to_file(String tail) {
    if (display_pos != null) {
      File dir = new File("/sdcard/LogMyGsm/prefs");
      if (!dir.exists()) {
        dir.mkdirs();
      }
      File file = new File(dir, tail);
      try {
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(String.format("%d\n", zoom));
        bw.write(String.format("%d\n", display_pos.X));
        bw.write(String.format("%d\n", display_pos.Y));
        bw.write(String.format("%d\n", map_source.get_code()));
        bw.close();
      } catch (IOException e) {
      }
    }
  }

  private void notify_position_update() {
    if (mPL != null) {
      mPL.display_position_update();
    }
  }

  String current_tile_string() {
    int X, Y;
    if (display_pos != null) {
      X = display_pos.X >> tile_shift;
      Y = display_pos.Y >> tile_shift;
    } else {
      X = 0;
      Y = 0;
    }
    if (zoom >= 17) {
      int x0 = X / 10000;
      int y0 = Y / 10000;
      int x1 = X % 10000;
      int y1 = Y % 10000;
      char xc = (char)('@' + x0);
      char yc = (char)('@' + y0);
      return String.format("%1c%04d%1c%04d",
          xc, x1, yc, y1);
    } else {
      return String.format("%5d%5d", X, Y);
    }
  }

  String current_grid_ref() {
    if (display_pos != null) {
      return display_pos.grid_ref_5m_nosp();
    } else {
      return "NO POSITION";
    }
  }

  void trigger_fetch_around(int delta, Context context) {
    if (display_pos != null) {
      int i, j;
      int x0, x1, y0, y1;
      LinkedList<TileStore.TilePos> targets;
      targets = new LinkedList<TileStore.TilePos> ();
      x0 = display_pos.X >> tile_shift;
      y0 = display_pos.Y >> tile_shift;
      x1 = x0 + delta;
      y1 = y0 + delta;
      x0 -= delta;
      y0 -= delta;

      for (i=x0; i<=x1; i++) {
        for (j=y0; j<=y1; j++) {
          targets.add(new TileStore.TilePos(zoom, i, j, map_source));
        }
      }
      Downloader.start_multiple_fetch(targets, true, context);
    }
  }

  // Considering the area in view on-screen, consider that same area
  // at zoom+1, zoom+2, ..., zoom+levels, and download any missing
  // map tiles in those regions.  If forced=true, download them again
  // whether they're missing or not
  void trigger_fetch_tree(int levels, boolean forced, Context context) {
    int dw = (last_w >> 1);
    int dh = (last_h >> 1);
    LinkedList<TileStore.TilePos> tiles;
    tiles = new LinkedList<TileStore.TilePos> ();
    if (display_pos == null) {
      return;
    }
    int px0 = display_pos.X - (dw << pixel_shift);
    int px1 = display_pos.X + (dw << pixel_shift);
    int py0 = display_pos.Y - (dh << pixel_shift);
    int py1 = display_pos.Y + (dh << pixel_shift);
    for (int dz=0; dz<=levels; dz++) {
      int z = zoom + dz;
      if (z <= MAX_ZOOM) {
        int tile_shift = Merc28.shift - z;
        int tx0 = (px0 >> tile_shift);
        int tx1 = (px1 >> tile_shift);
        int ty0 = (py0 >> tile_shift);
        int ty1 = (py1 >> tile_shift);
        // Log.i(TAG, "Got z=" + z + " tx0=" + tx0 + " ty0=" + ty0 + " tx1=" + tx1 + " ty1=" + ty1);
        for (int x=tx0; x<=tx1; x++) {
          for (int y=ty0; y<=ty1; y++) {
            tiles.add(new TileStore.TilePos(z, x, y, map_source));
            // Log.i(TAG, "Added z=" + z + " x=" + x + " y=" + y);
          }
        }
      }
    }
    Downloader.start_multiple_fetch(tiles, false, context);
  }

  void share_grid_ref(Activity _activity) {
    if (display_pos != null) {
      try {
        Intent the_intent = new Intent();
        the_intent.setAction(Intent.ACTION_SEND);
        the_intent.setType("text/plain");
        the_intent.putExtra(Intent.EXTRA_TEXT,
            "At " + display_pos.grid_ref_5m_nosp() + " : ");
        _activity.startActivity(Intent.createChooser(the_intent, "Share grid ref using"));
      } catch (Exception e) {
      }
    }
  }

  // Local UI callbacks

  void clear_trail() {
    Logger.mTrail.clear();
    TileStore.invalidate();
    invalidate();
  }

  void zoom_out() {
    if (zoom > MIN_ZOOM) {
      setZoom(zoom - 1);
      notify_position_update();
      invalidate();
    }
  }

  void zoom_in() {
    if (zoom < MAX_ZOOM) {
      setZoom(zoom + 1);
      notify_position_update();
      invalidate();
    }
  }

  private float mLastX;
  private float mLastY;

  private boolean try_zoom(float x, float y) {
    if (y < button_size) {
      if (x < button_size) {
        zoom_in();
        return true;
      } else if (x > (getWidth() - button_size)) {
        zoom_out();
        return true;
      }
    }
    return false;
  }

  private boolean check_zoom(float x, float y) {
    if (y < button_size) {
      if (x < button_size) {
        return true;
      } else if (x > (getWidth() - button_size)) {
        return true;
      }
    }
    return false;
  }

  private boolean try_recentre(float x, float y) {
    // Hit on the centre cross-hair region to re-centre the map on the GPS fix
    if ((y > ((getHeight() - button_size)>>1)) &&
        (y < ((getHeight() + button_size)>>1)) &&
        (x > ((getWidth()  - button_size)>>1)) &&
        (x < ((getWidth()  + button_size)>>1))) {
      // If (estimated_pos == null) here, it means no history of recent GPS fixes;
      // refuse to drop the display position then
      if (estimated_pos != null) {
        display_pos = new Merc28(estimated_pos);
        is_dragged = false;
        notify_position_update();
        invalidate();
        return true;
      } else {
        Merc28 tower_pos = new Merc28(0,0);
        if (TowerLine.find_tower_pos(0, tower_pos)) {
          display_pos = tower_pos;
          is_dragged = false;
          notify_position_update();
          invalidate();
          return true;
        }
      }
    }
    return false;
  }

  private boolean try_start_drag(float x, float y) {
    // Not inside the zoom buttons - initiate drag
    if (display_pos != null) {
      mLastX = x;
      mLastY = y;
      is_dragged = true;
      return true;
    }
    return false;
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        if (try_zoom(x, y)) {
          return true;
        }
        if (try_recentre(x, y)) {
          return true;
        }
        if (try_start_drag(x, y)) {
          return true;
        }
        break;
      case MotionEvent.ACTION_MOVE:
        // Prevent a drag starting on a zoom button etc, which would be bogus.
        if (is_dragged && !check_zoom(x, y)) {
          float dx, dy;
          dx = x - mLastX;
          dy = y - mLastY;
          display_pos.X -= (int)(0.5 + drag_scale * dx);
          display_pos.Y -= (int)(0.5 + drag_scale * dy);
          mLastX = x;
          mLastY = y;
          notify_position_update();
          invalidate();
          return true;
        }
        break;
      default:
        return false;
    }
    return false;
  }

  // Called by framework

  @Override
  protected void onDraw(Canvas canvas) {

    if (display_pos == null) {
      canvas.drawColor(Color.rgb(40,40,40));
      String foo2 = String.format("No fix");
      canvas.drawText(foo2, 10, 80, red_paint);
    } else {
      redraw_map(canvas);
    }
  }

  double da_offset_metres () {
    if ((estimated_pos != null) && (display_pos != null)) {
      return display_pos.metres_away(estimated_pos);
    } else {
      return 0;
    }
  }

  // -----------

  class TowerOffset {
    public boolean known;
    public boolean dragged;
    public double metres;
    public double bearing;
  };

  TowerOffset get_tower_offset () {
    TowerOffset result = new TowerOffset();
    if (display_pos == null) {
      result.known = false;
    } else {
      Merc28 tower_pos = new Merc28(0,0);
      if (TowerLine.find_tower_pos(0, tower_pos)) {
        result.metres = display_pos.metres_away(tower_pos);
        result.bearing = display_pos.bearing_to(tower_pos);
        result.known = true;
        result.dragged = is_dragged;
      } else {
        result.known = false;
      }
    }
    return result;
  }

  // -----------


  void add_landmark() {
    Logger.mMarks.add(display_pos);
    invalidate();
  }

  void delete_landmark() {
    if (Logger.mMarks.delete(display_pos, pixel_shift)) {
      invalidate();
    }
  }

  void delete_visible_landmarks() {
    if (Logger.mMarks.delete_visible(display_pos, pixel_shift, getWidth(), getHeight() )) {
      invalidate();
    }
  }

  void delete_all_landmarks() {
    Logger.mMarks.delete_all();
    invalidate();
  }

}

// vim:et:sw=2:sts=2
//

