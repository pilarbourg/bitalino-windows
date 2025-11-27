package ceu.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.bluetooth.RemoteDevice;

public class BitalinoApp extends JFrame {

    private BITalino bitalino;
    private JComboBox<Integer> samplingCombo;
    private JButton connectBtn, startBtn, stopBtn, closeBtn;
    private JTextArea outputArea;
    private JScrollPane scrollPane;
    private JTextField macField;
    private JButton saveBtn;
    private File lastRecordedFile;
    private BufferedWriter writer;
    private boolean firstSample = true;
    private File currentAcquisitionFile;


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

        saveBtn = new JButton("Save");
        saveBtn.setEnabled(false);
        topPanel.add(saveBtn);

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
        saveBtn.addActionListener(e -> saveFileAction());

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
            currentAcquisitionFile = File.createTempFile("bitalino_recording_", ".txt");
            writer = new BufferedWriter(new FileWriter(currentAcquisitionFile, false));

            writer.write(String.valueOf(samplingCombo.getSelectedItem()));
            writer.newLine();
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
        if (currentAcquisitionFile != null && currentAcquisitionFile.exists()) {
            saveBtn.setEnabled(true);
        }
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
            //outputArea.append("Acquisition stopped.\n");
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

    private void saveFileAction() {
        if (currentAcquisitionFile == null) {
            JOptionPane.showMessageDialog(this, "No recording available to save.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save recording");
        chooser.setSelectedFile(new File("bitalino_recording.txt"));

        int option = chooser.showSaveDialog(this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try {
                java.nio.file.Files.copy(
                        currentAcquisitionFile.toPath(),
                        dest.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );

                outputArea.append("File saved to: " + dest.getAbsolutePath() + "\n");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
            }
        }
    }


    private void readLoop() {
        final int blockSize = 10;

        try {
            // Segunda línea: empezamos una línea de datos
            firstSample = true;

            while (running.get()) {
                Frame[] frames = bitalino.read(blockSize);

                for (Frame f : frames) {
                    int a2 = f.analog[0];

                    // Escribir en buffer → valores separados por comas
                    if (!firstSample) {
                        writer.write(",");
                    }
                    writer.write(Integer.toString(a2));
                    firstSample = false;

                    // Mostrar en GUI
                    long ts = System.currentTimeMillis();
                    String text = "t=" + ts + " | A2=" + a2 + "\n";
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append(text);
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    });
                }
            }

            // Cuando termina la adquisición → cerrar línea
            writer.newLine();
            writer.flush();

            SwingUtilities.invokeLater(() ->
                    outputArea.append("Acquisition stopped.\n"));

        } catch (Throwable ex) {
            SwingUtilities.invokeLater(() ->
                    outputArea.append("Error during acquisition: " + ex.getMessage() + "\n"));
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (Exception ignored) {}
        }
    }
//98:D3:91:FD:69:49
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BitalinoApp().setVisible(true));
    }
}