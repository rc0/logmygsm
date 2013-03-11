// Copyright (c) 2012, 2013, Richard P. Curnow
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
import java.util.LinkedList;

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

  private static boolean download(String the_url, String the_dest, boolean forced) {
    boolean result;
    result = false;

    try {
      File file = new File(the_dest);
      File dir = file.getParentFile();
      if (!dir.exists()) {
        dir.mkdirs();
      }
      if ((forced || !file.exists()) &&
          (file.lastModified() < TileStore.get_epoch())) {
        URI uri = new URI("http", the_url, null);
        HttpGet get = new HttpGet(uri);
        HttpClient client = new DefaultHttpClient();
        HttpResponse response = client.execute(get);
        HttpEntity entity = response.getEntity();

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

  private static class OneJob {
    String url;
    String dest;
    boolean forced;

    OneJob(String _url, String _dest, boolean _forced) {
      url = _url;
      dest = _dest;
      forced = _forced;
    }
  }

  private static class DownloadThread extends Thread {
    private LinkedList<OneJob> jobs;

    DownloadThread(LinkedList<OneJob> _jobs) {
      jobs = _jobs;
      setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run () {
      boolean result;
      result = true; // unused
      while (jobs.size() > 0) {
        OneJob job = jobs.removeFirst();
        result = download(job.url, job.dest, job.forced);
      }
      mHandler.post (new DownloadResponse(result));
    }
  }

  // forced=1 means download even if tile exists (e.g. if you know the map has been updated)
  // forced=0 means download only if the tile is missing
  // in either case, never download more than once during a run of the app

  static void start_multiple_fetch(LinkedList<TileStore.TilePos> targets, boolean forced, Context context) {
    if (is_busy) {
      Logger.announce(context, "Already downloading");
      return;
    }

    LinkedList<OneJob> jobs;
    jobs = new LinkedList<OneJob> ();

    while (targets.size() > 0) {
      TileStore.TilePos target = targets.removeFirst();
      String url = target.map_source.get_download_url(target.zoom, target.x, target.y);
      String dest = target.map_source.get_tile_path(target.zoom, target.x, target.y);
      if (url != null) {
        jobs.add(new OneJob(url, dest, forced));
      }
    }
    is_busy = true;
    (new DownloadThread(jobs)).start();
  }

}

//
// vim:et:sw=2:sts=2

