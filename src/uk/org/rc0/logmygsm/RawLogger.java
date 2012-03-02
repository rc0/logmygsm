package uk.org.rc0.logmygsm;

class RawLogger {
  private Backend log;

  RawLogger () {
    log = null;
  }

  private void write(String tag, String data) {
    if (log == null) {
      log = new Backend("raw_", null);
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
    String data = String.format("%d", Logger.lastASU);
    write("AS", data);
  }

  void log_cell () {
    String data = String.format("%10d %10d %s",
        Logger.lastCid, Logger.lastLac,
        Logger.lastMccMnc);
    write("CL", data);
  }

  void log_service_state () {
    String data = String.format("%c", Logger.lastState);
    write("ST", data);
  }

  void log_network_type () {
    String data = String.format("%c %d", Logger.lastNetworkType, Logger.lastNetworkTypeRaw);
    write("NT", data);
  }

  void log_bad_location () {
    write("LB", "-- bad --");
  }

  void log_raw_location () {
    String data = String.format("%12.7f %12.7f %3d",
        Logger.lastLat, Logger.lastLon, Logger.lastAcc);
    write("LC", data);
  }

  void log_location_disabled () {
    write("LD", "-- disabled --");
  }

  void log_location_enabled () {
    write("LE", "-- enabled --");
  }

  void log_location_status () {
    // This seems to log every second - very wasteful!
    //String data = String.format("%d %d %d %d",
    //    last_n_sats, last_fix_sats, last_ephem_sats, last_alman_sats);
    //writeRaw("LS", data);
  }
}

// vim:et:sw=2:sts=2
