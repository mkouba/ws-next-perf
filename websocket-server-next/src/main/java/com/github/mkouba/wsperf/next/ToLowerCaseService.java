package com.github.mkouba.wsperf.next;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ToLowerCaseService {
    
    String convert(String value) {
        Log.debugf("Converting %s", value);
        return value.toLowerCase();
    }

}
