package redacted.client.value.impl;

import java.util.List;

// shared surface of the AutoHotBar-style item picker -- the category icon row, search box and
// scrollable registry grid. HotbarValue and CleanerGridValue both expose it so the ClickGUI can drive
// either one with a single set of hit-tests and the same search-focus / wheel-scroll plumbing. the
// per-cell decoration (HotbarValue's exclusion dim vs CleanerGridValue's mode badges) and any slot
// strip stay specific to each widget's own draw method.
public interface ItemPicker {
    List<HotbarValue.Category> getCategories();
    List<HotbarValue.Entry> getFilteredEntries();
    int getOpenCategory();
    void setOpenCategory(int idx);
    String getSearch();
    void setSearch(String s);
    double getScroll();
    void setScroll(double v);
    boolean isWidgetOpen();
    void setWidgetOpen(boolean open);
}
