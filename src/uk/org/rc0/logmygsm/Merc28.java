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
  Merc28(int x, int y) {
    X = x;
    Y = y;
  }
  Merc28(Merc28 orig) {
    X = orig.X;
    Y = orig.Y;
  }

}

// vim:et:sw=2:sts=2
