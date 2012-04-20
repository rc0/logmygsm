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
import android.util.Log;
import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

// Meant to be instantiated as a member in the service
class Trail {

  private Logger mLogger;

  private ArrayList<Merc28> recent;
  private Merc28 last_point;
  private int n_old;
  private int[] x_old;
  private int[] y_old;
  private History mHistory;

  static final int splot_gap = 10;
  static final float splot_radius = 5.0f;

  private static final String TAG = "Trail";

  class PointArray {
    int n;
    int [] x;
    int [] y;

    PointArray () {
      n = 0;
      x = null;
      y = null;
    }

    PointArray (int nn, int [] xx, int [] yy) {
      n = nn;
      if (n > 0) {
        x = xx;
        y = yy;
      } else {
        x = null;
        y = null;
      }
    }

    PointArray (ArrayList<Merc28> zz) {
      n = zz.size();
      if (n > 0) {
        x = new int[n];
        y = new int[n];
        for (int i = 0; i < n; i++) {
          x[i] = zz.get(i).X;
          y[i] = zz.get(i).Y;
        }
      } else {
        x = null;
        y = null;
      }
    }

  }

  class History {
    private Merc28 x0;
    private Merc28 x1;

    History() {
      clear();
    }

    void clear () {
      x0 = null;
      x1 = null;
    }

    void add(Merc28 x) {
      x1 = x0;
      x0 = new Merc28(x);
    }

    Merc28 estimated_position() {
      int xpred, ypred;
      if (x0 != null) {
        if (x1 != null) {
          // x0 is newer, x1 is older
          xpred = (x0.X * 3 - x1.X) >> 1;
          ypred = (x0.Y * 3 - x1.Y) >> 1;
          return new Merc28(xpred, ypred);
        } else {
          return x0;
        }
      } else {
        return null;
      }
    }
  }


  Trail(Logger the_logger) {
    mLogger = the_logger;
    mHistory = new History();
    restore_state_from_file();
  }

  private void init() {
    recent = new ArrayList<Merc28> ();
    last_point = null;
    n_old = 0;
    x_old = null;
    y_old = null;
  }

  void clear() {
    init();
    mHistory.clear();
  }

  // Move to subclass of service
  void save_state_to_file() {
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

  void restore_state_from_file() {
    File file = new File("/sdcard/LogMyGsm/prefs/trail.txt");
    boolean failed = false;
    init();
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
    }
    if (failed) {
      init();
    }

    mLogger.announce(String.format("Loaded %d trail points", n_old));
  }

  // Skip points that are too close together to ever be visible on the map display
  void add_point(Merc28 p) {
    mHistory.add(p);
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
      recent.add(new Merc28(p));
      last_point = new Merc28(p);
    }
  }

  // Internal

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
      recent = new ArrayList<Merc28> ();
      // leave last_point alone
    } else {
      // leave 'old' arrays as they were
    }
  }

  // If this is being requested, it's because the tile cache is being rebuilt, so it's a good time
  // to accumulate the recent points onto the historical list
  PointArray get_historical() {
    return new PointArray(n_old, x_old, y_old);
  }

  Merc28 get_estimated_position() {
    return mHistory.estimated_position();
  }

  // OK unless we ever decide to use HUGE spots for the trail
  static final int MIN_CENTRE = -5;
  static final int MAX_CENTRE = 256 - MIN_CENTRE;

  static class Upto {
    int lx;
    int ly;
    int next;
    Upto() {
      lx = -256;
      ly = -256;
      next=0;
    }
  }

  void draw_recent_trail(Canvas c, int xnw, int ynw, int pixel_shift, Upto upto) {
    int n = recent.size();
    for (int i=upto.next; i<n; i++) {
      Merc28 p = recent.get(i);
      int sx = (p.X - xnw) >> pixel_shift;
      int sy = (p.Y - ynw) >> pixel_shift;
      boolean do_add = true;
      int manhattan = Math.abs(sx - upto.lx) + Math.abs(sy - upto.ly);
      if (manhattan < splot_gap) {
        do_add = false;
      }
      if (do_add) {
        // Don't even bother invoking the library if we're off-screen.
        // // Loose bounds to allow for 
        if ((sx >= MIN_CENTRE) && (sy >= MIN_CENTRE) && (sx < MAX_CENTRE) && (sy < MAX_CENTRE)) {
          c.drawCircle((float)sx, (float)sy, Trail.splot_radius, TileStore.trail_paint);
          c.drawCircle((float)sx, (float)sy, TileStore.TRAIL_DOT_SIZE, TileStore.trail_dot_paint);
          upto.lx = sx;
          upto.ly = sy;
        }
      }
    }
    upto.next = n;
    return;
  }

  int n_recent () {
    return recent.size();
  }
}


// vim:et:sw=2:sts=2

