package uk.org.rc0.logmygsm;

import android.text.format.DateFormat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Backend {
  private File file;
  private FileWriter writer;
  private Logger mService;

  public Backend(String prefix, Logger the_service) {
    String basePath = "/sdcard";
    String ourDir = "LogMyGsm/logs";
    CharSequence cs = DateFormat.format("yyyyMMdd-kkmmss", System.currentTimeMillis());
    String timedFileName = prefix + cs.toString() + ".log";
    String fullPath = basePath + "/" + ourDir + "/" + timedFileName;
    mService = the_service;

    try {
      File root = new File(basePath, ourDir);
      if (!root.exists()) {
          root.mkdirs();
      }
      file = new File(root, timedFileName);
      writer = new FileWriter(file);
      if (mService != null) {
        mService.announce("Opened logfile");
      }
    } catch (IOException e) {
      file = null;
      writer = null;
    }
    if (writer == null) {
      if (mService != null) {
        mService.announce("COULD NOT LOG TO " + fullPath);
      }
    }
  }

  public void write(String data) {
    if (writer != null) {
      try {
        writer.append(data);
      } catch (IOException e) {
      }
    }
  }

  public void close() {
    if (writer != null) {
      if (mService != null) {
        mService.announce("Closing logfile");
      }
      try {
        writer.flush();
        writer.close();
      } catch (IOException e) {
      }
    }
    writer = null;
  }

}

// vim:et:sw=2:sts=2
