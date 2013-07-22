// Copyright (c) 2013 Richard P. Curnow
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
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.Color;
import android.util.FloatMath;
import android.util.Log;
import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Math;
import java.util.ArrayList;

// Storage for the the waypoints that the user can define

class Landmarks {

  static final private String TAG = "Landmarks";

  private ArrayList<Merc28> points;
  private Paint white_paint;
  private Paint black_paint;
  private Paint red_paint;

  static final private String TAIL = "landmarks.txt";

  Landmarks() {
    restore_state_from_file();

    black_paint = new Paint();
    black_paint.setColor(Color.argb(0xc0, 0x00, 0x00, 0x00));
    black_paint.setStyle(Paint.Style.FILL);

    white_paint = new Paint();
    white_paint.setColor(Color.argb(0xc0, 0xff, 0xff, 0xff));
    white_paint.setStyle(Paint.Style.FILL);

    red_paint = new Paint();
    red_paint.setColor(Color.argb(0xff, 0xff, 0x00, 0x00));
    red_paint.setStyle(Paint.Style.STROKE);
    red_paint.setStrokeWidth(2);
  }

  // ---------------------------

  void save_state_to_file() {
    File dir = new File("/sdcard/LogMyGsm/prefs");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    File file = new File(dir, TAIL);
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(file));
      int n = points.size();
      bw.write(String.format("%d\n", n));
      for (int i=0; i < n; i++) {
        bw.write(String.format("%d\n", points.get(i).X));
        bw.write(String.format("%d\n", points.get(i).Y));
      }
      bw.close();
    } catch (IOException e) {
    }
  }

  private void restore_state_from_file() {
    points = new ArrayList<Merc28> ();
    File file = new File("/sdcard/LogMyGsm/prefs/" + TAIL);
    boolean failed = false;
    if (file.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        line = br.readLine();
        int n = Integer.parseInt(line);
        for (int i = 0; i < n; i++) {
          line = br.readLine();
          int x = Integer.parseInt(line);
          line = br.readLine();
          int y = Integer.parseInt(line);
          points.add(new Merc28(x, y));
        }
        br.close();
      } catch (IOException e) {
        failed = true;
      } catch (NumberFormatException n) {
        failed = true;
      }
    }
    if (failed) {
      points = new ArrayList<Merc28> ();
    }
  }

  // ---------------------------

  void add(Merc28 pos) {
    points.add(new Merc28(pos));
  }

  private int find_closest_point(Merc28 pos, int pixel_shift) {
    int victim;
    int closest;
    int n = points.size();
    victim = -1;
    closest = 0;
    for (int i=0; i<n; i++) {
      int dx = (points.get(i).X - pos.X) >> pixel_shift;
      int dy = (points.get(i).Y - pos.Y) >> pixel_shift;
      int d = Math.abs(dx) + Math.abs(dy);
      if ((victim < 0) ||
          (d < closest)) {
        closest = d;
        victim = i;
      }
    }
    return victim;
  }

  // Return value is true if a deletion successfully occurred, false if no point was
  // close enough to 'pos' to qualify.  Only delete the point that is 'closest'
  boolean delete(Merc28 pos, int pixel_shift) {
    int victim;
    victim = find_closest_point(pos, pixel_shift);
    if (victim < 0) {
      return false;
    } else {
      points.remove(victim);
      return true;
    }
  }

  // ---------------------------

  private static class Transform {
    Merc28 base;
    int w2;
    int h2;
    int pixel_shift;

    Transform(Merc28 _base, int _w, int _h, int _pixel_shift) {
      base = _base;
      w2 = _w>>1;
      h2 = _h>>1;
      pixel_shift = _pixel_shift;
    }

    int X(Merc28 p) {
      return w2 + ((p.X - base.X) >> pixel_shift);
    }

    int Y(Merc28 p) {
      return h2 + ((p.Y - base.Y) >> pixel_shift);
    }
  };

  // ---------------------------

  private final static int RADIUS = 12;
  private final static float a1 = 0.38f * (float) RADIUS;
  private final static float a2 = 0.92f * (float) RADIUS;

  // pos is the position of the centre-screen
  void draw(Canvas c, Merc28 pos, int w, int h, int pixel_shift, boolean do_show_track) {
    int n = points.size();
    Transform t = new Transform(pos, w, h, pixel_shift);
    for (int i = 0; i < n; i++) {
      Merc28 p = points.get(i);
      int x = t.X(p);
      int y = t.Y(p);
      Path pa = new Path();
      pa.moveTo(x, y);
      pa.lineTo(x + a2, y + a1);
      pa.lineTo(x + a1, y + a2);
      pa.lineTo(x - a1, y - a2);
      pa.lineTo(x - a2, y - a1);
      pa.lineTo(x, y);
      pa.lineTo(x - a1, y + a2);
      pa.lineTo(x - a2, y + a1);
      pa.lineTo(x + a2, y - a1);
      pa.lineTo(x + a1, y - a2);
      pa.close();
      c.drawPath(pa, white_paint);
      pa.reset();

      pa.moveTo(x, y);
      pa.lineTo(x + a2, y + a1);
      pa.lineTo(x + a2, y - a1);
      pa.lineTo(x - a2, y + a1);
      pa.lineTo(x - a2, y - a1);
      pa.lineTo(x, y);
      pa.lineTo(x - a1, y - a2);
      pa.lineTo(x + a1, y - a2);
      pa.lineTo(x - a1, y + a2);
      pa.lineTo(x + a1, y + a2);
      pa.close();
      c.drawPath(pa, black_paint);

      c.drawCircle(x, y, RADIUS, red_paint);
    }
  }


}

// vim:et:sw=2:sts=2
//

