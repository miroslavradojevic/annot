package critpoint;

import ij.IJ;
import ij.plugin.PlugIn;

/**
 * Created by miroslav on 6/4/14.
 */
public class AnnotationerHelp implements PlugIn {

	public void run(String s) {

		String instructions = "";
		instructions += " 'u'        \t increase picker circle \n";
		instructions += " 'j'        \t decrease picker circle \n";
		instructions += " '+'        \t zoom in \n";
		instructions += " '-'        \t zoom out \n";
		instructions += " 'b' or '3' \t add bifurcation \n";
		instructions += " 'e' or '1' \t add endpoint \n";
		instructions += " 'c' or '4' \t add crosspoint \n";
		instructions += " 'n' or '0' \t add nonpoint (negative example - experimental) \n";
		instructions += " 'i' or '7' \t add ignore region (detections will be ignored there) \n";
		instructions += " 'd' \t delete detection \n";
		instructions += " 's' \t save and export annotations \n";

		IJ.showMessage("Help", instructions);
	}
}
