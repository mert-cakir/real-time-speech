package org.ioe.tprsa.audio;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.SoftBevelBorder;

import org.ioe.tprsa.mediator.Operations;
import org.ioe.tprsa.util.MessageType;

/**
 * Capture/Playback sample. Record audio in different formats and then playback the recorded audio. The captured audio can be saved either as a WAVE, AU or
 * AIFF. Or load an audio file for streaming playback.
 * 
 * @version @(#)Manual.java 1.11 99/12/03
 * @version 2.1
 * @author Brian Lichtenwalter-- visualization and capture
 * @modified-by Ganesh --> made a reusable class
 */

public class JSoundCapture extends JPanel implements ActionListener {

	private static final long 	serialVersionUID 	= 1943720871022387749L;
	private static final int 	INTERVAL 		= 2;
	byte[]				audioBytes		= null;
	float[]				audioData		= null;
	float[]				audioData2		= null;
	final int			BUFFER_SIZE		= 16384;
	int				counter			= 0;
	FormatControlConf		formatControls		= new FormatControlConf( );
	Capture				capture			= new Capture( );
	Playback			playback		= new Playback( );
	WaveData			wd;
	AudioInputStream		audioInputStream;
	AudioInputStream 		audioInputStream1;
	AudioInputStream 		audioInputStream2;
	SamplingGraph			samplingGraph;
	JButton				playB, captB, pausB;
	JButton				saveB;
	JLabel				text;
	String				errStr;
	double				duration, seconds;
	File				file;
	Vector< Line2D.Double >		lines			= new Vector< Line2D.Double >( );
	boolean				isDrawingRequired;
	boolean				isSaveRequired;
	JPanel				innerPanel;
	JPanel				verifyPanel		= null;
	String				saveFileName		= null;
	String				fileName		= null;
	private Operations		opr			= new Operations( );

	/**
	 * Instantiates a new j sound capture.
	 * 
	 * @param isDrawingRequired
	 *            the is drawing required
	 * @param isSaveRequired
	 *            the is save required
	 */
	public JSoundCapture( boolean isDrawingRequired, boolean isSaveRequired ) {
		wd = new WaveData( );
		this.isDrawingRequired = isDrawingRequired;
		this.isSaveRequired = isSaveRequired;
		setLayout( new BorderLayout( ) );
		setBorder( new EmptyBorder( 1, 1, 1, 1 ) );

		innerPanel = new JPanel( );

		innerPanel.setLayout( new BoxLayout( innerPanel, BoxLayout.X_AXIS ) );

		JPanel buttonsPanel = new JPanel( );
		buttonsPanel.setPreferredSize( new Dimension( 200, 50 ) );
		buttonsPanel.setBorder( new EmptyBorder( 5, 0, 1, 0 ) );
		buttonsPanel.setBackground( Color.WHITE );
		playB = addButton( "Play", buttonsPanel, false );
		captB = addButton( "Record", buttonsPanel, true );
		pausB = addButton( "Pause", buttonsPanel, false );
		saveB = addButton( "Save ", buttonsPanel, false );
		innerPanel.add( buttonsPanel );
		innerPanel.setBackground( Color.WHITE );
		
		// samplingPanel
		if ( isDrawingRequired ) {
			JPanel samplingPanel = new JPanel( new BorderLayout( ) );
			EmptyBorder eb = new EmptyBorder( 2, 2, 2, 2 );
			SoftBevelBorder sbb = new SoftBevelBorder( SoftBevelBorder.LOWERED );
			samplingPanel.setBorder( new CompoundBorder( eb, sbb ) );
			samplingPanel.add( samplingGraph = new SamplingGraph( ) );
			innerPanel.add( samplingPanel );
		}
		// whole panel
		JPanel completePanel = new JPanel( );
		completePanel.setLayout( new BoxLayout( completePanel, BoxLayout.Y_AXIS ) );
		completePanel.add( innerPanel );
		completePanel.add( getStatusText() );
		completePanel.setBackground( Color.WHITE );
		add( completePanel );
		setBackground( Color.WHITE );
	}

