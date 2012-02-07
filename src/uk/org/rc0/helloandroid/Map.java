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

  // the GPS fix from the logger
  private Slip28 actual_pos;
  // the location at the centre of the screen - may be != actual_pos if is_dragged is true.
  private Slip28 display_pos;
  // Set to true if we've off-centred the map
  private boolean is_dragged;

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
      X = (pos.X - base.X) >> (28 - (zoom+8));
      X -= (width >> 1);
      Y = (pos.Y - base.Y) >> (28 - (zoom+8));
      Y -= (height >> 1);
    }

    public PixelXY(Slip28 pos, int width, int height) {
      X = pos.X >> (28 - (zoom+8));
      X -= (width >> 1);
      Y = pos.Y >> (28 - (zoom+8));
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
    ul22 = new Slip28(tile_x << (28 - zoom), tile_y << (28 - zoom));
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
        int dx = (actual_pos.X - display_pos.X) >> (28 - (zoom+8));
        int dy = (actual_pos.Y - display_pos.Y) >> (28 - (zoom+8));
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
  }

  // Interface with main UI activity

  public void update_map() {
    if (Logger.validFix) {
      actual_pos = new Slip28(Logger.lastLat, Logger.lastLon);
      if ((display_pos == null) || !is_dragged) {
        display_pos = new Slip28(actual_pos);
      }
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
  }

  public void restore_state(Bundle icicle) {
    if (icicle != null) {
      if (icicle.containsKey(ZOOM_KEY)) {
        zoom = icicle.getInt(ZOOM_KEY);
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
  }

  private void restore_from_file() {
    File file = new File("/sdcard/LogMyGsm/prefs/prefs.txt");
    // defaults in case of strife
    display_pos = null;
    zoom = 14;
    if (file.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        line = br.readLine();
        zoom = Integer.parseInt(line);
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
  }

  // Local UI callbacks

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
        display_pos.X += (dx << (28 - (zoom+8)));
        display_pos.Y += (dy << (28 - (zoom+8)));
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
}

