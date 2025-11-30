package ceu.biolab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.bluetooth.RemoteDevice;
import javax.swing.border.EmptyBorder;

public class BitalinoApp extends JFrame {

    private BITalino bitalino;
    private JComboBox<Integer> samplingCombo;
    public JComboBox<String> typeCombo;
    public JButton connectBtn;
    public JButton startBtn;
    public JButton stopBtn;
    private JButton closeBtn;
    public JTextArea outputArea;
    private JScrollPane scrollPane;
    public JTextField macField;
    public JButton saveBtn;
    private JButton newRecBtn;
    private BufferedWriter writer;
    private boolean firstSample = true;
    private File currentAcquisitionFile;
    private SignalPanel signalPanel;
    private boolean isConnected = false;


    public AtomicBoolean running = new AtomicBoolean(false);
    private Thread acquisitionThread;

    public BitalinoApp() {
        bitalino = new BITalino();
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Panel raíz
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);  // solo para que no se vea gris

        //CABECERA (dos barras)
        JPanel headerPanel = crearHeaderPanel();  // -> función que te pongo abajo
        root.add(headerPanel, BorderLayout.NORTH);

        //Zona inferior: gráfica + logs
        outputArea = new JTextArea(8, 50);
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
        scrollPane = new JScrollPane(outputArea);

        signalPanel = new SignalPanel();

        // Scroll horizontal para la señal
        JScrollPane signalScroll = new JScrollPane(
                signalPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        signalScroll.getViewport().setBackground(Color.BLACK);

        // Split vertical: arriba gráfica (con scroll), abajo logs
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                signalScroll,
                scrollPane
        );
        splitPane.setResizeWeight(0.7);

        // === Layout principal ===
        //setLayout(new BorderLayout());
        //add(topPanel, BorderLayout.NORTH);
        root.add(splitPane, BorderLayout.CENTER);  // <-- solo el splitPane, nada más
        setContentPane(root);

        pack();
        setLocationRelativeTo(null); // en fullscreen no importa mucho, pero lo dejamos

        // === Listeners ===
        connectBtn.addActionListener(this::connectAction);
        startBtn.addActionListener(this::startAction);
        stopBtn.addActionListener(this::stopAction);
        closeBtn.addActionListener(e -> {
            if (bitalino != null) {
                System.out.println("Closing BITalino connection...");
                stopAcquisition();
                closeDevice();
            }
            System.out.println("No BITalino connection established -> closing application...");
            System.exit(0);
        });
        saveBtn.addActionListener(e -> saveFileAction());
        newRecBtn.addActionListener(e -> newRecording());

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();