	public boolean isSoundDataAvailable( ) {
		if ( audioBytes != null )
			return ( audioBytes.length > 100 );
		else
			return false;
	}

	public byte[] getAudioBytes( ) {
		return audioBytes;
	}
	
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getSaveFileName( ) {
		return saveFileName;
	}

	public void setSaveFileName( String saveFileName ) {
		this.saveFileName = saveFileName;
		System.out.println( "FileName Changed !!! " + saveFileName );
	}

	public float[] getAudioData( ) throws Exception {
		if ( audioData == null ) {
			audioData = wd.extractFloatDataFromAudioInputStream( audioInputStream );
		}
		return audioData;
	}

	public void setAudioData( float[] audioData ) {
		this.audioData = audioData;
	}

	public float[] getAudioData2( ) throws Exception {
		audioData = wd.extractFloatDataFromAudioInputStream( audioInputStream2 );
		return audioData;
	}

	public void setAudioData2( float[] audioData ) {
		this.audioData = audioData;
	}

	private JButton addButton( String name, JPanel p, boolean state ) {
		JButton b = new JButton( name );
		b.setPreferredSize( new Dimension( 85, 24 ) );
		b.addActionListener( this );
		b.setEnabled( state );
		b.setFocusable( false );
		p.add( b );
		return b;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed( ActionEvent e ) {
		System.out.println( "actionPerformed *********" );
		Object obj = e.getSource( );
		if ( isSaveRequired && obj.equals( saveB ) ) {

			try {
				getFileNameAndSaveFile( );

			} catch ( Exception e2 ) {
				reportStatus( "Error in savng file " + e2.getMessage( ), MessageType.ERROR );
			}

		} else if ( obj.equals( playB ) ) {
			if ( playB.getText( ).startsWith( "Play" ) ) {
				playCaptured( );
			} else {
				stopPlaying( );
			}
		} else if ( obj.equals( captB ) ) {
			if ( captB.getText( ).startsWith( "Record" ) ) {
				startRecord( );
			} else {
				stopRecording( );
			}
		} else if ( obj.equals( pausB ) ) {
			if ( pausB.getText( ).startsWith( "Pause" ) ) {
				pausePlaying( );
			} else {
				resumePlaying( );
			}
		}
	}

	public void playCaptured( ) {
		playback.start( );
		if ( isDrawingRequired )
			samplingGraph.start( );
		captB.setEnabled( false );
		pausB.setEnabled( true );
		playB.setText( "Stop" );
	}

	public void stopPlaying( ) {
		playback.stop( );
		if ( isDrawingRequired )
			samplingGraph.stop( );
		captB.setEnabled( true );
		pausB.setEnabled( false );
		playB.setText( "Play" );
	}

	public void startRecord( ) {
		file = null;
		capture.start( );
		if ( isDrawingRequired )
			samplingGraph.start( );
		playB.setEnabled( false );
		pausB.setEnabled( true );
		saveB.setEnabled( false );
		captB.setText( "Stop" );
	}

	public void stopRecording( ) {
		lines.removeAllElements( );
		capture.stop( );
		if ( isDrawingRequired )
			samplingGraph.stop( );
		playB.setEnabled( true );
		pausB.setEnabled( false );
		saveB.setEnabled( true );
		captB.setText( "Record" );
	}

	public void pausePlaying( ) {

		if ( capture.thread != null ) {
			capture.line.stop( );
		} else {
			if ( playback.thread != null ) {
				playback.line.stop( );
			}
		}
		pausB.setText( "Resume" );

	}

	public void resumePlaying( ) {
		if ( capture.thread != null ) {
			capture.line.start( );
		} else {
			if ( playback.thread != null ) {
				playback.line.start( );
			}
		}
		pausB.setText( "Pause" );
	}

	public void getFileNameAndSaveFile( ) throws Exception {
		while ( saveFileName == null ) {
			saveFileName = JOptionPane.showInputDialog( null, "Enter WAV File Name", "Getting File Name" );
		}
		wd.saveToFile( fileName, saveFileName, AudioFileFormat.Type.WAVE, audioInputStream );

	}

	/* public void saveFileAutoMode() {
	 wd.saveToFile(saveFileName, AudioFileFormat.Type.WAVE, audioInputStream);
	 saveFileName=null;
	 } */

	/**
	 * Creates the audio input stream.
	 * 
	 * @param file
	 *            the file
	 * @param updateComponents
	 *            the update components
	 */
	public void createAudioInputStream( File file, boolean updateComponents ) {
		if ( file != null && file.isFile( ) ) {
			try {
				this.file = file;
				errStr = null;
				audioInputStream = AudioSystem.getAudioInputStream( file );
				playB.setEnabled( true );
				// fileName = file.getName();
				long milliseconds = ( long ) ( ( audioInputStream.getFrameLength( ) * 1000 ) / audioInputStream.getFormat( ).getFrameRate( ) );
				duration = milliseconds / 1000.0;

				saveB.setEnabled( true );
				if ( updateComponents ) {
					formatControls.setFormat( audioInputStream.getFormat( ) );
					if ( isDrawingRequired )
						samplingGraph.createWaveForm( null );
				}
			} catch ( Exception ex ) {
				reportStatus( ex.toString( ), MessageType.ERROR );
			}
		} else {
			reportStatus( "Audio file required.", MessageType.INFO );
		}
	}

	/**
	 * Report status.
	 * 
	 * @param msg
	 *            the msg
	 */
	private void reportStatus( String msg, MessageType type ) {
		if ( ( errStr = msg ) != null ) {
			System.out.println( errStr );
			if ( isDrawingRequired )
				samplingGraph.repaint( );
		}
	}

	/**
	 * Write data to the OutputChannel.
	 */
	public class Playback implements Runnable {

		SourceDataLine	line;
		Thread			thread;

		public void start( ) {
			errStr = null;
			thread = new Thread( this );
			thread.setName( "Playback" );
			thread.start( );
		}

		public void stop( ) {
			thread = null;
		}

		private void shutDown( String message ) {
			if ( ( errStr = message ) != null ) {
				System.err.println( errStr );
				if ( isDrawingRequired )
					samplingGraph.repaint( );
			}
			if ( thread != null ) {
				thread = null;
				if ( isDrawingRequired )
					samplingGraph.stop( );
				captB.setEnabled( true );
				pausB.setEnabled( false );
				playB.setText( "Play" );
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		public void run( ) {

			// reload the file if loaded by file
			if ( file != null ) {
				createAudioInputStream( file, false );
			}

			// make sure we have something to play
			if ( audioInputStream == null ) {
				shutDown( "No loaded audio to play back" );
				return;
			}
			// reset to the beginnning of the stream
			try {
				audioInputStream.reset( );
			} catch ( Exception e ) {
				shutDown( "Unable to reset the stream\n" + e );
				return;
			}

			// get an AudioInputStream of the desired format for playback
			AudioFormat format = formatControls.getFormat( );
			AudioInputStream playbackInputStream = AudioSystem.getAudioInputStream( format, audioInputStream );

			if ( playbackInputStream == null ) {
				shutDown( "Unable to convert stream of format " + audioInputStream + " to format " + format );
				return;
			}

			// define the required attributes for our line,
			// and make sure a compatible line is supported.

			DataLine.Info info = new DataLine.Info( SourceDataLine.class, format );
			if ( !AudioSystem.isLineSupported( info ) ) {
				shutDown( "Line matching " + info + " not supported." );
				return;
			}

			// get and open the source data line for playback.

			try {
				line = ( SourceDataLine ) AudioSystem.getLine( info );
				line.open( format, BUFFER_SIZE );
			} catch ( LineUnavailableException ex ) {
				shutDown( "Unable to open the line: " + ex );
				return;
			}

			// play back the captured audio data

			int frameSizeInBytes = format.getFrameSize( );
			int bufferLengthInFrames = line.getBufferSize( ) / 8;
			int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			byte[] data = new byte[ bufferLengthInBytes ];
			int numBytesRead = 0;

			// start the source data line
			line.start( );

			while ( thread != null ) {
				try {
					if ( ( numBytesRead = playbackInputStream.read( data ) ) == -1 ) {
						break;
					}
					int numBytesRemaining = numBytesRead;
					while ( numBytesRemaining > 0 ) {
						numBytesRemaining -= line.write( data, 0, numBytesRemaining );
					}
				} catch ( Exception e ) {
					shutDown( "Error during playback: " + e );
					break;
				}
			}
			// we reached the end of the stream. let the data play out, then
			// stop and close the line.
			if ( thread != null ) {
				line.drain( );
			}
			line.stop( );
			line.close( );
			line = null;
			shutDown( null );
		}
	} // End class Playback

	/**
	 * Reads data from the input channel and writes to the output stream
	 * 
	 * @modified-by mert
	 */
	class Capture implements Runnable {

		TargetDataLine	line;
		Thread			thread;

		public void start( ) {
			errStr = null;
			thread = new Thread( this );
			thread.setName( "Capture" );
			thread.start( );
		}

		public void stop( ) {
			thread = null;
		}

		private void shutDown( String message ) {
			if ( ( errStr = message ) != null && thread != null ) {
				thread = null;
				if ( isDrawingRequired )
					samplingGraph.stop( );

				playB.setEnabled( true );
				pausB.setEnabled( false );
				saveB.setEnabled( true );
				captB.setText( "Record" );
				if ( isDrawingRequired )
					samplingGraph.repaint( );
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		public void run( ) {

			duration = 0;
			audioInputStream = null;

			// define the required attributes for our line,
			// and make sure a compatible line is supported.

			AudioFormat format = formatControls.getFormat( );
			DataLine.Info info = new DataLine.Info( TargetDataLine.class, format );

			if ( !AudioSystem.isLineSupported( info ) ) {
				shutDown( "Line matching " + info + " not supported." );
				return;
			}

			// get and open the target data line for capture.

			try {
				line = ( TargetDataLine ) AudioSystem.getLine( info );
				line.open( format, line.getBufferSize( ) );
			} catch ( LineUnavailableException ex ) {
				shutDown( "Unable to open the line: " + ex );
				return;
			} catch ( SecurityException ex ) {
				shutDown( ex.toString( ) );
				// JavaSound.showInfoDialog();
				return;
			} catch ( Exception ex ) {
				shutDown( ex.toString( ) );
				return;
			}

			// play back the captured audio data
			ByteArrayOutputStream out = new ByteArrayOutputStream( );
			int frameSizeInBytes = format.getFrameSize( );
			int bufferLengthInFrames = line.getBufferSize( ) / 8;
			int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			byte[] data = new byte[ bufferLengthInBytes ];
			int numBytesRead;

			line.start( );
			ByteArrayOutputStream out1 = out;
			
			List<Double> xList = new ArrayList<Double>();
			xList.add(0.0);
			List<Double> wList = new ArrayList<Double>(); //for word finding
			ByteArrayOutputStream out2 = new ByteArrayOutputStream();
			Vector< Line2D.Double > lines1 = new Vector< Line2D.Double >( );
			List<String> strArr = new ArrayList<String>();

			while ( thread != null ) {
				if ( ( numBytesRead = line.read( data, 0, bufferLengthInBytes ) ) == -1 ) {
					break;
				}
//				out.write( data, 0, numBytesRead );
				out1.write( data, 0, numBytesRead );
				
				audioBytes = out.toByteArray( );

				audioInputStream1 = new AudioInputStream( new ByteArrayInputStream( out1.toByteArray( ) ), 
						format, out1.toByteArray( ).length / frameSizeInBytes );
				
				//////////for detection of word breaks
				
				Dimension d = getSize( );
				int w = d.width;
				int h = d.height - 15;
				
				try {
					audioData = wd.extractFloatDataFromAudioInputStream( audioInputStream1 );
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				int frames_per_pixel = wd.getAudioBytes( ).length / wd.getFormat( ).getFrameSize( ) / w;
				byte my_byte = 0;
				double y_last = 0;
				int numChannels = wd.getFormat( ).getChannels( );
				for ( double x = 0; x < w && audioData != null; x++ ) {
					int idx = ( int ) ( frames_per_pixel * numChannels * x );
					if ( wd.getFormat( ).getSampleSizeInBits( ) == 8 ) {
						my_byte = ( byte ) audioData[ idx ];
					} else {
						my_byte = ( byte ) ( 128 * audioData[ idx ] / 32768 );
					}
					double y_new = ( double ) ( h * ( 128 - my_byte ) / 256 );
					lines1.add( new Line2D.Double( x, y_last, x, y_new ) );
					y_last = y_new;
				}
					
					for ( int i = 1; i < lines1.size( ); i++ ) {
//						if (lines1.get(lineValue).getY2() > 300)
//							System.out.println(lines1.get(i).getY2());
							wList.add(lines1.get(i).getY2());
//						}
					}
					
				//////////
				
				long milliseconds1 = ( long ) ( ( audioInputStream1.getFrameLength( ) * 1000 ) / format.getFrameRate( ) );
				double duration1 = milliseconds1 / 1000.0;

				double x = Math.floor(duration1);
				
				byte[] audioBytes1;
				
				out2.write(data, 0, numBytesRead);
				
//				if ( !wList.isEmpty() ) {
//					
//					for ( int k = 0; k < wList.size(); k++ ) {
//						
//					}
//					
//					wList.clear();
//				}
				
				if ( x % INTERVAL == 0 && x > 1 && !xList.contains(x) ) {
					
					xList.add(x);

					System.out.println( x );
					
					AudioFormat format1 = formatControls.getFormat( );
					int frameSizeInBytes1 = format1.getFrameSize( );
					
					try {
						out1.flush( );
						out1.close( );
					} catch ( IOException ex ) {
						reportStatus( "Error on inputstream  " + ex.getMessage( ), MessageType.ERROR );
					}

					audioBytes1 = out2.toByteArray( );
					
					ByteArrayInputStream bais1 = new ByteArrayInputStream( audioBytes1 );
					audioInputStream2 = new AudioInputStream( bais1, format, (audioBytes1.length / frameSizeInBytes1) );
					System.out.println("Audio Size : " + out2.size());
					try {
						audioData2 = wd.extractFloatDataFromAudioInputStream( audioInputStream2 );
						System.out.println( opr.hmmGetWordFromAmplitureArray( audioData2 ) );
						if (strArr.size() < 4) {
							strArr.add( opr.hmmGetWordFromAmplitureArray( audioData2 ) );
						} else {
							strArr.add( opr.hmmGetWordFromAmplitureArray( audioData2 ) );
							strArr.remove(0);
						}
						StringBuilder listString = new StringBuilder();
						for (String s : strArr)
							listString.append(s + " ");
							getStatusText().setText( listString.toString() );
					} catch (Exception e) {
						e.printStackTrace();
					}
					
					out2 = new ByteArrayOutputStream();
					lines1 = new Vector< Line2D.Double >( );
				}

			}

			// we reached the end of the stream. stop and close the line.
			line.stop( );
			line.close( );
			line = null;

			// stop and close the output stream
			try {
				out.flush( );
				out.close( );
			} catch ( IOException ex ) {
				reportStatus( "Error on inputstream  " + ex.getMessage( ), MessageType.ERROR );
			}

			// load bytes into the audio input stream for playback

			audioBytes = out.toByteArray( );
			System.out.println( out.size( ) );
			ByteArrayInputStream bais = new ByteArrayInputStream( audioBytes );
			audioInputStream = new AudioInputStream( bais, format, audioBytes.length / frameSizeInBytes );

			long milliseconds = ( long ) ( ( audioInputStream.getFrameLength( ) * 1000 ) / format.getFrameRate( ) );
			duration = milliseconds / 1000.0;

			try {
				audioInputStream.reset( );
			} catch ( Exception ex ) {
				reportStatus( "Error in reseting inputStream " + ex.getMessage( ), MessageType.ERROR );
			}
			try {
				if ( isDrawingRequired ) {
					samplingGraph.createWaveForm( audioBytes );
				}

			} catch ( Exception e ) {
				reportStatus( "Error in drawing waveform " + e.getMessage( ), MessageType.ERROR );
			}

		}
	} // End class Capture

	/**
	 * Render a WaveForm.
	 */
	class SamplingGraph extends JPanel implements Runnable {

		private static final long	serialVersionUID	= 1L;

		private Thread				thread;
		private Font				font10				= new Font( "serif", Font.PLAIN, 10 );
		private Font				font12				= new Font( "serif", Font.PLAIN, 12 );
		Color						jfcBlue				= Color.BLUE ;
		Color						pink				= Color.PINK;
		AudioFormat					format;

		public SamplingGraph( ) {
			setBackground( Color.WHITE );
		}

		/**
		 * Creates the wave form.
		 * 
		 * @param audioBytes
		 *            the audio bytes
		 * 
		 * @modified-by mert
		 */
		public void createWaveForm( byte[] audioBytes ) throws Exception {

			lines.removeAllElements( ); // clear the old vector

			Dimension d = getSize( );
			int w = d.width; 
			int h = d.height - 15;
			audioData = null;
			// wd.set
			audioData = wd.extractFloatDataFromAudioInputStream( audioInputStream );
			// ArrayWriter.printFloatArrayToConole(audioData);
			int frames_per_pixel = wd.getAudioBytes( ).length / wd.getFormat( ).getFrameSize( ) / w;
			byte my_byte = 0;
			double y_last = 0;
			// we need format object
			int numChannels = wd.getFormat( ).getChannels( );
			for ( double x = 0; x < w && audioData != null; x++ ) {
				int idx = ( int ) ( frames_per_pixel * numChannels * x );
				if ( wd.getFormat( ).getSampleSizeInBits( ) == 8 ) {
					my_byte = ( byte ) audioData[ idx ];
				} else {
					my_byte = ( byte ) ( 128 * audioData[ idx ] / 32768 );
				}
				double y_new = ( double ) ( h * ( 128 - my_byte ) / 256 );
				lines.add( new Line2D.Double( x, y_last, x, y_new ) );
				y_last = y_new;
			}
			// just need lines object to repaint()
			repaint( );
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see javax.swing.JComponent#paint(java.awt.Graphics)
		 */
		public void paint( Graphics g ) {

			Dimension d = getSize( );
			int w = d.width;
			int h = d.height;
			int INFOPAD = 15;

			Graphics2D g2 = ( Graphics2D ) g;
			g2.setBackground( getBackground( ) );
			g2.clearRect( 0, 0, w, h );
			g2.setColor( Color.BLACK );
			g2.fillRect( 0, h - INFOPAD, w, INFOPAD );

			if ( errStr != null ) {
				g2.setColor( jfcBlue );
				g2.setFont( new Font( "serif", Font.BOLD, 18 ) );
				g2.drawString( "ERROR", 5, 20 );
				AttributedString as = new AttributedString( errStr );
				as.addAttribute( TextAttribute.FONT, font12, 0, errStr.length( ) );
				AttributedCharacterIterator aci = as.getIterator( );
				FontRenderContext frc = g2.getFontRenderContext( );
				LineBreakMeasurer lbm = new LineBreakMeasurer( aci, frc );
				float x = 5, y = 25;
				lbm.setPosition( 0 );
				while ( lbm.getPosition( ) < errStr.length( ) ) {
					TextLayout tl = lbm.nextLayout( w - x - 5 );
					if ( !tl.isLeftToRight( ) ) {
						x = w - tl.getAdvance( );
					}
					tl.draw( g2, x, y += tl.getAscent( ) );
					y += tl.getDescent( ) + tl.getLeading( );
				}
			} else if ( capture.thread != null ) {
				// paint during capture
				g2.setColor( Color.WHITE );
				g2.setFont( font12 );
				g2.drawString( "Length: " + String.valueOf( seconds ), 3, h - 4 );
			} else {
				// paint during playback
				g2.setColor( Color.WHITE );
				g2.setFont( font12 );
				g2.drawString( "Length: " + String.valueOf( duration ) + "    Position: " + String.valueOf( seconds ), 3, h - 4 );

				if ( audioInputStream != null ) {
					// .. render sampling graph ..
					g2.setColor( jfcBlue );
					for ( int i = 1; i < lines.size( ); i++ ) {
//						if (Math.abs(lines.get(i).getY2() - lines.get(i).getY1()) > 90) //**90
							g2.draw( ( Line2D ) lines.get( i ) );
					}

					// .. draw current position ..
					if ( seconds != 0 ) {
						double loc = seconds / duration * w;
						g2.setColor( pink );
						g2.setStroke( new BasicStroke( 3 ) );
						g2.draw( new Line2D.Double( loc, 0, loc, h - INFOPAD - 2 ) );
					}
				}
			}
		}

		public void start( ) {
			thread = new Thread( this );
			thread.setName( "SamplingGraph" );
			thread.start( );
			seconds = 0;
		}

		public void stop( ) {
			if ( thread != null ) {
				thread.interrupt( );
			}
			thread = null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		public void run( ) {
			seconds = 0;
			while ( thread != null ) {
				if ( ( playback.line != null ) && ( playback.line.isOpen( ) ) ) {

					long milliseconds = ( long ) ( playback.line.getMicrosecondPosition( ) / 1000 );
					seconds = milliseconds / 1000.0;
				} else if ( ( capture.line != null ) && ( capture.line.isActive( ) ) ) {

					long milliseconds = ( long ) ( capture.line.getMicrosecondPosition( ) / 1000 );
					seconds = milliseconds / 1000.0;
				}

				try {
					thread.sleep( 100 );
				} catch ( Exception e ) {
					break;
				}

				repaint( );

				while ( ( capture.line != null && !capture.line.isActive( ) ) || ( playback.line != null && !playback.line.isOpen( ) ) ) {
					try {
						thread.sleep( 10 );
					} catch ( Exception e ) {
						break;
					}
				}
			}
			seconds = 0;
			repaint( );
		}
	} // End class SamplingGraph
	
	private JLabel getStatusText( ) {
		if ( text == null ) {
			text = new JLabel( "" );
			text.setHorizontalAlignment( SwingConstants.CENTER );
			text.setBounds( 225, 71, 189, 68 );
		}
		return text;
	}

	public static void main( String s[] ) {
		// boolean isDrawingRequired, boolean isSaveRequired
		JSoundCapture capturePlayback = new JSoundCapture( true, true );
		capturePlayback.setBounds(10, 10, 431, 74);
		JFrame f = new JFrame( "Capture/Playback/Save/Read for Speaker Data" );
		f.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		f.getContentPane( ).add( "Center", capturePlayback );
		f.pack( );
		Dimension screenSize = Toolkit.getDefaultToolkit( ).getScreenSize( );
		int w = 850;
		int h = 500;
		f.setLocation( screenSize.width / 2 - w / 2, screenSize.height / 2 - h / 2 );
		f.setSize( w, h );
		f.setResizable( false );
		f.setVisible( true );
	}
}

