// Copyright (c) 2012, 2013 Richard P. Curnow
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

  private class Landmark {
    Merc28 pos;
    boolean alive;

    Landmark(Merc28 _pos) {
      pos = new Merc28(_pos);
      alive = true;
    }

    Landmark(int _x, int _y) {
      pos = new Merc28(_x, _y);
      alive = true;
    }
  }

  private ArrayList<Landmark> points;
  private Merc28[] live_points = null;
  private Linkages mLinkages = null;
  private Paint marker_paint;
  private Paint track_paint;

  static final private String TAIL = "markers.txt";

  Landmarks() {
    restore_state_from_file();

    marker_paint = new Paint();
    marker_paint.setStrokeWidth(4);
    marker_paint.setColor(Color.argb(0xc0, 0x80, 0x00, 0x20));
    marker_paint.setStyle(Paint.Style.STROKE);

    track_paint = new Paint();
    track_paint.setStrokeWidth(12);
    track_paint.setColor(Color.argb(0x40, 0x80, 0x00, 0x20));
    track_paint.setStyle(Paint.Style.STROKE);
    track_paint.setStrokeCap(Paint.Cap.ROUND);

  }

  private int count_alive() {
    int n = 0;
    int m = points.size();
    for (int i=0; i<m; i++) {
      if (points.get(i).alive) {
        ++n;
      }
    }
    return n;
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
      bw.write(String.format("%d\n", count_alive()));
      for (int i=0; i < n; i++) {
        if (points.get(i).alive) {
          bw.write(String.format("%d\n", points.get(i).pos.X));
          bw.write(String.format("%d\n", points.get(i).pos.Y));
        }
      }
      bw.close();
    } catch (IOException e) {
    }
  }

  private void restore_state_from_file() {
    points = new ArrayList<Landmark> ();
    mLinkages = null;
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
          points.add(new Landmark(x, y));
        }
        br.close();
      } catch (IOException e) {
        failed = true;
      } catch (NumberFormatException n) {
        failed = true;
      }
    }
    if (failed) {
      points = new ArrayList<Landmark> ();
    }
  }

  // ---------------------------

  void add(Merc28 pos) {
    points.add(new Landmark(pos));
    mLinkages = null;
  }

  // Return value is true if a deletion successfully occurred, false if no point was
  // close enough to 'pos' to qualify.  Only delete the point that is 'closest'
  boolean delete(Merc28 pos, int pixel_shift) {
    int victim;
    int closest;
    int n = points.size();
    victim = -1;
    closest = 0;
    for (int i=0; i<n; i++) {
      if (points.get(i).alive) {
        int dx = (points.get(i).pos.X - pos.X) >> pixel_shift;
        int dy = (points.get(i).pos.Y - pos.Y) >> pixel_shift;
        int d = Math.abs(dx) + Math.abs(dy);
        if ((victim < 0) ||
            (d < closest)) {
          closest = d;
          victim = i;
        }
      }
    }
    if (victim < 0) {
      return false;
    } else {
      points.get(victim).alive = false;
      mLinkages = null;
      return true;
    }
  }

  boolean delete_visible(Merc28 pos, int pixel_shift, int width, int height) {
    int w2 = width >> 1;
    int h2 = height >> 1;
    int n = points.size();
    boolean did_any = false;
    for (int i=0; i<n; i++) {
      if (points.get(i).alive) {
        int dx = (points.get(i).pos.X - pos.X) >> pixel_shift;
        int dy = (points.get(i).pos.Y - pos.Y) >> pixel_shift;
        if ((Math.abs(dx) < w2) &&
            (Math.abs(dy) < h2)) {
          did_any = true;
          points.get(i).alive = false;
          mLinkages = null;
        }
      }
    }
    return did_any;
  }

  void delete_all() {
    points = new ArrayList<Landmark> ();
    mLinkages = null;
  }

  // ---------------------------

  private void update_live_points() {
    int n = count_alive();
    int m = points.size();
    int i, j;
    live_points = new Merc28[n];
    for (i=0, j=0; i<m; i++) {
      Landmark l = points.get(i);
      if (l.alive) {
        live_points[j++] = l.pos;
      }
    }
    Log.i(TAG, "Got " + n + " live points in the trail");
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

  private final static int RADIUS = 8;

  // pos is the position of the centre-screen
  void draw(Canvas c, Merc28 pos, int w, int h, int pixel_shift, boolean do_show_track) {
    int n = points.size();
    for (int i = 0; i < n; i++) {
      if (points.get(i).alive) {
        Merc28 p = points.get(i).pos;
        int dx = (p.X - pos.X) >> pixel_shift;
        int x = (w>>1) + dx;
        int dy = (p.Y - pos.Y) >> pixel_shift;
        int y = (h>>1) + dy;
        c.drawCircle(x, y, (float) RADIUS, marker_paint);
        c.drawPoint(x, y, marker_paint);
      }
    }
    if (do_show_track) {
      draw_track(c, pos, w, h, pixel_shift);
    }
  }

  // ---------------------------

  private void draw_track(Canvas c, Merc28 pos, int w, int h, int pixel_shift) {
    if (mLinkages == null) {
      update_live_points();
      mLinkages = new Linkages(live_points);
    }
    Transform t = new Transform(pos, w, h, pixel_shift);
    Linkages.Indices[] edges = mLinkages.get_edges();
    for (int i = 0; i<edges.length; i++) {
      Merc28 m0 = live_points[edges[i].a];
      Merc28 m1 = live_points[edges[i].b];
      int x0 = t.X(m0);
      int x1 = t.X(m1);
      int y0 = t.Y(m0);
      int y1 = t.Y(m1);
      c.drawLine(x0, y0, x1, y1, track_paint);
    }
  }

}

// vim:et:sw=2:sts=2
//

