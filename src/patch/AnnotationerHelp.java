package patch;

import ij.IJ;
import ij.plugin.PlugIn;

/**
 * Created by miroslav on 18-2-15.
 */
public class AnnotationerHelp  implements PlugIn {
    public void run(String s) {
        String instructions = "";
        instructions += " 'n'        \t NEURON PATCHES\n";
        instructions += " 'a'        \t ASTROCYTE PATCHES\n";
        instructions += " 'b'        \t GENERATE RANDOM BACKGROUND PATCHES \n";
        instructions += " 'e'        \t EXPORT ANNOTATION RESULTS \n";
        instructions += " 'd'        \t DELETE PATCH ANNOTATION \n";
        instructions += " '+'        \t zoom in \n";
        instructions += " '-'        \t zoom out \n";

        IJ.showMessage("Help", instructions);

    }
}
