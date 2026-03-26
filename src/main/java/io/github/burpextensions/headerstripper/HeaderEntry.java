package io.github.burpextensions.headerstripper;

final class HeaderEntry {
    private final String key;
    private String displayName;
    private boolean selected;
    private int requestCount;
    private int responseCount;
    private String lastTool;

    HeaderEntry(String displayName) {
        this.key = displayName.toLowerCase();
        this.displayName = displayName;
        this.lastTool = "-";
    }

    String getKey() {
        return key;
    }

    String getDisplayName() {
        return displayName;
    }

    void updateDisplayName(String candidate) {
        if (candidate != null && candidate.length() > displayName.length()) {
            this.displayName = candidate;
        }
    }

    boolean isSelected() {
        return selected;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    int getRequestCount() {
        return requestCount;
    }

    int getResponseCount() {
        return responseCount;
    }

    String getLastTool() {
        return lastTool;
    }

    void seen(boolean request, String toolName) {
        if (request) {
            requestCount++;
        } else {
            responseCount++;
        }
        lastTool = toolName;
    }
}
