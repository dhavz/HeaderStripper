package io.github.burpextensions.headerstripper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

final class HeaderInventoryTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "Strip",
            "Header",
            "Requests",
            "Responses",
            "Last Tool"
    };

    private final Map<String, HeaderEntry> entriesByKey = new LinkedHashMap<String, HeaderEntry>();
    private final List<HeaderEntry> rows = new ArrayList<HeaderEntry>();

    @Override
    public synchronized int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) {
            return Boolean.class;
        }
        if (columnIndex == 2 || columnIndex == 3) {
            return Integer.class;
        }
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
    }

    @Override
    public synchronized Object getValueAt(int rowIndex, int columnIndex) {
        HeaderEntry entry = rows.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return entry.isSelected();
            case 1:
                return entry.getDisplayName();
            case 2:
                return entry.getRequestCount();
            case 3:
                return entry.getResponseCount();
            case 4:
                return entry.getLastTool();
            default:
                return null;
        }
    }

    @Override
    public synchronized void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex != 0) {
            return;
        }
        rows.get(rowIndex).setSelected(Boolean.TRUE.equals(value));
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    synchronized void recordHeader(String headerName, boolean request, String toolName) {
        if (headerName == null || headerName.trim().isEmpty()) {
            return;
        }

        String normalized = headerName.trim();
        String key = normalized.toLowerCase(Locale.ROOT);
        HeaderEntry entry = entriesByKey.get(key);
        boolean inserted = false;
        if (entry == null) {
            entry = new HeaderEntry(normalized);
            entriesByKey.put(key, entry);
            rows.add(entry);
            Collections.sort(rows, (left, right) -> left.getDisplayName().compareToIgnoreCase(right.getDisplayName()));
            inserted = true;
        } else {
            entry.updateDisplayName(normalized);
        }
        entry.seen(request, toolName);

        final boolean changedStructure = inserted;
        SwingUtilities.invokeLater(() -> {
            if (changedStructure) {
                fireTableDataChanged();
            } else {
                fireTableDataChanged();
            }
        });
    }

    synchronized boolean shouldStrip(String headerName) {
        if (headerName == null) {
            return false;
        }
        HeaderEntry entry = entriesByKey.get(headerName.trim().toLowerCase(Locale.ROOT));
        return entry != null && entry.isSelected();
    }

    synchronized int getSelectedCount() {
        int count = 0;
        for (HeaderEntry entry : rows) {
            if (entry.isSelected()) {
                count++;
            }
        }
        return count;
    }

    synchronized int getUniqueCount() {
        return rows.size();
    }

    synchronized void clearInventory() {
        entriesByKey.clear();
        rows.clear();
        SwingUtilities.invokeLater(this::fireTableDataChanged);
    }

    synchronized void setAllSelected(boolean selected) {
        for (HeaderEntry entry : rows) {
            entry.setSelected(selected);
        }
        SwingUtilities.invokeLater(this::fireTableDataChanged);
    }

    synchronized void selectHeaders(Set<String> headerNames) {
        for (HeaderEntry entry : rows) {
            if (headerNames.contains(entry.getKey())) {
                entry.setSelected(true);
            }
        }
        SwingUtilities.invokeLater(this::fireTableDataChanged);
    }
}
