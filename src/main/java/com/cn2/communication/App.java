package com.cn2.communication;

import java.io.*;
import java.net.*;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.awt.event.*;
import java.awt.Color;
import java.lang.Thread;

import javax.sound.sampled.*;

public class App extends Frame implements WindowListener, ActionListener {

	/*
	 * Definition of the app's fields
	 */
	static TextField inputTextField;		
	static JTextArea textArea;				 
	static JFrame frame;					
	static JButton sendButton;				
	static JTextField messageTextField;
	public static Color gray;				
	final static String newline="\n";		
	static JButton callButton;

	// TODO: Please define and initialize your variables here...
	static TargetDataLine microphone;
	static SourceDataLine speaker;
	static DatagramSocket chatSocket;
	static DatagramSocket voipSocket;
	static  int LOCAL_CHAT_PORT = 2505;     // Port for receiving chat messages
	static  int PEER_CHAT_PORT = 2506;      // Port for sending chat messages
	static  int LOCAL_VOIP_PORT = 2507;     // Port for receiving audio
	static  int PEER_VOIP_PORT = 2508;      // Port for sending audio
	static final String PEER_ADDRESS = "192.168.1.3";	// Address of the peer. Provide actual address
	private static boolean isCallActive = false;

	/**
	 * Construct the app's frame and initialize important parameters
	 */
	public App(String title) {

		/*
		 * 1. Defining the components of the GUI
		 */
		// Setting up the characteristics of the frame
		super(title);									
		gray = new Color(254, 254, 254);		
		setBackground(gray);
		setLayout(new FlowLayout());			
		addWindowListener(this);
		
		// Setting up the TextField and the TextArea
		inputTextField = new TextField();
		inputTextField.setColumns(20);
		
		// Setting up the TextArea.
		textArea = new JTextArea(10,40);			
		textArea.setLineWrap(true);				
		textArea.setEditable(false);			
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		//Setting up the buttons
		sendButton = new JButton("Send");			
		callButton = new JButton("Call");			
						
		/*
		 * 2. Adding the components to the GUI
		 */
		add(scrollPane);								
		add(inputTextField);
		add(sendButton);
		add(callButton);
		
		/*
		 * 3. Linking the buttons to the ActionListener
		 */
		sendButton.addActionListener(this);
		callButton.addActionListener(this);
	}
	
	/**
	 * The main method of the application. It continuously listens for
	 * new messages.
	 */
	public static void main(String[] args){
	
		/*
		 * 1. Create the app's window
		 */
		App app = new App("CN2 - AUTH");
		app.setSize(500,250);				  
		app.setVisible(true);				  

		/*
		 * 2. 
		 */

		// TODO: Your code goes here...

		if (args.length == 4) {
			LOCAL_CHAT_PORT = Integer.parseInt(args[0]);
			PEER_CHAT_PORT = Integer.parseInt(args[1]);
			LOCAL_VOIP_PORT = Integer.parseInt(args[2]);
			PEER_VOIP_PORT = Integer.parseInt(args[3]);
		}
		else {
			System.out.println("Using default ports.");
		}

		// UDP implementation
		new Thread(() -> {
			try {
				byte[] buffer = new byte[1024];
				chatSocket = new DatagramSocket(LOCAL_CHAT_PORT);
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

				do {
					chatSocket.receive(packet);
					String receivedMessage = new String(packet.getData(), 0, packet.getLength());

					if (receivedMessage.startsWith("CONTROL:")) {
						handleControlMessage(receivedMessage.substring(8));
					}
					else {
						textArea.append("Peer: " + receivedMessage + newline);
					}

					packet.setLength(buffer.length);
				} while (true);
			} catch (Exception exception) {
				textArea.append("Error receiving chat messages: " + exception.getMessage() + newline);
			}
		}).start();
	}
	
