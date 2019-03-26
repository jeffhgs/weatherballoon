package com.groovescale.weatherballoon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JSCHLogger implements com.jcraft.jsch.Logger {
    Logger log;

    /*
    private Map<Integer, Logback.LEVEL> levels = new HashMap<Integer, MyLevel>();

    private final MyLogger LOGGER;
    */

    public JSCHLogger() {
        /*
        // Mapping between JSch levels and our own levels
        levels.put(DEBUG, MyLevel.FINE);
        levels.put(INFO, MyLevel.INFO);
        levels.put(WARN, MyLevel.WARNING);
        levels.put(ERROR, MyLevel.SEVERE);
        levels.put(FATAL, MyLevel.SEVERE);

        LOGGER = MyLogger.getLogger(...); // Anything you want here, depending on your logging framework
        */
        log = LoggerFactory.getLogger(JSCHLogger.class);
    }

    @Override
    public boolean isEnabled(int pLevel) {
        return true; // here, all levels enabled
    }

    @Override
    public void log(int pLevel, String pMessage) {
        log.info(pMessage);
        /*
        MyLevel level = levels.get(pLevel);
        if (level == null) {
            level = MyLevel.SEVERE;
        }
        LOGGER.log(level, pMessage); // logging-framework dependent...
        */
    }
}