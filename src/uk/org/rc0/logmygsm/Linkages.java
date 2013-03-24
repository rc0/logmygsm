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

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

class Linkages {

  // Turns a set of points into a set of "linkages" (in the mechanical sense)
  //
  // Coherency (if the set of points changes) must be managed by the caller

  // ---------------------------

  static class Edge {
    Merc28 m0;
    Merc28 m1;
    Edge(Merc28 _m0, Merc28 _m1) {
      m0 = _m0;
      m1 = _m1;
    }
  };

  // ---------------------------

  private static class WorkingEdge extends Edge {
    int i0;
    int i1;
    float d2;
    boolean alive;
    public WorkingEdge(int _a, int _b, Merc28[] points, float _d2) {
      super(points[_a], points[_b]);
      i0 = _a;
      i1 = _b;
      d2 = _d2;
      alive = true;
    }
  };

  // ---------------------------

  private static final Comparator<WorkingEdge> edge_comparator =
    new Comparator<WorkingEdge> () {

      public int compare(WorkingEdge e0, WorkingEdge e1) {
        if (e0.d2 > e1.d2) return -1;
        else if (e0.d2 < e1.d2) return +1;
        else if (e0.i0 < e1.i0) return -1;
        else if (e0.i0 > e1.i0) return +1;
        else if (e0.i1 < e1.i1) return -1;
        else if (e0.i1 > e1.i1) return +1;
        else return 0;
      }
    };

  // ---------------------------

  // Cached result for later reads
  private Edge[] edges;

  // ---------------------------

  private static Set<WorkingEdge> compute_mesh(Merc28[] p) {
    Set<WorkingEdge> result = new HashSet<WorkingEdge>();
    int n = p.length;
    int i, j, k;

    for (i=0; i<n; i++) {
      for (j=i+1; j<n; j++) {
        // candidate line is i, j
        boolean ok = true;
        for (k=0; k<n; k++) {
          if ((k != i) && (k != j)) {
            // work in FP to avoid integer overflow ?
            float dxi = (float)(p[k].X - p[i].X);
            float dyi = (float)(p[k].Y - p[i].Y);
            float dxj = (float)(p[k].X - p[j].X);
            float dyj = (float)(p[k].Y - p[j].Y);
            if ((dxi * dxj) + (dyi * dyj) < 0.0) {
              ok = false;
              break;
            }
          }
        }
        if (ok) {
          float dx = (float)(p[i].X - p[j].X);
          float dy = (float)(p[i].Y - p[j].Y);
          float d2 = (dx*dx) + (dy*dy);
          result.add(new WorkingEdge(i, j, p, d2));
        }
      }
    }
    return result;
  }

  // ---------------------------

  private Edge[] compute_pruned(int np, Set<WorkingEdge> fussy) {
    // remove edges from the overly-'fussy' initial set.  An edge is surplus to
    // requirements <=> the nodes at both end have >=3 neighbours, and there is
    // another path from one to the other through the network even if this edge
    // is culled.
    //
    int n = fussy.size();
    int[] n_neigh = new int[np];
    boolean[] visited = new boolean[np];

    WorkingEdge[] working = fussy.toArray(new WorkingEdge[0]);
    Arrays.sort(working, edge_comparator);

    for (int i=0; i<np; i++) n_neigh[i] = 0;
    for (int i=0; i<working.length; i++) {
      ++n_neigh[working[i].i0];
      ++n_neigh[working[i].i1];
    }
    int n_alive = working.length;
    for (int candidate=0; candidate<working.length; candidate++) {
      int i0 = working[candidate].i0;
      int i1 = working[candidate].i1;
      if ((n_neigh[i0] >= 3) && (n_neigh[i1] >= 3)) {
        for (int i=0; i<np; i++) { visited[i] = false; }
        boolean active = false;
        visited[i0] = true;
        do {
          active = false;
          for (int e = 0; e < working.length; e++) {
            if (e == candidate) continue;
            if (working[e].alive) {
              int e0 = working[e].i0;
              int e1 = working[e].i1;
              if (visited[e0] != visited[e1]) {
                visited[e0] = visited[e1] = true;
                active = true;
              }
            }
          }
        } while (active && !visited[i1]);
        if (visited[i1]) {
          working[candidate].alive = false;
          --n_neigh[i0];
          --n_neigh[i1];
          --n_alive;
        }
      }
    }

    //Log.i(TAG, "Start with " + working.length + ", " + n_alive + " remain");
    Edge[] result = new Edge[n_alive];
    int i, j;
    for (i = j = 0; i<working.length; i++) {
      if (working[i].alive) {
        result[j++] = working[i];
      }
    }

    return result;
  }

  // ---------------------------

  public Linkages(Merc28[] _points) {
    Merc28 [] points;

    // defensive copy of the points fed in
    points = new Merc28[_points.length];
    for (int i = 0; i < _points.length; i++) {
      points[i] = new Merc28(_points[i]);
    }

    Set<WorkingEdge> mesh = compute_mesh(points);
    edges = compute_pruned(points.length, mesh);
  }

  // ---------------------------

  public Edge[] get_edges() {
    return edges;
  }

}


// vim:et:sw=2:sts=2
//

