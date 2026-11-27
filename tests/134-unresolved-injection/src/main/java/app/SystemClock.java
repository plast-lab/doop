package app;

// Not a bean: the analysed code defines none of type Clock.
public class SystemClock implements Clock { public long now() { return System.currentTimeMillis(); } }