        setSize(screenSize);
        setLocation(0, 0);
    }

    public void newRecording() {
        try {
            // 1. Parar adquisición si estaba corriendo
            running.set(false);
            if (acquisitionThread != null && acquisitionThread.isAlive()) {
                acquisitionThread.join();
            }

            // 2. Parar el dispositivo si está activo
            try {
                bitalino.stop();
            } catch (Exception ignored) {}

            // 3. Cerrar el dispositivo si está abierto
            try {
                bitalino.close();
            } catch (Exception ignored) {}

            // Reconectar el objeto BITalino
            bitalino = new BITalino();

        } catch (Exception ex) {
            outputArea.append("Error resetting BITalino: " + ex.getMessage() + "\n");
        }

        // 4. Resetear UI
        outputArea.setText("");            // limpia logs
        signalPanel.clear();               // limpia gráfica → crear método abajo
        startBtn.setEnabled(false);
        stopBtn.setEnabled(false);
        saveBtn.setEnabled(false);
        connectBtn.setEnabled(true);       // hay que reconectar
        firstSample = true;

        outputArea.append("New recording ready, select new sampling rate to connect bitalino.\n");
    }


    private JPanel crearHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setPreferredSize(new Dimension(1920, 111));

        // ===== BARRA SUPERIOR (CLOSE) =====
        JPanel closeBar = new JPanel(new BorderLayout());
        closeBar.setOpaque(true);
        closeBar.setBackground(new Color(244, 234, 234, 242));
        closeBar.setBorder(new EmptyBorder(5, 10, 5, 10));

        Font buttonFont = new Font("Open sans", Font.BOLD, 18);

        newRecBtn = createToolbarButton("New recording");
        newRecBtn.setFont(buttonFont);
        newRecBtn.setPreferredSize(new Dimension(200, 30));


        closeBtn = createToolbarButton("Close");
        closeBtn.setFont(buttonFont);
        closeBtn.setPreferredSize(new Dimension(100, 30));
        closeBar.add(newRecBtn, BorderLayout.WEST);


        closeBar.add(closeBtn, BorderLayout.EAST);

        // ===== BARRA INFERIOR (CONTROLES) =====
        JPanel controlsBar = new JPanel();
        controlsBar.setOpaque(true);
        controlsBar.setBackground(new Color(146, 162, 218, 199));
        controlsBar.setBorder(new EmptyBorder(15, 30, 15, 30));
        controlsBar.setLayout(new BoxLayout(controlsBar, BoxLayout.X_AXIS));

        Font labelFont  = new Font("Open sans", Font.BOLD, 18);
        Font fieldFont  = new Font("Monospaced", Font.BOLD, 18);

        // IZQUIERDA
        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.X_AXIS));

        JLabel macLabel = new JLabel("MAC Address:");
        macLabel.setFont(labelFont);
        macLabel.setForeground(Color.BLACK);
        leftPanel.add(macLabel);
        leftPanel.add(Box.createRigidArea(new Dimension(10, 0)));

        macField = new JTextField();
        macField.setFont(fieldFont);
        macField.setMaximumSize(new Dimension(300, 35));
        macField.setMaximumSize(new Dimension(480, 35));
        macField.setPreferredSize(new Dimension(600, 35));
        leftPanel.add(macField);
        leftPanel.add(Box.createRigidArea(new Dimension(30, 0)));


        JLabel srLabel = new JLabel("Sampling Rate:");
        srLabel.setFont(labelFont);
        srLabel.setForeground(Color.black);
        leftPanel.add(srLabel);
        leftPanel.add(Box.createRigidArea(new Dimension(10, 0)));

        samplingCombo = new JComboBox<>(new Integer[]{10, 100, 1000});
        samplingCombo.setFont(fieldFont);
        samplingCombo.setMaximumSize(new Dimension(120, 35));
        leftPanel.add(samplingCombo);
        leftPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        connectBtn = createToolbarButton("Connect");
        leftPanel.add(connectBtn);
        //leftPanel.add(Box.createRigidArea(new Dimension(10, 0)));



        // DERECHA

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));

        JLabel typeLabel = new JLabel("Type of recording:");
        typeLabel.setFont(labelFont);
        typeLabel.setForeground(Color.black);
        rightPanel.add(typeLabel);
        typeCombo = new JComboBox<>(new String[]{"EMG", "ECG"});
        typeCombo.setFont(fieldFont);
        typeCombo.setMaximumSize(new Dimension(120, 35));
        rightPanel.add(typeCombo);
        rightPanel.add(Box.createRigidArea(new Dimension(30, 0)));

        Dimension bigButtonSize = new Dimension(120, 40);

        startBtn   = createToolbarButton("⏺");
        startBtn.setFont(srLabel.getFont().deriveFont(21f));
        startBtn.setForeground(new Color(175, 0, 0));
        stopBtn    = createToolbarButton("⏹");
        stopBtn.setFont(srLabel.getFont().deriveFont(19f));
        stopBtn.setForeground(new Color(0, 0, 0));
        stopBtn.setPreferredSize(new Dimension(50, 34));
        saveBtn    = createToolbarButton("Save ⎙");

        connectBtn.setFont(buttonFont);
        //startBtn.setFont(buttonFont);
        //stopBtn.setFont(buttonFont);
        saveBtn.setFont(buttonFont);

        connectBtn.setPreferredSize(bigButtonSize);
        startBtn.setPreferredSize(bigButtonSize);
        stopBtn.setPreferredSize(bigButtonSize);
        saveBtn.setPreferredSize(bigButtonSize);


        rightPanel.add(startBtn);
        rightPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        rightPanel.add(stopBtn);
        rightPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        rightPanel.add(saveBtn);

        controlsBar.add(leftPanel);
        controlsBar.add(Box.createRigidArea(new Dimension(80, 0)));
        controlsBar.add(Box.createHorizontalGlue());
        controlsBar.add(rightPanel);

        headerPanel.add(closeBar, BorderLayout.NORTH);
        headerPanel.add(controlsBar, BorderLayout.CENTER);

        return headerPanel;
    }


    private JButton createToolbarButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        btn.setBackground(new Color(255, 255, 255));
        btn.setForeground(Color.BLACK);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));

        return btn;
    }



    public void connectAction(ActionEvent e) {
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
                    isConnected = true;
                    connectBtn.setEnabled(false);
                    startBtn.setEnabled(true);
                });
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(() ->
                        outputArea.append("Connection failed: " + ex.getMessage() + "\n"));
            }
        }).start();
    }

    public void startAction(ActionEvent e) {
        int [] channels = null;
        String type = typeCombo.getSelectedItem().toString();
        if (type.equals("EMG")) {
            channels = new int[]{0};   // a1
        } else if (type.equals("ECG")) {
            channels = new int[]{1};   // a2
        } else {
            outputArea.append("Please select a type of recording.\n");
            return; // evita continuar con channels = null
        }


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
        long startTimeMs = System.currentTimeMillis();

        try {
            // Segunda línea: empezamos una línea de datos
            firstSample = true;
            int durationSeconds = 2*60;
            long maxDurationMs = durationSeconds * 1000L;
            long startLoopTime = System.currentTimeMillis();


            while (running.get() && (System.currentTimeMillis() - startLoopTime) < maxDurationMs) {
                Frame[] frames = bitalino.read(blockSize);

                for (Frame f : frames) {
                    int a2 = f.analog[0];

                    // Escribir en buffer → valores separados por comas
                    if (!firstSample) {
                        writer.write(",");
                    }
                    writer.write(Integer.toString(a2));
                    firstSample = false;

                    double ts = (System.currentTimeMillis() - startTimeMs) / 1000.0;
                    /*String text = String.format("t=%.3f s | value = %d%n", ts, a2);
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append(text);
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                    });*/
                    String text = "t=" + ts + " | value = " + a2 + "\n";
                    SwingUtilities.invokeLater(() -> {
                        outputArea.append(text);
                        outputArea.setCaretPosition(outputArea.getDocument().getLength());
                        if (signalPanel != null) {
                            signalPanel.addSample(a2);
                        }
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
    //0C:43:14:24:73:63
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // O si tienes FlatLaf añadido:
            // FlatLightLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> {
                BitalinoApp app = new BitalinoApp();
                app.setVisible(true);
            });
        });

    }

    //FOR TESTS
    BITalino getBitalino() {
        return bitalino;
    }

    public void setBitalino(BITalino bitalino) {
        this.bitalino = bitalino;
    }

    public JTextField getMacField() {
        return macField;
    }

    public JComboBox<Integer> getSamplingCombo() {
        return samplingCombo;
    }

    public JTextArea getOutputArea() {
        return outputArea;
    }

    // Como connectAction es private, exponemos un wrapper
    public void connectActionForTest(ActionEvent e) {
        connectAction(e);
    }


}