package ceu.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.bluetooth.RemoteDevice;

public class BitalinoApp extends JFrame {

    private BITalino bitalino;
    private JComboBox<Integer> samplingCombo;
    private JButton connectBtn, startBtn, stopBtn, closeBtn;
    private JTextArea outputArea;
    private JScrollPane scrollPane;
    private JTextField macField;

    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread acquisitionThread;

    public BitalinoApp() {
        super("BITalino Bluetooth GUI");

        bitalino = new BITalino();

        JPanel topPanel = new JPanel(new FlowLayout());

        macField = new JTextField(17); // MAC format XX:XX:XX:XX:XX:XX
        topPanel.add(new JLabel("MAC Address:"));
        topPanel.add(macField);

        samplingCombo = new JComboBox<>(new Integer[]{10, 100, 1000});
        topPanel.add(new JLabel("Sampling Rate:"));
        topPanel.add(samplingCombo);

        connectBtn = new JButton("Connect");
        connectBtn.setEnabled(true);
        topPanel.add(connectBtn);

        startBtn = new JButton("Start");
        startBtn.setEnabled(false);
        topPanel.add(startBtn);

        stopBtn = new JButton("Stop");
        stopBtn.setEnabled(false);
        topPanel.add(stopBtn);

        closeBtn = new JButton("Close");
        topPanel.add(closeBtn);

        outputArea = new JTextArea(20, 50);
        outputArea.setEditable(false);
        scrollPane = new JScrollPane(outputArea);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.SOUTH);

        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        connectBtn.addActionListener(this::connectAction);
        startBtn.addActionListener(this::startAction);
        stopBtn.addActionListener(this::stopAction);
        closeBtn.addActionListener(e -> {
            stopAcquisition();
            closeDevice();
            System.exit(0);
        });
    }

    private void connectAction(ActionEvent e) {
        String mac = macField.getText().trim();
        if (mac.isEmpty()) {
            outputArea.append("Please enter the MAC address.\n");
            return;
        }

        int rate = (Integer) samplingCombo.getSelectedItem();
        outputArea.append("Connecting to " + mac + " at " + rate + " Hz...\n");

        new Thread(() -> {
            try {
                bitalino.open(mac, rate); // <-- directly use MAC
                SwingUtilities.invokeLater(() -> {
                    outputArea.append("Connected!\n");
                    connectBtn.setEnabled(false);
                    startBtn.setEnabled(true);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("Connection failed: " + ex.getMessage() + "\n"));
            }
        }).start();
    }


    private void startAction(ActionEvent e) {
        int[] channels = {1};

        try {
            bitalino.start(channels);
            outputArea.append("Acquisition started on channel A1");
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