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

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.lang.Math;
import java.util.HashMap;

class TowerLine {

  static private HashMap<String, Merc28> lut;
  static private Paint [] line_paint;
  static private Paint [] thin_line_paint;
  static private Paint text_paint;

  static String simOperator;
  static String text_name;
  static String binary_name;
  static final private String PREFS_DIR = "/sdcard/LogMyGsm/prefs/";
  static final private String STEM = "cidxy_";
  static final private String TEXT = ".txt";
  static final private String BINARY = ".dat";
  static final private String TAG = "TowerLine";

  static private Merc28 tmp_pos;

  static private boolean mActive;

  static final private int N_PAINTS = 3;
  static final private int [] opacity      = {176, 128, 64};
  static final private int [] thin_opacity = {192, 128, 64};
  static final private int [] line_widths      = {8, 4, 4};
  static final private int [] thin_line_widths = {2, 1, 1};

  // -------------------------

  static final private void setup_filenames(Context _app_context) {
    TelephonyManager tm;
    String simOperator;

    tm = (TelephonyManager) _app_context.getSystemService(Context.TELEPHONY_SERVICE);
    simOperator = tm.getSimOperator();
    text_name = new String(PREFS_DIR + STEM + simOperator + TEXT);
    binary_name = new String(PREFS_DIR + STEM + simOperator + BINARY);
  }

  static final private void load_lut_from_text() {
    lut = new HashMap<String,Merc28>();
    File file = new File(text_name);
    int n = 0;
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
          line = br.readLine();
          int x = Integer.parseInt(line);
          line = br.readLine();
          int y = Integer.parseInt(line);
          lut.put(lac + "," + cid, new Merc28(x, y));
        }
        br.close();
      } catch (IOException e) {
      } catch (NumberFormatException e) {
      }
    }
  }

  static final private void write_lut_to_binary() {
    File file = new File(binary_name);
    try {
      ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
      int n = lut.size();
      oos.writeInt(n);
      if (n > 0) {
        for (String key : lut.keySet()) {
          oos.writeObject(key);
          Merc28 temp = lut.get(key);
          oos.writeInt(temp.X);
          oos.writeInt(temp.Y);
        }
        //Log.i(TAG, "Wrote " + n + " entries to binary cidxy file");
      }
      oos.close();
    } catch (IOException e) {
      //Log.i(TAG, "binary write excepted : " + e.getClass().getName() + " : " + e.getMessage());
    }
  }

  static final private void read_lut_from_binary() {
    File file = new File(binary_name);
    int i = -1;
    lut = new HashMap<String,Merc28>();
    try {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
      int n_obj = ois.readInt();
      //Log.i(TAG, n_obj + " objects in file header");
      for (i = 0; i < n_obj; i++) {
        String key = (String) ois.readObject();
        int X = ois.readInt();
        int Y = ois.readInt();
        //Log.i(TAG, "KEY:" + key + " X:" + X + "Y:" + Y);
        lut.put(key, new Merc28(X, Y));
      }
      ois.close();
      //Log.i(TAG, "Read " + n_obj + " entries from binary cidxy file");
    } catch (IOException e) {
      //Log.i(TAG, "binary read excepted after " + i + " entries : " + e.getClass().getName() + " : " + e.getMessage());
    } catch (ClassNotFoundException e) {
      //Log.i(TAG, "binary read got class not found : " + e.getClass().getName() + " : " + e.getMessage());
    }
  }

  static final private void load_tower_xy_data() {
    File text = new File(text_name);
    File binary = new File(binary_name);
    if (!binary.exists() ||
        (binary.lastModified() < text.lastModified())) {
      load_lut_from_text();
      write_lut_to_binary();
    } else {
      read_lut_from_binary();
    }
  }

  static void init(Context _app_context) {
    setup_filenames(_app_context);
    tmp_pos = new Merc28(0,0);
    mActive = false;
    line_paint = new Paint[N_PAINTS];
    thin_line_paint = new Paint[N_PAINTS];
    for (int i = 0; i<N_PAINTS; i++) {
      line_paint[i] = new Paint();
      line_paint[i].setStyle(Paint.Style.STROKE);
      line_paint[i].setStrokeWidth(line_widths[i]);
      line_paint[i].setColor(Color.argb(opacity[i], 0x00, 0x30, 0x10));
      thin_line_paint[i] = new Paint();
      thin_line_paint[i].setStyle(Paint.Style.STROKE);
      thin_line_paint[i].setStrokeWidth(thin_line_widths[i]);
      thin_line_paint[i].setColor(Color.argb(thin_opacity[i], 0xff, 0xff, 0xff));
    }

    text_paint = new Paint();
    text_paint.setColor(Color.argb(224, 0x38, 0x0, 0x58));
    Typeface face = Typeface.DEFAULT_BOLD;
    text_paint.setTypeface(face);
    text_paint.setAntiAlias(true);
    text_paint.setTextSize(22);

    load_tower_xy_data();

  }

  static final float BASE = 16.0f;
  static final float TEXT_RADIUS = 70.0f;

  static boolean find_tower_pos(int index, Merc28 tower_pos) {
    if (Logger.recent_cids == null) {
      // catch the early initialisation case before the service has started
      return false;
    }
    int cid = Logger.recent_cids[index].cid;
    int lac = Logger.recent_cids[index].lac;
    // uninitialised history entries have cid==-1 : this will never match in
    // the LUT
    String cl = lac + "," + cid;
    if (lut.containsKey(cl)) {
      tower_pos.copy_from(lut.get(cl));
      return true;
    } else {
      return false;
    }
  }

  static boolean is_active() {
    return mActive;
  }

  static void toggle_active() {
    mActive = mActive ? false : true;
  }

  static void draw_line(Canvas c, int w, int h, int pixel_shift, Merc28 display_pos) {
    int i;
    for (i=2; i>=0; i--) {
      if (find_tower_pos(i, tmp_pos)) {
        int dx = (tmp_pos.X - display_pos.X) >> pixel_shift;
        int dy = (tmp_pos.Y - display_pos.Y) >> pixel_shift;

        float fx = (float) dx;
        float fy = (float) dy;
        float f = (float) Math.sqrt(fx*fx + fy*fy);
        if (f > BASE) { // else tower is in centre of view
          float x0 = (float)(w>>1) + BASE * (fx / f);
          float y0 = (float)(h>>1) + BASE * (fy / f);
          float x1 = (float)(w>>1) + fx;
          float y1 = (float)(h>>1) + fy;
          c.drawLine(x0, y0, x1, y1, line_paint[i]);
          c.drawLine(x0, y0, x1, y1, thin_line_paint[i]);

          if (i == 0) {
            double zd = tmp_pos.metres_away(display_pos);

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
  }

}

// vim:et:sw=2:sts=2

