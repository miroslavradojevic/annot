package critpoint;

import ij.*;
import ij.plugin.*;
import ij.gui.*;
import java.awt.event.*;

/**
 This plugin implements the KeyListener interface and listens
 for key events generated by the current image.
 */
public class Key_Listener implements PlugIn, KeyListener, ImageListener {
	ImageWindow win;
	ImageCanvas canvas;

	public void run(String arg) {

		ImagePlus img = IJ.getImage();
		win = img.getWindow();
		canvas = win.getCanvas();
		win.removeKeyListener(IJ.getInstance());
		canvas.removeKeyListener(IJ.getInstance());
		win.addKeyListener(this);
		canvas.addKeyListener(this);
		ImagePlus.addImageListener(this);
		IJ.log("addKeyListener");
	}

	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();
		char keyChar = e.getKeyChar();
		int flags = e.getModifiers();
		IJ.log("keyPressed: keyCode=" + keyCode + " (" + KeyEvent.getKeyText(keyCode) + ")");
		//IJ.getInstance().keyPressed(e); // hand off event to ImageJ
	}

	public void imageClosed(ImagePlus imp) {
		System.out.println("closing...");
		if (win!=null)
			win.removeKeyListener(this);
		if (canvas!=null)
			canvas.removeKeyListener(this);
		ImagePlus.removeImageListener(this);
		IJ.log("removeKeyListener");
	}

	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}
	public void imageOpened(ImagePlus imp) {}
	public void imageUpdated(ImagePlus imp) {}

}


