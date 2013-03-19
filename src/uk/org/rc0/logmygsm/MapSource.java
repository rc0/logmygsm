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

import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import java.io.File;

class MapSource {
  private String menu_name = "";
  private String path_segment = "";
  private int code;
  final private static String last_hope_path = "/sdcard/LogMyGsm/tiles";
  final private static String possible_paths[] = {
    "/sdcard/external_sd/Maverick/tiles",
    "/sdcard/Maverick/tiles",
    "/sdcard/external_sd/LogMyGsm/tiles",
    last_hope_path
  };

  private static String path_start = null;

  String get_menu_name() {
    return menu_name;
  }

  String get_tile_path(int zoom, int x, int y) {
    return String.format("%s/%s/%d/%d/%d.png.tile",
        path_start, path_segment, zoom, x, y);
  }

  // Override in sub-classes for map types that can support download
  String get_download_url(int zoom, int x, int y) {
    return null;
  }

  int get_code() {
    return code;
  }

  MapSource(String _menu_name, String _path_segment, int _code) {
    menu_name = _menu_name;
    path_segment = _path_segment;
    code = _code;
  }

  // Discover where the tiles are stored.  Share tiles with Maverick (-Pro) if
  // possible, otherwise use our own storage
  static void init() {
    for (int i = 0; i < possible_paths.length; i++) {
      File f = new File(possible_paths[i]);
      if (f.isDirectory()) {
        path_start = new String(possible_paths[i]);
        break;
      }
    }

    // We must force a path to exist otherwise there is nowhere to download new
    // tiles into
    if (path_start == null) {
      path_start = new String(last_hope_path);
      File dir = new File(path_start);
      dir.mkdirs();
    }
  }

}

// ------------------------------------------------------------------

class MapSource_Mapnik extends MapSource {

  MapSource_Mapnik(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  String get_download_url(int zoom, int x, int y) {
    return String.format("//a.tile.openstreetmap.org/%d/%d/%d.png", zoom, x, y);
  }

};

// ------------------------------------------------------------------

class MapSource_Cycle extends MapSource {

  MapSource_Cycle(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  String get_download_url(int zoom, int x, int y) {
    return String.format("//a.tile.opencyclemap.org/cycle/%d/%d/%d.png", zoom, x, y);
  }

};

// ------------------------------------------------------------------

class MapSource_OS extends MapSource {

  MapSource_OS(String _menu_name, String _path_segment, int _code) {
    super(_menu_name, _path_segment, _code);
  }

  static final char [] qk03 = "0123" . toCharArray();

  String get_download_url(int zoom, int x, int y) {
    char [] quadkey = new char[zoom];
    int i;
    for (i=0; i<zoom; i++) {
      int j = zoom - 1 - i;
      int xx = (x >> j) & 1;
      int yy = (y >> j) & 1;
      quadkey[i] = qk03[xx + (yy<<1)];
    }
    return new String("//ecn.t3.tiles.virtualearth.net/tiles/r" + (new String(quadkey)) + ".png?g=41&productSet=mmOS");
  }

};


// ------------------------------------------------------------------

class MapSources {
  static final int MAP_2G  = 0;
  static final int MAP_3G  = 1;
  static final int MAP_TODO = 5;
  static final int MAP_AGE3G = 6;
  static final int MAP_MAPNIK = 2;
  static final int MAP_OS  = 3;
  static final int MAP_OPEN_CYCLE = 4;

  static final MapSource [] sources = {
    new MapSource("2G coverage", "Custom 2", MAP_2G),
    new MapSource("3G coverage", "Custom 3", MAP_3G),
    new MapSource("Visited", "logmygsm_todo", MAP_TODO),
    new MapSource("3G data age", "logmygsm_age3g", MAP_AGE3G),
    new MapSource_OS("Ordnance Survey", "Ordnance Survey Explorer Maps (UK)", MAP_OS),
    new MapSource_Mapnik("Mapnik (OSM)", "mapnik", MAP_MAPNIK),
    new MapSource_Cycle("Open Cycle Map", "OSM Cycle Map", MAP_OPEN_CYCLE),
  };

  static MapSource lookup(int code) {
    for (MapSource source: sources) {
      if (source.get_code() == code) {
        return source;
      }
    }
    return null;
  }

  static MapSource get_default() {
    return lookup(MAP_2G);
  }

}

//
// vim:et:sw=2:sts=2

