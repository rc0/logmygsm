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
import android.util.AttributeSet;

public class WaypointEditMap extends Map {

  public WaypointEditMap(Context _context, AttributeSet _attrs) {
    super(_context, _attrs);
  }

  void add_waypoint() {
    Logger.mWaypoints.add(display_pos);
    invalidate();
  }

  void delete_waypoint() {
    if (Logger.mWaypoints.delete(display_pos, pixel_shift)) {
      invalidate();
    }
  }

  void delete_visible_waypoints() {
    int adjusted_pixel_shift;
    if (mScaled) {
      adjusted_pixel_shift = pixel_shift - 1;
    } else {
      adjusted_pixel_shift = pixel_shift;
    }
    if (Logger.mWaypoints.delete_visible(display_pos, adjusted_pixel_shift, getWidth(), getHeight() )) {
      invalidate();
    }
  }

  void delete_all_waypoints() {
    Logger.mWaypoints.delete_all();
    invalidate();
  }

  void set_destination_waypoint() {
    Logger.mWaypoints.set_destination(display_pos, pixel_shift);
    invalidate();
  }

  void add_landmark() {
    Logger.mLandmarks.add(display_pos);
    invalidate();
  }

  void delete_landmark() {
    if (Logger.mLandmarks.delete(display_pos, pixel_shift)) {
      invalidate();
    }
  }

}

// vim:et:sw=2:sts=2
//

