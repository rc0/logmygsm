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
import java.util.ArrayList;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

public class Map extends View {

  private final Paint red_paint;
  private final Paint red_stroke_paint;
  private final Paint trail_paint;
  private final Paint button_stroke_paint;
  private final Paint grey_paint;

  static public enum Map_Source {
    MAP_2G, MAP_3G, MAP_OSM, MAP_OS
  }

  // Cache of 2x2 tiles containing the region around the viewport
  private Bitmap tile22;
  private Slip28 ul22;
  // The current level of the current tile cache
  private int zoom;
  private int tile_shift;
  private int pixel_shift;

  // the GPS fix from the logger
  private Slip28 actual_pos;
  // the location at the centre of the screen - may be != actual_pos if is_dragged is true.
  private Slip28 display_pos;
  // Set to true if we've off-centred the map
  private boolean is_dragged;

  private Trail mTrail;

  // --------------------------------------------------------------------------


  private Map_Source map_source;

  public Map(Context context, AttributeSet attrs) {
    super(context, attrs);

    map_source = Map_Source.MAP_2G;
    tile22 = null;

    red_paint = new Paint();
    red_paint.setStrokeWidth(1);
    red_paint.setColor(Color.RED);

    red_stroke_paint = new Paint();
    red_stroke_paint.setStrokeWidth(1);
    red_stroke_paint.setColor(Color.RED);
    red_stroke_paint.setStyle(Paint.Style.STROKE);

    trail_paint = new Paint();
    trail_paint.setColor(Color.argb(160, 255, 0, 170));
    trail_paint.setStyle(Paint.Style.FILL);

    button_stroke_paint = new Paint();
    button_stroke_paint.setStrokeWidth(2);
    button_stroke_paint.setColor(Color.BLACK);
    button_stroke_paint.setStyle(Paint.Style.STROKE);

    grey_paint = new Paint();
    grey_paint.setStrokeWidth(2);
    grey_paint.setColor(Color.GRAY);

    zoom = 14;
    actual_pos = null;
    display_pos = null;

    mTrail = new Trail();
  }

  public class Slip28 {
    public int X;
    public int Y;

    // public final double scale28 = 268435456.0;
    public final double scale28 = (double)(1<<28);

    public Slip28(double lat, double lon) {
      double x, yy, y, XX, YY;
      x = Math.toRadians(lon);
      yy = Math.toRadians(lat);
      y = Math.log(Math.tan(yy) + 1.0/Math.cos(yy));
      XX = 0.5 * (1.0 + x/Math.PI);
      YY = 0.5 * (1.0 - y/Math.PI);
      X = (int) Math.floor(XX * scale28);
      Y = (int) Math.floor(YY * scale28);
    }
    public Slip28(int x, int y) {
      X = x;
      Y = y;
    }
    public Slip28(Slip28 orig) {
      X = orig.X;
      Y = orig.Y;
    }
  }

  public class PixelXY {
    public int X;
    public int Y;

    public PixelXY(Slip28 pos, Slip28 base, int width, int height) {
      X = (pos.X - base.X) >> pixel_shift;
      X -= (width >> 1);
      Y = (pos.Y - base.Y) >> pixel_shift;
      Y -= (height >> 1);
    }

    public PixelXY(Slip28 pos, int width, int height) {
      X = pos.X >> pixel_shift;
      X -= (width >> 1);
      Y = pos.Y >> pixel_shift;
      Y -= (height >> 1);
    }
  }

  // Main drawing routines...

  private void render_tile(Canvas canvas, int z, int tile_x, int tile_y, int x01, int y01) {
    int xl = (256*x01);
    int xr = (256*(1+x01));
    int yt = (256*y01);
    int yb = (256*(1+y01));

    String filename = null;
    switch (map_source) {
      case MAP_2G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 2/%d/%d/%d.png.tile",
            z, tile_x+x01, tile_y+y01);
        break;
      case MAP_3G:
        filename = String.format("/sdcard/Maverick/tiles/Custom 3/%d/%d/%d.png.tile",
            z, tile_x+x01, tile_y+y01);
        break;
      case MAP_OSM:
        filename = String.format("/sdcard/Maverick/tiles/mapnik/%d/%d/%d.png.tile",
            z, tile_x+x01, tile_y+y01);
        break;
      case MAP_OS:
        filename = String.format("/sdcard/Maverick/tiles/Ordnance Survey Explorer Maps (UK)/%d/%d/%d.png.tile",
            z, tile_x+x01, tile_y+y01);
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
    int tile_x, tile_y;
    PixelXY pix = new PixelXY(pos, width, height);
    tile_x = pix.X >> 8;
    tile_y = pix.Y >> 8;
    // dx, dy are now the tile x,y coords of the tile containing left hand
    // corner of the viewport
    tile22 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
    Canvas my_canv = new Canvas(tile22);
    render_tile(my_canv, zoom, tile_x, tile_y, 0, 0);
    render_tile(my_canv, zoom, tile_x, tile_y, 0, 1);
    render_tile(my_canv, zoom, tile_x, tile_y, 1, 0);
    render_tile(my_canv, zoom, tile_x, tile_y, 1, 1);
    ul22 = new Slip28(tile_x << tile_shift, tile_y << tile_shift);
    mTrail.render_old(my_canv);
  }

