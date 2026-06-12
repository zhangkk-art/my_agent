package com.myagent.model;

/**
 * A single subtitle entry with custom text and timing.
 */
public class SubtitleEntry {

    private double startSec;   // start time in seconds
    private double endSec;     // end time in seconds
    private String text;

    public SubtitleEntry() {}

    public SubtitleEntry(double startSec, double endSec, String text) {
        this.startSec = startSec;
        this.endSec = endSec;
        this.text = text;
    }

    public double getStartSec() { return startSec; }
    public void setStartSec(double startSec) { this.startSec = startSec; }

    public double getEndSec() { return endSec; }
    public void setEndSec(double endSec) { this.endSec = endSec; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
