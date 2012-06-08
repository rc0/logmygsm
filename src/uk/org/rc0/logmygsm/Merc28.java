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

  // Convert GPS altitude into estimated height above mean sea level
  static double odn(double alt, double lat, double lon) {
    double P, Q;
    P = (2.0/9.0) * (lat - 54.5);
    Q = 0.25 * (lon + 2.0);
    return alt -50.7 + 4.4*Q + 0.6*Q*Q - 1.7*P - 0.5*P*Q + 2.3*P*P;
  }

  // ------------------------------------------------------------------
  // Deal with grid references

  static final char[] letters1 = "VQLFAWRMGBXSNHCYTOJDZUPKE" .toCharArray();
  static final char[] letters0 = "SNHTOJ" .toCharArray();

  String grid_ref() {
    double x, y;
    x = (double) X / scale;
    y = (double) Y / scale;
    x = 72.0 * (x - 0.494444);
    y = 36.0 * (y - 0.318700);

    double y2 = y*y;
    double y3 = y*y2;
    double y4 = y*y3;
    double y5 = y*y4;
    double y6 = y*y5;

    double x2 = x*x;
    double x3 = x*x2;
    double x4 = x*x3;
    double x5 = x*x4;

    double E0 = 400087.5 - 3.9*y;
    double E1 = 323810.0 + 45906.2*y + 1579*y2 - 37.6*y3 - 1.9*y4;
    double E3 = -133.5 + 60.0*y + 18.9*y2 + 1.7*y3;
    double E5 = -0.5;

    double E = E0 + E1*x + E3*x3 + E5*x5;

    double N0 = 511732.7 - 646151.6*y - 45594.0*y2 - 1016.5*y3 + 122.3*y4 + 14.8*y5 + 0.6*y6;
    double N1 = -1.9;
    double N2 = 11502.6 + 799.1*y - 180.0*y2 - 37.7*y3 - 2.5*y4;
    double N4 = 7.5 + 4.7*y + 0.6*y2;

    double N = N0 + N1*x + N2*x2 + N4*x4;

    if ((E < 0.0) || (E >= 700000.0) || (N < 0.0) || (N >= 1300000)) {
      return "NOT IN UK";
    }

    int e = (int)(0.5 + E);
    int n = (int)(0.5 + N);
    int e0 = e / 100000;
    int e1 = e % 100000;
    int n0 = n / 100000;
    int n1 = n % 100000;

    char c0 = letters0[3*(e0/5) + (n0/5)];
    char c1 = letters1[5*(e0%5) + (n0%5)];

    return String.format("%1c%1c %05d %05d", c0, c1, e1, n1);
  }

}

// vim:et:sw=2:sts=2
