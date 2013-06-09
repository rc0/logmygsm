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

class RawLogger {
  private Backend log;
  private boolean enabled;

  RawLogger (boolean _enabled) {
    log = null;
    enabled = _enabled;
  }

  private void write(String tag, String data) {
    if (log == null) {
      log = new Backend("raw_", "", null);
    }
    long now = System.currentTimeMillis();
    long seconds = now / 1000;
    long millis = now % 1000;
    String all = String.format("%10d.%03d %2s %s\n",
        seconds, millis,
        tag, data);
    log.write(all);
  }

  void close () {
    if (log != null) {
      log.close();
    }
  }

  void log_asu () {
    if (enabled) {
      String data = String.format("%d", Logger.lastASU);
      write("AS", data);
    }
  }

  void log_cell () {
    if (enabled) {
      String data = String.format("%10d %10d %s",
          Logger.lastCid, Logger.lastLac,
          Logger.lastMccMnc);
      write("CL", data);
    }
  }

  void log_service_state () {
    if (enabled) {
      String data = String.format("%c", Logger.lastState);
      write("ST", data);
    }
  }

  void log_network_type () {
    if (enabled) {
      String data = String.format("%c %d", Logger.lastNetworkType, Logger.lastNetworkTypeRaw);
      write("NT", data);
    }
  }

  void log_bad_location () {
    if (enabled) {
      write("LB", "-- bad --");
    }
  }

  void log_raw_location () {
    if (enabled) {
      String data = String.format("%12.7f %12.7f %3d",
          Logger.lastLat, Logger.lastLon, Logger.lastAcc);
      write("LC", data);
    }
  }

  void log_location_disabled () {
    if (enabled) {
      write("LD", "-- disabled --");
    }
  }

  void log_location_enabled () {
    if (enabled) {
      write("LE", "-- enabled --");
    }
  }

  void log_location_status () {
    if (enabled) {
      // This seems to log every second - very wasteful!
      //String data = String.format("%d %d %d %d",
      //    last_n_sats, last_fix_sats, last_ephem_sats, last_alman_sats);
      //writeRaw("LS", data);
    }
  }
}

// vim:et:sw=2:sts=2
