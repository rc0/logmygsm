package uk.org.rc0.helloandroid;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Integer;
import java.lang.Math;
import java.lang.NumberFormatException;
import android.content.Context;
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
  public  final Paint trail_paint;
  private final Paint button_stroke_paint;
  private final Paint grey_paint;

  static final public int MAP_2G  = 100;
  static final public int MAP_3G  = 101;
  static final public int MAP_MAPNIK = 102;
  static final public int MAP_OS  = 103;
  static final public int MAP_OPEN_CYCLE = 104;

  static final private String TAG = "Map";

  private int zoom;
  private int pixel_shift;
  private float drag_scale;
  private TileCache tile_cache;

  // the GPS fix from the logger
  private Merc28 actual_pos;
  private Merc28 last_actual_pos;
  private Merc28 estimated_pos;

  // the location at the centre of the screen - may be != actual_pos if is_dragged is true.
  private Merc28 display_pos;
  // Set to true if we've off-centred the map
  private boolean is_dragged;

  // --------------------------------------------------------------------------

  public Map(Context context, AttributeSet attrs) {
    super(context, attrs);

    tile_cache = new TileCache(this);

    red_paint = new Paint();
    red_paint.setStrokeWidth(1);
    red_paint.setColor(Color.RED);

    red_stroke_paint = new Paint();
    red_stroke_paint.setStrokeWidth(1);
    red_stroke_paint.setColor(Color.RED);
    red_stroke_paint.setStyle(Paint.Style.STROKE);

    red_double_stroke_paint = new Paint();
    red_double_stroke_paint.setStrokeWidth(2);
    red_double_stroke_paint.setColor(Color.RED);
    red_double_stroke_paint.setStyle(Paint.Style.STROKE);

    trail_paint = new Paint();
    trail_paint.setColor(Color.argb(128, 0x8d, 0, 0xcf));
    trail_paint.setStyle(Paint.Style.FILL);

    button_stroke_paint = new Paint();
    button_stroke_paint.setStrokeWidth(2);
    button_stroke_paint.setColor(Color.BLACK);
    button_stroke_paint.setStyle(Paint.Style.STROKE);

    grey_paint = new Paint();
    grey_paint.setStrokeWidth(2);
    grey_paint.setColor(Color.GRAY);

    setZoom(14);
    tile_cache.setMapSource(MAP_2G);
    actual_pos = null;
    last_actual_pos = null;
    estimated_pos = null;
    display_pos = null;

  }

  // Main drawing routines...

  final static float LEN1 = 8.0f;
  final static float LEN2 = 64.0f;
  final static float LEN3 = 3.0f * LEN1;

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
      x0 = xc - (LEN1 + LEN2);
      x1 = xc - (LEN1);
      x2 = xc + (LEN1);
      x3 = xc + (LEN1 + LEN2);
      y0 = yc - (LEN1 + LEN2);
      y1 = yc - (LEN1);
      y2 = yc + (LEN1);
      y3 = yc + (LEN1 + LEN2);
      c.drawLine(x0, yc, x1, yc, red_paint);
      c.drawLine(x2, yc, x3, yc, red_paint);
      c.drawLine(xc, y0, xc, y1, red_paint);
      c.drawLine(xc, y2, xc, y3, red_paint);
    }
  }

  private void draw_centre_circle(Canvas c, int w, int h) {
    float xc;
    float yc;
    xc = (float)(w/2);
    yc = (float)(h/2);
    c.drawCircle(xc, yc, LEN3, red_stroke_paint);
  }

  public void draw_bearing(Canvas c, int w, int h) {
    if (Logger.validFix) {
      c.save();
      c.rotate((float) Logger.lastBearing, (w>>1), (h>>1));
      float xl = (float)(w>>1) - (1.0f*LEN1);
      float xc = (float)(w>>1);
      float xr = (float)(w>>1) + (1.0f*LEN1);
      float yb = (float)(h>>1) - (LEN3 + (0.5f*LEN1));
      float yt = (float)(h>>1) - (LEN3 + (3.5f*LEN1));
      c.drawLine(xc, yt, xl, yb, red_double_stroke_paint);
      c.drawLine(xc, yt, xr, yb, red_double_stroke_paint);
      c.drawLine(xl, yb, xr, yb, red_double_stroke_paint);
      c.restore();
    }
  }

  private static final int button_half_line = 12;
  private static final int button_radius = 16;
  private static final int button_size = 40;

  private void draw_buttons(Canvas c, int w, int h) {
    int button_offset = button_radius + (button_radius >> 1);
    // draw minus
    c.drawCircle(button_offset, button_offset, button_radius, button_stroke_paint);
    c.drawLine(button_offset - button_half_line, button_offset,
        button_offset + button_half_line, button_offset,
        button_stroke_paint);
    // draw plus
    c.drawCircle(w - button_offset, button_offset, button_radius, button_stroke_paint);
    c.drawLine(w - button_offset - button_half_line, button_offset,
        w - button_offset + button_half_line, button_offset,
        button_stroke_paint);
    c.drawLine(w - button_offset, button_offset - button_half_line,
        w - button_offset, button_offset + button_half_line,
        button_stroke_paint);
  }

  private void draw_recent_trail(Canvas c, int w, int h) {
    Trail.PointArray pa = Logger.mTrail.get_recent();
    int last_x = 0, last_y = 0;
    for (int i=0; i<pa.n; i++) {
      int sx = ((pa.x[i] - display_pos.X) >> pixel_shift) + (w>>1);
      int sy = ((pa.y[i] - display_pos.Y) >> pixel_shift) + (h>>1);
      boolean do_add = true;
      if (i > 0) {
        int manhattan = Math.abs(sx - last_x) + Math.abs(sy - last_y);
        if (manhattan < Trail.splot_gap) {
          do_add = false;
        }
      }
      if (do_add) {
        c.drawCircle((float)sx, (float)sy, Trail.splot_radius, trail_paint);
        last_x = sx;
        last_y = sy;
      }
    }
  }

  private void update_map(Canvas canvas) {
    // Decide if we have to rebuild the tile22 cache
    int width = getWidth();
    int height = getHeight();

    tile_cache.draw(canvas, width, height, display_pos);

    draw_crosshair(canvas, width, height);
    draw_centre_circle(canvas, width, height);
    draw_buttons(canvas, width, height);
    draw_bearing(canvas, width, height);
    draw_recent_trail(canvas, width, height);
  }

  // Interface with main UI activity

  public void update_map() {
    // 2-elt 'shift register' of current and last position
    last_actual_pos = actual_pos;
    if (Logger.validFix) {
      actual_pos = new Merc28(Logger.lastLat, Logger.lastLon);
    } else {
      actual_pos = null;
    }
    if (last_actual_pos == null) {
      // for 'quick start' - only need 1 update before map draw can start properly.
      last_actual_pos = actual_pos;
    }
    estimated_pos = Merc28.predict(last_actual_pos, actual_pos);
    if ((estimated_pos != null) &&
        ((display_pos == null) || !is_dragged)) {
      display_pos = new Merc28(estimated_pos);
    }
    invalidate();
  }

  public void select_map_source(int which) {
    tile_cache.setMapSource(which);
    invalidate();
  }

  // Save/restore state in bundle
  //

  static final String ZOOM_KEY      = "LogMyGsm_Map_Zoom";
  static final String LAST_X_KEY    = "LogMyGsm_Last_X";
  static final String LAST_Y_KEY    = "LogMyGsm_Last_Y";
  static final String WHICH_MAP_KEY = "LogMyGsm_Which_Map";

  private void setZoom(int z) {
    tile_cache.setZoom(z);
    zoom = z;
    pixel_shift = Merc28.shift - (z+8);
    drag_scale = (float)(1 << pixel_shift);
  }

  // Yes, we should use the Android preferences system for this.
  // Saving to SD card allows us to edit the file offline etc
  // for debugging and so on
  public void restore_state_from_file(String tail) {
    File file = new File("/sdcard/LogMyGsm/prefs/" + tail);
    int map_source;
    // defaults in case of strife
    display_pos = null;
    setZoom(14);
    map_source = MAP_2G;
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
        map_source = Integer.parseInt(line);
        br.close();
      } catch (IOException e) {
      } catch (NumberFormatException n) {
      }
    }
    tile_cache.setMapSource(map_source);
  }

  public void save_state_to_file(String tail) {
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
        bw.write(String.format("%d\n", tile_cache.getMapSource()));
        bw.close();
      } catch (IOException e) {
      }
    }
  }

  // Local UI callbacks

  public void clear_trail() {
    Logger.mTrail.clear();
    tile_cache.clear();
    invalidate();
  }

  public void zoom_out() {
    if (zoom > 9) {
      setZoom(zoom - 1);
      invalidate();
    }
  }

  public void zoom_in() {
    if (zoom < 16) {
      setZoom(zoom + 1);
      invalidate();
    }
  }

  private float mLastX;
  private float mLastY;

  private boolean try_zoom(float x, float y) {
    if (y < button_size) {
      if (x < button_size) {
        zoom_out();
        return true;
      } else if (x > (getWidth() - button_size)) {
        zoom_in();
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
        display_pos = estimated_pos;
        is_dragged = false;
        invalidate();
        return true;
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
        if (is_dragged) {
          float dx, dy;
          dx = x - mLastX;
          dy = y - mLastY;
          display_pos.X -= (int)(0.5 + drag_scale * dx);
          display_pos.Y -= (int)(0.5 + drag_scale * dy);
          mLastX = x;
          mLastY = y;
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
      update_map(canvas);
    }
  }


}


