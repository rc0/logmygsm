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

import android.text.format.DateFormat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

class Backend {
  private File file;
  private FileWriter writer;
  private Logger mService;

  Backend(String prefix, Logger the_service) {
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

  void write(String data) {
    if (writer != null) {
      try {
        writer.append(data);
      } catch (IOException e) {
      }
    }
  }

  void close() {
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
