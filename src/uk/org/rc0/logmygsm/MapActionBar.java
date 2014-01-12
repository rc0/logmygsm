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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

class MapActionBar {
  // This is the bar that is instanced across the top of the map

  interface Callbacks {
    void toggle_scaled();
    void zoom_in();
    void zoom_out();
    void cycle_left();
    void cycle_right();
  }

  // ---------------------------------

  private Callbacks target;
  private Paint paint;
  private Bitmap cycle_left_bm;
  private Bitmap cycle_right_bm;
  private Bitmap binocular_bm;
  private Bitmap zoom_out_bm;
  private Bitmap zoom_in_bm;
  private int last_w = -1;
  private int last_button_height = -1;
  private int chunk = -1;
  private int button_width_2;
  private int button_height_2;
  private int button_radius;
  private int button_offset;
  private int button_half_line;
  private Rect r_zin, r_zout, r_right, r_left, r_2x;

  private final int N_BUTTONS = 5;

  // ---------------------------------

  MapActionBar(Context context, Callbacks target) {
    Resources res = context.getResources();

    this.target = target;
    paint = new Paint();
    paint.setStrokeWidth(4);
    paint.setColor(Color.BLACK);
    paint.setStyle(Paint.Style.STROKE);

    cycle_left_bm  = BitmapFactory.decodeResource(res,R.drawable.cycle_left_64);
    cycle_right_bm = BitmapFactory.decodeResource(res,R.drawable.cycle_right_64);
    binocular_bm   = BitmapFactory.decodeResource(res,R.drawable.binocular_64);
    zoom_out_bm    = BitmapFactory.decodeResource(res,R.drawable.zoom_out_64);
    zoom_in_bm     = BitmapFactory.decodeResource(res,R.drawable.zoom_in_64);
  }

  private void update_dimensions(int w, int button_height) {
    chunk = w / (2*N_BUTTONS);
    button_height_2 = button_height >> 1;
    button_radius = button_height_2 - (button_height_2 >> 2);
    button_offset = button_radius + (button_radius >> 1);
    button_half_line = button_radius - (button_radius >> 2);

    button_width_2 = button_height_2;
    if (button_width_2 > chunk) {
      button_width_2 = chunk;
    }

    r_zin = new Rect(chunk*1 - button_width_2, 0, chunk*1 + button_width_2, button_height);
    r_zout = new Rect(chunk*9 - button_width_2, 0, chunk*9 + button_width_2, button_height);
    r_left = new Rect(chunk*3 - button_width_2, 0, chunk*3 + button_width_2, button_height);
    r_right = new Rect(chunk*7 - button_width_2, 0, chunk*7 + button_width_2, button_height);
    r_2x = new Rect(chunk*5 - button_width_2, 0, chunk*5 + button_width_2, button_height);

    last_w = w;
    last_button_height = button_height;
  }


  public void draw(Canvas c, int w, int button_height)
  {
    if ((w != last_w) || (last_button_height != button_height)) {
      update_dimensions(w, button_height);
    }

    c.drawBitmap(zoom_in_bm,     null, r_zin,   null);
    c.drawBitmap(zoom_out_bm,    null, r_zout,  null);
    c.drawBitmap(cycle_left_bm,  null, r_left,  null);
    c.drawBitmap(cycle_right_bm, null, r_right, null);
    c.drawBitmap(binocular_bm,   null, r_2x,    null);
  }

  void decode(int x) {
    int which = x / (chunk << 1); // assume truncate
    switch (which) {
      case 0: target.zoom_in(); break;
      case 1: target.cycle_left(); break;
      case 2: target.toggle_scaled(); break;
      case 3: target.cycle_right(); break;
      case 4: target.zoom_out(); break;
      default:
        break;
    }
  }

}

// vim:et:sw=2:sts=2
//

