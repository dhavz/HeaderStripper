package io.github.burpextensions.headerstripper;

import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IExtensionHelpers;
import burp.IHttpListener;
import burp.IHttpRequestResponse;
import burp.IInterceptedProxyMessage;
import burp.IProxyListener;
import burp.IRequestInfo;
import burp.IResponseInfo;
import burp.ITab;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

public class HeaderStripperBurpExtender implements IBurpExtender, ITab, IHttpListener, IProxyListener {
    private static final String EXTENSION_NAME = "Header Stripper";
    private static final Set<String> COMMON_SENSITIVE_HEADERS = new HashSet<String>(Arrays.asList(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "x-forwarded-for",
            "x-real-ip",
            "referer",
            "origin"
    ));

    private final HeaderInventoryTableModel tableModel = new HeaderInventoryTableModel();

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private JPanel rootPanel;
    private JLabel statusLabel;
    private JCheckBox stripProxyRequestsCheckbox;
    private JCheckBox stripProxyResponsesCheckbox;
    private JCheckBox rewriteProxyOriginalRequestsCheckbox;
    private JCheckBox observeRequestsCheckbox;
    private JCheckBox observeResponsesCheckbox;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();

        callbacks.setExtensionName(EXTENSION_NAME);
        buildUi();
        callbacks.customizeUiComponent(rootPanel);
        callbacks.addSuiteTab(this);
        callbacks.registerHttpListener(this);
        callbacks.registerProxyListener(this);
        callbacks.printOutput(EXTENSION_NAME + " loaded");
        callbacks.printOutput("Rewrite Proxy original requests: disabled");
        updateStatusLabel();
    }

    @Override
    public String getTabCaption() {
        return EXTENSION_NAME;
    }

    @Override
    public java.awt.Component getUiComponent() {
        return rootPanel;
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        byte[] message = messageIsRequest ? messageInfo.getRequest() : messageInfo.getResponse();
        if (message == null || message.length == 0) {
            return;
        }

        if (toolFlag == IBurpExtenderCallbacks.TOOL_PROXY) {
            if (messageIsRequest && rewriteProxyOriginalRequestsCheckbox.isSelected()) {
                byte[] updated = stripSelectedHeaders(message, extractHeaders(message, true), true);
                if (updated != null) {
                    messageInfo.setRequest(updated);
                    callbacks.printOutput("Rewrote stored Proxy original request");
                }
            }
            return;
        }

        if (shouldObserve(messageIsRequest)) {
            observeHeaders(extractHeaders(message, messageIsRequest), messageIsRequest, toolName(toolFlag));
        }
    }

    @Override
    public void processProxyMessage(boolean messageIsRequest, IInterceptedProxyMessage message) {
        IHttpRequestResponse messageInfo = message.getMessageInfo();
        byte[] raw = messageIsRequest ? messageInfo.getRequest() : messageInfo.getResponse();
        if (raw == null || raw.length == 0) {
            return;
        }

        List<String> headers = extractHeaders(raw, messageIsRequest);
        if (shouldObserve(messageIsRequest)) {
            observeHeaders(headers, messageIsRequest, "Proxy");
        }

        if (!shouldStrip(messageIsRequest)) {
            return;
        }

        byte[] updated = stripSelectedHeaders(raw, headers, messageIsRequest);
        if (updated == null) {
            return;
        }

        if (messageIsRequest) {
            messageInfo.setRequest(updated);
        } else {
            messageInfo.setResponse(updated);
        }
        rehookProxyMessage(message);
    }

    private void buildUi() {
        rootPanel = new JPanel(new BorderLayout(10, 10));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));

        stripProxyRequestsCheckbox = new JCheckBox("Strip in Proxy requests", true);
        stripProxyResponsesCheckbox = new JCheckBox("Strip in Proxy responses", false);
        rewriteProxyOriginalRequestsCheckbox = new JCheckBox("Also rewrite Proxy original requests", false);
        rewriteProxyOriginalRequestsCheckbox.addActionListener(event -> callbacks.printOutput(
                "Rewrite Proxy original requests: "
                        + (rewriteProxyOriginalRequestsCheckbox.isSelected() ? "enabled" : "disabled")));
        observeRequestsCheckbox = new JCheckBox("Learn from requests", true);
        observeResponsesCheckbox = new JCheckBox("Learn from responses", true);

        controlsPanel.add(stripProxyRequestsCheckbox);
        controlsPanel.add(stripProxyResponsesCheckbox);
        controlsPanel.add(rewriteProxyOriginalRequestsCheckbox);
        controlsPanel.add(observeRequestsCheckbox);
        controlsPanel.add(observeResponsesCheckbox);

        JTextField filterField = new JTextField();
        filterField.setPreferredSize(new Dimension(280, 28));
        filterPanel.add(new JLabel("Filter headers:"), BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);

        JTable table = new JTable(tableModel);
        tableModel.addTableModelListener(event -> updateStatusLabel());
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(90);
        table.getColumnModel().getColumn(3).setMaxWidth(90);

        TableRowSorter<HeaderInventoryTableModel> sorter = new TableRowSorter<HeaderInventoryTableModel>(tableModel);
        table.setRowSorter(sorter);
        filterField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
            String text = filterField.getText().trim();
            if (text.isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1));
            }
        }));

        JButton clearInventoryButton = new JButton("Clear Inventory");
        clearInventoryButton.addActionListener(event -> {
            tableModel.clearInventory();
            updateStatusLabel();
        });

        JButton uncheckAllButton = new JButton("Uncheck All");
        uncheckAllButton.addActionListener(event -> {
            tableModel.setAllSelected(false);
            updateStatusLabel();
        });

        JButton selectSensitiveButton = new JButton("Select Common Sensitive");
        selectSensitiveButton.addActionListener(event -> {
            tableModel.selectHeaders(COMMON_SENSITIVE_HEADERS);
            updateStatusLabel();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.add(clearInventoryButton);
        buttonPanel.add(uncheckAllButton);
        buttonPanel.add(selectSensitiveButton);

        statusLabel = new JLabel();
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.add(buttonPanel, BorderLayout.WEST);
        footerPanel.add(statusLabel, BorderLayout.EAST);

        topPanel.add(controlsPanel, BorderLayout.NORTH);
        topPanel.add(filterPanel, BorderLayout.SOUTH);

        rootPanel.add(topPanel, BorderLayout.NORTH);
        rootPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        rootPanel.add(footerPanel, BorderLayout.SOUTH);
    }

    private boolean shouldObserve(boolean request) {
        return request ? observeRequestsCheckbox.isSelected() : observeResponsesCheckbox.isSelected();
    }

    private boolean shouldStrip(boolean request) {
        return request ? stripProxyRequestsCheckbox.isSelected() : stripProxyResponsesCheckbox.isSelected();
    }

    private List<String> extractHeaders(byte[] message, boolean request) {
        if (request) {
            IRequestInfo requestInfo = helpers.analyzeRequest(message);
            return requestInfo.getHeaders();
        }
        IResponseInfo responseInfo = helpers.analyzeResponse(message);
        return responseInfo.getHeaders();
    }

    private void observeHeaders(List<String> headers, boolean request, String toolName) {
        for (int i = 1; i < headers.size(); i++) {
            String headerName = extractHeaderName(headers.get(i));
            if (headerName != null) {
                tableModel.recordHeader(headerName, request, toolName);
            }
        }
        SwingUtilities.invokeLater(this::updateStatusLabel);
    }

    private byte[] stripSelectedHeaders(byte[] message, List<String> headers, boolean request) {
        int bodyOffset = request
                ? helpers.analyzeRequest(message).getBodyOffset()
                : helpers.analyzeResponse(message).getBodyOffset();

        List<String> filteredHeaders = new ArrayList<String>();
        filteredHeaders.add(headers.get(0));
        boolean changed = false;

        for (int i = 1; i < headers.size(); i++) {
            String headerLine = headers.get(i);
            String headerName = extractHeaderName(headerLine);
            if (headerName != null && tableModel.shouldStrip(headerName)) {
                changed = true;
                continue;
            }
            filteredHeaders.add(headerLine);
        }

        if (!changed) {
            return null;
        }

        byte[] body = Arrays.copyOfRange(message, bodyOffset, message.length);
        callbacks.printOutput("Stripped selected headers from proxy " + (request ? "request" : "response"));
        return helpers.buildHttpMessage(filteredHeaders, body);
    }

    private void rehookProxyMessage(IInterceptedProxyMessage message) {
        int action = message.getInterceptAction();
        if (action == IInterceptedProxyMessage.ACTION_DO_INTERCEPT) {
            message.setInterceptAction(IInterceptedProxyMessage.ACTION_DO_INTERCEPT_AND_REHOOK);
            return;
        }
        if (action == IInterceptedProxyMessage.ACTION_DONT_INTERCEPT) {
            message.setInterceptAction(IInterceptedProxyMessage.ACTION_DONT_INTERCEPT_AND_REHOOK);
            return;
        }
        if (action == IInterceptedProxyMessage.ACTION_FOLLOW_RULES) {
            message.setInterceptAction(IInterceptedProxyMessage.ACTION_FOLLOW_RULES_AND_REHOOK);
        }
    }

    private String extractHeaderName(String headerLine) {
        int colon = headerLine.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return headerLine.substring(0, colon).trim();
    }

    private void updateStatusLabel() {
        statusLabel.setText("Unique headers: " + tableModel.getUniqueCount()
                + " | Selected to strip: " + tableModel.getSelectedCount());
    }

    private String toolName(int toolFlag) {
        if (toolFlag == IBurpExtenderCallbacks.TOOL_PROXY) {
            return "Proxy";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_REPEATER) {
            return "Repeater";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_SCANNER) {
            return "Scanner";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_INTRUDER) {
            return "Intruder";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_EXTENDER) {
            return "Extender";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_TARGET) {
            return "Target";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_SPIDER) {
            return "Spider";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_SEQUENCER) {
            return "Sequencer";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_COMPARER) {
            return "Comparer";
        }
        if (toolFlag == IBurpExtenderCallbacks.TOOL_DECODER) {
            return "Decoder";
        }
        return "Other";
    }
}
