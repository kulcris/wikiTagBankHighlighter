package com.wikitagbankhighlighter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import javax.swing.*;
        import net.runelite.client.ui.PluginPanel;

public class WikiBankToolsPanel extends PluginPanel
{
    private final WikiBankToolsPlugin plugin;

    private final JTextField categoryField = new JTextField();
    private final JButton highlightBtn = new JButton("Highlight in bank");
    private final JButton createTabBtn = new JButton("Create Bank Tag Tab");
    private final JButton clearBtn = new JButton("Clear highlight");
    private final JLabel statusLabel = new JLabel(" ");

    public WikiBankToolsPanel(WikiBankToolsPlugin plugin)
    {
        this.plugin = plugin;

        setLayout(new BorderLayout(0, 8));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel catLabel = new JLabel("Wiki category (e.g. Herbs):");
        catLabel.setAlignmentX(LEFT_ALIGNMENT);

        categoryField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        categoryField.setAlignmentX(LEFT_ALIGNMENT);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(LEFT_ALIGNMENT);

        highlightBtn.setAlignmentX(LEFT_ALIGNMENT);
        createTabBtn.setAlignmentX(LEFT_ALIGNMENT);
        clearBtn.setAlignmentX(LEFT_ALIGNMENT);

        highlightBtn.addActionListener(this::onHighlight);
        createTabBtn.addActionListener(this::onCreateTab);
        clearBtn.addActionListener(e -> plugin.clearHighlight());

        buttons.add(highlightBtn);
        buttons.add(Box.createVerticalStrut(6));
        buttons.add(createTabBtn);
        buttons.add(Box.createVerticalStrut(6));
        buttons.add(clearBtn);

        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        top.add(catLabel);
        top.add(Box.createVerticalStrut(4));
        top.add(categoryField);
        top.add(Box.createVerticalStrut(10));
        top.add(buttons);
        top.add(Box.createVerticalStrut(10));
        top.add(statusLabel);

        add(top, BorderLayout.NORTH);
    }

    private void onHighlight(ActionEvent e)
    {
        plugin.highlightCategory(categoryField.getText());
    }

    private void onCreateTab(ActionEvent e)
    {
        plugin.createBankTagTab(categoryField.getText());
    }

    public void setBusy(boolean busy, String status)
    {
        highlightBtn.setEnabled(!busy);
        createTabBtn.setEnabled(!busy);
        clearBtn.setEnabled(!busy);
        categoryField.setEnabled(!busy);

        statusLabel.setText(status == null || status.isEmpty() ? " " : status);
    }
}