	/**
	 * The method that corresponds to the Action Listener. Whenever an action is performed
	 * (i.e., one of the buttons is clicked) this method is executed. 
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		/*
		 * Check which button was clicked.
		 */
		if (e.getSource() == sendButton){
			// The "Send" button was clicked

			// TODO: Your code goes here...

			String message = inputTextField.getText().trim();
			if (!message.isEmpty()) {
				sendChatMessage(message);
			}
			else {
				textArea.append("Cannot send an empty message." + newline);
			}
		}
		else if(e.getSource() == callButton){
			// The "Call" button was clicked
			
			// TODO: Your code goes here...

			if (!isCallActive) {
				// Start call
				textArea.append("Starting voice call..." + newline);
				callButton.setText("End Call");
				isCallActive = true;

				notifyPeer("CONTROL:START_CALL");

				startAudioReceiver();
				startAudioTransmitter();
			}
			else {
				// End call
				isCallActive = false;
				callButton.setText("Call");

				notifyPeer("CONTROL:END_CALL");
				closeAudioResources();

				textArea.append("Voice call ended." + newline);
			}
		}
	}

	private static void sendChatMessage(String message) {
		byte[] buffer = message.getBytes();
		try {
			InetAddress peerAddress = InetAddress.getByName(PEER_ADDRESS);
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, peerAddress, PEER_CHAT_PORT);
			chatSocket.send(packet);

			textArea.append("You: " + message + newline);
			inputTextField.setText("");
		} catch (Exception exception) {
			textArea.append("Error sending chat message: " + exception.getMessage() + newline);
		}
	}

	private static void startAudioReceiver() {
		// Receive Audio
		new Thread(() -> {
			try {
				voipSocket = new DatagramSocket(LOCAL_VOIP_PORT);

				AudioFormat format = new AudioFormat(8000, 8, 1, true, true);
				speaker = AudioSystem.getSourceDataLine(format);
				speaker.open(format);
				speaker.start();

				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

				while (true) {
					voipSocket.receive(packet);
					speaker.write(packet.getData(), 0, packet.getLength());

					packet.setLength(buffer.length);
				}
			} catch (Exception exception) {
				if (isCallActive) {
					textArea.append("Error during VoIP call: " + exception.getMessage() + newline);
				}
			}
		}).start();
	}

	private static void startAudioTransmitter() {
		// Send Audio
		new Thread(() -> {
			try {
				InetAddress peerAddress = InetAddress.getByName(PEER_ADDRESS);

				AudioFormat format = new AudioFormat(8000, 8, 1, true, true);
				microphone = AudioSystem.getTargetDataLine(format);
				microphone.open(format);
				microphone.start();

				byte[] buffer = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length, peerAddress, PEER_VOIP_PORT);
				while (true) {
					int bytesRead = microphone.read(buffer, 0, buffer.length);
					packet.setData(buffer, 0, bytesRead);
					voipSocket.send(packet);
				}
			} catch (Exception exception) {
				if (isCallActive) {
					textArea.append("Error during VoIP call: " + exception.getMessage() + newline);
				}
			}
		}).start();
	}

	private static void closeAudioResources() {
		try {
			if (microphone != null && microphone.isOpen()) {
				microphone.stop();
				microphone.close();
			}
			if (speaker != null && speaker.isOpen()) {
				speaker.stop();
				speaker.close();
			}
			if (voipSocket != null && !voipSocket.isClosed()) {
				voipSocket.close();
			}
		} catch (Exception exception) {
			textArea.append("Error closing VoIP call: " + exception.getMessage() + newline);
		}
	}

	private static void notifyPeer(String message) {
		try {
			byte[] handleCallPeer = message.getBytes();
			InetAddress peerAddress = InetAddress.getByName(PEER_ADDRESS);
			DatagramPacket packet = new DatagramPacket(handleCallPeer, handleCallPeer.length, peerAddress, PEER_CHAT_PORT);
			chatSocket.send(packet);
		} catch (Exception exception) {
			textArea.append("Error notifying peer: " + exception.getMessage() + newline);
		}
	}

	private static void handleControlMessage(String controlMessage) {
		if (controlMessage.equals("START_CALL") && !isCallActive) {
			textArea.append("Peer is calling..." + newline);
			callButton.setText("Pick Up");
		}
		else if (controlMessage.equals("END_CALL") && isCallActive) {
			textArea.append("Peer ended the call." + newline);
			callButton.setText("Call");
			isCallActive = false;
			closeAudioResources();
		}
	}

	/**
	 * These methods have to do with the GUI. You can use them if you wish to define
	 * what the program should do in specific scenarios (e.g., when closing the 
	 * window).
	 */
	@Override
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void windowClosing(WindowEvent e) {
		// TODO Auto-generated method stub
		try {
			if (chatSocket != null && !chatSocket.isClosed()) chatSocket.close();
			if (voipSocket != null && !voipSocket.isClosed()) voipSocket.close();
			if (microphone != null) microphone.close();
			if (speaker != null) speaker.close();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		dispose();
		System.exit(0);
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub	
	}

	@Override
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub	
	}
}
