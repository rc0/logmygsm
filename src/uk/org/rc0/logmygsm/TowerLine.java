package uk.org.rc0.logmygsm;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Math;
import java.util.HashMap;

class TowerLine {

  static private HashMap<String, Merc28> lut;
  static private Paint line_paint;
  static private Paint text_paint;

  static final private String TAIL = "cidxy.txt";
  static final private String TAG = "TowerLine";

  static void init() {
    lut = new HashMap<String,Merc28>();

    line_paint = new Paint();
    line_paint.setStyle(Paint.Style.STROKE);
    line_paint.setStrokeWidth(6);
    line_paint.setColor(Color.argb(128, 0x88, 0x0, 0x6d));

    text_paint = new Paint();
    text_paint.setColor(Color.argb(192, 0x38, 0x0, 0x58));
    Typeface face = Typeface.DEFAULT_BOLD;
    text_paint.setTypeface(face);
    text_paint.setAntiAlias(true);
    text_paint.setTextSize(22);

    File file = new File("/sdcard/LogMyGsm/prefs/" + TAIL);
    boolean failed = false;
    int n = 0;
    int actual = 0;
    if (file.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        line = br.readLine();
        n = Integer.parseInt(line);
        for (int i = 0; i < n; i++) {
          line = br.readLine();
          int cid = Integer.parseInt(line);
          line = br.readLine();
          int lac = Integer.parseInt(line);
          lac = 0;
          line = br.readLine();
          int x = Integer.parseInt(line);
          line = br.readLine();
          int y = Integer.parseInt(line);
          lut.put(cid + "," + lac, new Merc28(x, y));
          ++actual;
        }
        br.close();
      } catch (IOException e) {
        failed = true;
      } catch (NumberFormatException e) {
        failed = true;
      }
    }
    // Log.i(TAG, "Read " + actual + " points out of " + n);

  }

  static final float BASE = 16.0f;
  static final float TEXT_RADIUS = 70.0f;

  static void draw_line(Canvas c, int w, int h, int pixel_shift, Merc28 display_pos) {
    // CidLac cl = new CidLac(Logger.lastCid, Logger.lastLac);
    int cid = Logger.lastCid;
    int lac = 0;
    //Log.i(TAG, "Looking up " + cid + "," + lac);
    String cl = cid + "," + lac;
    if (lut.containsKey(cl)) {
      Merc28 pos = lut.get(cl);
      //Log.i(TAG, "Matched in lut");
      int dx = (pos.X - display_pos.X) >> pixel_shift;
      int dy = (pos.Y - display_pos.Y) >> pixel_shift;

      float fx = (float) dx;
      float fy = (float) dy;
      float f = (float) Math.sqrt(fx*fx + fy*fy);
      if (f > BASE) { // else tower is in centre of view
        float x0 = (float)(w>>1) + BASE * (fx / f);
        float y0 = (float)(h>>1) + BASE * (fy / f);
        float x1 = (float)(w>>1) + fx;
        float y1 = (float)(h>>1) + fy;
        c.drawLine(x0, y0, x1, y1, line_paint);

        double zx = (double)(pos.X - display_pos.X);
        double zy = (double)(pos.Y - display_pos.Y);
        double zd = Math.sqrt(zx*zx + zy*zy) * 25220000.0 / Merc28.scale;

        String caption;
        if (zd < 1000) {
          caption = String.format("%dm", (int)zd);
        } else {
          caption = String.format("%.1fkm", 0.001*zd);
        }
        float tw = text_paint.measureText(caption);
        float xt = (float)(w>>1) + TEXT_RADIUS * (fx / f);
        float yt = (float)(h>>1) + TEXT_RADIUS * (fy / f);
        c.drawText(caption, xt-(0.5f*tw), yt, text_paint);
      }
    }
  }


}

// vim:et:sw=2:sts=2