  private void draw_crosshair(Canvas c, int w, int h) {
    final float len1 = 8.0f;
    final float len2 = 64.0f;
    if (actual_pos != null) {
      float x0, x1, x2, x3, xc;
      float y0, y1, y2, y3, yc;
      xc = (float)(w/2);
      yc = (float)(h/2);
      // If we're dragged, offset the position
      if (is_dragged) {
        // if actual_pos is non-null, display_pos has to be
        int dx = (actual_pos.X - display_pos.X) >> pixel_shift;
        int dy = (actual_pos.Y - display_pos.Y) >> pixel_shift;
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
      c.drawLine(x0, yc, x1, yc, red_paint);
      c.drawLine(x2, yc, x3, yc, red_paint);
      c.drawLine(xc, y0, xc, y1, red_paint);
      c.drawLine(xc, y2, xc, y3, red_paint);
    }
  }

  private void draw_centre_circle(Canvas c, int w, int h) {
    float len1 = 8.0f;
    float len3 = 3.0f * len1;
    float xc;
    float yc;
    xc = (float)(w/2);
    yc = (float)(h/2);
    c.drawCircle(xc, yc, len3, red_stroke_paint);
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

  private void update_map(Canvas canvas) {
    // Decide if we have to rebuild the tile22 cache
    int width = getWidth();
    int height = getHeight();
    if (height > 240) {
      // Avoid the viewport height getting bigger than the size of a tile - for now
      height = 240;
    }
    PixelXY pix;
    if (tile22 == null) {
      rebuild_cache(display_pos, width, height);
      pix = new PixelXY(display_pos, ul22, width, height);
    } else {
      pix = new PixelXY(display_pos, ul22, width, height);
      // Now correspond to top left-hand corner
      if ((pix.X >= 0) && ((pix.X + width) < 512) &&
          (pix.Y >= 0) && ((pix.Y + height) < 512)) {
      } else {
        rebuild_cache(display_pos, width, height);
        pix = new PixelXY(display_pos, ul22, width, height);
      }
    }

    Rect src = new Rect(pix.X, pix.Y, pix.X + width, pix.Y + height);
    Rect dest = new Rect(0, 0, width, height);
    canvas.drawBitmap(tile22, src, dest, null);

    draw_crosshair(canvas, width, height);
    draw_centre_circle(canvas, width, height);
    draw_buttons(canvas, width, height);
    mTrail.render_recent(canvas, width, height);
  }

  // Interface with main UI activity

  public void update_map() {
    if (Logger.validFix) {
      actual_pos = new Slip28(Logger.lastLat, Logger.lastLon);
      if ((display_pos == null) || !is_dragged) {
        display_pos = new Slip28(actual_pos);
      }
      mTrail.add_point(actual_pos);
    } else {
      actual_pos = null;
    }
    invalidate();
  }

  public void select_map_source(Map_Source which) {
    map_source = which;
    // force map rebuild
    tile22 = null;
    invalidate();
  }

  // Save/restore state in bundle
  //

  static final String ZOOM_KEY      = "LogMyGsm_Map_Zoom";
  static final String LAST_X_KEY    = "LogMyGsm_Last_X";
  static final String LAST_Y_KEY    = "LogMyGsm_Last_Y";
  static final String WHICH_MAP_KEY = "LogMyGsm_Which_Map";

  public void save_state(Bundle icicle) {
    icicle.putInt(ZOOM_KEY, zoom);
    if (display_pos != null) {
      icicle.putInt(LAST_X_KEY, display_pos.X);
      icicle.putInt(LAST_Y_KEY, display_pos.Y);
    }
    switch (map_source) {
      case MAP_2G:
        icicle.putInt(WHICH_MAP_KEY, 1);
        break;
      case MAP_3G:
        icicle.putInt(WHICH_MAP_KEY, 2);
        break;
      case MAP_OSM:
        icicle.putInt(WHICH_MAP_KEY, 3);
        break;
      case MAP_OS:
        icicle.putInt(WHICH_MAP_KEY, 4);
        break;
    }
    // mTrail.save_state();
  }

  private void setZoom(int z) {
    zoom = z;
    tile_shift = (28 - z);
    pixel_shift = (28 - (z+8));
  }

  public void restore_state(Bundle icicle) {
    if (icicle != null) {
      if (icicle.containsKey(ZOOM_KEY)) {
        setZoom(icicle.getInt(ZOOM_KEY));
      }
      if (icicle.containsKey(LAST_X_KEY) && icicle.containsKey(LAST_Y_KEY)) {
        display_pos = new Slip28(icicle.getInt(LAST_X_KEY), icicle.getInt(LAST_Y_KEY));
      }
      if (icicle.containsKey(WHICH_MAP_KEY)) {
        switch (icicle.getInt(WHICH_MAP_KEY)) {
          case 1:
            map_source = Map_Source.MAP_2G;
            break;
          case 2:
            map_source = Map_Source.MAP_3G;
            break;
          case 3:
            map_source = Map_Source.MAP_OSM;
            break;
          default:
            map_source = Map_Source.MAP_OS;
            break;
        }
      }
    } else {
      restore_from_file();
    }
    // mTrail.restore_state();
  }

  private void restore_from_file() {
    File file = new File("/sdcard/LogMyGsm/prefs/prefs.txt");
    // defaults in case of strife
    display_pos = null;
    setZoom(14);
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
        display_pos = new Slip28(x, y);
        br.close();
      } catch (IOException e) {
      } catch (NumberFormatException n) {
      }
    }
    // mTrail.restore_state();
  }

