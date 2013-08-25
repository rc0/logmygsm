// Copyright (c) 2013, Richard P. Curnow
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

class Odometer {

  private Merc28 xlast;

  private double distance;

  // The amount by which the position has to move to increment the distance
  // travelled and replace the reference point.  If too low, noise whilst
  // standing still will get added and make the estimated distance too large.
  // If too high, small features in the track could get dropped (e.g. corners
  // will be cut and the tips of U-turns missed). These would make the
  // estimated distance too small.
  // 
  // There's nothing much we can do to avoid side-to-side noise along a
  // straight track.

  static final double THRESHOLD = 5.0;

  double get_metres_covered() {
    return distance;
  }

  void reset() {
    distance = 0.0;
    xlast = null;
  }

  void append(Merc28 xnew) {
    if (xlast != null) {
      double step;
      step = xlast.metres_away(xnew);
      if (step > THRESHOLD) {
        distance += step;
        xlast = xnew;
      }
    } else {
      xlast = xnew;
    }
  }
}

// vim:et:sw=2:sts=2
//
