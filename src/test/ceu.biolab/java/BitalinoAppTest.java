package ceu.biolab.java;

import ceu.biolab.BITalino;
import ceu.biolab.BitalinoApp;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;



public class BitalinoAppTest {

    @Test
    void connect_emptyMac_showsErrorMessage() throws Exception {

        final BitalinoApp[] ref = new BitalinoApp[1];

        SwingUtilities.invokeAndWait(() -> {
            ref[0] = new BitalinoApp();
        });

        BitalinoApp app = ref[0];

        app.macField.setText("");

        app.connectAction(new ActionEvent(app, 0, "connect"));

        String output = app.outputArea.getText();
        assertTrue(output.contains("Please enter the MAC address."),
                "Debe pedir al usuario que introduzca la MAC");
    }


    @Test
    void connect_failure() throws Exception {

        BITalino mockDevice = mock(BITalino.class);
        BitalinoApp app = new BitalinoApp();

        app.setBitalino(mockDevice);
        app.getMacField().setText("98:D3:91:FD:69:49");
        app.getSamplingCombo().setSelectedItem(100);

        // Mockito: simular error en open()
        doThrow(new RuntimeException("Connection failed"))
                .when(mockDevice)
                .open("98:D3:91:FD:69:49", 100);

        app.connectActionForTest(new ActionEvent(app, 0, "connect"));
        Thread.sleep(200); // porque el método corre en un Thread

        assertThat(app.getOutputArea().getText())
                .contains("Connection failed");

        verify(mockDevice).open("98:D3:91:FD:69:49", 100);
    }

    @Test
    void newRecording_resetsUiAndState() {
        BitalinoApp app = new BitalinoApp();

        app.outputArea.setText("Old logs");
        app.startBtn.setEnabled(true);
        app.stopBtn.setEnabled(true);
        app.saveBtn.setEnabled(true);
        app.connectBtn.setEnabled(false);
        app.running.set(true);

        app.newRecording();

        assertFalse(app.running.get(), "running should be false after newRecording");
        assertFalse(app.startBtn.isEnabled(), "startBtn disabled");
        assertFalse(app.stopBtn.isEnabled(), "stopBtn disabled");
        assertFalse(app.saveBtn.isEnabled(), "saveBtn disabled");
        assertTrue(app.connectBtn.isEnabled(), "connectBtn enabled to start new connection");

        String text = app.outputArea.getText();
        assertTrue(text.contains("New recording ready"),
                "Should show message about new recording being ready");
    }

    @Test
    void startAction_withEMG_usesChannel0() throws Throwable {
        BITalino mockDevice = mock(BITalino.class);
        BitalinoApp app = new BitalinoApp();
        app.setBitalino(mockDevice);

        app.getMacField().setText("98:D3:91:FD:69:49");
        app.getSamplingCombo().setSelectedItem(100);
        app.typeCombo.setSelectedItem("EMG");

        app.startAction(new ActionEvent(app, 0, "start"));

        verify(mockDevice).start(new int[]{0});
        assertTrue(app.running.get());
        assertFalse(app.startBtn.isEnabled());
        assertTrue(app.stopBtn.isEnabled());
    }
}
