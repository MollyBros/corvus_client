package com.example.corvus;

/** Цветовые темы Corvus Client. По умолчанию - BLUE. */
public enum Theme {
    //            название   акцент      фон панели   тусклый текст
    BLUE  ("Blue",   0xFF3B82F6, 0xE00B1020, 0xFF8FA6C8),
    CYAN  ("Cyan",   0xFF55FFFF, 0xE0101018, 0xFF8899AA),
    PURPLE("Purple", 0xFF9B6BFF, 0xE0120B20, 0xFFA898C8),
    RED   ("Red",    0xFFFF4D5E, 0xE0200B10, 0xFFC89098),
    GREEN ("Green",  0xFF3DDC84, 0xE00B2012, 0xFF90C8A0),
    ORANGE("Orange", 0xFFFF9F43, 0xE0201408, 0xFFC8A890);

    public final String label;
    public final int accent;
    public final int panel;
    public final int dim;

    Theme(String label, int accent, int panel, int dim) {
        this.label = label;
        this.accent = accent;
        this.panel = panel;
        this.dim = dim;
    }

    public Theme next() {
        Theme[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
