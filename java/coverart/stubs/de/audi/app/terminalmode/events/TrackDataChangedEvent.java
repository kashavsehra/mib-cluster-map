package de.audi.app.terminalmode.events;
public class TrackDataChangedEvent {
    public String getTitle() { return ""; }
    public String getArtist() { return ""; }
    public String getAlbum() { return ""; }
    public static class Builder { public Builder build() { return this; } public TrackDataChangedEvent build2() { return new TrackDataChangedEvent(); } }
    public static Builder builder() { return new Builder(); }
}