  public void save_state_to_file() {
    if (display_pos != null) {
      File dir = new File("/sdcard/LogMyGsm/prefs");
      if (!dir.exists()) {
        dir.mkdirs();
      }
      File file = new File(dir, "prefs.txt");
      try {
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(String.format("%d\n", zoom));
        bw.write(String.format("%d\n", display_pos.X));
        bw.write(String.format("%d\n", display_pos.Y));
        bw.close();
      } catch (IOException e) {
      }
    }
    // mTrail.save_state();
  }

  // Local UI callbacks

  public void clear_trail() {
    mTrail.clear();
  }

  public void zoom_out() {
    if (zoom > 9) {
      setZoom(zoom - 1);
      tile22 = null;
      invalidate();
    }
  }

  public void zoom_in() {
    if (zoom < 16) {
      setZoom(zoom + 1);
      tile22 = null;
      invalidate();
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX();
    float y = event.getY();

    if (event.getAction() == MotionEvent.ACTION_DOWN) {
      if (y < button_size) {
        if (x < button_size) {
          zoom_out();
          return true;
        } else if (x > (getWidth() - button_size)) {
          zoom_in();
          return true;
        }
      }
      // Hit on the centre cross-hair region to re-centre the map on the GPS fix
      if ((y > ((getHeight() - button_size)>>1)) &&
          (y < ((getHeight() + button_size)>>1)) &&
          (x > ((getWidth()  - button_size)>>1)) &&
          (x < ((getWidth()  + button_size)>>1))) {
        // If (actual_pos == null) here, it means no GPS fix; refuse to drop the display position then
        if (actual_pos != null) {
          display_pos = actual_pos;
          is_dragged = false;
          invalidate();
          return true;
        }
      }
      // Not inside the zoom buttons - initiate drag
      if (display_pos != null) {
        int dx = (int) x - (getWidth() >> 1);
        int dy = (int) y - (getHeight() >> 1);
        display_pos.X += (dx << pixel_shift);
        display_pos.Y += (dy << pixel_shift);
        is_dragged = true;
        invalidate();
        return true;
      }
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

  // Trail history

  private class Trail {
    private ArrayList<Slip28> recent;
    private Slip28 last_point;
    private int n_old;
    private int[] x_old;
    private int[] y_old;

    public Trail() {
      recent = new ArrayList<Slip28> ();
      last_point = null;
      n_old = 0;
      x_old = null;
      y_old = null;
    }

    private static final int splot_gap = 10;
    private static final float splot_radius = 3.0f;

    // TODO : implement icicle save/restore
    // Just save/restore through file - it might be a huge hunk of data.
    // (Threading issues??)

    // TODO : implement persistent file save/restore
    public void save_state() {
      gather();

      File dir = new File("/sdcard/LogMyGsm/prefs");
      if (!dir.exists()) {
        dir.mkdirs();
      }
      File file = new File(dir, "trail.txt");
      try {
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(String.format("%d\n", n_old));
        for (int i=0; i < n_old; i++) {
          bw.write(String.format("%d\n", x_old[i]));
          bw.write(String.format("%d\n", y_old[i]));
        }
        bw.close();
      } catch (IOException e) {
      }
    }

    public void restore_state() {
      File file = new File("/sdcard/LogMyGsm/prefs/prefs.txt");
      boolean failed = false;
      if (file.exists()) {
        try {
          BufferedReader br = new BufferedReader(new FileReader(file));
          String line;
          line = br.readLine();
          n_old = Integer.parseInt(line);
          x_old = new int[n_old];
          y_old = new int[n_old];
          for (int i = 0; i < n_old; i++) {
            line = br.readLine();
            x_old[i] = Integer.parseInt(line);
            line = br.readLine();
            y_old[i] = Integer.parseInt(line);
          }
          br.close();
        } catch (IOException e) {
          failed = true;
        } catch (NumberFormatException n) {
          failed = true;
        }
      } else {
        failed = true;
      }
      if (failed) {
        n_old = 0;
        x_old = null;
        y_old = null;
      }
    }

    // Skip points that are too close together to ever be visible on the map display
    public void add_point(Slip28 p) {
      boolean do_add = true;
      if (last_point != null) {
        // 4 is (28 - (16+8)), i.e. the pixel size at the highest zoom level.
        // Also, round it.
        int sx = (((p.X - last_point.X) >> 3) + 1) >> 1;
        int sy = (((p.Y - last_point.Y) >> 3) + 1) >> 1;
        int manhattan = Math.abs(sx) + Math.abs(sy);
        if (manhattan < splot_gap) {
          do_add = false;
        }
      }
      if (do_add) {
        recent.add(new Slip28(p));
        last_point = new Slip28(p);
      }
    }

    public void clear() {
      recent = new ArrayList<Slip28> ();
      last_point = null;
      n_old = 0;
      x_old = null;
      y_old = null;
    }

    private void gather() {
      // accumulate the 'recent' history onto the 'old' arrays
      int n_recent = recent.size();
      if (n_recent > 0) {

        // TODO : if n_old is too large, do some data reduction here (either
        // thin out the old stuff, or toss the earlier half of it)
        int n_new = n_old + n_recent;

        int [] x_new = new int[n_new];
        int [] y_new = new int[n_new];
        if (n_old > 0) {
          System.arraycopy(x_old, 0, x_new, 0, n_old);
          System.arraycopy(y_old, 0, y_new, 0, n_old);
        }
        for (int i = 0; i < n_recent; i++) {
          x_new[i + n_old] = recent.get(i).X;
          y_new[i + n_old] = recent.get(i).Y;
        }

        n_old = n_new;
        x_old = x_new;
        y_old = y_new;
        recent = new ArrayList<Slip28> ();
        // leave last_point alone
      } else {
        // leave 'old' arrays as they were
      }
    }

    public void render_old(Canvas c) {
      int last_x = 0, last_y = 0;

      gather();

      for (int i=0; i<n_old; i++) {
        int sx = ((x_old[i] - ul22.X) >> pixel_shift);
        int sy = ((y_old[i] - ul22.Y) >> pixel_shift);
        boolean do_add = true;
        if (i > 0) {
          int manhattan = Math.abs(sx - last_x) + Math.abs(sy - last_y);
          if (manhattan < splot_gap) {
            do_add = false;
          }
        }
        if (do_add) {
          c.drawCircle((float)sx, (float)sy, splot_radius, trail_paint);
          last_x = sx;
          last_y = sy;
        }
      }
    }

    // This is crude and inefficient.
    // It needs to be broken into 2 parts. First, we should render the bulk of
    // the trail onto the 2x2 tile cache when we rebuild that.  Then, the
    // newest points are rendered straight onto the UI canvas during each
    // onDraw().  This will need 2 lists to be maintained.
    public void render_recent(Canvas c, int w, int h) {
      int sz = recent.size();
      // guaranteed to be initialised properly on first point
      int last_x = 0, last_y = 0;
      for (int i=0; i<sz; i++) {
        Slip28 p = recent.get(i);
        int sx = ((p.X - display_pos.X) >> pixel_shift) + (w>>1);
        int sy = ((p.Y - display_pos.Y) >> pixel_shift) + (h>>1);
        boolean do_add = true;
        if (i > 0) {
          int manhattan = Math.abs(sx - last_x) + Math.abs(sy - last_y);
          if (manhattan < splot_gap) {
            do_add = false;
          }
        }
        if (do_add) {
          c.drawCircle((float)sx, (float)sy, splot_radius, trail_paint);
          last_x = sx;
          last_y = sy;
        }

      }
    }
  }
}

// vim:et:sw=2:sts=2

