package com.groundmarkervariables.variables.meta;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

// Render-affecting state collected from a label's meta variables (see MetaVariableResolver).
// A null field means "no override".
public class MetaState {
    @Nullable
    private Color tileColor;
    @Nullable
    private Float tileOpacity;
    @Nullable
    private Float borderWidth;
    @Nullable
    private Color hoverBackground;
    @Nullable
    private String hoverLabel;
    private List<TextAlign> hoverLineAlignments = List.of();
    private final Map<HighlightCorner, Color> highlights = new EnumMap<>(HighlightCorner.class);
    private List<TextAlign> lineAlignments = List.of();
    private final List<GroupMenu> groupMenus = new ArrayList<>();
    // What's inside the label's {ground} blocks, whose icons are drawn on the tile.
    @Nullable
    private String groundLabel;
    private int groundFacing;

    @Nullable
    public String getGroundLabel() {
        return groundLabel;
    }

    void setGroundLabel(String groundLabel) {
        this.groundLabel = groundLabel;
    }

    // {ground@dir}: where the top of the {ground} content faces, in degrees clockwise from north.
    public int getGroundFacing() {
        return groundFacing;
    }

    void setGroundFacing(int groundFacing) {
        this.groundFacing = groundFacing;
    }

    // A {setGroup} in the resolved label: its group, and its own options in order, or null for the declared ones.
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class GroupMenu {
        public final String name;
        @Nullable
        public final List<String> options;
    }

    public List<GroupMenu> getGroupMenus() {
        return groupMenus;
    }

    public void addGroupMenu(String name, @Nullable List<String> options) {
        groupMenus.add(new GroupMenu(name, options));
    }

    @Nullable
    public Color getTileColor() {
        return tileColor;
    }

    public void setTileColor(Color tileColor) {
        this.tileColor = tileColor;
    }

    // 0 (transparent) to 1 (opaque) -- outline only, see {tileCol=50%}.
    @Nullable
    public Float getTileOpacity() {
        return tileOpacity;
    }

    public void setTileOpacity(Float tileOpacity) {
        this.tileOpacity = tileOpacity;
    }

    // Overrides the Border width config for the outline and {highlight} brackets.
    @Nullable
    public Float getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(Float borderWidth) {
        this.borderWidth = borderWidth;
    }

    // {hover}: the background drawn behind this label while hovered; null if it has no hover effect.
    @Nullable
    public Color getHoverBackground() {
        return hoverBackground;
    }

    public void setHoverBackground(Color hoverBackground) {
        this.hoverBackground = hoverBackground;
    }

    // The label as shown while hovered, with its {hoverOnly} content; null if it has none.
    @Nullable
    public String getHoverLabel() {
        return hoverLabel;
    }

    public List<TextAlign> getHoverLineAlignments() {
        return hoverLineAlignments;
    }

    void setHoverLabel(String hoverLabel, List<TextAlign> hoverLineAlignments) {
        this.hoverLabel = hoverLabel;
        this.hoverLineAlignments = hoverLineAlignments;
    }

    public Map<HighlightCorner, Color> getHighlights() {
        return highlights;
    }

    public void setHighlight(HighlightCorner corner, Color color) {
        highlights.put(corner, color);
    }

    // One entry per rendered line; a line past the end of this list uses TextAlign.CENTER.
    public List<TextAlign> getLineAlignments() {
        return lineAlignments;
    }

    void setLineAlignments(List<TextAlign> lineAlignments) {
        this.lineAlignments = lineAlignments;
    }
}
