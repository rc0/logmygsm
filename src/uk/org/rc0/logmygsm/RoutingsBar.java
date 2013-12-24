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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

class RoutingsBar {

  private int text_size;
  private float dest_arrow_1;
  private float dest_arrow_2;
  private Paint dest_text;
  private Paint dest_backdrop, dest_arrow;

  RoutingsBar(int text_size) {
    this.text_size = text_size;
    float height = 1.5f * text_size;
    dest_arrow_1 = 0.3f * height;
    dest_arrow_2 = 0.5f * dest_arrow_1;

    dest_text = new Paint();
    dest_text.setColor(Color.argb(0xc0, 0x00, 0x30, 0x10));
    dest_text.setTextSize(text_size);
    dest_text.setTextAlign(Paint.Align.CENTER);
    dest_text.setTypeface(Typeface.DEFAULT_BOLD);

    dest_backdrop = new Paint();
    dest_backdrop.setColor(Color.argb(0x60, 0xff, 0xff, 0xff));
    dest_backdrop.setStyle(Paint.Style.FILL);

    dest_arrow = new Paint();
    dest_arrow.setColor(Color.argb(0xc0, 0x00, 0x30, 0x10));
    dest_arrow.setStyle(Paint.Style.FILL);

  }

  private void draw_arrow(Canvas c, float ox, float oy, Waypoints.Routing route) {
    float ux = route.ux;
    float uy = route.uy;
    float xx0 = ox + dest_arrow_1 * ux;
    float xx1 = ox - dest_arrow_1 * ux - dest_arrow_2 * uy;
    float xx2 = ox - dest_arrow_1 * ux + dest_arrow_2 * uy;
    float yy0 = oy + dest_arrow_1 * uy;
    float yy1 = oy - dest_arrow_1 * uy + dest_arrow_2 * ux;
    float yy2 = oy - dest_arrow_1 * uy - dest_arrow_2 * ux;
    Path path = new Path();
    path.moveTo(xx0, yy0);
    path.lineTo(xx1, yy1);
    path.lineTo(xx2, yy2);
    c.drawPath(path, dest_arrow);
  }

  void show_routings(Canvas c, int w, int h, Merc28 centre, int button_size) {
    Waypoints.Routing[] routes = Logger.mWaypoints.get_routings(centre);
    if (routes != null) {
      float x0 = 0.0f;
      float x1 = (float) w;
      float y0 = (float)(h - button_size);
      float y1 = (float) h;
      c.drawRect(x0, y0, x1, y1, dest_backdrop);
      if (routes.length == 1) {
        String distance;
        distance = Util.pretty_distance(routes[0].d);
        float swidth = dest_text.measureText(distance);
        float ox;
        if (routes[0].ux >= 0) {
          ox = (float) (w>>1) + 0.5f*swidth + dest_arrow_1;
        } else {
          ox = (float) (w>>1) - 0.5f*swidth - dest_arrow_1;
        }
        float oy = (float) (h - (button_size >> 1));
        c.drawText(distance, (float)(w>>1), (float)(h-16), dest_text);
        draw_arrow(c, ox, oy, routes[0]);
      } else {
        // presumably 2
        Waypoints.Routing left, right;
        if (routes[0].ux > routes[1].ux) {
          left = routes[1];
          right = routes[0];
        } else {
          left = routes[0];
          right = routes[1];
        }
        String distanceA, distanceB;
        distanceA = Util.pretty_distance(left.d);
        distanceB = Util.pretty_distance(right.d);
        String distance = distanceA + " " + distanceB;
        float swidth = dest_text.measureText(distance);
        float oxA = (float) (w>>1) - 0.5f*swidth - dest_arrow_1;
        float oxB = (float) (w>>1) + 0.5f*swidth + dest_arrow_1;
        float oy = (float) (h - (button_size >> 1));
        c.drawText(distance, (float)(w>>1), (float)(h-16), dest_text);
        draw_arrow(c, oxA, oy, left);
        draw_arrow(c, oxB, oy, right);
      }
    }
  }
}

// vim:et:sw=2:sts=2
//


