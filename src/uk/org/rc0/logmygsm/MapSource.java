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

class MapSource {
  private String menu_name = "";
  private String path_segment = "";
  private int code;
  private boolean tower_line;
  final private String path_start = "/sdcard/Maverick/tiles";

  String get_menu_name() {
    return menu_name;
  }

  String get_tile_path(int zoom, int x, int y) {
    return String.format("%s/%s/%d/%d/%d.png.tile",
        path_start, path_segment, zoom, x, y);
  }

  int get_code() {
    return code;
  }

  boolean want_tower_line() {
    return tower_line;
  }

  MapSource(String _menu_name, String _path_segment, int _code, boolean _want_tower_line) {
    menu_name = _menu_name;
    path_segment = _path_segment;
    code = _code;
    tower_line = _want_tower_line;
  }

}

class MapSources {
  static final int MAP_2G  = 100;
  static final int MAP_3G  = 101;
  static final int MAP_TODO = 105;
  static final int MAP_AGE3G = 106;
  static final int MAP_MAPNIK = 102;
  static final int MAP_OS  = 103;
  static final int MAP_OPEN_CYCLE = 104;

  static final MapSource [] sources = {
    new MapSource("2G coverage", "Custom 2", MAP_2G, true),
    new MapSource("3G coverage", "Custom 3", MAP_3G, true),
    new MapSource("Visited", "logmygsm_todo", MAP_TODO, true),
    new MapSource("3G data age", "logmygsm_age3g", MAP_AGE3G, true),
    new MapSource("Ordnance Survey", "Ordnance Survey Explorer Maps (UK)", MAP_OS, false),
    new MapSource("Mapnik (OSM)", "mapnik", MAP_MAPNIK, false),
    new MapSource("Open Cycle Map", "OSM Cycle Map", MAP_OPEN_CYCLE, false),
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

