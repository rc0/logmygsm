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

class Waypoints {

  static final private String TAG = "Waypoints";

  // ---------------------------

  static class Point extends Merc28 {
    boolean zombie;
    int index;
    Point(Merc28 pos) {
      super(pos);
      this.zombie = false;
      this.index = -1;
    }
    Point(int x, int y) {
      super(x, y);
      this.zombie = false;
      this.index = -1;
    }
  }

  private ArrayList<Point> points;
  private Point destination = null;
  private Linkages mLinkages = null;
  private Paint marker_paint;
  private Paint thick_marker_paint;
  private Paint track_paint;

  static final private String TAIL = "waypoints.txt";

  Waypoints() {
    restore_state_from_file();

    marker_paint = new Paint();
    marker_paint.setStrokeWidth(3);
    marker_paint.setColor(Color.argb(0xa0, 0x80, 0x00, 0x20));
    marker_paint.setStyle(Paint.Style.STROKE);

    thick_marker_paint = new Paint();
    thick_marker_paint.setStrokeWidth(6);
    thick_marker_paint.setColor(Color.argb(0xa0, 0x80, 0x00, 0x20));
    thick_marker_paint.setStyle(Paint.Style.STROKE);

    track_paint = new Paint();
    track_paint.setStrokeWidth(14);
    track_paint.setColor(Color.argb(0x38, 0x80, 0x00, 0x20));
    track_paint.setStyle(Paint.Style.STROKE);
    track_paint.setStrokeCap(Paint.Cap.ROUND);

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
      if (destination == null) {
        bw.write(String.format("-1\n", destination));
      } else {
        bw.write(String.format("%d\n", destination.index));
      }
      bw.close();
    } catch (IOException e) {
    }
  }

  private void restore_state_from_file() {
    points = new ArrayList<Point> ();
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
          points.add(new Point(x, y));
        }
        line = br.readLine();
        int destination_index = Integer.parseInt(line);
        if ((destination_index < 0) || (destination_index >= points.size())) {
          destination = null;
        } else {
          destination = points.get(destination_index);
        }
        br.close();
      } catch (IOException e) {
        failed = true;
      } catch (NumberFormatException n) {
        failed = true;
      }
    }
    if (failed) {
      points = new ArrayList<Point> ();
    }
    tidy();
  }

  // ---------------------------

  private void tidy() {
    // Bring the points array and everything referencing its entries back into a clean state
    if ((destination != null) && destination.zombie) {
      destination = null;
    }
    ArrayList<Point> new_points = new ArrayList<Point>();
    int n = points.size();
    for (int i=0, new_index=0; i<n; i++) {
      Point p = points.get(i);
      if (!p.zombie) {
        p.index = new_index++;
        new_points.add(p);
      }
    }
    points = new_points;
    mLinkages = null;
  }

  // ---------------------------

  void add(Merc28 pos) {
    points.add(new Point(pos));
    tidy(); // not very efficient!
  }

  private Point find_closest_point(Merc28 pos, int pixel_shift) {
    Point victim = null;
    int closest;
    int n = points.size();
    closest = 0;
    for (int i=0; i<n; i++) {
      Point p = points.get(i);
      int dx = (p.X - pos.X) >> pixel_shift;
      int dy = (p.Y - pos.Y) >> pixel_shift;
      int d = Math.abs(dx) + Math.abs(dy);
      if ((victim == null) ||
          (d < closest)) {
        closest = d;
        victim = p;
      }
    }
    return victim;
  }

  // Return value is true if a deletion successfully occurred, false if no point was
  // close enough to 'pos' to qualify.  Only delete the point that is 'closest'
  boolean delete(Merc28 pos, int pixel_shift) {
    Point victim;
    victim = find_closest_point(pos, pixel_shift);
    if (victim == null) {
      return false;
    } else {
      victim.zombie = true;
      tidy();
      return true;
    }
  }

  boolean delete_visible(Merc28 pos, int pixel_shift, int width, int height) {
    int w2 = width >> 1;
    int h2 = height >> 1;
    int n = points.size();
    boolean did_any = false;
    // work from the top down so that the indices of the points still to do
    // stay the same.
    for (int i=n-1; i>=0; i-- ) {
      Point p = points.get(i);
      int dx = (p.X - pos.X) >> pixel_shift;
      int dy = (p.Y - pos.Y) >> pixel_shift;
      if ((Math.abs(dx) < w2) &&
          (Math.abs(dy) < h2)) {
        did_any = true;
        p.zombie = true;
      }
    }
    if (did_any) {
      tidy();
    }
    return did_any;
  }

  void delete_all() {
    int n = points.size();
    for (int i=0; i<n; i++) {
      points.get(i).zombie = true;
    }
    tidy();
  }

  // ---------------------------

  boolean set_destination(Merc28 pos, int pixel_shift) {
    destination = find_closest_point(pos, pixel_shift);
    mLinkages = null; // crude way to force recalculation of mesh + minumum distances
    return true;
  }

  // ---------------------------


  // ---------------------------

  private final static int RADIUS = 7;
  private final static int RADIUS2 = RADIUS + RADIUS;

  // pos is the position of the centre-screen
  void draw(Canvas c, Transform t, boolean do_show_track) {
    int n = points.size();
    for (int i = 0; i < n; i++) {
      Merc28 p = points.get(i);
      int x = t.X(p);
      int y = t.Y(p);
      if ((destination != null) && (i == destination.index)) {
        c.drawCircle(x, y, (float) RADIUS2, thick_marker_paint);
      }
      c.drawCircle(x, y, (float) RADIUS, marker_paint);
      c.drawPoint(x, y, marker_paint);
    }
    if (do_show_track) {
      draw_track(c, t);
    }
  }

  // ---------------------------

  private void calculate_linkages() {
    if (mLinkages == null) {
      mLinkages = new Linkages(points, destination);
    }
  }

  // ---------------------------

  private void draw_track(Canvas c, Transform t) {
    calculate_linkages();
    Linkages.Edge[] edges = mLinkages.get_edges();
    for (int i = 0; i<edges.length; i++) {
      Merc28 m0 = edges[i].m0;
      Merc28 m1 = edges[i].m1;
      c.drawLine(t.X(m0), t.Y(m0), t.X(m1), t.Y(m1), track_paint);
    }
  }

  // ---------------------------

  Linkages.Edge[] get_edges () {
    calculate_linkages();
    return mLinkages.get_edges();
  }

  // ---------------------------

  static class Routing {
    // to return the direction to the next waypoint and the total
    // distance to the destination

    float ux, uy; // unit vector towards next waypoint
    //
    // distance to destination in metres, through the mesh plus the
    // direct path from the given point to the waypoint
    float d;

    Routing(Merc28 here, Merc28 there, float onward_distance) {
      ux = (float) (there.X - here.X);
      uy = (float) (there.Y - here.Y);
      float scale = 1.0f / FloatMath.sqrt(ux*ux + uy*uy);
      ux *= scale;
      uy *= scale;
      d = (float) here.metres_away(there) + onward_distance;
    }
  };

  Routing[] get_routings(Merc28 pos) {
    return mLinkages.get_routings(pos);
  }

}

// vim:et:sw=2:sts=2
//

