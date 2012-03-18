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

import java.lang.Math;

// Helper class to work with my Merc28 coordinate system.
//
// 'Merc' is the from the 'Mercator-like' projection used by openstreetmap
// which maps -180W, 85.foo N to 0.0,0.0 and +180E, 85.foo S to 1.0,1.0
//
// The '28' is from scaling these fractional coordinates by 2**28 so we
// can work more cheaply with integer arithmetic (e.g. use right shift to get
// the X,Y of the tile containing a location).

class Merc28 {
  int X;
  int Y;

  static final int shift = 28;
  static final double scale = (double)(1<<shift);

  Merc28(double lat, double lon) {
    double x, yy, y, XX, YY;
    x = Math.toRadians(lon);
    yy = Math.toRadians(lat);
    y = Math.log(Math.tan(yy) + 1.0/Math.cos(yy));
    XX = 0.5 * (1.0 + x/Math.PI);
    YY = 0.5 * (1.0 - y/Math.PI);
    X = (int) Math.floor(XX * scale);
    Y = (int) Math.floor(YY * scale);
  }
  Merc28(int _X, int _Y) {
    X = _X;
    Y = _Y;
  }
  Merc28(Merc28 orig) {
    X = orig.X;
    Y = orig.Y;
  }

  void copy_from(Merc28 src) {
    X = src.X;
    Y = src.Y;
  }

  static double metres_per_pixel = (25220000.0 / scale);
  static final double EARTH_RADIUS_IN_METRES = 6378140.0;

  static void update_latitude(double lat) {
    metres_per_pixel =
      2.0 * Math.PI *
      EARTH_RADIUS_IN_METRES *
      Math.cos(Math.toRadians(lat)) / scale;
  }

  double metres_away(Merc28 other) {
    double zx = (double)(X - other.X);
    double zy = (double)(Y - other.Y);
    double zd = Math.sqrt(zx*zx + zy*zy) * metres_per_pixel;
    return zd;
  }

}

// vim:et:sw=2:sts=2
