package main;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import javax.sound.sampled.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Pattern;
public class Controller {
    public Button button_start;
    public Button button_cancel;
    public ProgressBar progressBar;
    //First Row
    public Label macAddressLabel;
    //Second Row
    public ChoiceBox outputAudioChoiceBox;
    public Label outputAudioLabel;
    //Third Row
    public ChoiceBox<String> outputFormatOneChoiceBox;
    public ChoiceBox<String> outputFormatTwoChoiceBox;
    //TextArea
    public TextArea consoleLikeTextArea;

    private Thread taskThread;
    private Task<Void> connectionTask;
    public void start(ActionEvent actionEvent){
        AudioFormat selectedAudioFormat = constructAudioFormat();
        connectionTask = new Task<Void>() {
            private DataOutputStream dataOutputStream;
            private TargetDataLine audioOutputDataLine;
            private StreamConnection connection;
            @Override
            protected Void call() throws Exception {
                try {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            disableGUISinceWorkStarted();
                        }
                    });
                    // TODO: 2016/5/14 hint user, you have to try different audio format, since every cellphone, stop some app? It is service, you can do something else, prevent screen turn off.
                    consoleLikeTextArea.appendText("Server Started\n");
                    //step 1
                    UUID uuid = new UUID("1101", true);
                    String connectionString = "btspp://localhost:" + uuid + ";name=Sample SPP Server";
                    StreamConnectionNotifier streamConnectionNotifier = (StreamConnectionNotifier) Connector.open(connectionString);
                    connection = streamConnectionNotifier.acceptAndOpen();

                    //step 2
                    RemoteDevice remoteDevice = RemoteDevice.getRemoteDevice(connection);
                    consoleLikeTextArea.appendText("Remote device address: " + remoteDevice.getBluetoothAddress() + "\n");
                    consoleLikeTextArea.appendText("Remote device name: " + remoteDevice.getFriendlyName(true) + "\n");
                    dataOutputStream = connection.openDataOutputStream();

                    //step 3
                    //TODO support multiple device at same time? add gui choice box. Hint user to pair first? on client side. (much later)
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, selectedAudioFormat);
                    audioOutputDataLine = (TargetDataLine) AudioSystem.getLine(info);
                    audioOutputDataLine.open(selectedAudioFormat);
                    int frameSizeInBytes = selectedAudioFormat.getFrameSize();
                    int bufferLengthInFrames = audioOutputDataLine.getBufferSize() / 8;
                    int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
                    byte buffer[] = new byte[bufferLengthInBytes];
                    audioOutputDataLine.start();
                    while (!isCancelled()) {
                        int count = audioOutputDataLine.read(buffer, 0, buffer.length);
                        if (count > 0) {
                            // TODO: 2016/5/18 IOException Here (now)
                            dataOutputStream.write(buffer);
                        } else {
                            consoleLikeTextArea.appendText("No Sound Output. Try to change sample rate or choose other mixer.");
                            break;
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    consoleLikeTextArea.appendText("Device Offline!\n");
                    // TODO: 2016/5/18 save last successful button? show a save button? (now)
                    // TODO: 2016/5/18 cannnot restart? fix it now (now)
                }finally {
                    handleClose();
                }
                handleClose();
                return null;
            }
            private void handleClose() throws IOException {
                audioOutputDataLine.stop();
                audioOutputDataLine.close();
                audioOutputDataLine = null;
                dataOutputStream.close();
                connection.close();
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        restoreGUISinceWorkCompleted();
                    }
                });
            }
            private void restoreGUISinceWorkCompleted(){
                button_start.setDisable(false);
                button_cancel.setDisable(true);
                progressBar.setProgress(0);
                progressBar.setVisible(false);
                outputAudioChoiceBox.setDisable(false);
                outputFormatOneChoiceBox.setDisable(false);
                outputFormatTwoChoiceBox.setDisable(false);
            }
            private void disableGUISinceWorkStarted(){
                button_start.setDisable(true);
                button_cancel.setDisable(false);
                progressBar.setProgress(-1);
                progressBar.setVisible(true);
                outputAudioChoiceBox.setDisable(true);
                outputFormatOneChoiceBox.setDisable(true);
                outputFormatTwoChoiceBox.setDisable(true);
            }
        };
        taskThread = new Thread(connectionTask);
        taskThread.start();
    }
    private AudioFormat constructAudioFormat(){
        String chosenFormat = outputFormatOneChoiceBox.getValue();
        String[] subStringArray = chosenFormat.split(Pattern.quote(","));
        //Signed
        AudioFormat.Encoding signed;
        if(subStringArray[0].toLowerCase().contains("unsigned")){
            signed = AudioFormat.Encoding.PCM_UNSIGNED;
        }else{
            signed = AudioFormat.Encoding.PCM_SIGNED;
        }
        //sampleRate
        String sampleRateString = outputFormatTwoChoiceBox.getValue().replaceAll("\\D", "");//eliminate "Hz" characters
        float sampleRate = Float.parseFloat(sampleRateString);
        //Bits
        int bitsPerSample;
        bitsPerSample = Integer.parseInt(subStringArray[1].replaceAll("\\D", ""));//should be "8" or "16"
        //Channels
        int channels;
        if(subStringArray[2].contains("mono")){
            channels = 1;
        }else{
            channels = 2;
            // TODO: 2016/5/14 may be more channel? (much later)
        }
        //BytesPerFrame
        int bytesPerFrame;
        String stringBytesPerFrame = stringBytesPerFrame = subStringArray[3].replaceAll("\\D", "");//should be "1" or "2"
        bytesPerFrame = Integer.valueOf(stringBytesPerFrame);
        //frame rate
        float frameRate = sampleRate;
        //Endian
        boolean bigEndian = false;
        if(subStringArray[4].contains("little")){
            bigEndian = false;
        }else if(subStringArray[4].contains("big")){
            bigEndian = true;
        }else{
            // TODO: 2016/5/14 deal with unknown. (later)
        }
        AudioFormat selectedAudioFormat = new AudioFormat(signed, sampleRate,  bitsPerSample, channels, bytesPerFrame, frameRate, bigEndian);
        return selectedAudioFormat;
    }

    @Deprecated
    public void cancel(ActionEvent actionEvent){
        // TODO: 2016/5/15 cannot stop thread before connected (Right Now!)
        taskThread.stop();
        connectionTask.cancel();
    }

    //Connection
    //ConnectionTaskThread connectionThread;

    Vector<Mixer> mixerArray;
    ArrayList<String> mixerNameArray;
    HashMap<Integer, ArrayList<String>> mixerSupportAudioFormatStringArray;
    @FXML
    public void initialize(){
        getAudioMixerInformation();
        constructInterface();
    }
    private void getAudioMixerInformation(){
        mixerArray = new Vector<>();
        mixerNameArray = new ArrayList<>();
        mixerSupportAudioFormatStringArray = new HashMap<>();
        // TODO: 2016/5/14 hint user to install bluetooth driver if needed (later)
        //get sound...
        Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();
        for(int i=0;i<mixerInfo.length;i++){
            System.out.println(mixerInfo[i].getDescription());
            System.out.println(mixerInfo[i].getVendor());
            System.out.println("Mixer "+i);
            Mixer mixer = AudioSystem.getMixer(mixerInfo[i]);
            mixerArray.add(mixer);
            mixerNameArray.add((i+1)+" : " + mixerInfo[i].getDescription());
            try {
                mixer.open();

                System.out.println("Source Line Info");
                for(Line.Info info:mixer.getSourceLineInfo()){
                    System.out.println("Line info class" + info.getLineClass());
                    if(SourceDataLine.class.isAssignableFrom(info.getLineClass())){
                        TargetDataLine.Info ti = (TargetDataLine.Info)info;
                        AudioFormat[] formatArray =ti.getFormats();
                        for(AudioFormat format:formatArray){
                            System.out.println(format.toString());
                        }
                    }
                }

                int targetLineSupportedFormatNumber = 0;
                mixerSupportAudioFormatStringArray.put(i, new ArrayList<>());
                System.out.println("Target Line Info");
                for(Line.Info info:mixer.getTargetLineInfo()){
                    System.out.println("Line info class" + info.getLineClass());
                    if(TargetDataLine.class.isAssignableFrom(info.getLineClass())){
                        TargetDataLine.Info ti = (TargetDataLine.Info)info;
                        AudioFormat[] formatArray =ti.getFormats();
                        for(AudioFormat format:formatArray){
                            System.out.println(format.toString());
                            mixerSupportAudioFormatStringArray.get(i).add(format.toString());
                            targetLineSupportedFormatNumber++;
                        }
                    }
                }
                if(targetLineSupportedFormatNumber == 0){
                    mixerSupportAudioFormatStringArray.get(i).add("This device does not support capture sound");
                }
                System.out.println("\n");
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            }
        }
    }
    private void constructInterface(){

        progressBar.setVisible(false);
        //mixer combo box
        String[] mixerNameStringArray = mixerNameArray.toArray(new String[]{});
        outputAudioChoiceBox.setItems(FXCollections.observableArrayList(mixerNameStringArray));
        outputAudioChoiceBox.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                int i = newValue.intValue();
                String[] mixerSupportFormatStringArray = mixerSupportAudioFormatStringArray.get(i).toArray(new String[]{});

                ArrayList<String> outputFormatOneChoiceArrayList = new ArrayList<String>();
                for (String formatString : mixerSupportFormatStringArray) {
                    String formatStringWithoutSampleRate = formatString.replace("unknown sample rate", "");
                    outputFormatOneChoiceArrayList.add(formatStringWithoutSampleRate);
                }
                outputFormatOneChoiceBox.setItems(FXCollections.observableArrayList(outputFormatOneChoiceArrayList));

                String[] sampleRateOptions = new String[]{"44100Hz"};
                outputFormatTwoChoiceBox.setItems(FXCollections.observableArrayList(sampleRateOptions));

                if(mixerSupportFormatStringArray.length <= 1){
                    outputAudioLabel.setTextFill(Color.RED);
                    outputAudioLabel.setText("✖ "+"This Mixer Does Not Support");
                }else{
                    outputAudioLabel.setTextFill(Color.GREEN);
                    outputAudioLabel.setText("✔ "+"Give It A Try");
                    consoleLikeTextArea.appendText("Then Select Audio Output Format.\n");
                }
            }
        });
        outputFormatTwoChoiceBox.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if(mixerSupportAudioFormatStringArray.size() <= 1){
                    button_start.setDisable(true);
                }else{//enable the start button
                    button_start.setDisable(false);
                }
            }
        });
        String localDeviceAddress = macAddressLabel.getText();
        try {
            localDeviceAddress = LocalDevice.getLocalDevice().getBluetoothAddress();
        } catch (BluetoothStateException e) {
            e.printStackTrace();
        }
        macAddressLabel.setText(localDeviceAddress);

        consoleLikeTextArea.appendText("Select Your Mixer to Output Audio First.\n");
        // TODO: 2016/5/15 store last successful setting? (later)
    }
}
