package ceu.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

public class BitalinoApp extends JFrame {

    private BITalino bitalino;
    private JComboBox<String> portCombo;
    private JComboBox<Integer> samplingCombo;
    private JCheckBox[] channelChecks;
    private JButton connectBtn, startBtn, stopBtn, closeBtn;
    private JTextArea outputArea;
    private JScrollPane scrollPane;

    // Panel for visualization
    private SignalPanel signalPanel;

    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread acquisitionThread;

    // Keep last values for channels to draw
    private int[] lastValues = new int[6];

    public BitalinoApp() {
        super("BITalino GUI App");

        bitalino = new BITalino();

        // Top panel for port and sampling rate
        JPanel topPanel = new JPanel(new FlowLayout());
        portCombo = new JComboBox<>();
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portCombo);

        samplingCombo = new JComboBox<>(new Integer[]{1, 10, 100, 1000});
        topPanel.add(new JLabel("Sampling Rate:"));
        topPanel.add(samplingCombo);

        connectBtn = new JButton("Connect");
        startBtn = new JButton("Start");
        stopBtn = new JButton("Stop");
        closeBtn = new JButton("Close");

        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);

        topPanel.add(connectBtn);
        topPanel.add(startBtn);
        topPanel.add(stopBtn);
        topPanel.add(closeBtn);

        // Channel selection
        JPanel channelPanel = new JPanel(new FlowLayout());
        channelChecks = new JCheckBox[6];
        for (int i = 0; i < 6; i++) {
            channelChecks[i] = new JCheckBox("A" + (i + 1));
            channelPanel.add(channelChecks[i]);
        }

        // Output area
        outputArea = new JTextArea(10, 50);
        outputArea.setEditable(false);
        scrollPane = new JScrollPane(outputArea);

        // Visualization panel
        signalPanel = new SignalPanel();
        signalPanel.setPreferredSize(new Dimension(800, 200));

        // Layout
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(channelPanel, BorderLayout.WEST);
        add(signalPanel, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);

        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Discover devices on startup
        discoverDevices();

        // Button actions
        connectBtn.addActionListener(this::connectAction);
        startBtn.addActionListener(this::startAction);
        stopBtn.addActionListener(this::stopAction);
        closeBtn.addActionListener(e -> {
            stopAcquisition();
            closeDevice();
            System.exit(0);
        });
    }

    private void discoverDevices() {
        try {
            Vector<String> ports = bitalino.findDevices();
            portCombo.removeAllItems();
            for (String p : ports) portCombo.addItem(p);
            if (ports.isEmpty()) outputArea.append("No BITalino ports found.\n");
            else outputArea.append("Discovered BITalino ports: " + ports + "\n");
        } catch (InterruptedException e) {
            outputArea.append("Error discovering ports.\n");
        }
    }

    private void connectAction(ActionEvent e) {
        String port = (String) portCombo.getSelectedItem();
        int rate = (Integer) samplingCombo.getSelectedItem();
        if (port == null) {
            outputArea.append("No port selected.\n");
            return;
        }
        try {
            bitalino.open(port, rate);
            outputArea.append("Connected to " + port + " at " + rate + " Hz\n");
            connectBtn.setEnabled(false);
            startBtn.setEnabled(true);
        } catch (BITalinoException ex) {
            outputArea.append("Error connecting: " + ex.getMessage() + "\n");
        } catch (Throwable ex) {
            outputArea.append("Unexpected error: " + ex.getMessage() + "\n");
        }
    }

    private void startAction(ActionEvent e) {
        int[] channels = getSelectedChannels();
        if (channels.length == 0) {
            outputArea.append("Select at least one channel.\n");
            return;
        }
        try {
            bitalino.start(channels);
            outputArea.append("Acquisition started on channels: ");
            for (int c : channels) outputArea.append((c + 1) + " ");
            outputArea.append("\n");

            running.set(true);
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);

            acquisitionThread = new Thread(this::readLoop);
            acquisitionThread.start();

        } catch (Throwable ex) {
            outputArea.append("Error starting acquisition: " + ex.getMessage() + "\n");
        }
    }

    private void stopAction(ActionEvent e) {
        stopAcquisition();
    }

    private void stopAcquisition() {
        running.set(false);
        if (acquisitionThread != null) {
            try {
                acquisitionThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        try {
            bitalino.stop();
            outputArea.append("Acquisition stopped.\n");
        } catch (BITalinoException ex) {
            outputArea.append("Error stopping acquisition: " + ex.getMessage() + "\n");
        }
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void closeDevice() {
        try {
            bitalino.close();
            outputArea.append("Device closed.\n");
        } catch (BITalinoException ex) {
            outputArea.append("Error closing device: " + ex.getMessage() + "\n");
        }
    }

    private int[] getSelectedChannels() {
        return java.util.Arrays.stream(channelChecks)
                .filter(JCheckBox::isSelected)
                .mapToInt(cb -> java.util.Arrays.asList(channelChecks).indexOf(cb))
                .toArray();
    }

    private void readLoop() {
        final int blockSize = 5;
        while (running.get()) {
            try {
                Frame[] frames = bitalino.read(blockSize);
                SwingUtilities.invokeLater(() -> {
                    for (Frame f : frames) {
                        for (int i = 0; i < f.analog.length; i++) {
                            lastValues[i] = f.analog[i];
                        }
                        signalPanel.updateSignal(lastValues);
                    }
                });
            } catch (BITalinoException ex) {
                SwingUtilities.invokeLater(() -> outputArea.append("Error reading: " + ex.getMessage() + "\n"));
            }
        }
    }

    // Panel to draw live signal
    private static class SignalPanel extends JPanel {
        private int[] values = new int[6];

        public void updateSignal(int[] newValues) {
            System.arraycopy(newValues, 0, values, 0, values.length);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);

            Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA};
            for (int ch = 0; ch < values.length; ch++) {
                g.setColor(colors[ch]);
                int y = h - (values[ch] * h / 1024); // scale 0–1023 to panel height
                g.fillOval(50 + ch * 100, y, 10, 10);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitalinoApp().setVisible(true));
    }
}
