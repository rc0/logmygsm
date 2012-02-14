package uk.org.rc0.helloandroid;

import java.lang.Math;

// Helper class to work with my Merc28 coordinate system.
//
// 'Merc' is the from the 'Mercator-like' projection used by openstreetmap
// which maps -180W, 85.foo N to 0.0,0.0 and +180E, 85.foo S to 1.0,1.0
//
// The '28' is from scaling these fractional coordinates by 2**28 so we
// can work more cheaply with integer arithmetic (e.g. use right shift to get
// the X,Y of the tile containing a location).

public class Merc28 {
  public int X;
  public int Y;

  static public final int shift = 28;
  static public final double scale = (double)(1<<shift);

  public Merc28(double lat, double lon) {
    double x, yy, y, XX, YY;
    x = Math.toRadians(lon);
    yy = Math.toRadians(lat);
    y = Math.log(Math.tan(yy) + 1.0/Math.cos(yy));
    XX = 0.5 * (1.0 + x/Math.PI);
    YY = 0.5 * (1.0 - y/Math.PI);
    X = (int) Math.floor(XX * scale);
    Y = (int) Math.floor(YY * scale);
  }
  public Merc28(int x, int y) {
    X = x;
    Y = y;
  }
  public Merc28(Merc28 orig) {
    X = orig.X;
    Y = orig.Y;
  }

  static public Merc28 predict(Merc28 older, Merc28 newer) {
    int xpred, ypred;
    if ((older != null) && (newer != null)) {
      xpred = (newer.X * 3 - older.X) >> 1;
      ypred = (newer.Y * 3 - older.Y) >> 1;
      return new Merc28(xpred, ypred);
    } else {
      return null;
    }
  }

}

// vim:et:sw=2:sts=2
