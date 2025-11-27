package ceu.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.bluetooth.RemoteDevice;

public class BitalinoApp extends JFrame {

    private BITalino bitalino;
    private JComboBox<RemoteDevice> deviceCombo;
    private JComboBox<Integer> samplingCombo;
    private JCheckBox[] channelChecks;
    private JButton discoverBtn, connectBtn, startBtn, stopBtn, closeBtn;
    private JTextArea outputArea;
    private JScrollPane scrollPane;

    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread acquisitionThread;

    public BitalinoApp() {
        super("BITalino Bluetooth GUI");

        bitalino = new BITalino();

        // Top panel for device selection and sampling rate
        JPanel topPanel = new JPanel(new FlowLayout());

        deviceCombo = new JComboBox<>();
        topPanel.add(new JLabel("Device:"));
        topPanel.add(deviceCombo);

        discoverBtn = new JButton("Discover");
        topPanel.add(discoverBtn);

        samplingCombo = new JComboBox<>(new Integer[]{1, 10, 100, 1000});
        topPanel.add(new JLabel("Sampling Rate:"));
        topPanel.add(samplingCombo);

        connectBtn = new JButton("Connect");
        connectBtn.setEnabled(false);
        topPanel.add(connectBtn);

        startBtn = new JButton("Start");
        startBtn.setEnabled(false);
        topPanel.add(startBtn);

        stopBtn = new JButton("Stop");
        stopBtn.setEnabled(false);
        topPanel.add(stopBtn);

        closeBtn = new JButton("Close");
        topPanel.add(closeBtn);

        // Channel selection panel
        JPanel channelPanel = new JPanel(new FlowLayout());
        channelChecks = new JCheckBox[6];
        for (int i = 0; i < 6; i++) {
            channelChecks[i] = new JCheckBox("A" + (i + 1));
            channelPanel.add(channelChecks[i]);
        }

        // Output area
        outputArea = new JTextArea(20, 50);
        outputArea.setEditable(false);
        scrollPane = new JScrollPane(outputArea);

        // Add panels to frame
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(channelPanel, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);

        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Button actions
        discoverBtn.addActionListener(this::discoverAction);
        connectBtn.addActionListener(this::connectAction);
        startBtn.addActionListener(this::startAction);
        stopBtn.addActionListener(this::stopAction);
        closeBtn.addActionListener(e -> {
            stopAcquisition();
            closeDevice();
            System.exit(0);
        });
    }

    private void discoverAction(ActionEvent e) {
        deviceCombo.removeAllItems();
        outputArea.append("Discovering devices...\n");
        new Thread(() -> {
            try {
                Vector<RemoteDevice> devices = bitalino.findDevices();
                SwingUtilities.invokeLater(() -> {
                    for (RemoteDevice d : devices) deviceCombo.addItem(d);
                    if (!devices.isEmpty()) {
                        outputArea.append("Found " + devices.size() + " device(s)\n");
                        connectBtn.setEnabled(true);
                    } else {
                        outputArea.append("No BITalino devices found.\n");
                    }
                });
            } catch (InterruptedException ex) {
                SwingUtilities.invokeLater(() -> outputArea.append("Discovery interrupted.\n"));
            }
        }).start();
    }

    private void connectAction(ActionEvent e) {
        RemoteDevice device = (RemoteDevice) deviceCombo.getSelectedItem();
        if (device == null) {
            outputArea.append("No device selected.\n");
            return;
        }

        int rate = (Integer) samplingCombo.getSelectedItem();
        outputArea.append("Connecting to " + device.getBluetoothAddress() + " at " + rate + " Hz...\n");

        new Thread(() -> {
            try {
                bitalino.open(device.getBluetoothAddress(), rate);
                SwingUtilities.invokeLater(() -> {
                    outputArea.append("Connected!\n");
                    connectBtn.setEnabled(false);
                    startBtn.setEnabled(true);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() -> outputArea.append("Connection failed: " + ex.getMessage() + "\n"));
            }
        }).start();
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
            try { acquisitionThread.join(); } catch (InterruptedException ignored) {}
        }
        try {
            bitalino.stop();
            outputArea.append("Acquisition stopped.\n");
        } catch (Exception ex) {
            outputArea.append("Error stopping acquisition: " + ex.getMessage() + "\n");
        }
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
    }

    private void closeDevice() {
        try {
            bitalino.close();
            outputArea.append("Device closed.\n");
        } catch (Exception ex) {
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
                        StringBuilder sb = new StringBuilder("Seq: ").append(f.seq).append(" | ");
                        for (int i = 0; i < f.analog.length; i++) {
                            sb.append("A").append(i + 1).append(": ").append(f.analog[i]).append(" ");
                        }
                        outputArea.append(sb.toString() + "\n");
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    }
                });
            } catch (BITalinoException ex) {
                SwingUtilities.invokeLater(() -> outputArea.append("Error reading: " + ex.getMessage() + "\n"));
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitalinoApp().setVisible(true));
    }
}