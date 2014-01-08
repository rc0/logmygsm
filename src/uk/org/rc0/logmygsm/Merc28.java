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
  static final int shift_mask = (1<<shift) - 1;
  static final double scale = (double)(1<<shift);
  static final double iscale = 1.0 / scale;

  Merc28(double lat, double lon) {
    double x, yy, y, XX, YY;
    x = Math.toRadians(lon);
    yy = Math.toRadians(lat);
    y = Math.log(Math.tan(0.5*yy + 0.25*Math.PI));
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

  static final double EARTH_RADIUS_IN_METRES = 6378137.0;
  static final double K = (2.0 * Math.PI * EARTH_RADIUS_IN_METRES);
  static final double K0 = 1.0/K;
  static final double K2 = 19.42975297/K;
  static final double K4 = 74.22319781/K;
  static final double J0 = 0.99330562;
  static final double J2 = 0.18663111;
  static final double J4 = -1.45510549;

  double metres_away(Merc28 other) {
    // This approximation has worst-case error around 0.1% for distances up to
    // at least 200km and latitudes up to 71 deg
    double dx = (double)(X - other.X) * iscale;
    double dy = (double)(Y - other.Y) * iscale;
    double ay = (0.5 * iscale) * (double)(Y + other.Y);
    double az = ay - 0.5;
    double az2 = az * az;
    double az4 = az2 * az2;
    double scale_factor_y = J0 + J2*az2 + J4*az4;
    double scale_factor_total = K0 + K2*az2 + K4*az4;
    double Dy = dy * scale_factor_y;
    double d = Math.sqrt(dx*dx + Dy*Dy);
    return d / scale_factor_total;
  }

  double bearing_to(Merc28 other) {
    double zx = (double)(other.X - X);
    double zy = (double)(other.Y - Y);
    double za = (180 / Math.PI) * Math.atan2(zx, -zy);
    if (za < 0.0) {
      za += 360.0;
    }
    return za;
  }

  double to_lon() {
    double xwrap = X & shift_mask;
    double xx = iscale * (double) xwrap;
    return 360.0 * (xx - 0.5);
  }

  static final double P1 = 179.9989063857;
  static final double P3 = 507.2276380744;
  static final double P5 = 176.2675623673;
  static final double Q0 = 1.0000000000;
  static final double Q2 = 4.4623636863;
  static final double Q4 = 4.2727924855;
  static final double Q6 = 0.4175728442;

  // See
  // http://rc0rc0.wordpress.com/2013/11/25/approximating-the-gudermannian-function/
  double to_lat()
  {
    double y = iscale * (double) this.Y;
    double z = 1.0 - 2.0*y;
    double z2 = z*z;
    double z4 = z2*z2;
    double t, b0, b4, b, t2;
    b4 = Q4 + Q6*z2;
    b0 = Q0 + Q2*z2;
    t2 = P3 + P5*z2;
    b = b0 + b4*z4;
    t = P1 + t2*z2;
    return z*t/b;
  }

  // double to_lat_old() {
  //   double t = (1.0 - 2.0*iscale*(double)Y) * Math.PI;
  //   double tt = (2.0 * Math.atan(Math.exp(t))) - 0.5*Math.PI;
  //   return Math.toDegrees(tt);
  // }

  // See
  // http://issuu.com/rc0rc0/docs/wmen_paper
  // which explains the approximations that follow

  // Convert GPS altitude into estimated height above mean sea level
  static double odn(double alt, double lat, double lon) {
    double P, Q;
    P = (2.0/9.0) * (lat - 54.5);
    Q = 0.25 * (lon + 2.0);
    return alt - 50.1 + 6.1*Q - 1.1*P - 1.5*P*Q;
  }

  // ------------------------------------------------------------------
  // Deal with grid references

  static final char[] letters1 = "VQLFAWRMGBXSNHCYTOJDZUPKE" .toCharArray();
  static final char[] letters0 = "SNHTOJ" .toCharArray();

  private String inner_grid_ref_5m(String fmt) {
    double x, y;
    x = (double) X / scale;
    y = (double) Y / scale;

    double alpha, beta;
    double t90, t91, t92, t93, t94, t95, t96, t97;
    double t98, t99, t100;
    double t101, t102, t103, t104, t105, t106, t107, t108;
    double t109;
    double alpha2;
    double beta2;
    double beta4;
    double E, N;
    alpha = 61.000 * (x - 0.4944400930);
    beta = 36.000 * (y - 0.3126638550);
    if ((alpha < -1.0) || (alpha > 1.0) || (beta < -1.0) || (beta > 1.0)) {
      return "NOT IN UK";
    }
    alpha2 = alpha * alpha;
    beta2 = beta * beta;
    beta4 = beta2 * beta2;
    t90 = (400001.47) + (-17.07)*beta;
    t91 = (370523.38) + (53326.92)*beta;
    t92 = (2025.68) + (-241.27)*beta;
    t93 = t91 + t92*beta2;
    t94 = t93 + (-41.77)*beta4;
    t95 = t90 + t94*alpha;
    t96 = (-11.21)*beta;
    t97 = t96 + (14.84)*beta2;
    t98 = (-237.68) + (82.89)*beta;
    t99 = t98 + (41.21)*beta2;
    t100 = t97 + t99*alpha;
    E = t95 + t100*alpha2;
    t101 = (649998.33) + (-13.90)*alpha;
    t102 = t101 + (15782.38)*alpha2;
    t103 = (-626496.42) + (1220.67)*alpha2;
    t104 = t102 + t103*beta;
    t105 = (-44898.11) + (10.01)*alpha;
    t106 = t105 + (-217.21)*alpha2;
    t107 = (-1088.27) + (-49.59)*alpha2;
    t108 = t106 + t107*beta;
    t109 = t104 + t108*beta2;
    N = t109 + (107.47)*beta4;
    if ((E < 0.0) || (E >= 700000.0) || (N < 0.0) || (N >= 1300000)) {
      return "NOT IN UK";
    }

    int e = (int)(0.5 + E) / 10;
    int n = (int)(0.5 + N) / 10;
    int e0 = e / 10000;
    int e1 = e % 10000;
    int n0 = n / 10000;
    int n1 = n % 10000;

    char c0 = letters0[3*(e0/5) + (n0/5)];
    char c1 = letters1[5*(e0%5) + (n0%5)];

    return String.format(fmt, c0, c1, e1, n1);
  }

  String grid_ref_5m() {
    return inner_grid_ref_5m("%1c%1c %04d %04d");
  }

  String grid_ref_5m_nosp() {
    return inner_grid_ref_5m("%1c%1c%04d%04d");
  }
}

// vim:et:sw=2:sts=2
