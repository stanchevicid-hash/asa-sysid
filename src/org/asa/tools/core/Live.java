package org.asa.tools.core;

import java.util.List;

/** Something the web UI can show live: named values and warnings (Black Box implements it). */
public interface Live {
    List<String> names();
    double[] values();
    List<String> warnings();
}
