package de.audi.atip.interapp.combi.bap.audio.data;
public class PlayPosition {
    public static class Builder {
        public Builder setTimePosition(Object t) { return this; }
        public Builder setTotalPlayTime(Object t) { return this; }
        public PlayPosition build() { return new PlayPosition(); }
    }
    public static Builder builder() { return new Builder(); }
    public static class Time { public int getTimeInSeconds() { return 0; } }
    public Time getTimePosition() { return new Time(); }
    public Time getTotalPlayTime() { return new Time(); }
    public boolean isVariableBitRate() { return false; }
}
