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

// Deal with fetching map tiles off the net
//

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import java.lang.Runnable;
import java.net.URI;
import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

class Downloader {

  static private boolean is_busy;
  static private Context mContext;
  static private Handler mHandler;
  static final private String TAG = "Downloader";

  static void init (Context _app_context) {
    mContext = _app_context;
    mHandler = new Handler();
    is_busy = false;
  }

  private static boolean download(String the_url, String the_dest) {
    boolean result;
    result = false;

    try {
      URI uri = new URI("http", the_url, null);
      HttpGet get = new HttpGet(uri);
      HttpClient client = new DefaultHttpClient();
      HttpResponse response = client.execute(get);
      HttpEntity entity = response.getEntity();

      File file = new File(the_dest);
      File dir = file.getParentFile();
      if (!dir.exists()) {
        dir.mkdirs();
      }
      OutputStream out = null;
      try {
        out = new BufferedOutputStream(new FileOutputStream(file));
        entity.writeTo(out);
        result = true;
      } finally {
        if (out != null) {
          out.close();
        }
      }
    } catch (Exception e) {
      //Log.i(TAG, "download excepted : " + e.getClass().getName() + " : " + e.getMessage());
    }
    return result;
  }

  private static class DownloadResponse implements Runnable {
    private boolean response;
    DownloadResponse(boolean _response) {
      response = _response;
    }
    @Override
    public void run() {
      // Crude, needs to be made finer-grained maybe...
      // Toss the whole tile cache then redraw.
      // It's the only way to get the newly fetched tile to be used
      is_busy = false;
      TileStore.invalidate();
      Intent intent = new Intent(Logger.UPDATE_GPS);
      mContext.sendBroadcast(intent);
    }
  }

  private static class DownloadThread extends Thread {
    private String url;
    private String dest;

    DownloadThread(String _url, String _dest) {
      url = new String(_url);
      dest = new String(_dest);
      setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run () {
      boolean result;
      result = download(url, dest);
      mHandler.post (new DownloadResponse(result));
    }
  }

  static void start_fetch(int zoom, MapSource map_source, int tile_x, int tile_y, Context context) {
    // If already fetching, post a "busy" toast
    // otherwise start the download in a thread.
    // Eventually, could have a queue of tiles.

    if (is_busy) {
      Logger.announce(context, "Already fetching a tile");
      return;
    }

    String url = map_source.get_download_url(zoom, tile_x, tile_y);
    if (url == null) {
      Logger.announce(context, "Cannot download this map");
      return;
    }

    String dest = map_source.get_tile_path(zoom, tile_x, tile_y);

    is_busy = true;
    (new DownloadThread(url, dest)).start();
  }

}

//
// vim:et:sw=2:sts=2

